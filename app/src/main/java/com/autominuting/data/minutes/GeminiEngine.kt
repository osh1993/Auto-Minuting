package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.BuildConfig
import com.autominuting.domain.model.MinutesFormat
import com.google.ai.client.generativeai.GenerativeModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API를 사용하여 전사 텍스트에서 구조화된 회의록을 생성하는 엔진.
 *
 * Google AI Client SDK(generativeai)를 사용하며, Firebase 없이 API 키만으로 동작한다.
 * 모델: gemini-2.5-flash (POC-04에서 검증된 모델)
 */
@Singleton
class GeminiEngine @Inject constructor() {

    companion object {
        private const val TAG = "GeminiEngine"
        private const val MODEL_NAME = "gemini-2.5-flash"

        /** 구조화된 회의록 프롬프트 (POC gemini-test.py에서 변환) */
        private val STRUCTURED_PROMPT = """
            |당신은 전문 회의록 작성자입니다. 아래 회의 전사 텍스트를 읽고, 다음 형식에 맞춰 구조화된 회의록을 작성해주세요.
            |
            |## 출력 형식
            |
            |### 1. 회의 개요
            |- 날짜:
            |- 참석자:
            |- 회의 시간:
            |
            |### 2. 주요 안건 및 논의 내용
            |(안건별로 구분하여 핵심 논의 내용을 요약)
            |
            |### 3. 결정 사항
            |(번호를 매겨 명확하게 나열)
            |
            |### 4. 액션 아이템
            || 담당자 | 할 일 | 기한 |
            ||--------|--------|------|
            |(테이블 형식으로 정리)
            |
            |## 작성 지침
            |- 핵심 내용만 간결하게 요약한다
            |- 화자의 이름을 사용한다 (화자 A 등이 아닌 실제 이름)
            |- 결정 사항과 액션 아이템은 빠짐없이 포함한다
            |- 한국어로 작성한다
            |
            |---
            |
            |## 회의 전사 텍스트
            |
        """.trimMargin()

        /** 핵심 내용 요약 프롬프트 */
        private val SUMMARY_PROMPT = """
            |당신은 전문 회의록 작성자입니다. 아래 회의 전사 텍스트를 읽고, 핵심 내용을 3~5줄로 간결하게 요약해주세요.
            |
            |## 작성 지침
            |- 가장 중요한 결정 사항과 논의 결과를 중심으로 요약한다
            |- 불필요한 세부사항은 제외한다
            |- 한국어로 작성한다
            |
            |---
            |
            |## 회의 전사 텍스트
            |
        """.trimMargin()

        /** 액션 아이템 중심 프롬프트 */
        private val ACTION_ITEMS_PROMPT = """
            |당신은 전문 회의록 작성자입니다. 아래 회의 전사 텍스트에서 결정 사항과 액션 아이템만 추출해주세요.
            |
            |## 출력 형식
            |
            |### 결정 사항
            |(번호를 매겨 명확하게 나열)
            |
            |### 액션 아이템
            || 담당자 | 할 일 | 기한 |
            ||--------|--------|------|
            |(테이블 형식으로 정리)
            |
            |## 작성 지침
            |- 결정 사항과 할 일만 포함한다. 논의 과정은 제외한다
            |- 담당자가 불분명하면 "미정"으로 표시한다
            |- 한국어로 작성한다
            |
            |---
            |
            |## 회의 전사 텍스트
            |
        """.trimMargin()
    }

    /**
     * 전사 텍스트를 Gemini API에 전달하여 구조화된 회의록을 생성한다.
     *
     * @param transcriptText 전사된 회의 텍스트
     * @param format 회의록 출력 형식 (기본값: STRUCTURED)
     * @return 성공 시 Markdown 형식의 회의록, 실패 시 예외를 포함한 Result
     */
    suspend fun generate(
        transcriptText: String,
        format: MinutesFormat = MinutesFormat.STRUCTURED
    ): Result<String> {
        // API 키 유효성 검사
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API 키가 설정되지 않았습니다")
            return Result.failure(
                IllegalStateException("Gemini API 키가 설정되지 않았습니다")
            )
        }

        return try {
            Log.d(TAG, "Gemini API 호출 시작: 전사 텍스트 ${transcriptText.length}자")

            val model = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )

            val prompt = when (format) {
                MinutesFormat.STRUCTURED -> STRUCTURED_PROMPT
                MinutesFormat.SUMMARY -> SUMMARY_PROMPT
                MinutesFormat.ACTION_ITEMS -> ACTION_ITEMS_PROMPT
            } + transcriptText
            val response = model.generateContent(prompt)
            val minutesText = response.text

            if (minutesText.isNullOrBlank()) {
                Log.e(TAG, "Gemini API가 빈 응답을 반환했습니다")
                return Result.failure(
                    IllegalStateException("Gemini API가 빈 응답을 반환했습니다")
                )
            }

            Log.d(TAG, "회의록 생성 성공: ${minutesText.length}자")
            Result.success(minutesText)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API 호출 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 엔진 이름을 반환한다 (로깅용). */
    fun engineName(): String = "Gemini 2.5 Flash"
}
