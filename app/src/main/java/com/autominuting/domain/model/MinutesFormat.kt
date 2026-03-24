package com.autominuting.domain.model

/**
 * 회의록 출력 형식을 나타내는 열거형.
 * GeminiEngine에서 형식별 프롬프트를 분기할 때 사용한다.
 */
enum class MinutesFormat {
    /** 구조화된 회의록 (기본값) — 개요, 안건, 결정사항, 액션아이템을 포함하는 전체 형식 */
    STRUCTURED,

    /** 핵심 내용 요약 — 3~5줄로 간결하게 요약된 형식 */
    SUMMARY,

    /** 액션 아이템 중심 — 결정 사항과 할 일 목록만 추출하는 형식 */
    ACTION_ITEMS
}
