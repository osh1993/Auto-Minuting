package com.nicebuild.sdk.constants

/** Plaud SDK 상수 스텁 */
object Constants {
    const val BLE_CONNECTED = 1
    const val BLE_DISCONNECTED = 0

    enum class ConnectBleFailed {
        TIMEOUT,
        REJECTED,
        UNKNOWN
    }

    enum class ScanFailed {
        BLUETOOTH_DISABLED,
        PERMISSION_DENIED,
        UNKNOWN
    }
}
