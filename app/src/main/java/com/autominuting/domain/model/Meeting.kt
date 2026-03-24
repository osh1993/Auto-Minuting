package com.autominuting.domain.model

import java.time.Instant

/**
 * 회의 정보를 나타내는 순수 도메인 모델.
 * Room 어노테이션 없이 도메인 레이어의 독립성을 유지한다.
 *
 * @property id 회의 고유 식별자
 * @property title 회의 제목
 * @property recordedAt 녹음 시각
 * @property audioFilePath 오디오 파일 경로
 * @property transcriptPath 전사 텍스트 파일 경로 (전사 완료 시 설정)
 * @property minutesPath 회의록 파일 경로 (회의록 생성 완료 시 설정)
 * @property pipelineStatus 현재 파이프라인 처리 상태
 * @property errorMessage 오류 발생 시 메시지
 * @property createdAt 레코드 생성 시각
 * @property updatedAt 레코드 최종 수정 시각
 */
data class Meeting(
    val id: Long = 0,
    val title: String,
    val recordedAt: Instant,
    val audioFilePath: String,
    val transcriptPath: String? = null,
    val minutesPath: String? = null,
    val pipelineStatus: PipelineStatus,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
