package com.autominuting.data.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Android SpeechRecognizer를 활용한 2차 폴백 STT 엔진.
 *
 * Whisper 엔진 실패 시 자동으로 전환되는 폴백 경로이다.
 * 온디바이스 음성 인식기(createOnDeviceSpeechRecognizer)를 우선 사용하며,
 * 사용 불가 시 기본 SpeechRecognizer로 폴백한다.
 *
 * 설정:
 * - 로케일: ko-KR (한국어)
 * - 모드: Basic (온디바이스 우선)
 *
 * 제한사항:
 * - Samsung 일부 기기에서 동작하지 않을 수 있음
 * - 오디오 파일 직접 입력이 아닌 마이크 입력 기반이므로, 파일 기반 전사에 제약이 있음
 * - 현재는 스텁 모드로 동작하며, 실기기에서 SpeechRecognizer 가용성에 따라 실제 전사 수행
 */
@Singleton
class MlKitEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SttEngine {

    companion object {
        private const val TAG = "MlKitEngine"
        private const val LANGUAGE_CODE = "ko-KR"
    }

    override fun engineName(): String = "ML Kit (SpeechRecognizer)"

    /**
     * SpeechRecognizer가 현재 기기에서 사용 가능한지 확인한다.
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.Main) {
        try {
            val available = SpeechRecognizer.isRecognitionAvailable(context)
            Log.d(TAG, "SpeechRecognizer 사용 가능 여부: $available")
            available
        } catch (e: Exception) {
            Log.w(TAG, "SpeechRecognizer 가용성 확인 실패: ${e.message}")
            false
        }
    }

    /**
     * SpeechRecognizer를 사용하여 오디오 파일을 전사한다.
     *
     * 주의: Android SpeechRecognizer는 기본적으로 마이크 입력을 사용하므로,
     * 파일 기반 전사는 EXTRA_AUDIO_SOURCE를 통해 시도한다.
     * 지원되지 않는 기기에서는 Result.failure()를 반환한다.
     */
    override suspend fun transcribe(audioFilePath: String): Result<String> {
        return try {
            if (!isAvailable()) {
                return Result.failure(
                    MlKitNotAvailableException("SpeechRecognizer를 사용할 수 없습니다")
                )
            }

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                return Result.failure(
                    IllegalArgumentException("오디오 파일이 존재하지 않습니다: $audioFilePath")
                )
            }

            Log.d(TAG, "SpeechRecognizer 전사 시작: $audioFilePath")

            val result = performRecognition(audioFilePath)
            if (result != null) {
                Log.d(TAG, "전사 완료: ${result.length}자")
                Result.success(result)
            } else {
                Result.failure(
                    MlKitTranscriptionException("SpeechRecognizer 전사 결과가 비어있습니다")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer 전사 중 오류: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * SpeechRecognizer를 통해 음성 인식을 수행한다.
     * Main 스레드에서 실행되어야 한다.
     */
    private suspend fun performRecognition(audioFilePath: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val recognizer = try {
                    // 온디바이스 인식기 우선 시도 (API 31+)
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } catch (e: Exception) {
                    Log.w(TAG, "온디바이스 인식기 생성 실패, 기본 인식기로 폴백: ${e.message}")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_CODE)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE_CODE)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // 오디오 파일 경로를 전달 (기기 지원 시)
                    putExtra("android.speech.extra.AUDIO_SOURCE", audioFilePath)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        val text = matches?.firstOrNull()
                        recognizer.destroy()
                        if (continuation.isActive) {
                            continuation.resume(text)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.e(TAG, "SpeechRecognizer 오류 코드: $error")
                        recognizer.destroy()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "음성 인식 준비 완료")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "음성 인식 시작")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // 볼륨 변화 (무시)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // 버퍼 수신 (무시)
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "음성 인식 종료")
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        // 부분 결과 (무시)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // 이벤트 (무시)
                    }
                })

                recognizer.startListening(intent)

                continuation.invokeOnCancellation {
                    recognizer.cancel()
                    recognizer.destroy()
                }
            }
        }
}

/** ML Kit/SpeechRecognizer를 사용할 수 없을 때 발생하는 예외 */
class MlKitNotAvailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** ML Kit/SpeechRecognizer 전사 과정에서 발생하는 예외 */
class MlKitTranscriptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
