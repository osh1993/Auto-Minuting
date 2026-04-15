package com.autominuting.presentation.dashboard

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.presentation.transcripts.ManualMinutesDialog
import com.autominuting.util.NotebookLmHelper

/**
 * 대시보드 화면.
 * 앱의 메인 홈 화면으로, 진행 중인 파이프라인이 있으면 상단 배너로 상태를 표시한다.
 * URL 입력으로 음성 파일을 다운로드하여 전사 파이프라인에 진입시킬 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val activePipeline by viewModel.activePipeline.collectAsStateWithLifecycle()
    val automationMode by viewModel.automationMode.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val defaultTemplateId by viewModel.defaultTemplateId.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val plaudShareUrl by viewModel.plaudShareUrl.collectAsStateWithLifecycle()
    val quotaUsage by viewModel.quotaUsage.collectAsStateWithLifecycle()
    val apiUsageState by viewModel.apiUsageState.collectAsStateWithLifecycle()
    val transcriptionProgress by viewModel.transcriptionProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val localFileState by viewModel.localFileState.collectAsStateWithLifecycle()

    var urlText by remember { mutableStateOf("") }
    // 하이브리드 모드에서 템플릿 선택 다이얼로그 표시 여부
    var showPipelineTemplateDialog by remember { mutableStateOf(false) }

    // 제목 입력 다이얼로그 제어용 state
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileTitle by remember { mutableStateOf("") }

    // SAF 파일 피커 런처 — audio/* MIME (M4A/MP3 모두 포함)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // ContentResolver로 실제 파일명 조회 후 확장자 제외한 기본 제목 추출 (D-05)
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: ""
            val defaultTitle = displayName.substringBeforeLast(".", displayName)
                .ifBlank { "로컬 파일 회의" }
            pendingFileUri = uri
            pendingFileTitle = defaultTitle
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Minuting") }
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // 진행 중인 파이프라인 배너
        activePipeline?.let { meeting ->
            val isHybridTranscribed = meeting.pipelineStatus == PipelineStatus.TRANSCRIBED
                && automationMode == AutomationMode.HYBRID
            val statusText = when (meeting.pipelineStatus) {
                PipelineStatus.AUDIO_RECEIVED -> "오디오 수신됨"
                PipelineStatus.TRANSCRIBING -> if (transcriptionProgress > 0f) {
                    "전사 중 ${(transcriptionProgress * 100).toInt()}%"
                } else {
                    "전사 중..."
                }
                PipelineStatus.TRANSCRIBED -> {
                    if (automationMode == AutomationMode.HYBRID) {
                        "전사 완료"
                    } else {
                        "전사 완료 — 회의록 생성 대기"
                    }
                }
                PipelineStatus.GENERATING_MINUTES -> "회의록 생성 중..."
                else -> null
            }
            if (statusText != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isHybridTranscribed) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "전사 완료",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = meeting.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        // 전사 중 프로그레스바
                        if (meeting.pipelineStatus == PipelineStatus.TRANSCRIBING) {
                            if (transcriptionProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { transcriptionProgress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                        // 하이브리드 모드 + 전사 완료: 확인/무시 버튼
                        if (isHybridTranscribed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.dismissPipeline(meeting.id) }) {
                                    Text("무시")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (defaultTemplateId > 0) {
                                        viewModel.generateMinutesForPipeline(meeting.id)
                                    } else {
                                        showPipelineTemplateDialog = true
                                    }
                                }) {
                                    Text("회의록 생성")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 90% 쿼터 경고 배너
        if (quotaUsage.isOverThreshold) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "쿼터 경고",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Gemini 쿼터 경고",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "일일 사용량이 90%를 초과했습니다 (${quotaUsage.totalCount}/${quotaUsage.dailyLimit})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Text(
            text = "녹음에서 회의록까지, 자동으로.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // NotebookLM 바로가기
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NotebookLM",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "AI 기반 회의록 분석을 활용할 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { NotebookLmHelper.openNotebookLmWeb(context) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "NotebookLM",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NotebookLM 열기")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 엔진별 API 사용량 요약 카드
        ApiUsageCard(apiUsageState = apiUsageState)

        Spacer(modifier = Modifier.height(16.dp))

        // URL 음성 다운로드 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "URL에서 음성 다운로드",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "음성 파일 URL을 입력하면 다운로드 후 자동 전사합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("음성 파일 URL") },
                    placeholder = { Text("URL 또는 Plaud 공유 링크") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = downloadState !is DashboardViewModel.DownloadState.Downloading
                        && downloadState !is DashboardViewModel.DownloadState.ExtractingAudioUrl
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val state = downloadState) {
                    is DashboardViewModel.DownloadState.Idle -> {
                        Button(
                            onClick = { viewModel.downloadFromUrl(urlText) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = urlText.isNotBlank()
                        ) {
                            Text("다운로드 시작")
                        }
                    }
                    is DashboardViewModel.DownloadState.ExtractingAudioUrl -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Plaud 공유 링크에서 오디오 추출 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is DashboardViewModel.DownloadState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "다운로드 중... ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is DashboardViewModel.DownloadState.Processing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "전사 파이프라인 진입 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is DashboardViewModel.DownloadState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearDownloadError() }
                        ) {
                            Text("다시 시도")
                        }
                    }
                }

                // 로컬 파일 불러오기 섹션 (D-01/D-02)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "로컬 파일 불러오기",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "기기에 저장된 M4A/MP3 파일을 선택하여 전사합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val lfs = localFileState) {
                    is DashboardViewModel.LocalFileState.Idle -> {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("파일 불러오기")
                        }
                    }
                    is DashboardViewModel.LocalFileState.Processing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "파일 처리 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is DashboardViewModel.LocalFileState.Error -> {
                        Text(
                            text = lfs.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { viewModel.clearLocalFileError() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }
        }

        // Plaud 공유 링크 오디오 추출용 숨겨진 WebView
        plaudShareUrl?.let { shareUrl ->
            PlaudAudioExtractorWebView(
                url = shareUrl,
                onAudioUrlFound = { audioUrl -> viewModel.onPlaudAudioUrlExtracted(audioUrl) },
                onError = { error -> viewModel.onPlaudExtractionFailed(error) }
            )
        }

        // 하단 여백
        Spacer(modifier = Modifier.height(16.dp))
    }
    } // Scaffold

    // 하이브리드 모드 템플릿 선택 다이얼로그
    if (showPipelineTemplateDialog) {
        val pipelineMeeting = activePipeline
        if (pipelineMeeting != null) {
            ManualMinutesDialog(
                meetingTitle = pipelineMeeting.title,
                templates = templates,
                isGenerating = false,
                onGenerate = { templateId, customPrompt ->
                    viewModel.generateMinutesForPipelineWithTemplate(
                        pipelineMeeting.id, templateId, customPrompt
                    )
                    showPipelineTemplateDialog = false
                },
                onDismiss = { showPipelineTemplateDialog = false }
            )
        } else {
            showPipelineTemplateDialog = false
        }
    }

    // 로컬 파일 제목 입력 다이얼로그 (D-04 ~ D-07)
    pendingFileUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingFileUri = null
                pendingFileTitle = ""
            },
            title = { Text("회의 제목 입력") },
            text = {
                OutlinedTextField(
                    value = pendingFileTitle,
                    onValueChange = { pendingFileTitle = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.processLocalFile(uri, pendingFileTitle.trim())
                        pendingFileUri = null
                        pendingFileTitle = ""
                    },
                    enabled = pendingFileTitle.isNotBlank()
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingFileUri = null
                    pendingFileTitle = ""
                }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun ApiUsageCard(apiUsageState: com.autominuting.data.quota.ApiUsageState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "API 사용량 요약",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (apiUsageState.isEmpty) {
                Text(
                    text = "아직 API 사용 기록이 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // STT 섹션
                val activeSttStats = apiUsageState.sttEngineStats.filter { it.callCount > 0 }
                if (activeSttStats.isNotEmpty()) {
                    Text(
                        text = "STT 전사 엔진",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    activeSttStats.forEach { stat ->
                        EngineStatRow(stat)
                    }
                }

                // Minutes 섹션
                val activeMinutesStats = apiUsageState.minutesEngineStats.filter { it.callCount > 0 }
                if (activeMinutesStats.isNotEmpty()) {
                    if (activeSttStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "회의록 생성 엔진",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    activeMinutesStats.forEach { stat ->
                        EngineStatRow(stat)
                    }
                }

                // 총 예상 비용 (무료만 사용 시 숨김)
                if (apiUsageState.totalEstimatedCostUsd > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "총 예상 비용",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$%.4f".format(apiUsageState.totalEstimatedCostUsd),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineStatRow(stat: com.autominuting.data.quota.EngineCallStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stat.displayName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${stat.callCount}회",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (stat.estimatedCostUsd > 0.0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$%.4f".format(stat.estimatedCostUsd),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Plaud 공유 링크에서 오디오 URL을 추출하는 숨겨진 WebView.
 *
 * WebView가 공유 페이지를 로드하면 내부적으로 S3 presigned URL을 요청한다.
 * shouldInterceptRequest에서 amazonaws.com/audiofiles/ 패턴의 요청을 감지하여
 * 오디오 URL을 콜백으로 반환한다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PlaudAudioExtractorWebView(
    url: String,
    onAudioUrlFound: (String) -> Unit,
    onError: (String) -> Unit
) {
    var audioFound by remember { mutableStateOf(false) }

    // 30초 타임아웃
    androidx.compose.runtime.LaunchedEffect(url) {
        kotlinx.coroutines.delay(30_000)
        if (!audioFound) {
            onError("오디오 URL 추출 타임아웃 (30초)")
        }
    }

    // 0dp 크기의 숨겨진 WebView
    AndroidView(
        modifier = Modifier.size(0.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null

                        // S3 presigned URL 패턴 감지 (amazonaws.com/audiofiles/)
                        if (requestUrl.contains("amazonaws.com") &&
                            requestUrl.contains("audiofiles/") &&
                            !audioFound
                        ) {
                            audioFound = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onAudioUrlFound(requestUrl)
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        // 재생 버튼 자동 클릭 시도 (오디오 URL 요청 유도)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var audios = document.querySelectorAll('audio');
                                for (var i = 0; i < audios.length; i++) {
                                    audios[i].play().catch(function(){});
                                }
                                var buttons = document.querySelectorAll('button, [role="button"], .play-btn, svg');
                                for (var i = 0; i < buttons.length; i++) {
                                    buttons[i].click();
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && !audioFound) {
                            audioFound = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onError("페이지 로드 실패: ${error?.description}")
                            }
                        }
                    }
                }

                loadUrl(url)
            }
        }
    )
}
