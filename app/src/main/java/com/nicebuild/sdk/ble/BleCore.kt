package com.nicebuild.sdk.ble

/** Plaud SDK BLE Core 스텁 */
class BleCore private constructor() {
    companion object {
        private val instance = BleCore()
        fun getInstance(): BleCore = instance
    }

    fun getFileList(): List<String> = emptyList()
}
