package com.autominuting.data.stt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whisper 모델 파일(ggml-small.bin) 다운로드 및 관리.
 *
 * 모델은 약 500MB로 APK에 번들할 수 없어 최초 사용 시 다운로드한다.
 * HuggingFace에서 OkHttp로 직접 다운로드하며 진행률을 StateFlow로 제공한다.
 */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WhisperModelManager"
        private const val MODEL_DIR = "models"
        private const val MODEL_FILE = "ggml-small.bin"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        /** 다운로드 중 사용할 임시 파일 확장자 */
        private const val TEMP_SUFFIX = ".downloading"
    }

    /** 모델 다운로드 상태 */
    sealed interface ModelState {
        /** 모델 미설치 */
        data object NotDownloaded : ModelState
        /** 다운로드 진행 중 */
        data class Downloading(val progress: Float) : ModelState
        /** 모델 설치 완료 */
        data object Ready : ModelState
        /** 다운로드 오류 */
        data class Error(val message: String) : ModelState
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /** 다운로드가 ViewModel 스코프와 독립적으로 유지되도록 자체 스코프 사용 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelDir: File
        get() = File(context.filesDir, MODEL_DIR)

    private val modelFile: File
        get() = File(modelDir, MODEL_FILE)

    private val tempFile: File
        get() = File(modelDir, MODEL_FILE + TEMP_SUFFIX)

    init {
        _modelState.value = if (modelFile.exists() && modelFile.length() > 0) {
            ModelState.Ready
        } else {
            ModelState.NotDownloaded
        }
    }

    /** 모델이 사용 가능한지 확인 */
    fun isModelReady(): Boolean = modelFile.exists() && modelFile.length() > 0

    /** 모델 파일의 절대 경로 */
    fun getModelPath(): String = modelFile.absolutePath

    /** HuggingFace에서 모델을 다운로드한다. 진행률을 StateFlow로 제공. */
    fun downloadModel() {
        if (_modelState.value is ModelState.Downloading) return

        scope.launch {
            try {
                _modelState.value = ModelState.Downloading(0f)
                modelDir.mkdirs()

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(MODEL_URL)
                    .build()

                Log.d(TAG, "모델 다운로드 시작: $MODEL_URL")

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _modelState.value = ModelState.Error("다운로드 실패: HTTP ${response.code}")
                    return@launch
                }

                val body = response.body ?: run {
                    _modelState.value = ModelState.Error("응답 본문이 비어있습니다")
                    return@launch
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                _modelState.value = ModelState.Downloading(
                                    downloadedBytes.toFloat() / totalBytes
                                )
                            }
                        }
                    }
                }

                // 다운로드 완료 후 임시 파일을 최종 파일로 rename (원자적)
                if (tempFile.renameTo(modelFile)) {
                    Log.d(TAG, "모델 다운로드 완료: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                    _modelState.value = ModelState.Ready
                } else {
                    _modelState.value = ModelState.Error("파일 이름 변경 실패")
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "모델 다운로드 중 오류: ${e.message}", e)
                _modelState.value = ModelState.Error("다운로드 오류: ${e.message}")
                tempFile.delete()
            }
        }
    }

    /** 다운로드된 모델을 삭제한다. */
    fun deleteModel() {
        modelFile.delete()
        tempFile.delete()
        _modelState.value = ModelState.NotDownloaded
        Log.d(TAG, "모델 삭제 완료")
    }
}
