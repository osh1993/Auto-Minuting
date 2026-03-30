package com.autominuting.domain.model

import java.time.Instant

/**
 * 회의록 정보를 나타내는 순수 도메인 모델.
 * Room 어노테이션 없이 도메인 레이어의 독립성을 유지한다.
 *
 * @property id 회의록 고유 식별자
 * @property meetingId 연결된 Meeting ID (Meeting 삭제 시 null)
 * @property minutesPath 회의록 파일 경로
 * @property minutesTitle 자동 추출 제목
 * @property templateId 생성에 사용된 프롬프트 템플릿 ID
 * @property createdAt 생성 시각
 * @property updatedAt 최종 수정 시각
 */
data class Minutes(
    val id: Long = 0,
    val meetingId: Long?,
    val minutesPath: String,
    val minutesTitle: String?,
    val templateId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
)
