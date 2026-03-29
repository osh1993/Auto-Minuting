package com.autominuting.presentation.transcripts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 전사 목록 화면.
 * 전사가 진행 중이거나 완료된 회의 목록을 Card 형태로 표시한다.
 * 전사 완료된 항목을 탭하면 편집 화면으로 이동한다.
 *
 * @param viewModel 전사 목록 상태를 관리하는 ViewModel
 * @param onEditClick 전사 편집 화면으로 이동하는 콜백 (meetingId 전달)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptsScreen(
    viewModel: TranscriptsViewModel = hiltViewModel(),
    onEditClick: (Long) -> Unit = {}
) {
    val meetings by viewModel.meetings.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val defaultTemplateId by viewModel.defaultTemplateId.collectAsState()
    val context = LocalContext.current
    var meetingToDelete by remember { mutableStateOf<Meeting?>(null) }
    // 이름 편집 다이얼로그에 표시할 대상 회의
    var meetingToRename by remember { mutableStateOf<Meeting?>(null) }
    // 회의록 재생성 확인 다이얼로그에 표시할 대상 회의
    var meetingToRegenerate by remember { mutableStateOf<Meeting?>(null) }
    // 템플릿 선택 다이얼로그에 표시할 대상 회의 (기본 템플릿 미설정 시)
    var meetingForTemplateSelect by remember { mutableStateOf<Meeting?>(null) }

    // RECORD_AUDIO 런타임 권한 요청 (재전사 시 필요)
    var pendingRetranscribeId by remember { mutableStateOf<Long?>(null) }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingRetranscribeId?.let { viewModel.retranscribe(it) }
        }
        pendingRetranscribeId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("전사 목록") }
            )
        }
    ) { innerPadding ->
    if (meetings.isEmpty()) {
        // 빈 목록 안내
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                    contentDescription = "빈 목록",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "전사된 항목이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(meetings, key = { it.id }) { meeting ->
                TranscriptMeetingCard(
                    meeting = meeting,
                    onClick = {
                        // 전사 파일이 있는 상태에서만 편집 화면으로 이동
                        if (meeting.pipelineStatus.isEditable() && meeting.transcriptPath != null) {
                            onEditClick(meeting.id)
                        }
                    },
                    onRenameRequest = { m -> meetingToRename = m },
                    onDeleteRequest = { id ->
                        meetingToDelete = meetings.find { it.id == id }
                    },
                    onGenerateMinutes = { id ->
                        if (defaultTemplateId > 0) {
                            viewModel.generateMinutes(id)
                        } else {
                            meetingForTemplateSelect = meetings.find { it.id == id }
                        }
                    },
                    onRegenerateMinutes = { m ->
                        if (defaultTemplateId > 0) {
                            meetingToRegenerate = m // 재생성 확인 다이얼로그 표시
                        } else {
                            meetingForTemplateSelect = m // 템플릿 선택 다이얼로그로 직접 이동
                        }
                    },
                    onRetranscribe = { id ->
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.retranscribe(id)
                        } else {
                            pendingRetranscribeId = id
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onShare = { id -> viewModel.shareTranscript(id, context) }
                )
            }
        }
    }
    } // Scaffold

    // 이름 편집 대화상자
    meetingToRename?.let { meeting ->
        var editedName by remember(meeting.id) { mutableStateOf(meeting.title) }
        AlertDialog(
            onDismissRequest = { meetingToRename = null },
            title = { Text("이름 편집") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("전사 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editedName.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.updateTitle(meeting.id, trimmed)
                        }
                        meetingToRename = null
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { meetingToRename = null }) { Text("취소") }
            }
        )
    }

    // 전사 삭제 확인 대화상자
    meetingToDelete?.let { meeting ->
        AlertDialog(
            onDismissRequest = { meetingToDelete = null },
            title = { Text("전사 파일 삭제") },
            text = { Text("\"${meeting.title}\"의 전사 파일을 삭제할까요?\n회의록은 보존됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTranscript(meeting.id)
                    meetingToDelete = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { meetingToDelete = null }) { Text("취소") }
            }
        )
    }

    // 회의록 재생성 확인 대화상자 (기본 템플릿 설정 시)
    meetingToRegenerate?.let { meeting ->
        AlertDialog(
            onDismissRequest = { meetingToRegenerate = null },
            title = { Text("회의록 재생성") },
            text = { Text("기존 회의록을 삭제하고 새로 생성할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.generateMinutes(meeting.id)
                    meetingToRegenerate = null
                }) {
                    Text("재생성")
                }
            },
            dismissButton = {
                TextButton(onClick = { meetingToRegenerate = null }) { Text("취소") }
            }
        )
    }

    // 템플릿 선택 다이얼로그 (기본 템플릿 미설정 시)
    meetingForTemplateSelect?.let { meeting ->
        ManualMinutesDialog(
            meetingTitle = meeting.title,
            templates = templates,
            isGenerating = false,
            onGenerate = { templateId, customPrompt ->
                viewModel.generateMinutesWithTemplate(meeting.id, templateId, customPrompt)
                meetingForTemplateSelect = null
            },
            onDismiss = { meetingForTemplateSelect = null }
        )
    }
}

