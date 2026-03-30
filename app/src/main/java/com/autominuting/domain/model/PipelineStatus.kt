package com.autominuting.domain.model

/**
 * 회의 파이프라인의 처리 상태를 나타내는 열거형.
 * 오디오 수신부터 회의록 생성까지 각 단계의 상태를 추적한다.
 */
enum class PipelineStatus {
    /** 오디오 파일이 수신되어 로컬에 저장된 상태 */
    AUDIO_RECEIVED,

    /** 음성-텍스트 전사가 진행 중인 상태 */
    TRANSCRIBING,

    /** 전사가 완료되어 텍스트가 저장된 상태 */
    TRANSCRIBED,

    /** 회의록 생성이 진행 중인 상태 */
    GENERATING_MINUTES,

    /** 회의록 생성이 완료된 최종 상태 */
    COMPLETED,

    /** 파이프라인 처리 중 오류가 발생한 상태 */
    FAILED,

    /** 전사/오디오 파일은 삭제됐지만 회의록은 보존된 상태 */
    MINUTES_ONLY
}
