package com.autominuting.spike

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * 삼성 녹음앱 파일 감지를 위한 ContentObserver 스파이크 구현.
 *
 * MediaStore.Audio 및 MediaStore.Files 변경 이벤트를 수신하고,
 * "Recordings/Voice Recorder/" 경로 기반으로 삼성 녹음앱 파일을 필터링한다.
 * 감지 결과를 SharedFlow로 emit하여 UI에서 수신 가능하게 한다.
 *
 * 이 코드는 스파이크 전용이며 임시 코드이다.
 */
class SamsungRecorderObserver(
    handler: Handler,
    private val contentResolver: ContentResolver,
    private val detectionFlow: MutableSharedFlow<DetectionEvent>
) : ContentObserver(handler) {

    /** 중복 감지 방지를 위한 마지막 확인 타임스탬프 (epoch seconds) */
    private var lastCheckedTimestamp: Long = System.currentTimeMillis() / 1000

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore 변경 감지: uri=$uri, selfChange=$selfChange")

        // 최근 60초 이내 추가된 오디오 파일 쿼리
        queryRecentAudioFiles()

        // MediaStore.Files에서 텍스트 파일 감지 시도
        queryRecentTextFiles()

        // 타임스탬프 갱신
        lastCheckedTimestamp = System.currentTimeMillis() / 1000
    }

    /**
     * MediaStore.Audio에서 최근 추가된 파일을 쿼리한다.
     * RELATIVE_PATH가 "Recordings/Voice Recorder/"를 포함하면 삼성 녹음앱 파일로 판정한다.
     */
    private fun queryRecentAudioFiles() {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        )

        // API 29+에서 OWNER_PACKAGE_NAME 추가 (nullable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
        }

        val cutoffTime = lastCheckedTimestamp - 60 // 60초 여유
        val selection = "${MediaStore.Audio.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(cutoffTime.toString())

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val ownerIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "unknown"
                    val path = cursor.getString(pathIdx) ?: ""
                    val dateAdded = cursor.getLong(dateIdx)
                    val mimeType = cursor.getString(mimeIdx) ?: "unknown"
                    val size = cursor.getLong(sizeIdx)
                    val owner = if (ownerIdx >= 0) cursor.getString(ownerIdx) else null

                    // 삼성 녹음앱 파일 필터링: RELATIVE_PATH 기반
                    val isSamsungRecorder = path.contains(SAMSUNG_VOICE_RECORDER_PATH)

                    Log.d(TAG, "오디오 파일 감지: name=$name, path=$path, owner=$owner, " +
                            "mime=$mimeType, size=$size, samsung=$isSamsungRecorder")

                    if (isSamsungRecorder) {
                        val event = DetectionEvent(
                            id = id,
                            fileName = name,
                            relativePath = path,
                            mimeType = mimeType,
                            size = size,
                            ownerPackage = owner,
                            dateAdded = dateAdded,
                            type = DetectionType.AUDIO,
                            timestamp = System.currentTimeMillis()
                        )
                        // runBlocking으로 emit (스파이크 코드이므로 간결성 우선)
                        runBlocking { detectionFlow.emit(event) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "오디오 파일 쿼리 실패: ${e.message}", e)
        }
    }

    /**
     * MediaStore.Files에서 text/plain 파일이 등록되는지 확인한다.
     * 삼성 녹음앱이 전사 텍스트를 별도 파일로 저장하는지 검증하기 위한 시도.
     */
    private fun queryRecentTextFiles() {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE
        )

        val cutoffTime = lastCheckedTimestamp - 60
        val selection = "${MediaStore.Files.FileColumns.DATE_ADDED} > ? AND " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf(cutoffTime.toString(), "text/plain")

        try {
            contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "unknown"
                    val path = cursor.getString(pathIdx) ?: ""
                    val mimeType = cursor.getString(mimeIdx) ?: "text/plain"
                    val dateAdded = cursor.getLong(dateIdx)
                    val size = cursor.getLong(sizeIdx)

                    val isSamsungRelated = path.contains(SAMSUNG_VOICE_RECORDER_PATH)

                    Log.d(TAG, "텍스트 파일 감지: name=$name, path=$path, " +
                            "mime=$mimeType, size=$size, samsungRelated=$isSamsungRelated")

                    val event = DetectionEvent(
                        id = id,
                        fileName = name,
                        relativePath = path,
                        mimeType = mimeType,
                        size = size,
                        ownerPackage = null,
                        dateAdded = dateAdded,
                        type = DetectionType.TEXT,
                        timestamp = System.currentTimeMillis()
                    )
                    runBlocking { detectionFlow.emit(event) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 파일 쿼리 실패: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SpikeObserver"

        /** 삼성 녹음앱의 기본 저장 경로 */
        const val SAMSUNG_VOICE_RECORDER_PATH = "Recordings/Voice Recorder/"
    }
}

/** 감지 이벤트 타입 */
enum class DetectionType {
    /** 오디오 파일 (MediaStore.Audio에서 감지) */
    AUDIO,
    /** 텍스트 파일 (MediaStore.Files에서 감지) */
    TEXT
}

/**
 * ContentObserver가 감지한 파일 이벤트 데이터.
 */
data class DetectionEvent(
    val id: Long,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val ownerPackage: String?,
    val dateAdded: Long,
    val type: DetectionType,
    val timestamp: Long
)
