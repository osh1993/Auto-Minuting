package com.autominuting.presentation.transcripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autominuting.domain.model.PromptTemplate

/**
 * 수동 회의록 생성 다이얼로그.
 * 프롬프트 템플릿 선택 또는 직접 입력 모드를 제공한다.
 *
 * @param meetingTitle 회의 제목 (상단 표시용)
 * @param templates 사용 가능한 프롬프트 템플릿 목록
 * @param isGenerating 생성 진행 중 여부
 * @param onGenerate 생성 콜백 (templateId 또는 customPrompt 중 하나 전달)
 * @param onDismiss 다이얼로그 닫기 콜백
 */
@Composable
fun ManualMinutesDialog(
    meetingTitle: String,
    templates: List<PromptTemplate>,
    isGenerating: Boolean,
    onGenerate: (templateId: Long?, customPrompt: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 0: 템플릿 선택, 1: 직접 입력
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTemplateId by remember { mutableLongStateOf(-1L) }
    var customPromptText by remember { mutableStateOf("") }

    // 생성 버튼 활성화 조건
    val isGenerateEnabled = !isGenerating && when (selectedTab) {
        0 -> selectedTemplateId >= 0
        1 -> customPromptText.isNotBlank()
        else -> false
    }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Column {
                Text("회의록 생성")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meetingTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 모드 선택 탭
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("템플릿 선택") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("직접 입력") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    // 템플릿 선택 모드
                    0 -> {
                        if (templates.isEmpty()) {
                            Text(
                                text = "등록된 템플릿이 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(templates, key = { it.id }) { template ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedTemplateId == template.id,
                                                onClick = { selectedTemplateId = template.id },
                                                role = Role.RadioButton
                                            )
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedTemplateId == template.id,
                                            onClick = null // Row에서 처리
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = template.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
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
                            }
                        }
                    }
                    // 직접 입력 모드
                    1 -> {
                        OutlinedTextField(
                            value = customPromptText,
                            onValueChange = { customPromptText = it },
                            label = { Text("프롬프트 입력") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            minLines = 5,
                            enabled = !isGenerating
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isGenerating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("생성 중...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> onGenerate(selectedTemplateId, null)
                            1 -> onGenerate(null, customPromptText)
                        }
                    },
                    enabled = isGenerateEnabled
                ) {
                    Text("생성")
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}
