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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 전사 목록 화면.
 * 전사가 진행 중이거나 완료된 회의 목록을 Card 형태로 표시한다.
 * 전사 완료된 항목을 탭하면 편집 화면으로 이동한다.
 * 전사 완료/완료 상태에서 "회의록 생성" 버튼을 눌러 수동 생성을 시작할 수 있다.
 *
 * @param viewModel 전사 목록 상태를 관리하는 ViewModel
 * @param onEditClick 전사 편집 화면으로 이동하는 콜백 (meetingId 전달)
 */
@Composable
fun TranscriptsScreen(
    viewModel: TranscriptsViewModel = hiltViewModel(),
    onEditClick: (Long) -> Unit = {}
) {
    val meetings by viewModel.meetings.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val isManualGenerating by viewModel.isManualGenerating.collectAsState()

    // 수동 회의록 생성 다이얼로그 대상 회의
    var showManualDialog by remember { mutableStateOf<Meeting?>(null) }

    // Snackbar 상태
    val snackbarHostState = remember { SnackbarHostState() }

    // 수동 생성 결과 수집
    LaunchedEffect(Unit) {
        viewModel.manualGenerationResult.collect { result ->
            when (result) {
                is ManualGenerationResult.Success -> {
                    showManualDialog = null
                    snackbarHostState.showSnackbar("회의록이 생성되었습니다")
                }
                is ManualGenerationResult.Error -> {
                    snackbarHostState.showSnackbar("회의록 생성 실패: ${result.message}")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (meetings.isEmpty()) {
            // 빈 목록 안내
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "전사된 항목이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(meetings, key = { it.id }) { meeting ->
                    TranscriptMeetingCard(
                        meeting = meeting,
                        onClick = {
                            // TRANSCRIBED 이상 상태에서만 편집 화면으로 이동
                            if (meeting.pipelineStatus.isEditable()) {
                                onEditClick(meeting.id)
                            }
                        },
                        onGenerateMinutes = {
                            showManualDialog = meeting
                        }
                    )
                }
            }
        }

        // Snackbar 호스트
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 수동 회의록 생성 다이얼로그
    showManualDialog?.let { meeting ->
        ManualMinutesDialog(
            meetingTitle = meeting.title,
            templates = templates,
            isGenerating = isManualGenerating,
            onGenerate = { templateId, customPrompt ->
                viewModel.generateManualMinutes(meeting.id, templateId, customPrompt)
            },
            onDismiss = { showManualDialog = null }
        )
    }
}

/**
 * 개별 전사 회의 항목 카드.
 * 회의 제목, 녹음 시각, 파이프라인 상태를 표시한다.
 * 전사 완료/완료 상태에서 "회의록 생성" 버튼을 표시한다.
 */
@Composable
private fun TranscriptMeetingCard(
    meeting: Meeting,
    onClick: () -> Unit,
    onGenerateMinutes: () -> Unit
) {
    val isEditable = meeting.pipelineStatus.isEditable()
    // 회의록 생성 가능 상태: 전사 완료 또는 이미 완료된 경우 (재생성)
    val canGenerateMinutes = meeting.pipelineStatus in setOf(
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.COMPLETED,
        PipelineStatus.FAILED
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEditable) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 회의 제목 + 회의록 생성 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 회의록 생성/재생성 버튼
                if (canGenerateMinutes) {
                    IconButton(
                        onClick = onGenerateMinutes,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = if (meeting.pipelineStatus == PipelineStatus.COMPLETED) {
                                "회의록 재생성"
                            } else {
                                "회의록 생성"
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 녹음 시각
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.recordedAt.atZone(ZoneId.systemDefault())
                        .format(DATE_TIME_FORMATTER),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 파이프라인 상태 칩
                PipelineStatusChip(status = meeting.pipelineStatus)
            }
        }
    }
}

/**
 * 파이프라인 상태를 나타내는 칩 컴포넌트.
 * 상태별로 색상과 텍스트가 다르게 표시된다.
 */
@Composable
private fun PipelineStatusChip(status: PipelineStatus) {
    val (label, containerColor, labelColor) = when (status) {
        PipelineStatus.TRANSCRIBING -> Triple(
            "전사 중",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        PipelineStatus.TRANSCRIBED -> Triple(
            "전사 완료",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PipelineStatus.GENERATING_MINUTES -> Triple(
            "회의록 생성 중",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PipelineStatus.COMPLETED -> Triple(
            "완료",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PipelineStatus.FAILED -> Triple(
            "실패",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            status.name,
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

/** 편집 가능한 상태인지 확인하는 확장 함수 (TRANSCRIBED 이상) */
private fun PipelineStatus.isEditable(): Boolean =
    this in setOf(
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.GENERATING_MINUTES,
        PipelineStatus.COMPLETED
    )

/** 녹음 시각 포맷터 (yyyy.MM.dd HH:mm) */
private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
