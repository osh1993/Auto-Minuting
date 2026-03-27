package com.autominuting.data.stt

import android.util.Log
import com.autominuting.BuildConfig
import com.autominuting.data.security.SecureApiKeyRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API를 사용한 오디오 파일 전사 엔진.
 *
 * Whisper 네이티브 라이브러리가 없고, SpeechRecognizer가 파일 전사를 지원하지 않으므로
 * Gemini 2.5 Flash 멀티모달 API에 오디오 파일을 전송하여 전사한다.
 *
 * 폴백 경로: Whisper(1차, 네이티브) → Gemini STT(2차, 클라우드)
 */
@Singleton
class GeminiSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : SttEngine {

    companion object {
        private const val TAG = "GeminiSttEngine"
        private const val MODEL_NAME = "gemini-2.5-flash"

        private val TRANSCRIPTION_PROMPT = """
            |다음 오디오 파일의 내용을 한국어로 정확하게 전사해주세요.
            |
            |규칙:
            |- 들리는 그대로 전사한다 (요약하지 않는다)
            |- 화자가 구분 가능하면 "화자1:", "화자2:" 형식으로 구분한다
            |- 화자 이름이 언급되면 실제 이름을 사용한다
            |- 불확실한 부분은 [불명확] 표시한다
            |- 의미 없는 추임새(아, 음, 그...)는 생략한다
            |- 문장 단위로 줄바꿈한다
        """.trimMargin()

        /** 지원하는 오디오 MIME 타입 */
        private val MIME_MAP = mapOf(
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
            "flac" to "audio/flac",
            "aac" to "audio/aac"
        )
    }

    override fun engineName(): String = "Gemini STT (Cloud)"

    override suspend fun isAvailable(): Boolean {
        val apiKey = getApiKey()
        val available = apiKey.isNotBlank()
        Log.d(TAG, "Gemini STT 사용 가능 여부: $available (API 키 ${if (available) "있음" else "없음"})")
        return available
    }

    override suspend fun transcribe(audioFilePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(
                        GeminiSttException("Gemini API 키가 설정되지 않았습니다")
                    )
                }

                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("오디오 파일이 존재하지 않습니다: $audioFilePath")
                    )
                }

                val extension = audioFile.extension.lowercase()
                val mimeType = MIME_MAP[extension]
                    ?: return@withContext Result.failure(
                        GeminiSttException("지원하지 않는 오디오 형식: $extension")
                    )

                Log.d(TAG, "Gemini STT 전사 시작: $audioFilePath (${audioFile.length()} bytes, $mimeType)")

                val model = GenerativeModel(
                    modelName = MODEL_NAME,
                    apiKey = apiKey
                )

                val audioBytes = audioFile.readBytes()

                val response = model.generateContent(
                    content {
                        blob(mimeType, audioBytes)
                        text(TRANSCRIPTION_PROMPT)
                    }
                )

                val text = response.text?.trim()
                if (text.isNullOrBlank()) {
                    return@withContext Result.failure(
                        GeminiSttException("Gemini 전사 결과가 비어있습니다")
                    )
                }

                Log.d(TAG, "Gemini STT 전사 완료: ${text.length}자")
                Result.success(text)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini STT 전사 중 오류: ${e.message}", e)
                Result.failure(GeminiSttException("Gemini STT 전사 실패: ${e.message}", e))
            }
        }

    private fun getApiKey(): String {
        return secureApiKeyRepository.getGeminiApiKey()
            ?: BuildConfig.GEMINI_API_KEY
            ?: ""
    }
}

/** Gemini STT 전사 과정에서 발생하는 예외 */
class GeminiSttException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
