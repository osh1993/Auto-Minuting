package com.nicebuild.sdk

import android.content.Context
import com.nicebuild.sdk.ble.BleAgentListener
import com.nicebuild.sdk.export.AudioExportFormat
import java.io.File

/**
 * Plaud SDK 스텁 — plaud-sdk.aar 미배치 시 컴파일용.
 * 실제 SDK AAR 배치 후 이 파일 삭제.
 */
object NiceBuildSdk {
    fun initSdk(
        context: Context,
        appKey: String,
        appSecret: String,
        listener: BleAgentListener,
        appName: String
    ) {
        // 스텁: SDK AAR 배치 후 실제 동작
    }

    fun exportAudio(
        sessionId: String,
        outputDir: File,
        format: AudioExportFormat,
        channels: Int,
        callback: ExportCallback
    ) {
        callback.onError("SDK AAR 미배치 — 스텁 모드")
    }

    interface ExportCallback {
        fun onProgress(progress: Float)
        fun onComplete(filePath: String)
        fun onError(error: String)
    }
}
