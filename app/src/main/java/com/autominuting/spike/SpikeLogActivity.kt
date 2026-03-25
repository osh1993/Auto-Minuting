package com.autominuting.spike

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autominuting.presentation.theme.AutoMinutingTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 삼성 녹음앱 파일 감지 스파이크 로그 UI.
 *
 * SpikeService를 시작/중지하고, 감지된 이벤트를 실시간으로 표시한다.
 * MediaStore 스냅샷 조회와 텍스트 파일 스캔 기능도 제공한다.
 * Before/After 스냅샷 비교 검증 기능을 포함한다.
 *
 * 이 코드는 스파이크 전용이며 임시 코드이다.
 */
class SpikeLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoMinutingTheme {
                SpikeLogScreen(
                    onStartService = { startSpikeService() },
                    onStopService = { stopSpikeService() },
                    onQuerySnapshot = { queryMediaStoreSnapshot() },
                    onQueryTextFiles = { queryTextFiles() },
                    contentResolverProvider = { contentResolver }
                )
            }
        }
    }

    /** SpikeService를 Foreground Service로 시작한다 */
    private fun startSpikeService() {
        val intent = Intent(this, SpikeService::class.java).apply {
            action = SpikeService.ACTION_START
        }
        startForegroundService(intent)
        Log.d(TAG, "SpikeService 시작 요청")
    }

    /** SpikeService를 중지한다 */
    private fun stopSpikeService() {
        val intent = Intent(this, SpikeService::class.java).apply {
            action = SpikeService.ACTION_STOP
        }
        startService(intent)
        Log.d(TAG, "SpikeService 중지 요청")
    }

    /**
     * 현재 MediaStore에서 Recordings/Voice Recorder/ 경로의 모든 오디오 파일을 조회한다.
     * 전사 전후 비교용.
     */
    private fun queryMediaStoreSnapshot(): List<SnapshotItem> {
        val items = mutableListOf<SnapshotItem>()

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
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val ownerIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
                } else -1

                while (cursor.moveToNext()) {
                    items.add(
                        SnapshotItem(
                            fileName = cursor.getString(nameIdx) ?: "unknown",
                            relativePath = cursor.getString(pathIdx) ?: "",
                            mimeType = cursor.getString(mimeIdx) ?: "unknown",
                            size = cursor.getLong(sizeIdx),
                            dateAdded = cursor.getLong(dateIdx),
                            ownerPackage = if (ownerIdx >= 0) cursor.getString(ownerIdx) else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 스냅샷 쿼리 실패: ${e.message}", e)
        }

        Log.d(TAG, "스냅샷: ${items.size}개 오디오 파일 발견")
        return items
    }

    /**
     * MediaStore.Files에서 Voice Recorder 관련 text/plain 파일을 검색한다.
     */
    private fun queryTextFiles(): List<SnapshotItem> {
        val items = mutableListOf<SnapshotItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("text/plain", "%Voice Recorder%")

        try {
            contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext()) {
                    items.add(
                        SnapshotItem(
                            fileName = cursor.getString(nameIdx) ?: "unknown",
                            relativePath = cursor.getString(pathIdx) ?: "",
                            mimeType = cursor.getString(mimeIdx) ?: "text/plain",
                            size = cursor.getLong(sizeIdx),
                            dateAdded = cursor.getLong(dateIdx),
                            ownerPackage = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 파일 쿼리 실패: ${e.message}", e)
        }

        Log.d(TAG, "텍스트 파일 스캔: ${items.size}개 발견")
        return items
    }

    companion object {
        private const val TAG = "SpikeLogActivity"
    }
}

/** 스냅샷 쿼리 결과 항목 */
data class SnapshotItem(
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val ownerPackage: String?
)

/**
 * 스파이크 로그 화면 Composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeLogScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onQuerySnapshot: () -> List<SnapshotItem>,
    onQueryTextFiles: () -> List<SnapshotItem>,
    contentResolverProvider: () -> android.content.ContentResolver
) {
    // 실시간 감지 이벤트 수집
    val detectionEvents = remember { mutableStateListOf<DetectionEvent>() }
    val isRunning by SpikeService.isRunning.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 스냅샷/텍스트 파일 결과
    var snapshotItems by remember { mutableStateOf<List<SnapshotItem>>(emptyList()) }
    var textFileItems by remember { mutableStateOf<List<SnapshotItem>>(emptyList()) }
    var showingMode by remember { mutableStateOf(ShowingMode.LOG) }

    // 검증 스냅샷 상태
    var beforeAudioSnapshot by remember { mutableStateOf<List<MediaStoreEntry>?>(null) }
    var beforeTextSnapshot by remember { mutableStateOf<List<MediaStoreEntry>?>(null) }
    var verificationReport by remember { mutableStateOf<String?>(null) }

    // FileObserver 상태
    var fileObserver by remember { mutableStateOf<FileObserver?>(null) }
    val fileObserverEvents = remember { mutableStateListOf<String>() }
    var fileObserverRunning by remember { mutableStateOf(false) }

    // Runtime 퍼미션 요청
    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    // 퍼미션 요청 (최초 1회)
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // SpikeService.detectionFlow 수집
    LaunchedEffect(Unit) {
        SpikeService.detectionFlow.collect { event ->
            detectionEvents.add(0, event) // 최신 이벤트가 위에 표시
        }
    }

    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("삼성 녹음앱 감지 스파이크") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // 하단 감시 상태 표시
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRunning) "감시 상태: 실행 중" else "감시 상태: 중지됨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 시작/중지 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStartService,
                    enabled = !isRunning
                ) {
                    Text("감시 시작")
                }
                Button(
                    onClick = onStopService,
                    enabled = isRunning
                ) {
                    Text("감시 중지")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 검증 시나리오 버튼 (Before/After 스냅샷)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val cr = contentResolverProvider()
                            beforeAudioSnapshot = SpikeVerificationHelper.snapshotMediaStoreAudio(cr)
                            beforeTextSnapshot = SpikeVerificationHelper.snapshotMediaStoreFiles(cr)
                            verificationReport = null
                            showingMode = ShowingMode.VERIFICATION
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("검증 시작\n(Before 스냅샷)", maxLines = 2)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val cr = contentResolverProvider()
                            val afterAudio = SpikeVerificationHelper.snapshotMediaStoreAudio(cr)
                            val afterText = SpikeVerificationHelper.snapshotMediaStoreFiles(cr)
                            val audioDiff = SpikeVerificationHelper.diffSnapshots(
                                beforeAudioSnapshot ?: emptyList(), afterAudio
                            )
                            val textDiff = SpikeVerificationHelper.diffSnapshots(
                                beforeTextSnapshot ?: emptyList(), afterText
                            )
                            verificationReport = SpikeVerificationHelper.formatReport(
                                audioEntries = beforeAudioSnapshot ?: emptyList(),
                                textEntries = beforeTextSnapshot ?: emptyList(),
                                audioDiff = audioDiff,
                                textDiff = textDiff
                            )
                            showingMode = ShowingMode.VERIFICATION
                        }
                    },
                    enabled = beforeAudioSnapshot != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("검증 완료\n(After + Diff)", maxLines = 2)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // FileObserver 검증 + 기존 스캔 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = {
                    if (!fileObserverRunning) {
                        fileObserverEvents.clear()
                        val obs = SpikeVerificationHelper.createVoiceRecorderFileObserver { eventType, path ->
                            val eventName = when (eventType) {
                                FileObserver.CREATE -> "CREATE"
                                FileObserver.CLOSE_WRITE -> "CLOSE_WRITE"
                                FileObserver.MOVED_TO -> "MOVED_TO"
                                FileObserver.DELETE -> "DELETE"
                                else -> "OTHER($eventType)"
                            }
                            fileObserverEvents.add(0, "$eventName: $path")
                        }
                        obs.startWatching()
                        fileObserver = obs
                        fileObserverRunning = true
                        showingMode = ShowingMode.FILE_OBSERVER
                    } else {
                        fileObserver?.stopWatching()
                        fileObserver = null
                        fileObserverRunning = false
                    }
                }) {
                    Text(if (fileObserverRunning) "FileObserver 중지" else "FileObserver 시작")
                }
                OutlinedButton(onClick = {
                    snapshotItems = onQuerySnapshot()
                    showingMode = ShowingMode.SNAPSHOT
                }) {
                    Text("MediaStore 스냅샷")
                }
                OutlinedButton(onClick = {
                    showingMode = ShowingMode.LOG
                }) {
                    Text("실시간 로그")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // 모드별 표시
            when (showingMode) {
                ShowingMode.LOG -> {
                    Text(
                        text = "실시간 감지 로그 (${detectionEvents.size}건)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (detectionEvents.isEmpty()) {
                        Text(
                            text = "감지된 이벤트가 없습니다. 감시를 시작하고 삼성 녹음앱에서 녹음/전사를 수행하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(state = listState) {
                        items(detectionEvents) { event ->
                            DetectionEventCard(event = event, dateFormat = dateFormat)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                ShowingMode.SNAPSHOT -> {
                    Text(
                        text = "Voice Recorder 오디오 스냅샷 (${snapshotItems.size}개)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn {
                        items(snapshotItems) { item ->
                            SnapshotItemCard(item = item, dateFormat = dateFormat)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                ShowingMode.TEXT_FILES -> {
                    Text(
                        text = "Voice Recorder 텍스트 파일 (${textFileItems.size}개)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (textFileItems.isEmpty()) {
                        Text(
                            text = "텍스트 파일이 발견되지 않았습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn {
                        items(textFileItems) { item ->
                            SnapshotItemCard(item = item, dateFormat = dateFormat)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                ShowingMode.VERIFICATION -> {
                    Text(
                        text = "검증 결과",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (verificationReport != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = verificationReport!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        val audioCount = beforeAudioSnapshot?.size ?: 0
                        val textCount = beforeTextSnapshot?.size ?: 0
                        Text(
                            text = "Before 스냅샷 저장 완료.\n" +
                                    "오디오: ${audioCount}개, 텍스트: ${textCount}개\n\n" +
                                    "이제 삼성 녹음앱에서 녹음 + 전사를 수행한 후\n" +
                                    "'검증 완료 (After + Diff)' 버튼을 누르세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ShowingMode.FILE_OBSERVER -> {
                    Text(
                        text = "FileObserver 이벤트 (${fileObserverEvents.size}건)" +
                                if (fileObserverRunning) " - 감시 중" else " - 중지됨",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (fileObserverEvents.isEmpty()) {
                        Text(
                            text = "FileObserver 이벤트가 없습니다.\n" +
                                    "Recordings/Voice Recorder/ 경로를 감시 중입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn {
                        items(fileObserverEvents) { eventLog ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = eventLog,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 표시 모드 */
enum class ShowingMode {
    LOG, SNAPSHOT, TEXT_FILES, VERIFICATION, FILE_OBSERVER
}

/** 감지 이벤트 카드 */
@Composable
fun DetectionEventCard(event: DetectionEvent, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.type == DetectionType.AUDIO)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (event.type == DetectionType.AUDIO) "[오디오]" else "[텍스트]",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = event.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "경로: ${event.relativePath}",
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                Text(
                    text = "MIME: ${event.mimeType}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "크기: ${formatFileSize(event.size)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (event.ownerPackage != null) {
                Text(
                    text = "소유자: ${event.ownerPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 스냅샷 항목 카드 */
@Composable
fun SnapshotItemCard(item: SnapshotItem, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "경로: ${item.relativePath}",
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                Text(
                    text = "MIME: ${item.mimeType}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "크기: ${formatFileSize(item.size)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "추가일: ${dateFormat.format(Date(item.dateAdded * 1000))}",
                style = MaterialTheme.typography.bodySmall
            )
            if (item.ownerPackage != null) {
                Text(
                    text = "소유자: ${item.ownerPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 파일 크기를 사람이 읽기 쉬운 형식으로 변환 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}
