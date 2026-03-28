package com.autominuting.presentation.dashboard

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.platform.LocalContext
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.util.NotebookLmHelper

/**
 * 대시보드 화면.
 * 앱의 메인 홈 화면으로, 진행 중인 파이프라인이 있으면 상단 배너로 상태를 표시한다.
 * 디버그 모드에서 테스트 데이터 삽입 및 Gemini API 테스트 버튼을 제공한다.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val activePipeline by viewModel.activePipeline.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()
    val isTestingGemini by viewModel.isTestingGemini.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var urlText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 진행 중인 파이프라인 배너
        activePipeline?.let { meeting ->
            val statusText = when (meeting.pipelineStatus) {
                PipelineStatus.AUDIO_RECEIVED -> "오디오 수신됨"
                PipelineStatus.TRANSCRIBING -> "전사 중..."
                PipelineStatus.TRANSCRIBED -> "전사 완료 — 회의록 생성 대기"
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
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
                }
            }
        }

        // 대시보드 타이틀
        Text(
            text = "Auto Minuting",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        )

        Text(
            text = "녹음에서 회의록까지, 자동으로.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
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
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NotebookLM 열기")
                }
            }
        }

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
                    placeholder = { Text("https://example.com/audio.m4a") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = downloadState !is DashboardViewModel.DownloadState.Downloading
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 테스트 도구 섹션
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "테스트 도구",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 테스트 데이터 삽입 버튼
                Button(
                    onClick = { viewModel.insertTestData() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("더미 회의록 3건 삽입")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gemini API 테스트 버튼
                OutlinedButton(
                    onClick = { viewModel.testGeminiApi() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTestingGemini
                ) {
                    if (isTestingGemini) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isTestingGemini) "Gemini API 호출 중..." else "Gemini API로 회의록 생성 테스트")
                }

                // 상태 메시지
                if (testStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = testStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testStatus.startsWith("✓"))
                            MaterialTheme.colorScheme.primary
                        else if (testStatus.startsWith("✗"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 하단 여백
        Spacer(modifier = Modifier.height(16.dp))
    }
}
