package com.autominuting.data.stt

import android.util.Log
import com.autominuting.data.security.SecureApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groq Whisper API를 사용한 오디오 파일 전사 엔진.
 *
 * Groq에서 호스팅하는 whisper-large-v3-turbo 모델을 사용하여
 * multipart/form-data로 오디오 파일을 전송하고 전사 결과를 받는다.
 * Free 티어는 25MB 파일 크기 제한이 있다.
 */
@Singleton
class GroqSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : SttEngine {

    companion object {
        private const val TAG = "GroqSttEngine"
        private const val API_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3-turbo"
        private const val MAX_FILE_SIZE = 25 * 1024 * 1024L  // 25MB (Free 티어 제한)
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L

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

    // OkHttpClient는 커넥션 풀과 스레드 풀을 포함하므로 Singleton 클래스에서 멤버로 재사용
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    override fun engineName(): String = "Groq Whisper (Cloud)"

    override suspend fun isAvailable(): Boolean =
        secureApiKeyRepository.getGroqApiKey()?.isNotBlank() == true

    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = secureApiKeyRepository.getGroqApiKey()
                if (apiKey.isNullOrBlank()) {
                    return@withContext Result.failure(
                        GroqSttException("Groq API 키가 설정되지 않았습니다")
                    )
                }

                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("오디오 파일이 존재하지 않습니다: $audioFilePath")
                    )
                }

                val fileSize = audioFile.length()
                if (fileSize > MAX_FILE_SIZE) {
                    return@withContext Result.failure(
                        GroqSttException(
                            "파일 크기(${fileSize / 1024 / 1024}MB)가 Groq 제한(25MB)을 초과합니다. " +
                                "압축된 형식(M4A/MP3)으로 변환하거나 Groq Dev 플랜으로 업그레이드하세요."
                        )
                    )
                }

                val extension = audioFile.extension.lowercase()
                val mimeType = MIME_MAP[extension]
                    ?: return@withContext Result.failure(
                        GroqSttException("지원하지 않는 오디오 형식: $extension")
                    )

                Log.d(TAG, "Groq STT 전사 시작: $audioFilePath ($fileSize bytes, $mimeType)")

                // 할당량 초과 시 자동 재시도
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        // 매 시도마다 requestBody와 request를 새로 생성 (OkHttp 스트림은 재사용 불가)
                        val requestBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "file", audioFile.name,
                                audioFile.asRequestBody(mimeType.toMediaType())
                            )
                            .addFormDataPart("model", MODEL)
                            .addFormDataPart("language", "ko")
                            .addFormDataPart("response_format", "verbose_json")
                            .build()

                        val request = Request.Builder()
                            .url(API_URL)
                            .header("Authorization", "Bearer $apiKey")
                            .post(requestBody)
                            .build()

                        Log.d(TAG, "Groq API 호출 (시도 $attempt/$MAX_RETRIES)")
                        // response.use{}로 모든 경로(429 continue 포함)에서 커넥션 자동 반환
                        val callResult = client.newCall(request).execute().use { resp ->
                            val responseBody = resp.body?.string()

                            if (resp.code == 429) {
                                lastException = GroqSttException("할당량 초과 (HTTP 429)")
                                if (attempt < MAX_RETRIES) {
                                    Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                                }
                                // null 반환으로 루프 상단의 재시도 로직과 연결
                                return@use null
                            }

                            if (!resp.isSuccessful || responseBody == null) {
                                return@withContext Result.failure(
                                    GroqSttException("Groq API 오류: HTTP ${resp.code} — ${responseBody?.take(200)}")
                                )
                            }

                            // 응답에서 텍스트 추출
                            val json = JSONObject(responseBody)
                            val text = json.optString("text").trim()

                            if (text.isBlank()) {
                                return@withContext Result.failure(
                                    GroqSttException("Groq 전사 결과가 비어있습니다")
                                )
                            }

                            Log.d(TAG, "Groq STT 전사 완료: ${text.length}자 (시도 $attempt)")
                            Result.success(text)
                        }

                        if (callResult != null) {
                            return@withContext callResult
                        }

                        // 429 재시도 대기
                        if (attempt < MAX_RETRIES) {
                            delay(RETRY_DELAY_MS)
                        } else {
                            break
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        lastException = e
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }

                Result.failure(
                    GroqSttException("Groq API 요청 실패 (${MAX_RETRIES}회 재시도)", lastException)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Groq STT 전사 중 오류: ${e.message}", e)
                Result.failure(GroqSttException("Groq STT 전사 실패: ${e.message}", e))
            }
        }
}

/** Groq STT 전사 과정에서 발생하는 예외 */
class GroqSttException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
