package com.autominuting.spike

import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 실기기 검증을 체계적으로 수행하기 위한 헬퍼 유틸리티.
 *
 * MediaStore 전후 스냅샷을 비교하여 삼성 녹음앱의 전사 전후
 * 파일 변화를 감지한다.
 *
 * 이 코드는 스파이크 전용이며 임시 코드이다.
 */
object SpikeVerificationHelper {

    private const val TAG = "SpikeVerifyHelper"

    /**
     * MediaStore.Audio에서 Voice Recorder 관련 오디오 파일 목록을 조회한다.
     */
    suspend fun snapshotMediaStoreAudio(
        contentResolver: ContentResolver
    ): List<MediaStoreEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<MediaStoreEntry>()

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
        }

        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Voice Recorder%")

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
                    entries.add(
                        MediaStoreEntry(
                            id = cursor.getLong(idIdx),
                            displayName = cursor.getString(nameIdx) ?: "unknown",
                            relativePath = cursor.getString(pathIdx) ?: "",
                            dateAdded = cursor.getLong(dateIdx),
                            mimeType = cursor.getString(mimeIdx) ?: "unknown",
                            size = cursor.getLong(sizeIdx),
                            ownerPackageName = if (ownerIdx >= 0) cursor.getString(ownerIdx) else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore.Audio 스냅샷 쿼리 실패: ${e.message}", e)
        }

        Log.d(TAG, "오디오 스냅샷: ${entries.size}개 파일")
        entries
    }

    /**
     * MediaStore.Files에서 Voice Recorder 관련 텍스트 파일 목록을 조회한다.
     */
    suspend fun snapshotMediaStoreFiles(
        contentResolver: ContentResolver
    ): List<MediaStoreEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<MediaStoreEntry>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("%Voice Recorder%", "text/plain")

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
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext()) {
                    entries.add(
                        MediaStoreEntry(
                            id = cursor.getLong(idIdx),
                            displayName = cursor.getString(nameIdx) ?: "unknown",
                            relativePath = cursor.getString(pathIdx) ?: "",
                            dateAdded = cursor.getLong(dateIdx),
                            mimeType = cursor.getString(mimeIdx) ?: "text/plain",
                            size = cursor.getLong(sizeIdx),
                            ownerPackageName = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore.Files 스냅샷 쿼리 실패: ${e.message}", e)
        }

        Log.d(TAG, "텍스트 파일 스냅샷: ${entries.size}개 파일")
        entries
    }

    /**
     * Before/After 스냅샷을 비교하여 새로 추가된 파일 목록을 반환한다.
     * dateAdded 기준으로 after에만 존재하는 항목을 필터링한다.
     */
    fun diffSnapshots(
        before: List<MediaStoreEntry>,
        after: List<MediaStoreEntry>
    ): List<MediaStoreEntry> {
        val beforeIds = before.map { it.id }.toSet()
        return after.filter { it.id !in beforeIds }
    }

    /**
     * 검증 결과를 사람이 읽기 쉬운 형태로 포맷팅한다.
     */
    fun formatReport(
        audioEntries: List<MediaStoreEntry>,
        textEntries: List<MediaStoreEntry>,
        audioDiff: List<MediaStoreEntry>,
        textDiff: List<MediaStoreEntry>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== 삼성 녹음앱 파일 감지 검증 리포트 ===")
        sb.appendLine()

        sb.appendLine("## Before/After 스냅샷 비교")
        sb.appendLine("- 오디오 파일 (Before): ${audioEntries.size}개")
        sb.appendLine("- 텍스트 파일 (Before): ${textEntries.size}개")
        sb.appendLine("- 새 오디오 파일: ${audioDiff.size}개")
        sb.appendLine("- 새 텍스트 파일: ${textDiff.size}개")
        sb.appendLine()

        if (audioDiff.isNotEmpty()) {
            sb.appendLine("## 새로 감지된 오디오 파일")
            audioDiff.forEach { entry ->
                sb.appendLine("  - ${entry.displayName}")
                sb.appendLine("    경로: ${entry.relativePath}")
                sb.appendLine("    MIME: ${entry.mimeType}, 크기: ${formatSize(entry.size)}")
                sb.appendLine("    소유자: ${entry.ownerPackageName ?: "null"}")
            }
            sb.appendLine()
        }

        if (textDiff.isNotEmpty()) {
            sb.appendLine("## 새로 감지된 텍스트 파일")
            textDiff.forEach { entry ->
                sb.appendLine("  - ${entry.displayName}")
                sb.appendLine("    경로: ${entry.relativePath}")
                sb.appendLine("    MIME: ${entry.mimeType}, 크기: ${formatSize(entry.size)}")
            }
            sb.appendLine()
        }

        // 판정 요약
        sb.appendLine("## 판정 요약")
        sb.appendLine("- 오디오 파일(m4a) 감지: ${if (audioDiff.isNotEmpty()) "YES" else "NO"}")
        sb.appendLine("- 전사 텍스트 파일 감지: ${if (textDiff.isNotEmpty()) "YES" else "NO"}")

        return sb.toString()
    }

    /**
     * FileObserver를 생성하여 Voice Recorder 디렉토리를 감시한다.
     * 이벤트 발생 시 콜백으로 로그 메시지를 전달한다.
     *
     * @return FileObserver 인스턴스 (startWatching/stopWatching 호출 필요)
     */
    fun createVoiceRecorderFileObserver(
        onEvent: (eventType: Int, path: String?) -> Unit
    ): FileObserver {
        @Suppress("DEPRECATION")
        val voiceRecorderDir = Environment.getExternalStorageDirectory()
            .resolve("Recordings/Voice Recorder")

        Log.d(TAG, "FileObserver 감시 경로: ${voiceRecorderDir.absolutePath}")

        return object : FileObserver(
            voiceRecorderDir,
            CREATE or CLOSE_WRITE or MOVED_TO or DELETE
        ) {
            override fun onEvent(event: Int, path: String?) {
                val eventName = when (event) {
                    CREATE -> "CREATE"
                    CLOSE_WRITE -> "CLOSE_WRITE"
                    MOVED_TO -> "MOVED_TO"
                    DELETE -> "DELETE"
                    else -> "UNKNOWN($event)"
                }
                Log.d(TAG, "FileObserver 이벤트: $eventName, path=$path")
                onEvent(event, path)
            }
        }
    }

    /** 파일 크기를 사람이 읽기 쉬운 형식으로 변환 */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }
}

/**
 * MediaStore 스냅샷 항목 데이터.
 */
data class MediaStoreEntry(
    val id: Long,
    val displayName: String,
    val relativePath: String,
    val dateAdded: Long,
    val mimeType: String,
    val size: Long,
    val ownerPackageName: String?
)
