package com.autominuting.data.stt

import android.util.Log
import com.autominuting.data.quota.ApiUsageTracker
import com.autominuting.data.security.SecureApiKeyRepository
import com.autominuting.util.AudioChunker
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
 * 25MB 이하: 단일 요청, 25MB 초과: AudioChunker로 분할 후 순차 전사 (GROQ-01/02/03).
 */
@Singleton
class GroqSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val apiUsageTracker: ApiUsageTracker,
    private val audioConverter: AudioConverter
) : SttEngine {

    companion object {
        private const val TAG = "GroqSttEngine"
        private const val API_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3-turbo"
        private const val MAX_FILE_SIZE = 25 * 1024 * 1024L  // 25MB (Free 티어 제한)
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 20_000L
        private const val CHUNK_DELAY_MS = 500L  // Free tier RPM 20 안전 마진 (55-RESEARCH.md)

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
    ): Result<String> = withContext(Dispatchers.IO) {
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
            Log.d(TAG, "Groq STT 진입: $audioFilePath (${fileSize / 1024 / 1024}MB)")

            // 크기 기반 분기 (GROQ-01): 25MB 이하는 단일 요청, 초과 시 청크 분할
            if (fileSize <= MAX_FILE_SIZE) {
                return@withContext transcribeSingle(audioFile, apiKey, onProgress)
            }
            return@withContext transcribeChunked(audioFile, apiKey, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Groq STT 전사 중 오류: ${e.message}", e)
            Result.failure(GroqSttException("Groq STT 전사 실패: ${e.message}", e))
        }
    }

    /**
     * 단일 파일 전사 (25MB 이하 또는 분할된 청크 개별 전송).
     * 기존 transcribe() 본문의 단일 요청 로직을 그대로 유지.
     */
    private suspend fun transcribeSingle(
        audioFile: File,
        apiKey: String,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val extension = audioFile.extension.lowercase()
        val mimeType = MIME_MAP[extension]
            ?: return@withContext Result.failure(
                GroqSttException("지원하지 않는 오디오 형식: $extension")
            )

        Log.d(TAG, "Groq 단일 요청: ${audioFile.name} (${audioFile.length()} bytes, $mimeType)")

        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
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
                val callResult = client.newCall(request).execute().use { resp ->
                    val responseBody = resp.body?.string()

                    if (resp.code == 429) {
                        lastException = GroqSttException("할당량 초과 (HTTP 429)")
                        if (attempt < MAX_RETRIES) {
                            Log.w(TAG, "할당량 초과, ${RETRY_DELAY_MS / 1000}초 후 재시도 ($attempt/$MAX_RETRIES)")
                        }
                        return@use null
                    }

                    if (!resp.isSuccessful || responseBody == null) {
                        return@withContext Result.failure<String>(
                            GroqSttException("Groq API 오류: HTTP ${resp.code} — ${responseBody?.take(200)}")
                        )
                    }

                    val json = JSONObject(responseBody)
                    val text = json.optString("text").trim()

                    if (text.isBlank()) {
                        return@withContext Result.failure<String>(
                            GroqSttException("Groq 전사 결과가 비어있습니다")
                        )
                    }

                    Log.d(TAG, "Groq 단일 요청 완료: ${text.length}자 (시도 $attempt)")
                    Result.success(text)
                }

                if (callResult != null) {
                    apiUsageTracker.record(ApiUsageTracker.KEY_GROQ_STT)
                    return@withContext callResult
                }

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
    }

    /**
     * 25MB 초과 파일을 청크 분할하여 순차 전사 후 이어붙인다 (GROQ-01/02/03).
     */
    private suspend fun transcribeChunked(
        audioFile: File,
        apiKey: String,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        // 청크 임시 디렉토리: 회의 폴더 하위 (Pitfall 2/4 참조 — cacheDir 대신 meeting dir 사용)
        val parentDir = audioFile.parentFile
            ?: return@withContext Result.failure(
                GroqSttException("오디오 파일의 부모 디렉토리를 찾을 수 없습니다: ${audioFile.absolutePath}")
            )
        val tempDir = File(parentDir, "groq_chunks_${System.currentTimeMillis()}")

        try {
            Log.d(TAG, "Groq 청크 분할 시작: ${audioFile.absolutePath} (${audioFile.length() / 1024 / 1024}MB)")
            val chunks = AudioChunker.split(
                inputPath = audioFile.absolutePath,
                outputDir = tempDir,
                audioConverter = audioConverter
            )
            Log.d(TAG, "Groq 청크 분할 완료: ${chunks.size}개 청크")

            if (chunks.isEmpty()) {
                return@withContext Result.failure(
                    GroqSttException("오디오 분할 결과가 비어있습니다")
                )
            }

            val transcripts = mutableListOf<String>()
            chunks.forEachIndexed { index, chunk ->
                // GROQ-02: 순서대로 전사
                Log.d(TAG, "청크 ${index + 1}/${chunks.size} 전사 시작: ${chunk.name} (${chunk.length()} bytes)")
                val result = transcribeSingle(chunk, apiKey, onProgress = { /* 청크별 내부 진행률 무시 */ })
                if (result.isFailure) {
                    Log.e(TAG, "청크 ${index + 1}/${chunks.size} 전사 실패")
                    return@withContext result
                }
                transcripts += result.getOrThrow()

                // 청크 완료 기반 이산 진행률 (Pitfall 5 회피)
                onProgress((index + 1).toFloat() / chunks.size)

                // RPM 20 안전 마진 (마지막 청크 뒤에는 delay 불필요)
                if (index < chunks.lastIndex) delay(CHUNK_DELAY_MS)
            }

            // GROQ-03: 순서대로 이어붙임 (단순 concat, 55-RESEARCH.md Open Question 1 참조)
            val combined = transcripts.joinToString("\n")
            Log.d(TAG, "Groq 청크 전사 전체 완료: ${chunks.size}개 청크, 총 ${combined.length}자")
            Result.success(combined)
        } finally {
            // 임시 청크 정리 (Pitfall 4)
            if (tempDir.exists()) {
                val deleted = tempDir.deleteRecursively()
                Log.d(TAG, "청크 임시 디렉토리 정리: ${tempDir.absolutePath} (success=$deleted)")
            }
        }
    }
}

/** Groq STT 전사 과정에서 발생하는 예외 */
class GroqSttException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
