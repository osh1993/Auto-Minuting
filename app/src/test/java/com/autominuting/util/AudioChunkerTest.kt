package com.autominuting.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AudioChunker.splitPcmForTest() 순수 함수 단위 테스트.
 * 실제 오디오 디코딩(MediaCodec)은 Android 환경이 필요하므로 여기서 테스트하지 않고,
 * 시간 기반 분할 로직 자체를 PCM 배열로 검증한다.
 */
class AudioChunkerTest {

    private val bytesPerSec = AudioChunker.BYTES_PER_SECOND  // 32_000

    @Test
    fun `600초 청크는 25MB 제한 안에 들어간다`() {
        val chunkBytes = AudioChunker.CHUNK_SECONDS * bytesPerSec
        val wavHeader = 44
        val total = chunkBytes + wavHeader
        assertTrue(total < 25 * 1024 * 1024, "청크 크기 $total bytes가 25MB 초과")
    }

    @Test
    fun `BYTES_PER_SECOND 상수가 16kHz mono 16bit 값과 일치한다`() {
        assertEquals(32_000, AudioChunker.BYTES_PER_SECOND)
    }

    @Test
    fun `빈 PCM 입력은 빈 리스트를 반환한다`() {
        val result = AudioChunker.splitPcmForTest(ByteArray(0))
        assertEquals(0, result.size)
    }

    @Test
    fun `300초 입력은 1청크로 반환된다 (오버랩 분할 없음)`() {
        val pcm = ByteArray(300 * bytesPerSec)
        val result = AudioChunker.splitPcmForTest(pcm)
        assertEquals(1, result.size)
        assertEquals(pcm.size, result[0].size)
    }

    @Test
    fun `600초 정확히 입력은 1청크로 반환된다`() {
        val pcm = ByteArray(600 * bytesPerSec)
        val result = AudioChunker.splitPcmForTest(pcm)
        assertEquals(1, result.size)
        assertEquals(pcm.size, result[0].size)
    }

    @Test
    fun `1800초 입력은 4청크로 분할된다 (오버랩 10초)`() {
        // step = 590s
        // 청크 1: offset=0,    end=600s (600초)
        // 청크 2: offset=590s, end=1190s (600초)
        // 청크 3: offset=1180s, end=1780s (600초)
        // 청크 4: offset=1770s, end=1800s (30초) — 마지막 잔여 청크
        val pcm = ByteArray(1800 * bytesPerSec)
        val result = AudioChunker.splitPcmForTest(pcm)
        assertEquals(4, result.size)
        assertEquals(600 * bytesPerSec, result[0].size)
        assertEquals(600 * bytesPerSec, result[1].size)
        assertEquals(600 * bytesPerSec, result[2].size)
        // 마지막 청크: 1800 - 1770 = 30초
        assertEquals(30 * bytesPerSec, result[3].size)
    }

    @Test
    fun `오버랩 10초가 정확히 320_000 bytes다`() {
        val overlapBytes = AudioChunker.OVERLAP_SECONDS * bytesPerSec
        assertEquals(320_000, overlapBytes)
    }

    @Test
    fun `청크 간 step은 590초(18_880_000 bytes)다`() {
        val stepBytes =
            (AudioChunker.CHUNK_SECONDS - AudioChunker.OVERLAP_SECONDS) * bytesPerSec
        assertEquals(18_880_000, stepBytes)
    }

    @Test
    fun `900초 입력은 2청크로 분할된다`() {
        // step = 590s, 첫 청크 = 0..600, 두 번째 청크 offset = 590, end = min(1190, 900) = 900
        val pcm = ByteArray(900 * bytesPerSec)
        val result = AudioChunker.splitPcmForTest(pcm)
        assertEquals(2, result.size)
        assertEquals(600 * bytesPerSec, result[0].size)
        assertEquals((900 - 590) * bytesPerSec, result[1].size)
    }

    @Test
    fun `overlapSec이 chunkSec보다 크거나 같으면 예외`() {
        val pcm = ByteArray(100)
        assertThrows(IllegalArgumentException::class.java) {
            AudioChunker.splitPcmForTest(pcm, chunkSec = 10, overlapSec = 10, bytesPerSec = 1)
        }
    }
}
