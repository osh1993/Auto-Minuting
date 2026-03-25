package com.autominuting.data.minutes

import com.autominuting.domain.model.MinutesFormat

/**
 * 회의록 생성 엔진의 공통 인터페이스.
 *
 * API 키 모드(GeminiEngine)와 OAuth 모드(향후 추가)를 추상화하여
 * MinutesRepositoryImpl이 엔진 교체 없이 동작할 수 있도록 한다.
 *
 * Phase 8 D-09 결정에 따라 도입.
 */
interface MinutesEngine {

    /**
     * 전사 텍스트를 받아 구조화된 회의록을 생성한다.
     *
     * @param transcriptText 전사된 회의 텍스트
     * @param format 회의록 출력 형식 (기본값: STRUCTURED)
     * @return 성공 시 Markdown 형식의 회의록, 실패 시 예외를 포함한 Result
     */
    suspend fun generate(
        transcriptText: String,
        format: MinutesFormat = MinutesFormat.STRUCTURED
    ): Result<String>

    /** 엔진 이름을 반환한다 (로깅용). */
    fun engineName(): String

    /** 현재 엔진이 사용 가능한지 여부를 반환한다. */
    fun isAvailable(): Boolean
}
