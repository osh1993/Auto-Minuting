package com.autominuting.data.security

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API 키 라운드로빈 순환과 오류 자동 전환을 담당하는 컴포넌트.
 *
 * GeminiEngine과 GeminiSttEngine 양쪽에서 재사용된다.
 * 인덱스는 EncryptedSharedPreferences에 동기 저장하여 Worker 컨텍스트에서 suspend 없이 사용 가능.
 */
@Singleton
class GeminiKeyRotator @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) {
    companion object {
        private const val TAG = "GeminiKeyRotator"
    }

    /**
     * 현재 라운드로빈 인덱스의 API 키를 반환한다.
     * 키 목록이 비어있으면 null 반환.
     * 인덱스가 범위를 벗어나면 coerceIn으로 보정 (키 삭제 후 방어).
     */
    fun getCurrentKey(): String? {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return null
        val idx = secureApiKeyRepository.getRoundRobinIndex().coerceIn(0, keys.size - 1)
        return keys[idx]
    }

    /**
     * 현재 인덱스를 반환한다. 범위 보정 적용.
     */
    fun getCurrentIndex(): Int {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return 0
        return secureApiKeyRepository.getRoundRobinIndex().coerceIn(0, keys.size - 1)
    }

    /**
     * 지정 인덱스의 키가 오류를 반환했을 때 다음 키로 전환한다.
     * 전환 결과 다시 같은 인덱스로 돌아오면 (단일 키) null 반환.
     *
     * @param failedKeyIndex 오류 발생 키의 인덱스
     * @return 다음 키 값. 단일 키이거나 다음 키가 없으면 null.
     */
    fun rotateOnError(failedKeyIndex: Int): String? {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.size <= 1) {
            // 단일 키 — 전환할 키 없음
            Log.w(TAG, "키 오류 발생 (index=$failedKeyIndex), 전환 가능한 다른 키 없음")
            return null
        }
        val nextIdx = (failedKeyIndex + 1) % keys.size
        secureApiKeyRepository.saveRoundRobinIndex(nextIdx)
        Log.i(TAG, "키 전환: $failedKeyIndex → $nextIdx (등록 키 ${keys.size}개)")
        return keys[nextIdx]
    }

    /**
     * 성공 시 인덱스를 다음으로 전진한다 (라운드로빈 사전 순환).
     * 호출 빈도: 각 Gemini API 성공 호출 후 1회.
     */
    fun advance() {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return
        val nextIdx = (secureApiKeyRepository.getRoundRobinIndex() + 1) % keys.size
        secureApiKeyRepository.saveRoundRobinIndex(nextIdx)
        Log.d(TAG, "라운드로빈 전진: → $nextIdx")
    }
}

/** 등록된 모든 Gemini API 키가 오류를 반환했을 때 발생하는 예외 (GEMINI-03). */
class GeminiAllKeysFailedException(
    message: String,
    val triedKeyCount: Int = 0
) : Exception(message)
