package com.autominuting.data.stt

/**
 * STT 엔진의 공통 인터페이스.
 *
 * Whisper(1차)와 ML Kit(2차) 등 다양한 STT 엔진이
 * 이 인터페이스를 구현하여 동일한 방식으로 전사를 수행한다.
 */
interface SttEngine {

    /**
     * 오디오 파일을 텍스트로 전사한다.
     * @param audioFilePath 전사할 오디오 파일의 절대 경로
     * @return 성공 시 전사된 텍스트, 실패 시 예외를 포함한 Result
     */
    suspend fun transcribe(audioFilePath: String): Result<String>

    /**
     * 현재 엔진이 사용 가능한 상태인지 확인한다.
     * (네이티브 라이브러리 로드 여부, 모델 파일 존재 여부 등)
     */
    suspend fun isAvailable(): Boolean

    /**
     * 엔진 이름을 반환한다. (로깅/디버깅용)
     */
    fun engineName(): String
}
