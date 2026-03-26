package com.autominuting.presentation.templates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominuting.domain.model.PromptTemplate

/**
 * 프롬프트 템플릿 관리 화면.
 * 템플릿 목록 표시, 추가/편집/삭제 기능을 제공한다.
 *
 * @param onBack 뒤로가기 콜백
 * @param viewModel 템플릿 관리 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateScreen(
    onBack: () -> Unit,
    viewModel: PromptTemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    // 추가/편집 다이얼로그 상태
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<PromptTemplate?>(null) }

    // 삭제 확인 다이얼로그 상태
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingTemplate by remember { mutableStateOf<PromptTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프롬프트 템플릿") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingTemplate = null
                        showEditDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "템플릿 추가"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onEdit = {
                        editingTemplate = template
                        showEditDialog = true
                    },
                    onDelete = {
                        deletingTemplate = template
                        showDeleteDialog = true
                    }
                )
            }
        }

        // 추가/편집 다이얼로그
        if (showEditDialog) {
            TemplateEditDialog(
                template = editingTemplate,
                onDismiss = { showEditDialog = false },
                onSave = { name, promptText ->
                    val existing = editingTemplate
                    if (existing != null) {
                        viewModel.updateTemplate(existing.copy(name = name, promptText = promptText))
                    } else {
                        viewModel.addTemplate(name, promptText)
                    }
                    showEditDialog = false
                }
            )
        }

        // 삭제 확인 다이얼로그
        if (showDeleteDialog && deletingTemplate != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("템플릿 삭제") },
                text = { Text("이 템플릿을 삭제할까요?") },
                confirmButton = {
                    TextButton(onClick = {
                        deletingTemplate?.let { viewModel.deleteTemplate(it.id) }
                        showDeleteDialog = false
                    }) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("취소")
                    }
                }
            )
        }
    }
}

/**
 * 템플릿 카드 컴포저블.
 * 탭하면 편집, 롱프레스하면 삭제 (기본 제공 템플릿은 삭제 불가).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateCard(
    template: PromptTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = if (!template.isBuiltIn) onDelete else null
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (template.isBuiltIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("기본 제공") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = template.promptText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 템플릿 추가/편집 다이얼로그.
 *
 * @param template 편집할 템플릿 (null이면 새로 추가)
 * @param onDismiss 다이얼로그 닫기 콜백
 * @param onSave 저장 콜백 (이름, 프롬프트 텍스트)
 */
@Composable
private fun TemplateEditDialog(
    template: PromptTemplate?,
    onDismiss: () -> Unit,
    onSave: (name: String, promptText: String) -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var promptText by remember { mutableStateOf(template?.promptText ?: "") }
    val isEditing = template != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "템플릿 편집" else "새 템플릿 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    readOnly = template?.isBuiltIn == true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("프롬프트 텍스트") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), promptText.trim()) },
                enabled = name.isNotBlank() && promptText.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
