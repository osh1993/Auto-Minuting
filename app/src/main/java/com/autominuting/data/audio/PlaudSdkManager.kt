package com.autominuting.data.audio

import android.content.Context
import android.util.Log
import com.nicebuild.sdk.NiceBuildSdk
import com.nicebuild.sdk.ble.BleCore
import com.nicebuild.sdk.ble.TntAgent
import com.nicebuild.sdk.export.AudioExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plaud SDK BLE 연결/스캔/다운로드를 관리하는 매니저.
 *
 * NiceBuildSdk API를 직접 호출하여 Plaud 녹음기와 BLE로 통신하고,
 * 오디오 파일을 다운로드한다. appKey가 미설정이면 SDK를 초기화하지 않는다.
 *
 * 주의: plaud-sdk.aar이 libs/에 배치되어야 컴파일 가능하다.
 */
@Singleton
class PlaudSdkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PlaudBLE"

        /** BLE 스캔 타임아웃 (밀리초) */
        private const val SCAN_TIMEOUT_MS = 30_000L

        /** BLE 연결 타임아웃 (밀리초) */
        private const val CONNECT_TIMEOUT_MS = 45_000L

        /** connectionBLE 연결 타임아웃 파라미터 (밀리초) */
        private const val BLE_CONNECT_TIMEOUT = 30_000L

        /** connectionBLE 핸드셰이크 타임아웃 파라미터 (밀리초) */
        private const val BLE_HANDSHAKE_TIMEOUT = 15_000L
    }

    /** SDK 초기화 여부 */
    private var isInitialized = false

    /** BLE 이벤트 리스너 */
    private val bleListener = PlaudBleAgentListener()

    /** BLE 연결 상태 (외부 구독용) */
    val connectionState: StateFlow<BleConnectionState> = bleListener.connectionState

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

        NiceBuildSdk.initSdk(
            context,
            appKey,
            appSecret,
            bleListener,
            "AutoMinuting"
        )
        isInitialized = true
        Log.d(TAG, "SDK 초기화 완료: appKey=${appKey.take(4)}***")
    }

    /**
     * BLE 스캔을 시작하고 Plaud 녹음기에 연결한다.
     *
     * 3단계 비동기 흐름:
     * 1. scanBle() - BLE 스캔 시작
     * 2. awaitDeviceFound() - 기기 발견 대기
     * 3. connectionBLE() + awaitConnected() - 연결 완료 대기
     *
     * @throws PlaudSdkException BLE 스캔/연결 실패 시
     */
    suspend fun scanAndConnect() {
        if (!isInitialized) {
            throw PlaudSdkException("SDK가 초기화되지 않았습니다. appKey를 확인하세요.")
        }

        // 이전 상태 초기화
        bleListener.reset()

        try {
            val bleAgent = TntAgent.getInstant().bleAgent

            // 1단계: BLE 스캔 시작
            Log.d(TAG, "BLE 스캔 시작")
            bleAgent.scanBle(true) { errorCode ->
                if (errorCode != 0) {
                    Log.e(TAG, "BLE 스캔 에러: $errorCode")
                }
            }

            // 2단계: 기기 발견 대기
            Log.d(TAG, "기기 발견 대기 중... (타임아웃: ${SCAN_TIMEOUT_MS}ms)")
            val device = withTimeout(SCAN_TIMEOUT_MS) {
                bleListener.awaitDeviceFound()
            }
            Log.d(TAG, "기기 발견 완료: ${device.sn}")

            // 3단계: BLE 연결
            Log.d(TAG, "BLE 연결 시도: ${device.sn}")
            bleAgent.connectionBLE(
                device,
                null,           // bindToken (첫 연결 시 null)
                null,           // devToken (첫 연결 시 null)
                "AutoMinuting",
                BLE_CONNECT_TIMEOUT,
                BLE_HANDSHAKE_TIMEOUT
            )

            // 연결 완료 대기
            Log.d(TAG, "연결 완료 대기 중... (타임아웃: ${CONNECT_TIMEOUT_MS}ms)")
            withTimeout(CONNECT_TIMEOUT_MS) {
                bleListener.awaitConnected()
            }
            Log.d(TAG, "BLE 연결 완료: ${device.sn}")

        } catch (e: PlaudSdkException) {
            throw e
        } catch (e: Exception) {
            // 연결 실패 시 정리
            disconnect()
            throw PlaudSdkException("BLE 연결 중 오류 발생: ${e.message}", e)
        }
    }

    /**
     * 오디오 파일을 내보낸다.
     *
     * @param sessionId 녹음 세션 ID
     * @param outputDir 출력 디렉토리
     * @param format 오디오 포맷 (기본: "WAV")
     * @return 저장된 파일의 절대 경로
     * @throws PlaudSdkException 내보내기 실패 시
     */
    suspend fun exportAudio(
        sessionId: String,
        outputDir: File,
        format: String = "WAV"
    ): String = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            continuation.resumeWithException(
                PlaudSdkException("SDK가 초기화되지 않았습니다. appKey를 확인하세요.")
            )
            return@suspendCancellableCoroutine
        }

        // format 문자열을 AudioExportFormat enum으로 매핑
        val exportFormat = when (format.uppercase()) {
            "WAV" -> AudioExportFormat.WAV
            "PCM" -> AudioExportFormat.PCM
            else -> {
                Log.w(TAG, "미지원 포맷 '$format', WAV로 폴백")
                AudioExportFormat.WAV
            }
        }

        try {
            Log.d(TAG, "오디오 내보내기 시작: sessionId=$sessionId, format=$exportFormat")
            NiceBuildSdk.exportAudio(
                sessionId,
                outputDir,
                exportFormat,
                1, // channels: 모노
                object : NiceBuildSdk.ExportCallback {
                    override fun onProgress(progress: Float) {
                        Log.d(TAG, "내보내기 진행률: ${(progress * 100).toInt()}%")
                        onProgressUpdate?.invoke(progress)
                    }

                    override fun onComplete(filePath: String) {
                        Log.d(TAG, "내보내기 완료: $filePath")
                        if (continuation.isActive) {
                            continuation.resume(filePath)
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "내보내기 실패: $error")
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                PlaudSdkException("오디오 내보내기 실패: $error")
                            )
                        }
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

    /**
     * 녹음기에 저장된 파일 목록을 조회한다.
     *
     * BLE 연결 상태에서 호출해야 하며, 녹음 세션 ID 목록을 반환한다.
     * 호출자는 이 목록에서 최신 세션을 선택하여 exportAudio에 전달할 수 있다.
     *
     * @return 세션 ID 목록
     * @throws PlaudSdkException 파일 목록 조회 실패 시
     */
    suspend fun getFileList(): List<String> {
        if (!isInitialized) {
            throw PlaudSdkException("SDK가 초기화되지 않았습니다. appKey를 확인하세요.")
        }

        return try {
            val fileList = BleCore.getInstance().getFileList()
            Log.d(TAG, "파일 목록 조회 완료: ${fileList.size}개")
            fileList
        } catch (e: Exception) {
            throw PlaudSdkException("파일 목록 조회 실패: ${e.message}", e)
        }
    }

    /** SDK BLE 연결을 해제한다. */
    fun disconnect() {
        if (isInitialized) {
            try {
                TntAgent.getInstant().bleAgent.disconnect()
                Log.d(TAG, "BLE 연결 해제 완료")
            } catch (e: Exception) {
                Log.e(TAG, "BLE 연결 해제 중 오류: ${e.message}")
            }
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
