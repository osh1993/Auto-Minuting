package com.autominuting.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 음성-텍스트 전사를 위한 Repository 인터페이스.
 * Phase 4에서 Whisper(whisper.cpp)로 구현 예정.
 *
 * 오디오 파일을 입력받아 한국어 텍스트로 전사하는 역할을 담당한다.
 */
interface TranscriptionRepository {

    /**
     * 오디오 파일을 텍스트로 전사한다.
     * @param audioFilePath 전사할 오디오 파일의 절대 경로
     * @param onProgress 전사 진행률 콜백 (0.0~1.0). Whisper 엔진만 실제 진행률 제공.
     * @return 성공 시 전사된 텍스트, 실패 시 예외를 포함한 Result
     */
    suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String>

    /** 현재 전사가 진행 중인지 여부를 관찰한다. */
    fun isTranscribing(): Flow<Boolean>
}
