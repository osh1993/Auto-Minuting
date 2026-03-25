package com.nicebuild.sdk.ble

/** Plaud SDK TntAgent 스텁 */
class TntAgent private constructor() {
    companion object {
        private val instance = TntAgent()
        fun getInstant(): TntAgent = instance
    }

    val bleAgent: BleAgent = BleAgent()
}

/** Plaud SDK BleAgent 스텁 */
class BleAgent {
    fun scanBle(enable: Boolean, callback: (Int) -> Unit) {
        callback(-1) // 스텁: 스캔 불가
    }

    fun connectionBLE(
        device: BleDevice,
        bindToken: String?,
        devToken: String?,
        appName: String,
        connectTimeout: Long,
        handshakeTimeout: Long
    ) {
        // 스텁
    }

    fun disconnect() {
        // 스텁
    }
}
