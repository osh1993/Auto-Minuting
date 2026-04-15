package com.autominuting.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Mp3Merger 단위 테스트 (JUnit 5)
 *
 * - ID3v2 태그 스트립 정책 (첫 파일 유지, 이후 제거)
 * - ID3v1 트레일러 모두 제거
 * - 순수 frame concat 크기
 * - Frame header 불일치 실패
 * - 빈 리스트 실패
 * - Frame sync 없음 실패
 */
class Mp3MergerTest {

    @TempDir
    lateinit var tempDir: File

    // MPEG1 Layer3 128kbps 44100Hz Stereo frame header: FF FB 90 00
    private val frameHeader44100Stereo = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)

    // MPEG1 Layer3 128kbps 48000Hz Stereo frame header: FF FB 94 00 (sampleRateIdx=01)
    private val frameHeader48000Stereo = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x94.toByte(), 0x00)

    private fun fakeMp3Frames(header: ByteArray, payloadSize: Int): ByteArray {
        return header + ByteArray(payloadSize) { (it and 0xFF).toByte() }
    }

    private fun id3v2Tag(payloadSize: Int): ByteArray {
        // "ID3" + version(2) + flags(1) + synchsafe size(4) + payload
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x03, 0x00, 0x00,
            ((payloadSize shr 21) and 0x7F).toByte(),
            ((payloadSize shr 14) and 0x7F).toByte(),
            ((payloadSize shr 7) and 0x7F).toByte(),
            (payloadSize and 0x7F).toByte()
        )
        return header + ByteArray(payloadSize) { 0x41 }  // 'A'
    }

    private fun id3v1Tag(): ByteArray {
        return byteArrayOf('T'.code.toByte(), 'A'.code.toByte(), 'G'.code.toByte()) + ByteArray(125) { 0x20 }
    }

    private fun writeFile(name: String, bytes: ByteArray): String {
        val f = File(tempDir, name)
        f.writeBytes(bytes)
        return f.absolutePath
    }

    @Test
    fun `첫 파일의 ID3v2 태그는 유지되고 두 번째 파일의 ID3v2는 제거된다`() {
        val framesA = fakeMp3Frames(frameHeader44100Stereo, 100)
        val fileA = writeFile("a.mp3", id3v2Tag(10) + framesA)
        val fileB = writeFile("b.mp3", id3v2Tag(10) + framesA)
        val out = File(tempDir, "out.mp3")

        Mp3Merger.merge(listOf(fileA, fileB), out)

        // 첫 파일 전체(ID3v2 20 + frames 104) + 두 번째 파일 frames만(104)
        assertEquals((10 + 10 + 104) + 104, out.length().toInt())
    }

    @Test
    fun `모든 파일의 ID3v1 트레일러는 제거된다`() {
        val framesA = fakeMp3Frames(frameHeader44100Stereo, 200)
        val fileA = writeFile("a.mp3", framesA + id3v1Tag())
        val fileB = writeFile("b.mp3", framesA + id3v1Tag())
        val out = File(tempDir, "out.mp3")

        Mp3Merger.merge(listOf(fileA, fileB), out)

        // frames_A(204) + frames_A(204), ID3v1(128) 2개 모두 제거
        assertEquals(204 + 204, out.length().toInt())
    }

    @Test
    fun `태그가 없는 순수 frame 파일들의 concat 크기는 입력 합과 같다`() {
        val framesA = fakeMp3Frames(frameHeader44100Stereo, 300)
        val fileA = writeFile("a.mp3", framesA)
        val fileB = writeFile("b.mp3", framesA)
        val out = File(tempDir, "out.mp3")

        Mp3Merger.merge(listOf(fileA, fileB), out)

        assertEquals(304 + 304, out.length().toInt())
    }

    @Test
    fun `sampleRate가 다른 파일들은 IllegalArgumentException`() {
        val fileA = writeFile("a.mp3", fakeMp3Frames(frameHeader44100Stereo, 100))
        val fileB = writeFile("b.mp3", fakeMp3Frames(frameHeader48000Stereo, 100))
        val out = File(tempDir, "out.mp3")

        assertThrows(IllegalArgumentException::class.java) {
            Mp3Merger.merge(listOf(fileA, fileB), out)
        }
    }

    @Test
    fun `빈 리스트는 IllegalArgumentException`() {
        val out = File(tempDir, "out.mp3")
        assertThrows(IllegalArgumentException::class.java) {
            Mp3Merger.merge(emptyList(), out)
        }
    }

    @Test
    fun `frame sync가 없는 파일은 IllegalArgumentException`() {
        val garbage = ByteArray(100) { 0x00 }
        val fileA = writeFile("a.mp3", garbage)
        val out = File(tempDir, "out.mp3")
        assertThrows(IllegalArgumentException::class.java) {
            Mp3Merger.merge(listOf(fileA), out)
        }
    }
}
