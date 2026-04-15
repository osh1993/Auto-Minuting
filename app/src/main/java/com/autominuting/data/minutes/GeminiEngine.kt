package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.data.security.GeminiAllKeysFailedException
import com.autominuting.data.security.GeminiKeyRotator
import com.autominuting.data.security.SecureApiKeyRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.QuotaExceededException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API를 사용하여 전사 텍스트에서 구조화된 회의록을 생성하는 엔진.
 *
 * Google AI Client SDK(generativeai)를 사용하며, Firebase 없이 API 키만으로 동작한다.
 * 모델: gemini-2.5-flash (POC-04에서 검증된 모델)
 * MinutesEngine 인터페이스를 구현하여 API 키 모드 엔진으로 동작한다.
 * Phase 52: 다중 키 라운드로빈 순환 + 오류 자동 전환 (GEMINI-02, GEMINI-03)
 */
@Singleton
class GeminiEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val keyRotator: GeminiKeyRotator
) : MinutesEngine {

    companion object {
        private const val TAG = "GeminiEngine"
        private const val MODEL_NAME = "gemini-2.5-flash"

        /** 구조화된 회의록 프롬프트 (POC gemini-test.py에서 변환) — customPrompt null 시 기본 폴백 */
        private val STRUCTURED_PROMPT = """
            |당신은 전문 회의록 작성자입니다. 아래 회의 전사 텍스트를 읽고, 다음 형식에 맞춰 구조화된 회의록을 작성해주세요.
            |
            |## 출력 형식
            |
            |### 1. 회의 개요
            |- 날짜:
            |- 참석자:
            |- 회의 시간:
            |
            |### 2. 주요 안건 및 논의 내용
            |(안건별로 구분하여 핵심 논의 내용을 요약)
            |
            |### 3. 결정 사항
            |(번호를 매겨 명확하게 나열)
            |
            |### 4. 액션 아이템
            || 담당자 | 할 일 | 기한 |
            ||--------|--------|------|
            |(테이블 형식으로 정리)
            |
            |## 작성 지침
            |- 핵심 내용만 간결하게 요약한다
            |- 화자의 이름을 사용한다 (화자 A 등이 아닌 실제 이름)
            |- 결정 사항과 액션 아이템은 빠짐없이 포함한다
            |- 한국어로 작성한다
            |
            |---
            |
            |## 회의 전사 텍스트
            |
        """.trimMargin()
    }

    /**
     * 전사 텍스트를 Gemini API에 전달하여 구조화된 회의록을 생성한다.
     *
     * 등록된 키를 라운드로빈 순서로 순환하여 할당량 제한을 분산한다.
     * 429/403 오류 시 다음 키로 자동 전환하고, 모든 키 실패 시 GeminiAllKeysFailedException 반환.
     *
     * @param transcriptText 전사된 회의 텍스트
     * @param customPrompt 사용자 정의 프롬프트 (null이면 STRUCTURED_PROMPT 기본 사용)
     * @return 성공 시 Markdown 형식의 회의록, 실패 시 예외를 포함한 Result
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) {
            Log.e(TAG, "등록된 Gemini API 키가 없습니다")
            return Result.failure(IllegalStateException("등록된 Gemini API 키가 없습니다"))
        }

        val prompt = if (customPrompt != null) {
            customPrompt + "\n\n---\n\n## 회의 전사 텍스트\n\n" + transcriptText
        } else {
            STRUCTURED_PROMPT + transcriptText
        }

        var currentIndex = keyRotator.getCurrentIndex()
        var triedCount = 0

        while (triedCount < keys.size) {
            val apiKey = keys[currentIndex]
            val model = GenerativeModel(modelName = MODEL_NAME, apiKey = apiKey)
            try {
                Log.d(TAG, "Gemini API 호출 (키 index=$currentIndex): 전사 텍스트 ${transcriptText.length}자")
                val response = model.generateContent(prompt)
                val minutesText = response.text
                if (minutesText.isNullOrBlank()) {
                    Log.e(TAG, "Gemini API 빈 응답 (키 index=$currentIndex)")
                    return Result.failure(IllegalStateException("Gemini API가 빈 응답을 반환했습니다"))
                }
                Log.d(TAG, "회의록 생성 성공 (키 index=$currentIndex): ${minutesText.length}자")
                keyRotator.advance()
                return Result.success(minutesText)
            } catch (e: QuotaExceededException) {
                val label = secureApiKeyRepository.getGeminiApiKeys()
                    .getOrNull(currentIndex)?.label ?: "키 #$currentIndex"
                Log.w(TAG, "할당량 초과 (키 index=$currentIndex, label=$label): ${e.message}")
                val nextKey = keyRotator.rotateOnError(currentIndex)
                currentIndex = keyRotator.getCurrentIndex()
                triedCount++
                if (nextKey == null) break  // 단일 키 — 루프 탈출
            } catch (e: Exception) {
                val message = e.message ?: ""
                // HTTP 403 권한 오류 감지 (SDK가 메시지에 403 포함)
                if (message.contains("403") || message.contains("permission", ignoreCase = true)
                    || message.contains("forbidden", ignoreCase = true)) {
                    val label = secureApiKeyRepository.getGeminiApiKeys()
                        .getOrNull(currentIndex)?.label ?: "키 #$currentIndex"
                    Log.w(TAG, "권한 오류 (키 index=$currentIndex, label=$label): $message")
                    keyRotator.rotateOnError(currentIndex)
                    currentIndex = keyRotator.getCurrentIndex()
                    triedCount++
                } else {
                    Log.e(TAG, "Gemini API 호출 실패 (키 index=$currentIndex): $message", e)
                    return Result.failure(e)
                }
            }
        }

        Log.e(TAG, "등록된 모든 Gemini API 키(${keys.size}개)가 오류를 반환했습니다")
        return Result.failure(
            GeminiAllKeysFailedException(
                "등록된 모든 Gemini API 키(${keys.size}개)가 오류를 반환했습니다",
                triedKeyCount = keys.size
            )
        )
    }

    /** 엔진 이름을 반환한다 (로깅용). */
    override fun engineName(): String = "Gemini 2.5 Flash"

    /** 등록된 API 키가 1개 이상이면 사용 가능하다. */
    override fun isAvailable(): Boolean =
        secureApiKeyRepository.getAllGeminiApiKeyValues().isNotEmpty()
}
