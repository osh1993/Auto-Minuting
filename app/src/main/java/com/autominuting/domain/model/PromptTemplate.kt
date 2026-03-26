package com.autominuting.domain.model

import java.time.Instant

/**
 * 프롬프트 템플릿 도메인 모델.
 * 회의록 생성에 사용할 사용자 정의 프롬프트를 나타낸다.
 *
 * @property isBuiltIn true이면 기본 제공 템플릿으로 삭제 불가
 */
data class PromptTemplate(
    val id: Long = 0,
    val name: String,
    val promptText: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
