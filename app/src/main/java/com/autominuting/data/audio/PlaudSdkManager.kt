package com.autominuting.data.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plaud SDK BLE 연결/스캔/다운로드를 래핑하는 매니저.
 *
 * NiceBuildSdk API를 사용하여 Plaud 녹음기와 BLE로 통신하고,
 * 오디오 파일을 다운로드한다. appKey가 미설정이면 SDK를 초기화하지 않는다.
 */
@Singleton
class PlaudSdkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** SDK 초기화 여부 */
    private var isInitialized = false

    /** 진행률 콜백 (0.0 ~ 1.0) */
    var onProgressUpdate: ((Float) -> Unit)? = null

    /**
     * Plaud SDK를 초기화한다.
     * appKey가 빈 문자열이면 초기화를 건너뛴다 (Cloud API 폴백 모드).
     *
     * @param appKey Plaud 앱 키
     * @param appSecret Plaud 앱 시크릿
     */
    fun initialize(appKey: String, appSecret: String) {
        if (appKey.isBlank()) return

        // NiceBuildSdk.initSdk(context, appKey, appSecret, PlaudBleListener(), "AutoMinuting")
        // TODO: Plaud SDK AAR 배치 후 실제 SDK 호출로 교체
        NiceBuildSdkWrapper.initSdk(context, appKey, appSecret, "AutoMinuting")
        isInitialized = true
    }

    /**
     * BLE 스캔을 시작하고 Plaud 녹음기에 연결한다.
     *
     * @throws PlaudSdkException BLE 스캔/연결 실패 시
     */
    suspend fun scanAndConnect() = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            continuation.resumeWithException(
                PlaudSdkException("SDK가 초기화되지 않았습니다. appKey를 확인하세요.")
            )
            return@suspendCancellableCoroutine
        }

        try {
            // TntAgent.getInstant().bleAgent.scanBle(true) 콜백 래핑
            // TODO: Plaud SDK AAR 배치 후 실제 SDK 호출로 교체
            NiceBuildSdkWrapper.scanBle(
                onConnected = {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                },
                onError = { errorCode ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            PlaudSdkException("BLE 스캔 실패: 에러 코드 $errorCode")
                        )
                    }
                }
            )
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    PlaudSdkException("BLE 연결 중 오류 발생", e)
                )
            }
        }

        continuation.invokeOnCancellation {
            disconnect()
        }
    }

    /**
     * 오디오 파일을 내보낸다.
     *
     * @param sessionId 녹음 세션 ID
     * @param outputDir 출력 디렉토리
     * @param format 오디오 포맷 (기본: "MP3")
     * @return 저장된 파일의 절대 경로
     * @throws PlaudSdkException 내보내기 실패 시
     */
    suspend fun exportAudio(
        sessionId: String,
        outputDir: File,
        format: String = "MP3"
    ): String = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            continuation.resumeWithException(
                PlaudSdkException("SDK가 초기화되지 않았습니다. appKey를 확인하세요.")
            )
            return@suspendCancellableCoroutine
        }

        try {
            // NiceBuildSdk.exportAudio() 콜백 래핑
            // TODO: Plaud SDK AAR 배치 후 실제 SDK 호출로 교체
            NiceBuildSdkWrapper.exportAudio(
                sessionId = sessionId,
                outputDir = outputDir,
                format = format,
                onProgress = { progress ->
                    onProgressUpdate?.invoke(progress)
                },
                onComplete = { filePath ->
                    if (continuation.isActive) {
                        continuation.resume(filePath)
                    }
                },
                onError = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            PlaudSdkException("오디오 내보내기 실패: $error")
                        )
                    }
                }
            )
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    PlaudSdkException("오디오 내보내기 중 오류 발생", e)
                )
            }
        }
    }

    /** SDK BLE 연결을 해제한다. */
    fun disconnect() {
        if (isInitialized) {
            // TODO: Plaud SDK AAR 배치 후 실제 SDK 호출로 교체
            NiceBuildSdkWrapper.disconnect()
        }
    }

    /** SDK가 초기화되어 사용 가능한지 반환한다. */
    fun isAvailable(): Boolean = isInitialized
}

/**
 * Plaud SDK 관련 예외.
 *
 * SDK BLE 연결, 스캔, 파일 다운로드 실패 시 발생한다.
 */
class PlaudSdkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Plaud SDK 래퍼 (AAR 미배치 시 컴파일 호환용).
 *
 * plaud-sdk.aar 배치 후 실제 NiceBuildSdk API로 교체한다.
 * 현재는 컴파일만 통과하도록 스텁을 제공한다.
 */
internal object NiceBuildSdkWrapper {

    /** SDK 초기화 */
    fun initSdk(
        context: Context,
        appKey: String,
        appSecret: String,
        hostName: String
    ) {
        // 실제 구현: NiceBuildSdk.initSdk(context, appKey, appSecret, PlaudBleListener(), hostName)
    }

    /** BLE 스캔 시작 */
    fun scanBle(
        onConnected: () -> Unit,
        onError: (Int) -> Unit
    ) {
        // 실제 구현: TntAgent.getInstant().bleAgent.scanBle(true) { errorCode -> ... }
        onError(-1) // AAR 미배치 시 항상 실패 -> Cloud API 폴백
    }

    /** 오디오 내보내기 */
    fun exportAudio(
        sessionId: String,
        outputDir: File,
        format: String,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // 실제 구현: NiceBuildSdk.exportAudio(sessionId, outputDir, format, callback)
        onError("SDK AAR이 배치되지 않았습니다") // AAR 미배치 시 항상 실패
    }

    /** BLE 연결 해제 */
    fun disconnect() {
        // 실제 구현: TntAgent.getInstant().bleAgent.disconnect()
    }
}
