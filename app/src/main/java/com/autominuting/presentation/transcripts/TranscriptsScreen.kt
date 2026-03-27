package com.autominuting.presentation.transcripts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    var meetingToDelete by remember { mutableStateOf<Meeting?>(null) }
    // 회의록 재생성 확인 다이얼로그에 표시할 대상 회의
    var meetingToRegenerate by remember { mutableStateOf<Meeting?>(null) }

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
                    onDeleteRequest = { id ->
                        meetingToDelete = meetings.find { it.id == id }
                    },
                    onGenerateMinutes = { id -> viewModel.generateMinutes(id) },
                    onRegenerateMinutes = { m -> meetingToRegenerate = m }
                )
            }
        }
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

    // 회의록 재생성 확인 대화상자
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
}

/**
 * 개별 전사 회의 항목 카드.
 * 회의 제목, 녹음 시각, 파이프라인 상태를 표시한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptMeetingCard(
    meeting: Meeting,
    onClick: () -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onGenerateMinutes: (Long) -> Unit,
    onRegenerateMinutes: (Meeting) -> Unit
) {
    val isEditable = meeting.pipelineStatus.isEditable()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isEditable) onClick() },
                onLongClick = { onDeleteRequest(meeting.id) }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 회의 제목
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

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

            // 회의록 작성/재생성 버튼 (TRANSCRIBING, GENERATING_MINUTES 상태에서는 미표시)
            when (meeting.pipelineStatus) {
                PipelineStatus.TRANSCRIBED, PipelineStatus.FAILED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { onGenerateMinutes(meeting.id) }
                        ) {
                            Text("회의록 작성")
                        }
                    }
                }
                PipelineStatus.COMPLETED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onRegenerateMinutes(meeting) }
                        ) {
                            Text("회의록 재생성")
                        }
                    }
                }
                else -> Unit
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
