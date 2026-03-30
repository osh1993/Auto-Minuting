package com.autominuting.presentation.minutes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autominuting.domain.model.Minutes
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 회의록 목록 화면.
 * 상단에 검색바를 제공하며, 생성된 회의록 목록을 Card 형태로 표시한다.
 * 항목을 탭하면 상세 화면으로 이동한다.
 *
 * @param onMinutesClick 회의록 상세 화면으로 이동하는 콜백 (minutesId 전달)
 * @param viewModel 회의록 목록 상태를 관리하는 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinutesScreen(
    onMinutesClick: (Long) -> Unit = {},
    viewModel: MinutesViewModel = hiltViewModel()
) {
    val minutesList by viewModel.minutes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    var minutesToDelete by remember { mutableStateOf<Minutes?>(null) }
    var minutesToRename by remember { mutableStateOf<Minutes?>(null) }
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

        if (minutesList.isEmpty()) {
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
                        contentDescription = "빈 목록",
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
                items(minutesList, key = { it.id }) { minutes ->
                    MinutesCard(
                        minutes = minutes,
                        isSelectionMode = isSelectionMode,
                        isSelected = minutes.id in selectedIds,
                        onClick = {
                            if (isSelectionMode) {
                                // 선택 모드: toggle 선택
                                selectedIds = if (minutes.id in selectedIds) {
                                    selectedIds - minutes.id
                                } else {
                                    selectedIds + minutes.id
                                }
                            } else {
                                // 일반 모드: 회의록 상세 화면으로 이동
                                onMinutesClick(minutes.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                // 선택 모드 진입
                                selectedIds = setOf(minutes.id)
                            }
                        },
                        onRenameRequest = { m -> minutesToRename = m },
                        onDeleteRequest = { id ->
                            minutesToDelete = minutesList.find { it.id == id }
                        },
                        onShare = { id -> viewModel.shareMinutes(id, context) }
                    )
                }
            }
        }

        // 이름 편집 대화상자
        minutesToRename?.let { minutes ->
            var editedName by remember(minutes.id) {
                mutableStateOf(minutes.minutesTitle ?: "")
            }
            AlertDialog(
                onDismissRequest = { minutesToRename = null },
                title = { Text("회의록 이름 편집") },
                text = {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("회의록 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = editedName.trim()
                            if (trimmed.isNotBlank()) {
                                viewModel.updateMinutesTitle(minutes.id, trimmed)
                            }
                            minutesToRename = null
                        }
                    ) { Text("확인") }
                },
                dismissButton = {
                    TextButton(onClick = { minutesToRename = null }) { Text("취소") }
                }
            )
        }

        // 단건 삭제 확인 대화상자
        minutesToDelete?.let { minutes ->
            DeleteConfirmationDialog(
                minutesTitle = minutes.minutesTitle ?: "회의록",
                onConfirm = {
                    viewModel.deleteMinutes(minutes.id)
                    minutesToDelete = null
                },
                onDismiss = { minutesToDelete = null }
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
 * 회의록 제목과 생성 시각을 표시한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinutesCard(
    minutes: Minutes,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameRequest: (Minutes) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onShare: (Long) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
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
                // 회의록 제목 (minutesTitle, 없으면 "회의록" 표시)
                Text(
                    text = minutes.minutesTitle ?: "회의록",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onRenameRequest(minutes) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 생성 시각
                Text(
                    text = minutes.createdAt.atZone(ZoneId.systemDefault())
                        .format(DATE_TIME_FORMATTER),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // MoreVert 액션 메뉴 (선택 모드가 아닐 때만 표시)
            if (!isSelectionMode) {
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
                        // 공유: 항상 표시 (Minutes Row가 있으면 minutesPath가 NOT NULL)
                        DropdownMenuItem(
                            text = { Text("공유") },
                            onClick = {
                                showMenu = false
                                onShare(minutes.id)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = "공유")
                            }
                        )
                        // 삭제: 항상 표시
                        DropdownMenuItem(
                            text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteRequest(minutes.id)
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
        }
    }
}

/**
 * 회의록 삭제 확인 대화상자.
 * 삭제 시 전사 파일은 보존됨을 안내한다.
 */
@Composable
private fun DeleteConfirmationDialog(
    minutesTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회의록 삭제") },
        text = { Text("\"$minutesTitle\" 회의록을 삭제할까요?\n전사 파일은 보존됩니다.") },
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

/** 생성 시각 포맷터 (yyyy.MM.dd HH:mm) */
private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
