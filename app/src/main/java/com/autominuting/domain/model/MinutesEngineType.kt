package com.autominuting.domain.model

/**
 * 회의록 생성 엔진 유형.
 * SttEngineType과 동일한 패턴으로, UserPreferencesRepository DataStore에 저장된다.
 */
enum class MinutesEngineType {
    /** Gemini 2.5 Flash (기본값) */
    GEMINI,
    /** Deepgram Text Intelligence (영어만 지원) */
    DEEPGRAM,
    /** Naver CLOVA Summary (한국어 네이티브) */
    NAVER_CLOVA
}
