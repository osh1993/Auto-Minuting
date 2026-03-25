// plaud-sdk.aar 배치 필수 - libs/ 디렉토리에 AAR 파일을 배치해야 컴파일 가능
package com.autominuting.data.audio

import android.util.Log
import com.nicebuild.sdk.ble.BleAgentListener
import com.nicebuild.sdk.ble.BleDevice
import com.nicebuild.sdk.ble.BluetoothStatus
import com.nicebuild.sdk.constants.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE 연결 상태를 나타내는 sealed interface.
 *
 * Plaud SDK의 BLE 이벤트에 따라 상태가 전이된다:
 * IDLE -> SCANNING -> DEVICE_FOUND -> CONNECTING -> CONNECTED
 * 어느 단계에서든 ERROR 또는 DISCONNECTED로 전이 가능
 */
sealed interface BleConnectionState {
    /** 초기 상태 - 아직 아무 동작도 시작하지 않음 */
    data object IDLE : BleConnectionState

    /** BLE 스캔 진행 중 */
    data object SCANNING : BleConnectionState

    /** Plaud 기기 발견됨 */
    data object DEVICE_FOUND : BleConnectionState

    /** BLE 연결 시도 중 */
    data object CONNECTING : BleConnectionState

    /** BLE 연결 완료 */
    data object CONNECTED : BleConnectionState

    /** BLE 연결 해제됨 */
    data object DISCONNECTED : BleConnectionState

    /** 에러 발생 */
    data class ERROR(val reason: String) : BleConnectionState
}

/**
 * Plaud SDK BLE 이벤트 콜백 구현체.
 *
 * BleAgentListener 인터페이스를 구현하여 스캔/연결/에러 이벤트를 수신한다.
 * 내부 상태를 StateFlow로 관리하여 외부에서 구독 가능하며,
 * CompletableDeferred를 활용하여 비동기 대기를 지원한다.
 */
class PlaudBleAgentListener : BleAgentListener {

    companion object {
        private const val TAG = "PlaudBLE"
    }

    // --- 상태 관리 ---

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.IDLE)
    /** 현재 BLE 연결 상태 */
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevice = MutableStateFlow<BleDevice?>(null)
    /** 스캔에서 발견된 Plaud 기기 */
    val discoveredDevice: StateFlow<BleDevice?> = _discoveredDevice.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** 마지막 에러 메시지 */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // --- CompletableDeferred 기반 비동기 대기 ---

    private var deviceFoundDeferred: CompletableDeferred<BleDevice>? = null
    private var connectedDeferred: CompletableDeferred<Unit>? = null

    // --- BleAgentListener 콜백 구현 ---

    /**
     * BLE 스캔 중 Plaud 기기가 발견되었을 때 호출된다.
     *
     * @param device 발견된 BLE 기기 정보
     */
    override fun scanBleDeviceReceiver(device: BleDevice) {
        Log.d(TAG, "기기 발견: ${device.sn}, RSSI: ${device.rssi}")
        _discoveredDevice.value = device
        _connectionState.value = BleConnectionState.DEVICE_FOUND
        deviceFoundDeferred?.complete(device)
    }

    /**
     * BLE 연결 상태가 변경되었을 때 호출된다.
     *
     * @param sn 기기 시리얼 넘버
     * @param status 새로운 블루투스 상태
     */
    override fun btStatusChange(sn: String?, status: BluetoothStatus) {
        Log.d(TAG, "BLE 상태 변경: $sn -> $status")
        when (status) {
            BluetoothStatus.CONNECTED -> {
                Log.d(TAG, "기기 연결 완료: $sn")
                _connectionState.value = BleConnectionState.CONNECTED
                connectedDeferred?.complete(Unit)
            }
            BluetoothStatus.DISCONNECTED -> {
                Log.d(TAG, "기기 연결 해제: $sn")
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
            else -> {
                Log.d(TAG, "기타 BLE 상태: $sn -> $status")
            }
        }
    }

    /**
     * BLE 연결 실패 시 호출된다.
     *
     * @param sn 기기 시리얼 넘버
     * @param reason 연결 실패 이유
     */
    override fun bleConnectFail(sn: String?, reason: Constants.ConnectBleFailed) {
        val errorMsg = "BLE 연결 실패: $sn, 이유: $reason"
        Log.e(TAG, errorMsg)
        _lastError.value = errorMsg
        _connectionState.value = BleConnectionState.ERROR(errorMsg)
        connectedDeferred?.completeExceptionally(
            PlaudSdkException(errorMsg)
        )
    }

    /**
     * 핸드셰이크 대기 중 호출된다.
     *
     * @param sn 기기 시리얼 넘버
     * @param param 핸드셰이크 파라미터
     */
    override fun handshakeWaitSure(sn: String?, param: Long) {
        Log.d(TAG, "핸드셰이크 대기: $sn, param: $param")
    }

    /**
     * BLE 스캔 실패 시 호출된다.
     *
     * @param reason 스캔 실패 이유
     */
    override fun scanFail(reason: Constants.ScanFailed) {
        val errorMsg = "BLE 스캔 실패: $reason"
        Log.e(TAG, errorMsg)
        _lastError.value = errorMsg
        _connectionState.value = BleConnectionState.ERROR(errorMsg)
        deviceFoundDeferred?.completeExceptionally(
            PlaudSdkException(errorMsg)
        )
    }

    // --- 비동기 대기 메서드 ---

    /**
     * 기기 발견을 대기한다.
     *
     * 스캔 시작 후 scanBleDeviceReceiver 콜백이 호출될 때까지 suspend한다.
     * 타임아웃은 호출자(PlaudSdkManager)에서 withTimeout으로 처리한다.
     *
     * @return 발견된 BLE 기기
     * @throws PlaudSdkException 스캔 실패 시
     */
    suspend fun awaitDeviceFound(): BleDevice {
        val deferred = CompletableDeferred<BleDevice>()
        deviceFoundDeferred = deferred
        return deferred.await()
    }

    /**
     * BLE 연결 완료를 대기한다.
     *
     * connectionBLE 호출 후 btStatusChange에서 CONNECTED가 올 때까지 suspend한다.
     * 타임아웃은 호출자(PlaudSdkManager)에서 withTimeout으로 처리한다.
     *
     * @throws PlaudSdkException 연결 실패 시
     */
    suspend fun awaitConnected() {
        val deferred = CompletableDeferred<Unit>()
        connectedDeferred = deferred
        deferred.await()
    }

    // --- 상태 초기화 ---

    /**
     * 모든 상태를 초기화하여 새 스캔/연결 사이클을 준비한다.
     *
     * 이전 사이클의 CompletableDeferred를 취소하고,
     * 모든 StateFlow를 초기 값으로 리셋한다.
     */
    fun reset() {
        Log.d(TAG, "리스너 상태 초기화")
        deviceFoundDeferred?.cancel()
        connectedDeferred?.cancel()
        deviceFoundDeferred = null
        connectedDeferred = null
        _connectionState.value = BleConnectionState.IDLE
        _discoveredDevice.value = null
        _lastError.value = null
    }
}
