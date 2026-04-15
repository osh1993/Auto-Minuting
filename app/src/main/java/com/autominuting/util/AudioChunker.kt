package com.autominuting.util

import com.autominuting.data.stt.AudioConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 25MB 초과 오디오 파일을 Groq 전송 가능한 시간 기반 WAV 청크로 분할한다.
 * AudioConverter를 재사용하여 16kHz mono PCM으로 디코딩한 뒤,
 * 600초 청크 + 10초 오버랩으로 잘라 각 청크를 독립 WAV 파일로 작성한다.
 */
object AudioChunker {
    const val CHUNK_SECONDS = 600
    const val OVERLAP_SECONDS = 10
    const val TARGET_SAMPLE_RATE = 16_000
    const val TARGET_CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SECOND = TARGET_SAMPLE_RATE * TARGET_CHANNELS * (BITS_PER_SAMPLE / 8)

    /**
     * 입력 오디오 파일을 16kHz mono WAV 청크 리스트로 분할한다.
     *
     * @param inputPath 원본 오디오 파일 경로 (M4A/MP3/WAV 지원)
     * @param outputDir 청크 WAV 파일을 저장할 디렉토리
     * @param audioConverter AudioConverter 인스턴스 (PCM 디코딩/WAV 작성 재사용)
     * @return 생성된 WAV 청크 파일 리스트 (순서 보장)
     */
    suspend fun split(
        inputPath: String,
        outputDir: File,
        audioConverter: AudioConverter
    ): List<File> = withContext(Dispatchers.IO) {
        require(File(inputPath).exists()) { "입력 파일 없음: $inputPath" }
        outputDir.mkdirs()

        // 1. 16kHz mono PCM 디코딩 (AudioConverter 재사용)
        val pcm = audioConverter.decodeAudioToPcm(inputPath)

        // 2. 시간 기반 청크 분할
        val chunkByteArrays = splitPcmForTest(pcm, CHUNK_SECONDS, OVERLAP_SECONDS, BYTES_PER_SECOND)

        // 3. 각 청크를 독립 WAV 파일로 작성
        chunkByteArrays.mapIndexed { index, chunkPcm ->
            val chunkFile = File(outputDir, "chunk_${"%03d".format(index)}.wav")
            audioConverter.writeWavFile(chunkFile, chunkPcm)
            chunkFile
        }
    }

    /**
     * 순수 함수: PCM 바이트 배열을 시간 기반 청크로 분할한다 (단위 테스트용).
     *
     * - 입력이 단일 청크로 충분하면 오버랩 없이 그대로 반환
     * - 입력이 비어 있으면 빈 리스트 반환
     * - step = chunkSec - overlapSec (오버랩 구간 보장)
     *
     * @param pcm 16kHz mono PCM 바이트 배열
     * @param chunkSec 청크 길이(초), 기본값 600
     * @param overlapSec 오버랩 길이(초), 기본값 10
     * @param bytesPerSec 초당 바이트 수, 기본값 32_000 (16kHz * 1ch * 2byte)
     * @return PCM 청크 바이트 배열 리스트
     */
    internal fun splitPcmForTest(
        pcm: ByteArray,
        chunkSec: Int = CHUNK_SECONDS,
        overlapSec: Int = OVERLAP_SECONDS,
        bytesPerSec: Int = BYTES_PER_SECOND
    ): List<ByteArray> {
        if (pcm.isEmpty()) return emptyList()
        val chunkBytes = chunkSec * bytesPerSec
        val overlapBytes = overlapSec * bytesPerSec
        val stepBytes = chunkBytes - overlapBytes
        require(stepBytes > 0) { "overlapSec must be < chunkSec" }

        // 입력이 단일 청크로 충분하면 오버랩 생성 안 함
        if (pcm.size <= chunkBytes) return listOf(pcm)

        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + chunkBytes, pcm.size)
            result += pcm.copyOfRange(offset, end)
            if (end >= pcm.size) break
            offset += stepBytes
        }
        return result
    }
}
