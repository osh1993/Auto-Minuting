package com.autominuting.data.stt

import android.util.Log
import com.autominuting.data.quota.ApiUsageTracker
import com.autominuting.data.security.SecureApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deepgram Nova-3 API를 사용한 오디오 파일 전사 엔진.
 *
 * Groq과 달리 multipart가 아닌 binary body로 오디오를 직접 전송하며,
 * 인증 헤더가 Bearer가 아닌 Token 프리픽스를 사용한다.
 * 응답 JSON 경로: results.channels[0].alternatives[0].transcript
 */
@Singleton
class DeepgramSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val apiUsageTracker: ApiUsageTracker
) : SttEngine {

    companion object {
        private const val TAG = "DeepgramSttEngine"
        private const val BASE_URL = "https://api.deepgram.com/v1/listen"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L
    }

    /** 지원하는 오디오 MIME 타입 맵 */
    private val MIME_MAP = mapOf(
        "m4a" to "audio/mp4",
        "mp4" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac"
    )

    override fun engineName(): String = "Deepgram Nova-3 (Cloud)"

    override suspend fun isAvailable(): Boolean =
        secureApiKeyRepository.getDeepgramApiKey()?.isNotBlank() == true

    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = secureApiKeyRepository.getDeepgramApiKey()
                if (apiKey.isNullOrBlank()) {
                    return@withContext Result.failure(
                        DeepgramSttException("Deepgram API 키가 설정되지 않았습니다")
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
                        DeepgramSttException("지원하지 않는 오디오 형식: $extension")
                    )

                val fileSize = audioFile.length()
                Log.d(TAG, "Deepgram STT 전사 시작: $audioFilePath ($fileSize bytes, $mimeType)")

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.MINUTES)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .build()

                // binary body 방식: 오디오 파일을 직접 RequestBody로 전송
                val requestBody = audioFile.asRequestBody(mimeType.toMediaType())

                val url = "$BASE_URL?model=nova-3&language=ko&smart_format=true&punctuate=true"

                // 할당량 초과 / 타임아웃 시 자동 재시도
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Token $apiKey") // Bearer가 아닌 Token
                            .post(requestBody)
                            .build()

                        Log.d(TAG, "Deepgram REST API 호출 (시도 $attempt/$MAX_RETRIES)")
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string()

                        if (response.code == 429) {
                            lastException = DeepgramSttException("할당량 초과 (HTTP 429)")
                            if (attempt < MAX_RETRIES) {
                                Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        if (!response.isSuccessful || responseBody == null) {
                            return@withContext Result.failure(
                                DeepgramSttException("Deepgram API 오류: HTTP ${response.code} — ${responseBody?.take(200)}")
                            )
                        }

                        // 응답 JSON 파싱 — 깊은 경로: results.channels[0].alternatives[0].transcript
                        val json = JSONObject(responseBody)
                        val text = json
                            .getJSONObject("results")
                            .getJSONArray("channels")
                            .getJSONObject(0)
                            .getJSONArray("alternatives")
                            .getJSONObject(0)
                            .getString("transcript")
                            .trim()

                        if (text.isBlank()) {
                            return@withContext Result.failure(
                                DeepgramSttException("Deepgram 전사 결과가 비어있습니다")
                            )
                        }

                        Log.d(TAG, "Deepgram STT 전사 완료: ${text.length}자 (시도 $attempt)")
                        apiUsageTracker.record(ApiUsageTracker.KEY_DEEPGRAM_STT)
                        return@withContext Result.success(text)
                    } catch (e: java.net.SocketTimeoutException) {
                        lastException = e
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }

                Result.failure(DeepgramSttException(
                    "Deepgram API 요청 실패 (${MAX_RETRIES}회 재시도)", lastException
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Deepgram STT 전사 중 오류: ${e.message}", e)
                Result.failure(DeepgramSttException("Deepgram STT 전사 실패: ${e.message}", e))
            }
        }
}

/** Deepgram STT 전사 과정에서 발생하는 예외 */
class DeepgramSttException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
