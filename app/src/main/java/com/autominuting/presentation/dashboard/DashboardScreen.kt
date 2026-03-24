package com.autominuting.presentation.dashboard

import androidx.compose.foundation.layout.Box
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
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominuting.domain.model.PipelineStatus

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
    val isCollecting by viewModel.isCollecting.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()
    val isTestingGemini by viewModel.isTestingGemini.collectAsStateWithLifecycle()

    // BLE 런타임 권한 요청 런처
    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.toggleCollection()
        }
    }

    val requiredPermissions = buildList {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

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

        // 녹음기 연결 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCollecting)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "녹음기 연결",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCollecting)
                        "Plaud 녹음기에서 오디오를 수집하고 있습니다."
                    else
                        "녹음기를 연결하여 오디오 수집을 시작합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCollecting)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isCollecting) {
                    OutlinedButton(
                        onClick = { viewModel.toggleCollection() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("수집 중지")
                    }
                } else {
                    Button(
                        onClick = { blePermissionLauncher.launch(requiredPermissions) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("녹음기 연결 시작")
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

        Spacer(modifier = Modifier.height(32.dp))

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
    }
}
