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
 * Naver CLOVA Summary Legacy API를 사용하여 한국어 전사 텍스트를 요약하는 엔진.
 *
 * CLOVA Summary API는 2000자 제한이 있으므로, 긴 텍스트는 문장 경계 기준으로
 * 분할(chunk)하여 각각 API 호출 후 결과를 합친다.
 */
@Singleton
class NaverClovaMinutesEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "NaverClovaMinutesEngine"
        private const val ENDPOINT = "https://naveropenapi.apigw.ntruss.com/text-summary/v1/summarize"
        /** CLOVA Summary Legacy 2000자 제한 */
        private const val MAX_CHUNK_SIZE = 2000
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L
    }

    /** 엔진 이름을 반환한다. */
    override fun engineName(): String = "Naver CLOVA Summary"

    /** CLOVA Summary Client ID와 Client Secret이 설정되어 있으면 사용 가능하다. */
    override fun isAvailable(): Boolean =
        secureApiKeyRepository.getClovaSummaryClientId()?.isNotBlank() == true &&
                secureApiKeyRepository.getClovaSummaryClientSecret()?.isNotBlank() == true

    /**
     * 전사 텍스트를 CLOVA Summary API에 전달하여 요약을 생성한다.
     *
     * 2000자 초과 시 문장 경계 기준으로 분할하여 각각 API 호출 후 결과를 합친다.
     *
     * @param transcriptText 전사된 회의 텍스트 (한국어)
     * @param customPrompt 사용자 정의 프롬프트 (CLOVA Summary는 커스텀 프롬프트를 지원하지 않으므로 무시됨)
     * @return 성공 시 요약 텍스트, 실패 시 예외를 포함한 Result
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        if (customPrompt != null) {
            Log.d(TAG, "CLOVA Summary는 커스텀 프롬프트를 지원하지 않습니다 — 무시합니다")
        }

        val clientId = secureApiKeyRepository.getClovaSummaryClientId()
        val clientSecret = secureApiKeyRepository.getClovaSummaryClientSecret()
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("CLOVA Summary API 키가 설정되지 않았습니다")
            )
        }

        return withContext(Dispatchers.IO) {
            val chunks = splitIntoChunks(transcriptText)
            Log.d(TAG, "전사 텍스트 ${transcriptText.length}자 → ${chunks.size}개 청크로 분할")

            val summaries = mutableListOf<String>()
            for ((index, chunk) in chunks.withIndex()) {
                Log.d(TAG, "청크 ${index + 1}/${chunks.size} 요약 중 (${chunk.length}자)")
                val result = summarizeChunk(chunk, clientId, clientSecret)
                result.onSuccess { summaries.add(it) }
                result.onFailure { return@withContext Result.failure(it) }
            }

            val combined = summaries.joinToString("\n\n")
            Result.success(combined)
        }
    }

    /**
     * 텍스트를 MAX_CHUNK_SIZE 이하의 청크로 분할한다.
     * 문장 경계(마침표, 느낌표, 물음표, 줄바꿈)를 기준으로 분할하며,
     * 문장 경계를 찾지 못하면 MAX_CHUNK_SIZE 위치에서 강제 분할한다.
     */
    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_CHUNK_SIZE) {
                chunks.add(remaining)
                break
            }
            // MAX_CHUNK_SIZE 이내에서 마지막 문장 경계 찾기
            val searchRange = remaining.substring(0, MAX_CHUNK_SIZE)
            val sentenceEnd = maxOf(
                searchRange.lastIndexOf("。"),   // 일본어 마침표도 대비
                searchRange.lastIndexOf(". "),
                searchRange.lastIndexOf(".\n"),
                searchRange.lastIndexOf("! "),
                searchRange.lastIndexOf("? "),
                searchRange.lastIndexOf("\n")
            )
            val splitAt = if (sentenceEnd > MAX_CHUNK_SIZE / 2) sentenceEnd + 1 else MAX_CHUNK_SIZE
            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }
        return chunks
    }

    /**
     * 단일 청크를 CLOVA Summary API에 요약 요청한다.
     * HTTP 429 및 SocketTimeoutException 시 재시도한다.
     */
    private suspend fun summarizeChunk(
        chunk: String,
        clientId: String,
        clientSecret: String
    ): Result<String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val jsonBody = JSONObject().apply {
            put("document", JSONObject().put("content", chunk))
            put("option", JSONObject().apply {
                put("language", "ko")
                put("model", "general")
                put("tone", 2)
                put("summaryCount", 3)
            })
        }.toString()

        val mediaType = "application/json".toMediaType()

        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(mediaType))
                    .build()

                Log.d(TAG, "CLOVA Summary API 호출 (시도 $attempt/$MAX_RETRIES): ${chunk.length}자")
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
                    return Result.failure(
                        IllegalStateException("CLOVA Summary API 오류: HTTP ${response.code} — ${responseBody?.take(200)}")
                    )
                }

                // 응답 JSON 파싱
                val json = JSONObject(responseBody)
                val summary = json.getString("summary")

                if (summary.isBlank()) {
                    return Result.failure(
                        IllegalStateException("CLOVA Summary 요약 결과가 비어있습니다")
                    )
                }

                Log.d(TAG, "CLOVA Summary 요약 성공: ${summary.length}자 (시도 $attempt)")
                return Result.success(summary)
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "타임아웃, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        // 모든 재시도 소진
        Log.e(TAG, "CLOVA Summary API 요청 실패 (${MAX_RETRIES}회 재시도)")
        return Result.failure(
            lastException ?: IllegalStateException("CLOVA Summary API 요청 실패 (${MAX_RETRIES}회 재시도)")
        )
    }
}
