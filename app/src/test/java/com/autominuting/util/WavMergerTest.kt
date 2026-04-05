package com.autominuting.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WavMerger 단위 테스트
 *
 * - 동일 포맷 WAV 합치기
 * - 헤더 ChunkSize 검증
 * - fmt 불일치 시 예외
 * - 비WAV 파일 시 예외
 * - 단일 파일 리스트 입력 시 그대로 복사
 */
class WavMergerTest {

    @TempDir
    lateinit var tempDir: File

    /**
     * 테스트용 WAV 바이트 생성 헬퍼.
     * 표준 44바이트 헤더 + PCM 데이터로 ByteArray를 생성한다.
     */
    private fun createTestWav(
        sampleRate: Int = 44100,
        numChannels: Short = 1,
        bitsPerSample: Short = 16,
        pcmData: ByteArray
    ): ByteArray {
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(chunkSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // SubChunk1Size (PCM)
        buffer.putShort(1) // AudioFormat = PCM
        buffer.putShort(numChannels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)
        return buffer.array()
    }

    @Test
    fun `동일 포맷 WAV 2개 합치기 - PCM 데이터 크기가 입력의 합`() {
        val pcm1 = ByteArray(1000) { (it % 256).toByte() }
        val pcm2 = ByteArray(2000) { ((it + 50) % 256).toByte() }
        val wav1 = createTestWav(pcmData = pcm1)
        val wav2 = createTestWav(pcmData = pcm2)

        val outputFile = File(tempDir, "merged.wav")
        val streams = listOf<InputStream>(
            ByteArrayInputStream(wav1),
            ByteArrayInputStream(wav2)
        )
        WavMerger.merge(streams, outputFile)

        // 출력 파일의 data 청크 크기 = 입력1 + 입력2
        val outputBytes = outputFile.readBytes()
        val outputBuffer = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outputDataSize = outputBuffer.getInt(40) // SubChunk2Size at offset 40
        assertEquals(pcm1.size + pcm2.size, outputDataSize)

        // PCM 데이터 내용 검증
        val outputPcm = outputBytes.copyOfRange(44, outputBytes.size)
        assertArrayEquals(pcm1 + pcm2, outputPcm)
    }

    @Test
    fun `합친 파일의 WAV 헤더 ChunkSize = 36 + 총 PCM 데이터 크기`() {
        val pcm1 = ByteArray(500) { 0x01 }
        val pcm2 = ByteArray(300) { 0x02 }
        val wav1 = createTestWav(pcmData = pcm1)
        val wav2 = createTestWav(pcmData = pcm2)

        val outputFile = File(tempDir, "merged.wav")
        WavMerger.merge(
            listOf(ByteArrayInputStream(wav1), ByteArrayInputStream(wav2)),
            outputFile
        )

        val outputBytes = outputFile.readBytes()
        val outputBuffer = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkSize = outputBuffer.getInt(4) // ChunkSize at offset 4
        val totalPcmSize = pcm1.size + pcm2.size
        assertEquals(36 + totalPcmSize, chunkSize)
    }

    @Test
    fun `fmt 불일치 (샘플레이트 다름) 시 IllegalArgumentException 발생`() {
        val pcm1 = ByteArray(100)
        val pcm2 = ByteArray(100)
        val wav1 = createTestWav(sampleRate = 44100, pcmData = pcm1)
        val wav2 = createTestWav(sampleRate = 22050, pcmData = pcm2)

        val outputFile = File(tempDir, "merged.wav")
        assertThrows(IllegalArgumentException::class.java) {
            WavMerger.merge(
                listOf(ByteArrayInputStream(wav1), ByteArrayInputStream(wav2)),
                outputFile
            )
        }
    }

    @Test
    fun `WAV가 아닌 파일 (RIFF 헤더 없음) 시 IllegalArgumentException 발생`() {
        val notWav = "This is not a WAV file at all".toByteArray()
        val outputFile = File(tempDir, "merged.wav")
        assertThrows(IllegalArgumentException::class.java) {
            WavMerger.merge(
                listOf(ByteArrayInputStream(notWav)),
                outputFile
            )
        }
    }

    @Test
    fun `단일 파일 리스트 입력 시 해당 파일 그대로 복사`() {
        val pcm = ByteArray(800) { (it % 128).toByte() }
        val wav = createTestWav(pcmData = pcm)

        val outputFile = File(tempDir, "single.wav")
        WavMerger.merge(listOf(ByteArrayInputStream(wav)), outputFile)

        val outputBytes = outputFile.readBytes()
        // 단일 파일이므로 출력이 입력과 동일해야 한다
        val outputBuffer = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outputDataSize = outputBuffer.getInt(40)
        assertEquals(pcm.size, outputDataSize)

        val outputPcm = outputBytes.copyOfRange(44, outputBytes.size)
        assertArrayEquals(pcm, outputPcm)
    }
}