/**
 * 개별 전사 회의 항목 카드.
 * 회의 제목, 녹음 시각, 파이프라인 상태를 표시한다.
 * 더보기(MoreVert) 아이콘으로 재전사/공유/삭제 액션 메뉴를 제공한다.
 */
@Composable
private fun TranscriptMeetingCard(
    meeting: Meeting,
    onClick: () -> Unit,
    onRenameRequest: (Meeting) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onGenerateMinutes: (Long) -> Unit,
    onRegenerateMinutes: (Meeting) -> Unit,
    onRetranscribe: (Long) -> Unit,
    onShare: (Long) -> Unit
) {
    val isEditable = meeting.pipelineStatus.isEditable()
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isEditable) onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 회의 제목 + 파일 종류 아이콘 + 더보기 메뉴
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileTypeIcon(meeting)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRenameRequest(meeting) }
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "더보기"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 재전사: audioFilePath가 있고 진행 중이 아닐 때 표시
                        if (meeting.audioFilePath.isNotBlank() &&
                            meeting.pipelineStatus !in setOf(
                                PipelineStatus.TRANSCRIBING,
                                PipelineStatus.GENERATING_MINUTES
                            )
                        ) {
                            DropdownMenuItem(
                                text = { Text("재전사") },
                                onClick = {
                                    showMenu = false
                                    onRetranscribe(meeting.id)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = "재전사")
                                }
                            )
                        }
                        // 회의록 작성: TRANSCRIBED 또는 (FAILED + transcriptPath 있음)일 때 표시
                        if (meeting.pipelineStatus == PipelineStatus.TRANSCRIBED ||
                            (meeting.pipelineStatus == PipelineStatus.FAILED && meeting.transcriptPath != null)) {
                            DropdownMenuItem(
                                text = { Text("회의록 작성") },
                                onClick = {
                                    showMenu = false
                                    onGenerateMinutes(meeting.id)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = "회의록 작성")
                                }
                            )
                        }
                        // 회의록 재생성: COMPLETED일 때 표시
                        if (meeting.pipelineStatus == PipelineStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("회의록 재생성") },
                                onClick = {
                                    showMenu = false
                                    onRegenerateMinutes(meeting)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = "회의록 재생성")
                                }
                            )
                        }
                        // 공유: transcriptPath가 있을 때만 표시
                        if (meeting.transcriptPath != null) {
                            DropdownMenuItem(
                                text = { Text("공유") },
                                onClick = {
                                    showMenu = false
                                    onShare(meeting.id)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = "공유")
                                }
                            )
                        }
                        // 삭제: 항상 표시
                        DropdownMenuItem(
                            text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteRequest(meeting.id)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 녹음 시각
            Text(
                text = meeting.recordedAt.atZone(ZoneId.systemDefault())
                    .format(DATE_TIME_FORMATTER),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 상태 배지 Row
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TranscriptionStatusBadge(meeting)
                MinutesStatusBadge(meeting)
                // FAILED 상태일 때 오류 배지 표시
                if (meeting.pipelineStatus == PipelineStatus.FAILED) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "오류",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            // 회의록 작성/재생성 버튼은 MoreVert 드롭다운 메뉴로 이동됨
        }
    }
}

/**
 * 파일 종류 아이콘.
 * audioFilePath가 비어있지 않으면 음성 파일, 비어있으면 텍스트 공유로 표시한다.
 */
@Composable
private fun FileTypeIcon(meeting: Meeting) {
    val isAudio = meeting.audioFilePath.isNotBlank()
    Icon(
        imageVector = if (isAudio) Icons.Default.AudioFile else Icons.AutoMirrored.Filled.TextSnippet,
        contentDescription = if (isAudio) "음성 파일" else "텍스트 공유",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 전사 상태 배지.
 * TRANSCRIBING -> CircularProgressIndicator + "전사 중", transcriptPath != null -> "전사 완료", else -> "전사 미완료"
 */
@Composable
private fun TranscriptionStatusBadge(meeting: Meeting) {
    when {
        meeting.pipelineStatus == PipelineStatus.TRANSCRIBING -> {
            // 전사 중: 스피너 + 텍스트
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "전사 중",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        meeting.transcriptPath != null -> {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = "전사 완료",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        else -> {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = "전사 미완료",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 회의록 상태 배지.
 * GENERATING_MINUTES -> "회의록 생성 중", minutesPath != null -> "회의록 완료", else -> "회의록 미작성"
 */
@Composable
private fun MinutesStatusBadge(meeting: Meeting) {
    val (label, containerColor, labelColor) = when {
        meeting.pipelineStatus == PipelineStatus.GENERATING_MINUTES -> Triple(
            "회의록 생성 중",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        meeting.minutesPath != null -> Triple(
            "회의록 완료",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> Triple(
            "회의록 미작성",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}

/** 편집 가능한 상태인지 확인하는 확장 함수 (전사 파일이 존재하는 상태) */
private fun PipelineStatus.isEditable(): Boolean =
    this in setOf(
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.GENERATING_MINUTES,
        PipelineStatus.COMPLETED,
        PipelineStatus.FAILED
    )

/** 녹음 시각 포맷터 (yyyy.MM.dd HH:mm) */
private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
