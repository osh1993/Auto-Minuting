package com.autominuting.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat

/**
 * 설정 화면.
 * 회의록 형식 선택(드롭다운)과 자동화 모드 토글(Switch)을 제공한다.
 * DataStore를 통해 설정이 즉시 저장된다.
 *
 * @param viewModel 설정 상태를 관리하는 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedFormat by viewModel.minutesFormat.collectAsStateWithLifecycle()
    val automationMode by viewModel.automationMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 회의록 형식 섹션
            Text(
                text = "회의록 형식",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "기본 회의록 생성 형식을 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 형식 드롭다운
            var expanded by remember { mutableStateOf(false) }
            val formatLabels = mapOf(
                MinutesFormat.STRUCTURED to "구조화된 회의록",
                MinutesFormat.SUMMARY to "요약",
                MinutesFormat.ACTION_ITEMS to "액션 아이템"
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = formatLabels[selectedFormat] ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MinutesFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(formatLabels[format] ?: format.name) },
                            onClick = {
                                viewModel.setMinutesFormat(format)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 자동화 모드 섹션
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "자동화 모드",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "완전 자동 모드",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "오디오 감지부터 회의록 생성까지 자동 진행",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = automationMode == AutomationMode.FULL_AUTO,
                    onCheckedChange = { isAuto ->
                        viewModel.setAutomationMode(
                            if (isAuto) AutomationMode.FULL_AUTO else AutomationMode.HYBRID
                        )
                    }
                )
            }

            if (automationMode == AutomationMode.HYBRID) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "하이브리드 모드: 전사 완료 후 확인을 거쳐 회의록 생성",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
