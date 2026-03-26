package com.autominuting.domain.repository

import com.autominuting.domain.model.MinutesFormat
import kotlinx.coroutines.flow.Flow

/**
 * 회의록 생성을 위한 Repository 인터페이스.
 * Phase 5에서 Gemini API(Firebase AI Logic SDK)로 구현 예정.
 *
 * 전사된 텍스트를 입력받아 구조화된 회의록을 생성하는 역할을 담당한다.
 */
interface MinutesRepository {

    /**
     * 전사 텍스트로부터 회의록을 생성한다.
     * @param transcriptText 전사된 회의 텍스트
     * @return 성공 시 생성된 회의록 텍스트, 실패 시 예외를 포함한 Result
     */
    suspend fun generateMinutes(
        transcriptText: String,
        format: MinutesFormat = MinutesFormat.STRUCTURED,
        customPrompt: String? = null
    ): Result<String>

    /** 현재 회의록 생성이 진행 중인지 여부를 관찰한다. */
    fun isGenerating(): Flow<Boolean>
}
