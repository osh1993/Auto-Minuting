package com.autominuting.domain.model

/**
 * 파이프라인 자동화 수준을 나타내는 열거형.
 * 사용자가 전사 후 회의록 생성을 자동으로 진행할지, 수동 확인 후 진행할지 선택한다.
 */
enum class AutomationMode {
    /** 완전 자동 모드 -- 파이프라인 전체 자동 진행 (기본값) */
    FULL_AUTO,

    /** 하이브리드 모드 -- 전사 완료 후 사용자 확인을 거쳐 회의록 생성 */
    HYBRID
}
