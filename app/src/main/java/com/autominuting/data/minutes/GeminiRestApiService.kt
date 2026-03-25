package com.autominuting.data.minutes

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit 기반 Gemini REST API 호출 인터페이스.
 *
 * OAuth 인증 모드에서 액세스 토큰을 사용하여 Gemini API를 직접 호출할 때 사용한다.
 * baseUrl: https://generativelanguage.googleapis.com/
 */
interface GeminiRestApiService {

    /**
     * Gemini 모델에 콘텐츠 생성을 요청한다.
     *
     * @param model 사용할 모델명 (기본값: gemini-2.5-flash)
     * @param request 생성 요청 본문
     * @return 생성된 콘텐츠 응답
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-2.5-flash",
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}
