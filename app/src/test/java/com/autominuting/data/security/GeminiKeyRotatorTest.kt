package com.autominuting.data.security

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * GeminiKeyRotator 유닛 테스트 (GEMINI-02, GEMINI-03).
 *
 * SecureApiKeyRepository를 MockK로 목킹하여
 * EncryptedSharedPreferences 없이 순수 로직만 테스트한다.
 */
class GeminiKeyRotatorTest {

    private lateinit var mockRepo: SecureApiKeyRepository
    private lateinit var rotator: GeminiKeyRotator

    @BeforeEach
    fun setUp() {
        mockRepo = mockk()
        rotator = GeminiKeyRotator(mockRepo)
    }

    /**
     * GEMINI-02: 키 3개 등록 상태에서 인덱스 0의 키 반환 확인.
     */
    @Test
    fun `getCurrentKey 키3개 첫호출 인덱스0 반환`() {
        // Given
        val keys = listOf("key-a", "key-b", "key-c")
        every { mockRepo.getAllGeminiApiKeyValues() } returns keys
        every { mockRepo.getRoundRobinIndex() } returns 0

        // When
        val result = rotator.getCurrentKey()

        // Then
        assertEquals("key-a", result)
    }

    /**
     * GEMINI-02: advance() 호출 후 인덱스가 순환되는지 확인.
     */
    @Test
    fun `advance 호출 후 인덱스 순환`() {
        // Given
        val keys = listOf("key-a", "key-b", "key-c")
        every { mockRepo.getAllGeminiApiKeyValues() } returns keys
        every { mockRepo.getRoundRobinIndex() } returns 0
        every { mockRepo.saveRoundRobinIndex(any()) } just runs

        // When: advance() → 인덱스 1로 전진
        rotator.advance()

        // Then
        verify { mockRepo.saveRoundRobinIndex(1) }

        // 인덱스 2에서 advance() → 다시 0으로 순환 (wrapping)
        every { mockRepo.getRoundRobinIndex() } returns 2
        rotator.advance()
        verify { mockRepo.saveRoundRobinIndex(0) }
    }

    /**
     * GEMINI-02: 저장 인덱스가 키 목록 크기를 초과할 때 coerceIn 보정 확인.
     */
    @Test
    fun `인덱스 범위초과 시 coerceIn 보정`() {
        // Given: 키 2개인데 저장 인덱스가 5로 손상됨 (키 삭제 후 시나리오)
        val keys = listOf("key-a", "key-b")
        every { mockRepo.getAllGeminiApiKeyValues() } returns keys
        every { mockRepo.getRoundRobinIndex() } returns 5

        // When
        val result = rotator.getCurrentKey()

        // Then: coerceIn(0, 1)이므로 index 1의 키 반환
        assertEquals("key-b", result)
    }

    /**
     * GEMINI-03: rotateOnError(failedIndex=0) 시 다음 키(index 1) 반환 및 저장 확인.
     */
    @Test
    fun `rotateOnError 다음키 반환`() {
        // Given
        val keys = listOf("key-a", "key-b", "key-c")
        every { mockRepo.getAllGeminiApiKeyValues() } returns keys
        every { mockRepo.saveRoundRobinIndex(any()) } just runs

        // When
        val result = rotator.rotateOnError(failedKeyIndex = 0)

        // Then
        assertEquals("key-b", result)
        verify { mockRepo.saveRoundRobinIndex(1) }
    }

    /**
     * GEMINI-03: 단일 키일 때 rotateOnError() → null 반환 (전환 불가).
     */
    @Test
    fun `rotateOnError 단일키 null 반환`() {
        // Given: 키가 1개뿐
        val keys = listOf("key-only")
        every { mockRepo.getAllGeminiApiKeyValues() } returns keys

        // When
        val result = rotator.rotateOnError(failedKeyIndex = 0)

        // Then: 전환할 키 없으므로 null
        assertNull(result)
    }

    /**
     * GEMINI-02: 키 목록이 비어있을 때 getCurrentKey() → null 반환.
     */
    @Test
    fun `키목록비어있을때 getCurrentKey null`() {
        // Given
        every { mockRepo.getAllGeminiApiKeyValues() } returns emptyList()

        // When
        val result = rotator.getCurrentKey()

        // Then
        assertNull(result)
    }
}
