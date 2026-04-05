package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.data.auth.GoogleAuthRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OAuth 기반 Gemini REST API 회의록 생성 엔진.
 *
 * Google 계정으로 로그인한 사용자의 access token을 사용하여
 * Gemini REST API를 직접 호출한다. API 키가 필요하지 않다.
 *
 * Bearer 토큰은 BearerTokenInterceptor가 OkHttp 레벨에서 자동 첨부한다.
 */
@Singleton
class GeminiOAuthEngine @Inject constructor(
    @Named("oauth") private val geminiRestApiService: GeminiRestApiService,
    private val googleAuthRepository: GoogleAuthRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "GeminiOAuthEngine"
        private const val MODEL_NAME = "gemini-2.5-flash"
    }

    /**
     * 전사 텍스트를 Gemini REST API에 전달하여 회의록을 생성한다.
     *
     * @param transcriptText 전사된 회의 텍스트
     * @param customPrompt 사용자 정의 프롬프트 (null이면 STRUCTURED 기본 사용)
     * @return 성공 시 Markdown 형식의 회의록, 실패 시 예외를 포함한 Result
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        if (!isAvailable()) {
            return Result.failure(
                IllegalStateException("Google 계정으로 로그인되어 있지 않습니다")
            )
        }

        return try {
            Log.d(TAG, "Gemini OAuth API 호출 시작: 전사 텍스트 ${transcriptText.length}자")

            val prompt = if (customPrompt != null) {
                customPrompt + "\n\n---\n\n## 회의 전사 텍스트\n\n" + transcriptText
            } else {
                MinutesPrompts.STRUCTURED + transcriptText
            }

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                )
            )

            val response = geminiRestApiService.generateContent(
                model = MODEL_NAME,
                request = request
            )

            val minutesText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text

            if (minutesText.isNullOrBlank()) {
                Log.e(TAG, "Gemini OAuth API가 빈 응답을 반환했습니다")
                return Result.failure(
                    IllegalStateException("Gemini API가 빈 응답을 반환했습니다")
                )
            }

            Log.d(TAG, "회의록 생성 성공 (OAuth): ${minutesText.length}자")
            Result.success(minutesText)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini OAuth API 호출 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 엔진 이름을 반환한다 (로깅용). */
    override fun engineName(): String = "Gemini 2.5 Flash (OAuth)"

    /**
     * Google 계정 로그인 + access token 보유 시 사용 가능하다.
     * token이 없으면 API 호출이 401로 실패하므로 false를 반환한다.
     */
    override fun isAvailable(): Boolean =
        googleAuthRepository.isSignedIn() && !googleAuthRepository.getAccessToken().isNullOrBlank()
}
