package com.autominuting.data.minutes

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 인터셉터 — Bearer 토큰을 Authorization 헤더에 추가한다.
 *
 * OAuth 인증 모드에서 Gemini REST API 호출 시 access token을 자동으로 첨부한다.
 * 토큰이 null이면 헤더 없이 요청을 전달한다 (서버에서 401 반환).
 *
 * @param tokenProvider access token을 반환하는 suspend 함수
 */
class BearerTokenInterceptor(
    private val tokenProvider: suspend () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = runBlocking { tokenProvider() }

        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
