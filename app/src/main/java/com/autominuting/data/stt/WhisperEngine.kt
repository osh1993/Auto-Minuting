package com.autominuting.data.stt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * whisper.cpp JNI 래퍼를 통한 온디바이스 STT 엔진.
 *
 * Whisper small 모델(ggml-small.bin)을 사용하여 한국어 음성을 텍스트로 전사한다.
 * 네이티브 라이브러리가 로드되지 않으면 스텁 모드로 동작하여 Result.failure()를 반환한다.
 * (Phase 3의 NiceBuildSdkWrapper 스텁 패턴과 동일 — per D-08)
 *
 * 설정:
 * - language = "ko" (한국어 전사)
 * - temperature = 0.0 (PoC hallucination 대응, 결정론적 출력)
 */
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioConverter: AudioConverter
) : SttEngine {

    companion object {
        private const val TAG = "WhisperEngine"

        /** 모델 파일 경로 (앱 내부 저장소) */
        private const val MODEL_DIR = "models"
        private const val MODEL_FILE = "ggml-small.bin"

        /** Whisper 파라미터 */
        private const val LANGUAGE = "ko"
        private const val TEMPERATURE = 0.0f

        /** 네이티브 라이브러리 로드 상태 */
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("whisper")
                isNativeLoaded = true
                Log.d(TAG, "whisper 네이티브 라이브러리 로드 성공")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "whisper 네이티브 라이브러리 로드 실패 (스텁 모드): ${e.message}")
                isNativeLoaded = false
            }
        }
    }

    /** 현재 전사의 진행률 콜백 (JNI 콜백에서 참조) */
    @Volatile
    private var currentProgressCallback: ((Float) -> Unit)? = null

    /**
     * JNI에서 직접 호출하는 진행률 콜백 메서드.
     * whisper_progress_cb -> JNI CallVoidMethod -> 이 메서드 호출.
     * progress 범위: 0~100 (정수) → 0.0~1.0 (Float)으로 변환하여 전달.
     */
    fun onNativeProgress(progress: Int) {
        currentProgressCallback?.invoke(progress / 100f)
    }

    /** 모델 파일의 절대 경로 */
    private val modelPath: String
        get() = File(context.filesDir, "$MODEL_DIR/$MODEL_FILE").absolutePath

    override fun engineName(): String = "Whisper (whisper.cpp)"

    /**
     * 네이티브 라이브러리 로드 가능 여부와 모델 파일 존재 여부를 확인한다.
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            Log.d(TAG, "네이티브 라이브러리 미로드 상태")
            return@withContext false
        }

        val modelFile = File(modelPath)
        val available = modelFile.exists() && modelFile.length() > 0
        if (!available) {
            Log.d(TAG, "모델 파일이 없습니다: $modelPath")
        }
        available
    }

    /**
     * 오디오 파일을 Whisper로 전사한다.
     *
     * 1. AudioConverter로 16kHz mono WAV 변환
     * 2. JNI를 통해 whisper.cpp 전사 호출
     * 3. 네이티브 라이브러리 미로드 시 Result.failure() 반환
     */
    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                currentProgressCallback = onProgress

                if (!isAvailable()) {
                    return@withContext Result.failure(
                        WhisperNotAvailableException(
                            "Whisper 엔진을 사용할 수 없습니다 " +
                                "(네이티브 라이브러리 또는 모델 파일 누락)"
                        )
                    )
                }

                Log.d(TAG, "전사 시작: $audioFilePath")

                // 16kHz mono WAV로 변환
                val cacheDir = File(context.cacheDir, "whisper_tmp").absolutePath
                val wavPath = audioConverter.convertToWhisperFormat(audioFilePath, cacheDir)

                // JNI를 통한 Whisper 전사 (progressListener로 this를 전달하여 진행률 수신)
                val result = nativeTranscribe(
                    modelPath = modelPath,
                    audioPath = wavPath,
                    language = LANGUAGE,
                    temperature = TEMPERATURE,
                    progressListener = this@WhisperEngine
                )

                if (result.isNullOrBlank()) {
                    return@withContext Result.failure(
                        WhisperTranscriptionException("Whisper 전사 결과가 비어있습니다")
                    )
                }

                Log.d(TAG, "전사 완료: ${result.length}자")
                Result.success(result)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI 호출 실패 (스텁 모드): ${e.message}")
                Result.failure(
                    WhisperNotAvailableException("whisper 네이티브 라이브러리 호출 실패", e)
                )
            } catch (e: Exception) {
                Log.e(TAG, "전사 중 오류 발생: ${e.message}", e)
                Result.failure(e)
            } finally {
                currentProgressCallback = null
            }
        }

    // ── JNI 네이티브 메서드 ──

    /**
     * whisper.cpp JNI를 통한 전사 수행.
     * 네이티브 라이브러리(libwhisper.so)가 로드되어 있어야 동작한다.
     */
    private external fun nativeTranscribe(
        modelPath: String,
        audioPath: String,
        language: String,
        temperature: Float,
        progressListener: Any?
    ): String?
}

/** Whisper 엔진을 사용할 수 없을 때 발생하는 예외 */
class WhisperNotAvailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Whisper 전사 과정에서 발생하는 예외 */
class WhisperTranscriptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
