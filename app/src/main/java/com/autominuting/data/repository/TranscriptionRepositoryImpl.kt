package com.autominuting.data.repository

import android.content.Context
import android.util.Log
import com.autominuting.data.stt.MlKitEngine
import com.autominuting.data.stt.WhisperEngine
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
 * Whisper(1차)와 ML Kit/SpeechRecognizer(2차)의 이중 경로 폴백 패턴을 지원한다.
 * AudioRepositoryImpl의 SDK(1차) + Cloud API(2차) 패턴과 동일한 구조 — per D-08.
 *
 * 전사 흐름:
 * 1. Whisper 온디바이스 전사 시도 (1차)
 * 2. Whisper 실패 시 ML Kit/SpeechRecognizer 폴백 (2차)
 * 3. 둘 다 실패 시 Result.failure() 반환
 * 4. 성공 시 전사 텍스트를 파일로 저장
 */
@Singleton
class TranscriptionRepositoryImpl @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val mlKitEngine: MlKitEngine,
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
     * 1차: Whisper 온디바이스 전사 시도
     * 2차: Whisper 실패 시 ML Kit/SpeechRecognizer 폴백
     * 양쪽 모두 실패 시 Result.failure() 반환
     *
     * @param audioFilePath 전사할 오디오 파일의 절대 경로
     * @return 성공 시 전사된 텍스트, 실패 시 예외를 포함한 Result
     */
    override suspend fun transcribe(audioFilePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            _isTranscribing.value = true
            try {
                Log.d(TAG, "전사 파이프라인 시작: $audioFilePath")

                // 1차: Whisper 엔진 시도
                val whisperResult = try {
                    Log.d(TAG, "1차 경로: ${whisperEngine.engineName()} 시도")
                    whisperEngine.transcribe(audioFilePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Whisper 전사 예외: ${e.message}")
                    Result.failure(e)
                }

                if (whisperResult.isSuccess) {
                    val text = whisperResult.getOrThrow()
                    Log.d(TAG, "Whisper 전사 성공: ${text.length}자")
                    return@withContext Result.success(text)
                }

                Log.w(
                    TAG,
                    "Whisper 전사 실패, ML Kit 폴백 전환: " +
                        "${whisperResult.exceptionOrNull()?.message}"
                )

                // 2차: ML Kit/SpeechRecognizer 폴백
                val mlKitResult = try {
                    Log.d(TAG, "2차 경로: ${mlKitEngine.engineName()} 시도")
                    mlKitEngine.transcribe(audioFilePath)
                } catch (e: Exception) {
                    Log.w(TAG, "ML Kit 전사 예외: ${e.message}")
                    Result.failure(e)
                }

                if (mlKitResult.isSuccess) {
                    val text = mlKitResult.getOrThrow()
                    Log.d(TAG, "ML Kit 전사 성공: ${text.length}자")
                    return@withContext Result.success(text)
                }

                // 양쪽 모두 실패
                val whisperError = whisperResult.exceptionOrNull()
                val mlKitError = mlKitResult.exceptionOrNull()
                val combinedMessage =
                    "전사 실패 — Whisper: ${whisperError?.message}, " +
                        "ML Kit: ${mlKitError?.message}"
                Log.e(TAG, combinedMessage)

                Result.failure(TranscriptionException(combinedMessage, mlKitError))
            } finally {
                _isTranscribing.value = false
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
