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
 * Naver CLOVA Speech (Long) API를 사용한 오디오 파일 전사 엔진.
 *
 * CLOVA Speech는 invoke URL이 사용자별로 다르며,
 * multipart/form-data + JSON params 방식으로 요청한다.
 * sync 모드로 동작하므로 긴 오디오의 경우 응답 대기 시간이 길 수 있다.
 */
@Singleton
class NaverClovaSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : SttEngine {

    companion object {
        private const val TAG = "NaverClovaSttEngine"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L

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

    override fun engineName(): String = "Naver CLOVA Speech (Cloud)"

    override suspend fun isAvailable(): Boolean {
        val invokeUrl = secureApiKeyRepository.getClovaInvokeUrl()
        val secretKey = secureApiKeyRepository.getClovaSecretKey()
        return !invokeUrl.isNullOrBlank() && !secretKey.isNullOrBlank()
    }

    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val invokeUrl = secureApiKeyRepository.getClovaInvokeUrl()
                if (invokeUrl.isNullOrBlank()) {
                    return@withContext Result.failure(
                        NaverClovaSttException(
                            "CLOVA Speech invoke URL이 설정되지 않았습니다. " +
                                "Naver Cloud Console에서 CLOVA Speech 앱의 invoke URL을 확인하세요."
                        )
                    )
                }

                val secretKey = secureApiKeyRepository.getClovaSecretKey()
                if (secretKey.isNullOrBlank()) {
                    return@withContext Result.failure(
                        NaverClovaSttException("CLOVA Speech Secret Key가 설정되지 않았습니다")
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
                        NaverClovaSttException("지원하지 않는 오디오 형식: $extension")
                    )

                val fileSize = audioFile.length()
                Log.d(TAG, "CLOVA Speech 전사 시작: $audioFilePath ($fileSize bytes, $mimeType)")

                // sync 모드 장시간 대기를 위해 readTimeout 10분 설정
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.MINUTES)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .build()

                // CLOVA Speech 요청 파라미터 (JSON 문자열로 form field에 포함)
                val params = JSONObject().apply {
                    put("language", "ko-KR")
                    put("completion", "sync")
                    put("fullText", true)
                }.toString()

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "media", audioFile.name,
                        audioFile.asRequestBody(mimeType.toMediaType())
                    )
                    .addFormDataPart("params", params)
                    .build()

                // invoke URL 끝에 /recognizer/upload 경로가 포함되어 있어야 한다
                val url = if (invokeUrl.endsWith("/")) {
                    "${invokeUrl}recognizer/upload"
                } else if (invokeUrl.contains("/recognizer/upload")) {
                    invokeUrl
                } else {
                    "$invokeUrl/recognizer/upload"
                }

                // 재시도 루프
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("X-CLOVASPEECH-API-KEY", secretKey)
                            .post(requestBody)
                            .build()

                        Log.d(TAG, "CLOVA Speech API 호출 (시도 $attempt/$MAX_RETRIES)")
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string()

                        if (response.code == 429) {
                            lastException = NaverClovaSttException("할당량 초과 (HTTP 429)")
                            if (attempt < MAX_RETRIES) {
                                Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        if (!response.isSuccessful || responseBody == null) {
                            return@withContext Result.failure(
                                NaverClovaSttException(
                                    "CLOVA Speech API 오류: HTTP ${response.code} — ${responseBody?.take(200)}"
                                )
                            )
                        }

                        // 응답 파싱
                        val json = JSONObject(responseBody)
                        val result = json.optString("result", "")
                        if (result != "COMPLETED") {
                            return@withContext Result.failure(
                                NaverClovaSttException("CLOVA Speech 전사 실패: result=$result")
                            )
                        }

                        val text = json.optString("text", "").trim()
                        if (text.isBlank()) {
                            return@withContext Result.failure(
                                NaverClovaSttException("CLOVA Speech 전사 결과가 비어있습니다")
                            )
                        }

                        Log.d(TAG, "CLOVA Speech 전사 완료: ${text.length}자 (시도 $attempt)")
                        return@withContext Result.success(text)
                    } catch (e: java.net.SocketTimeoutException) {
                        lastException = e
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }

                Result.failure(
                    NaverClovaSttException(
                        "CLOVA Speech API 요청 실패 (${MAX_RETRIES}회 재시도)", lastException
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "CLOVA Speech 전사 중 오류: ${e.message}", e)
                Result.failure(NaverClovaSttException("CLOVA Speech 전사 실패: ${e.message}", e))
            }
        }
}

/** CLOVA Speech 전사 과정에서 발생하는 예외 */
class NaverClovaSttException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
