package com.nicebuild.sdk.ble

/** Plaud SDK BLE 디바이스 스텁 */
data class BleDevice(
    val sn: String = "",
    val name: String = "",
    val mac: String = "",
    val rssi: Int = 0
)
