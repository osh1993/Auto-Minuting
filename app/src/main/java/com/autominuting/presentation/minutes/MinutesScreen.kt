package com.autominuting.presentation.minutes

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * 회의록 목록 화면.
 * 상단에 검색바를 제공하며, 생성된 회의록 목록을 Card 형태로 표시한다.
 * 파이프라인 상태를 칩으로 보여주고, 완료된 항목을 탭하면 상세 화면으로 이동한다.
 *
 * @param onMinutesClick 회의록 상세 화면으로 이동하는 콜백 (meetingId 전달)
 * @param viewModel 회의록 목록 상태를 관리하는 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinutesScreen(
    onMinutesClick: (Long) -> Unit = {},
    viewModel: MinutesViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var meetingToDelete by remember { mutableStateOf<Meeting?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 선택 모드에서 뒤로가기 시 선택 해제
    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptySet()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 선택 모드 TopBar
        if (isSelectionMode) {
            TopAppBar(
                title = { Text("${selectedIds.size}개 선택됨") },
                navigationIcon = {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "선택 취소"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showBatchDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "선택 삭제"
                        )
                    }
                }
            )
        }

        // 검색바 (선택 모드가 아닐 때만 표시)
        if (!isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(text = "회의록 검색...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "검색"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "검색어 지우기"
                            )
                        }
                    }
                },
                singleLine = true
            )
        }

        if (meetings.isEmpty()) {
            // 빈 목록 안내: 검색 결과 없음 vs 전체 목록 비어있음 구분
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "검색 결과가 없습니다"
                        } else {
                            "생성된 회의록이 없습니다"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(meetings, key = { it.id }) { meeting ->
                    MinutesMeetingCard(
                        meeting = meeting,
                        isSelectionMode = isSelectionMode,
                        isSelected = meeting.id in selectedIds,
                        onClick = {
                            if (isSelectionMode) {
                                // 선택 모드: toggle 선택
                                selectedIds = if (meeting.id in selectedIds) {
                                    selectedIds - meeting.id
                                } else {
                                    selectedIds + meeting.id
                                }
                            } else {
                                // 일반 모드: COMPLETED 상태에서만 상세 화면으로 이동
                                if (meeting.pipelineStatus == PipelineStatus.COMPLETED && meeting.minutesPath != null) {
                                    onMinutesClick(meeting.id)
                                }
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                // 선택 모드 진입
                                selectedIds = setOf(meeting.id)
                            }
                        },
                        onDeleteRequest = { id ->
                            meetingToDelete = meetings.find { it.id == id }
                        }
                    )
                }
            }
        }

        // 단건 삭제 확인 대화상자
        meetingToDelete?.let { meeting ->
            DeleteConfirmationDialog(
                meetingTitle = meeting.title,
                onConfirm = {
                    viewModel.deleteMeeting(meeting.id)
                    meetingToDelete = null
                },
                onDismiss = { meetingToDelete = null }
            )
        }

        // 일괄 삭제 확인 대화상자
        if (showBatchDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = { Text("회의록 일괄 삭제") },
                text = { Text("${selectedIds.size}개의 회의록을 삭제할까요?\n전사 파일은 보존됩니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelectedMinutes(selectedIds)
                        selectedIds = emptySet()
                        showBatchDeleteDialog = false
                    }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) { Text("취소") }
                }
            )
        }
    }
}

/**
 * 개별 회의록 항목 카드.
 * 회의 제목, 녹음 시각, 파이프라인 상태를 표시한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinutesMeetingCard(
    meeting: Meeting,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequest: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 선택 모드에서 체크박스 표시
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            }

            // 콘텐츠 영역: 클릭/롱클릭 처리
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            if (!isSelectionMode) onLongClick()
                        }
                    )
            ) {
                // 회의 제목
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 출처 뱃지 (삼성 공유)
                if (meeting.source == "SAMSUNG_SHARE") {
                    Spacer(modifier = Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "삼성 공유",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 녹음 시각 + 파이프라인 상태 칩
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
                    MinutesPipelineStatusChip(status = meeting.pipelineStatus)
                }
            }

            // 단건 삭제 버튼 (선택 모드가 아닐 때만 표시)
            // combinedClickable 밖에 위치하여 클릭 이벤트가 정상 전달됨
            if (!isSelectionMode) {
                IconButton(onClick = { onDeleteRequest(meeting.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 회의록 화면용 파이프라인 상태 칩.
 * 상태별로 색상과 텍스트가 다르게 표시된다.
 */
@Composable
private fun MinutesPipelineStatusChip(status: PipelineStatus) {
    val (label, containerColor, labelColor) = when (status) {
        PipelineStatus.COMPLETED -> Triple(
            "회의록 완료",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PipelineStatus.GENERATING_MINUTES -> Triple(
            "생성 중...",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        PipelineStatus.TRANSCRIBED -> Triple(
            "전사 완료 (대기)",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
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

/**
 * 회의록 삭제 확인 대화상자.
 * 삭제 시 관련 오디오, 전사, 회의록 파일이 모두 삭제됨을 안내한다.
 */
@Composable
private fun DeleteConfirmationDialog(
    meetingTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회의록 삭제") },
        text = { Text("\"$meetingTitle\" 회의록을 삭제할까요?\n전사 파일은 보존됩니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/** 녹음 시각 포맷터 (yyyy-MM-dd HH:mm) */
private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
