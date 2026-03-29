package com.autominuting.data.stt

import android.util.Base64
import android.util.Log
import com.autominuting.BuildConfig
import com.autominuting.data.security.SecureApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
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
        /** 할당량 초과 시 최대 재시도 횟수 */
        private const val MAX_RETRIES = 3
        /** 재시도 간격 (밀리초) */
        private const val RETRY_DELAY_MS = 20_000L
        /** Gemini 인라인 blob 최대 크기 (20MB) */
        private const val MAX_INLINE_BYTES = 20 * 1024 * 1024L

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

                val fileSize = audioFile.length()
                Log.d(TAG, "Gemini STT 전사 시작: $audioFilePath ($fileSize bytes, $mimeType)")

                if (fileSize > MAX_INLINE_BYTES) {
                    Log.w(TAG, "파일 크기 ${fileSize / 1024 / 1024}MB — 인라인 blob 제한(20MB) 초과 가능성")
                }

                val audioBytes = audioFile.readBytes()
                val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                // Gemini REST API 직접 호출 (OkHttp — 타임아웃 완전 제어)
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.MINUTES)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .build()

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", mimeType)
                                    put("data", audioBase64)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", TRANSCRIPTION_PROMPT)
                            })
                        })
                    }))
                }

                val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

                // 할당량 초과 시 자동 재시도
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val httpRequest = Request.Builder()
                            .url(apiUrl)
                            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        Log.d(TAG, "Gemini REST API 호출 (시도 $attempt/$MAX_RETRIES)")
                        val response = client.newCall(httpRequest).execute()
                        val responseBody = response.body?.string()

                        if (response.code == 429) {
                            // 할당량 초과
                            lastException = GeminiSttException("할당량 초과 (HTTP 429)")
                            if (attempt < MAX_RETRIES) {
                                Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        if (!response.isSuccessful || responseBody == null) {
                            return@withContext Result.failure(
                                GeminiSttException("Gemini API 오류: HTTP ${response.code} — ${responseBody?.take(200)}")
                            )
                        }

                        // 응답에서 텍스트 추출
                        val json = JSONObject(responseBody)
                        val text = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            ?.trim()

                        if (text.isNullOrBlank()) {
                            return@withContext Result.failure(
                                GeminiSttException("Gemini 전사 결과가 비어있습니다")
                            )
                        }

                        Log.d(TAG, "Gemini STT 전사 완료: ${text.length}자 (시도 $attempt)")
                        return@withContext Result.success(text)
                    } catch (e: java.net.SocketTimeoutException) {
                        lastException = e
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }

                Result.failure(GeminiSttException(
                    "Gemini API 요청 실패 (${MAX_RETRIES}회 재시도)", lastException
                ))
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
