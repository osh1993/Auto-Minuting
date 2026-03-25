package com.autominuting.data.minutes

/**
 * Gemini REST API 요청/응답 데이터 모델.
 *
 * Gson 직렬화 호환을 위해 필드명이 JSON 키와 동일하다.
 * OAuth 인증 모드에서 Gemini REST API를 직접 호출할 때 사용한다.
 */

/** Gemini REST API 콘텐츠 생성 요청 */
data class GenerateContentRequest(
    val contents: List<Content>
)

/** 대화 콘텐츠 (역할 + 파트 목록) */
data class Content(
    val parts: List<Part>,
    val role: String = "user"
)

/** 콘텐츠의 단일 파트 (텍스트) */
data class Part(
    val text: String
)

/** Gemini REST API 콘텐츠 생성 응답 */
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

/** 응답 후보 */
data class Candidate(
    val content: Content?
)
