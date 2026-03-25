package com.nicebuild.sdk.ble

import com.nicebuild.sdk.constants.Constants

/** Plaud SDK BLE 이벤트 리스너 스텁 */
interface BleAgentListener {
    fun scanBleDeviceReceiver(device: BleDevice)
    fun btStatusChange(sn: String?, status: BluetoothStatus)
    fun bleConnectFail(sn: String?, reason: Constants.ConnectBleFailed)
    fun handshakeWaitSure(sn: String?, param: Long)
    fun scanFail(reason: Constants.ScanFailed)
}
