package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.BuildConfig
import com.autominuting.data.security.SecureApiKeyRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.QuotaExceededException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API를 사용하여 전사 텍스트에서 구조화된 회의록을 생성하는 엔진.
 *
 * Google AI Client SDK(generativeai)를 사용하며, Firebase 없이 API 키만으로 동작한다.
 * 모델: gemini-2.5-flash (POC-04에서 검증된 모델)
 * MinutesEngine 인터페이스를 구현하여 API 키 모드 엔진으로 동작한다.
 */
@Singleton
class GeminiEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "GeminiEngine"
        private const val MODEL_NAME = "gemini-2.5-flash"

        /** 쿼터 초과 시 최대 재시도 횟수 */
        private const val MAX_QUOTA_RETRIES = 3

        /** retry-after 파싱 실패 시 기본 대기 시간 (ms) */
        private const val DEFAULT_RETRY_DELAY_MS = 60_000L

        /** 쿼터 초과 예외 메시지에서 retry-after 초를 파싱한다. */
        private fun parseRetryAfterMs(message: String?): Long {
            if (message == null) return DEFAULT_RETRY_DELAY_MS
            val match = Regex("""retry in ([0-9]+(?:\.[0-9]+)?)s""").find(message)
            val seconds = match?.groupValues?.get(1)?.toDoubleOrNull() ?: return DEFAULT_RETRY_DELAY_MS
            // 여유 5초 추가
            return ((seconds + 5.0) * 1000).toLong()
        }

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
     * @param transcriptText 전사된 회의 텍스트
     * @param customPrompt 사용자 정의 프롬프트 (null이면 STRUCTURED_PROMPT 기본 사용)
     * @return 성공 시 Markdown 형식의 회의록, 실패 시 예외를 포함한 Result
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        // 사용자 설정 API 키 우선, 없으면 BuildConfig 폴백
        val apiKey = secureApiKeyRepository.getGeminiApiKey()
            ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API 키가 설정되지 않았습니다")
            return Result.failure(
                IllegalStateException("Gemini API 키가 설정되지 않았습니다")
            )
        }

        val model = GenerativeModel(modelName = MODEL_NAME, apiKey = apiKey)
        val prompt = if (customPrompt != null) {
            customPrompt + "\n\n---\n\n## 회의 전사 텍스트\n\n" + transcriptText
        } else {
            STRUCTURED_PROMPT + transcriptText
        }

        var lastException: Exception? = null
        repeat(MAX_QUOTA_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Gemini API 호출 시작 (시도 ${attempt + 1}/$MAX_QUOTA_RETRIES): 전사 텍스트 ${transcriptText.length}자")
                val response = model.generateContent(prompt)
                val minutesText = response.text

                if (minutesText.isNullOrBlank()) {
                    Log.e(TAG, "Gemini API가 빈 응답을 반환했습니다")
                    return Result.failure(IllegalStateException("Gemini API가 빈 응답을 반환했습니다"))
                }

                Log.d(TAG, "회의록 생성 성공: ${minutesText.length}자")
                return Result.success(minutesText)
            } catch (e: QuotaExceededException) {
                lastException = e
                val delayMs = parseRetryAfterMs(e.message)
                val retryingSuffix = if (attempt < MAX_QUOTA_RETRIES - 1) ", ${delayMs / 1000}초 후 재시도" else ""
                Log.w(TAG, "Gemini API 쿼터 초과 (시도 ${attempt + 1}/$MAX_QUOTA_RETRIES)$retryingSuffix: ${e.message}")
                if (attempt < MAX_QUOTA_RETRIES - 1) {
                    delay(delayMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API 호출 실패: ${e.message}", e)
                return Result.failure(e)
            }
        }

        // 모든 재시도 소진
        Log.e(TAG, "Gemini API 쿼터 초과로 ${MAX_QUOTA_RETRIES}회 재시도 후 실패")
        return Result.failure(lastException ?: IllegalStateException("쿼터 초과 후 재시도 실패"))
    }

    /** 엔진 이름을 반환한다 (로깅용). */
    override fun engineName(): String = "Gemini 2.5 Flash"

    /** API 키가 설정되어 있으면 사용 가능하다. */
    override fun isAvailable(): Boolean {
        val apiKey = secureApiKeyRepository.getGeminiApiKey()
            ?: BuildConfig.GEMINI_API_KEY
        return apiKey.isNotBlank()
    }
}
