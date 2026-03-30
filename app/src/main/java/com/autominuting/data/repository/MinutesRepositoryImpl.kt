package com.autominuting.data.repository

import android.content.Context
import android.util.Log
import com.autominuting.data.minutes.MinutesEngine
import com.autominuting.data.quota.GeminiQuotaTracker
import com.autominuting.data.quota.QuotaCategory
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.autominuting.domain.repository.MinutesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MinutesRepository]의 구현체.
 *
 * Gemini API(1차)로 전사 텍스트를 구조화된 회의록으로 변환한다.
 * TranscriptionRepositoryImpl의 이중 경로 폴백 패턴과 동일한 구조 — per D-08.
 * 현재는 Gemini API만 1차 경로로 구현. NotebookLM MCP는 Phase 6 이후에서 폴백으로 추가 가능.
 */
@Singleton
class MinutesRepositoryImpl @Inject constructor(
    private val minutesEngine: MinutesEngine,
    @ApplicationContext private val context: Context,
    private val quotaTracker: GeminiQuotaTracker
) : MinutesRepository {

    companion object {
        private const val TAG = "MinutesRepo"
        /** 회의록 저장 디렉토리 */
        private const val MINUTES_DIR = "minutes"
    }

    /** 현재 회의록 생성 진행 중 여부 */
    private val _isGenerating = MutableStateFlow(false)

    /**
     * 전사 텍스트로부터 회의록을 생성한다.
     *
     * 1차: Gemini API를 통한 구조화된 회의록 생성
     * (향후 2차 폴백: NotebookLM MCP 서버)
     *
     * @param transcriptText 전사된 회의 텍스트
     * @return 성공 시 생성된 회의록 Markdown 텍스트, 실패 시 예외를 포함한 Result
     */
    override suspend fun generateMinutes(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> =
        withContext(Dispatchers.IO) {
            _isGenerating.value = true
            try {
                Log.d(TAG, "회의록 생성 시작: 전사 텍스트 ${transcriptText.length}자")

                // 1차: Gemini 엔진 시도
                val result = try {
                    Log.d(TAG, "1차 경로: ${minutesEngine.engineName()} 시도")
                    minutesEngine.generate(transcriptText, customPrompt)
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini 회의록 생성 예외: ${e.message}")
                    Result.failure(e)
                }

                if (result.isSuccess) {
                    val minutesText = result.getOrThrow()
                    Log.d(TAG, "회의록 생성 성공: ${minutesText.length}자")
                    // 쿼터 사용량 기록 (성공한 호출만 카운트)
                    quotaTracker.recordUsage(QuotaCategory.MINUTES)
                    return@withContext Result.success(minutesText)
                }

                // 현재 폴백 없음 — 향후 NotebookLM MCP 추가 예정
                val error = result.exceptionOrNull()
                Log.e(TAG, "회의록 생성 실패: ${error?.message}")

                // 쿼터 초과는 래핑하지 않고 그대로 전달 — Worker에서 Result.retry() 처리
                if (error is QuotaExceededException) {
                    return@withContext Result.failure(error)
                }

                Result.failure(
                    MinutesGenerationException(
                        "회의록 생성 실패 — Gemini: ${error?.message}",
                        error
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }

    /** 현재 회의록 생성이 진행 중인지 여부를 관찰한다. */
    override fun isGenerating(): Flow<Boolean> = _isGenerating.asStateFlow()

    /**
     * 생성된 회의록을 파일로 저장한다.
     *
     * 파일명에 타임스탬프를 포함하여, 재생성 시에도 이전 파일을 덮어쓰지 않도록 한다.
     * 형식: minutes/{meetingId}_{epochMillis}.md
     *
     * @param meetingId 회의 ID
     * @param minutesText 생성된 회의록 텍스트
     * @return 저장된 파일의 절대 경로
     */
    fun saveMinutesToFile(meetingId: Long, minutesText: String): String {
        val minutesDir = File(context.filesDir, MINUTES_DIR)
        minutesDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val file = File(minutesDir, "${meetingId}_${timestamp}.md")
        file.writeText(minutesText)
        Log.d(TAG, "회의록 저장 완료: ${file.absolutePath} (${minutesText.length}자)")
        return file.absolutePath
    }
}

/** 회의록 생성 과정에서 발생하는 통합 예외 */
class MinutesGenerationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
