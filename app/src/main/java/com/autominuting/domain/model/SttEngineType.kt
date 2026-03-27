package com.autominuting.domain.model

/**
 * STT 엔진 유형을 나타내는 열거형.
 * 사용자가 설정에서 선택하고, TranscriptionRepository에서 엔진 선택에 사용한다.
 */
enum class SttEngineType {
    /** Gemini 2.5 Flash 멀티모달 API (클라우드, 기본값) */
    GEMINI,
    /** Whisper (whisper.cpp 온디바이스) */
    WHISPER
}
