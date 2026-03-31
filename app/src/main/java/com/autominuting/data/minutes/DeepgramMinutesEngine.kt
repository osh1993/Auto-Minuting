package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.data.security.SecureApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deepgram Text Intelligence API (/v1/read)를 사용하여 영어 전사 텍스트를 요약하는 엔진.
 * 한국어 미지원.
 *
 * Deepgram의 자체 요약 로직을 사용하므로 커스텀 프롬프트는 지원하지 않는다.
 * 인증 헤더는 Bearer가 아닌 Token 프리픽스를 사용한다 (Deepgram 고유 방식).
 */
@Singleton
class DeepgramMinutesEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "DeepgramMinutesEngine"
        private const val ENDPOINT = "https://api.deepgram.com/v1/read"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L
    }

    /** 엔진 이름을 반환한다. 한국어 미지원 안내 포함. */
    override fun engineName(): String = "Deepgram Intelligence (영어만)"

    /** API 키가 설정되어 있으면 사용 가능하다. */
    override fun isAvailable(): Boolean =
        secureApiKeyRepository.getDeepgramApiKey()?.isNotBlank() == true

    /**
     * 전사 텍스트를 Deepgram Text Intelligence API에 전달하여 요약을 생성한다.
     *
     * @param transcriptText 전사된 회의 텍스트 (영어)
     * @param customPrompt 사용자 정의 프롬프트 (Deepgram은 커스텀 프롬프트를 지원하지 않으므로 무시됨)
     * @return 성공 시 요약 텍스트, 실패 시 예외를 포함한 Result
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        if (customPrompt != null) {
            Log.d(TAG, "Deepgram은 커스텀 프롬프트를 지원하지 않습니다 — 무시합니다")
        }

        val apiKey = secureApiKeyRepository.getDeepgramApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("Deepgram API 키가 설정되지 않았습니다")
            )
        }

        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val url = "$ENDPOINT?summarize=true&language=en"
            val jsonBody = JSONObject().put("text", transcriptText).toString()
            val mediaType = "application/json".toMediaType()

            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Token $apiKey")
                        .header("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody(mediaType))
                        .build()

                    Log.d(TAG, "Deepgram Text Intelligence API 호출 (시도 $attempt/$MAX_RETRIES): 전사 텍스트 ${transcriptText.length}자")
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    // HTTP 429 할당량 초과 — 재시도
                    if (response.code == 429) {
                        lastException = IllegalStateException("할당량 초과 (HTTP 429)")
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                            continue
                        }
                        break
                    }

                    // 기타 HTTP 에러
                    if (!response.isSuccessful || responseBody == null) {
                        return@withContext Result.failure(
                            IllegalStateException("Deepgram API 오류: HTTP ${response.code} — ${responseBody?.take(200)}")
                        )
                    }

                    // 응답 JSON 파싱: results.summary.short 또는 results.summary.text
                    val json = JSONObject(responseBody)
                    val results = json.getJSONObject("results")
                    val summary = results.getJSONObject("summary")

                    var summaryText = summary.optString("short", "")
                    if (summaryText.isBlank()) {
                        summaryText = summary.optString("text", "")
                    }

                    if (summaryText.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("Deepgram 요약 결과가 비어있습니다")
                        )
                    }

                    Log.d(TAG, "Deepgram 요약 생성 성공: ${summaryText.length}자 (시도 $attempt)")
                    return@withContext Result.success(summaryText)
                } catch (e: java.net.SocketTimeoutException) {
                    lastException = e
                    if (attempt < MAX_RETRIES) {
                        Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                        delay(RETRY_DELAY_MS)
                    }
                }
            }

            // 모든 재시도 소진
            Log.e(TAG, "Deepgram API 요청 실패 (${MAX_RETRIES}회 재시도)")
            Result.failure(
                lastException ?: IllegalStateException("Deepgram API 요청 실패 (${MAX_RETRIES}회 재시도)")
            )
        }
    }
}
