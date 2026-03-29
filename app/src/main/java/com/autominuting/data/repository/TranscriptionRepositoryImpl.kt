package com.autominuting.data.repository

import android.content.Context
import android.util.Log
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.stt.GeminiSttEngine
import com.autominuting.data.stt.SttEngine
import com.autominuting.data.stt.WhisperEngine
import com.autominuting.domain.model.SttEngineType
import com.autominuting.domain.repository.TranscriptionRepository
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
 * [TranscriptionRepository]의 구현체.
 *
 * 사용자 설정에 따라 STT 엔진을 선택하고, 실패 시 다른 엔진으로 폴백한다.
 * - GEMINI 선택: Gemini STT(1차) → Whisper(2차 폴백)
 * - WHISPER 선택: Whisper(1차) → Gemini STT(2차 폴백)
 */
@Singleton
class TranscriptionRepositoryImpl @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val geminiSttEngine: GeminiSttEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : TranscriptionRepository {

    companion object {
        private const val TAG = "TranscriptionRepo"
        /** 전사 결과 저장 디렉토리 */
        private const val TRANSCRIPTS_DIR = "transcripts"
    }

    /** 현재 전사 진행 중 여부 */
    private val _isTranscribing = MutableStateFlow(false)

    /**
     * 오디오 파일을 텍스트로 전사한다.
     *
     * 사용자가 설정한 STT 엔진을 1차로 시도하고, 실패 시 다른 엔진으로 폴백한다.
     *
     * @param audioFilePath 전사할 오디오 파일의 절대 경로
     * @return 성공 시 전사된 텍스트, 실패 시 예외를 포함한 Result
     */
    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> =
        withContext(Dispatchers.IO) {
            _isTranscribing.value = true
            try {
                val selectedEngine = userPreferencesRepository.getSttEngineTypeOnce()
                Log.d(TAG, "전사 파이프라인 시작: $audioFilePath (선택 엔진: $selectedEngine)")

                // 선택된 엔진으로만 전사 (폴백 없음 — 사용자 설정 존중)
                val engine: SttEngine = when (selectedEngine) {
                    SttEngineType.GEMINI -> geminiSttEngine
                    SttEngineType.WHISPER -> whisperEngine
                }

                val result = tryEngine(engine, audioFilePath, onProgress)
                if (result.isSuccess) return@withContext result

                val errorMessage = "전사 실패 — ${engine.engineName()}: ${result.exceptionOrNull()?.message}"
                Log.e(TAG, errorMessage)

                Result.failure(TranscriptionException(errorMessage, result.exceptionOrNull()))
            } finally {
                _isTranscribing.value = false
            }
        }

    /** 엔진 하나로 전사를 시도하고 결과를 반환한다. */
    private suspend fun tryEngine(
        engine: SttEngine,
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            Log.d(TAG, "${engine.engineName()} 시도")
            val result = engine.transcribe(audioFilePath, onProgress)
            if (result.isSuccess) {
                Log.d(TAG, "${engine.engineName()} 전사 성공: ${result.getOrThrow().length}자")
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "${engine.engineName()} 전사 예외: ${e.message}")
            Result.failure(e)
        }
    }

    /** 현재 전사가 진행 중인지 여부를 관찰한다. */
    override fun isTranscribing(): Flow<Boolean> = _isTranscribing.asStateFlow()

    /**
     * 전사 결과를 파일로 저장한다.
     *
     * @param meetingId 회의 ID
     * @param text 전사된 텍스트
     * @return 저장된 파일의 절대 경로
     */
    fun saveTranscriptToFile(meetingId: Long, text: String): String {
        val transcriptsDir = File(context.filesDir, TRANSCRIPTS_DIR)
        transcriptsDir.mkdirs()

        val file = File(transcriptsDir, "${meetingId}.txt")
        file.writeText(text)
        Log.d(TAG, "전사 텍스트 저장 완료: ${file.absolutePath} (${text.length}자)")
        return file.absolutePath
    }
}

/** 전사 과정에서 발생하는 통합 예외 */
class TranscriptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
