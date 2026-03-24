package com.autominuting.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.autominuting.worker.TestWorker

/**
 * 설정 화면.
 * WorkManager 테스트 버튼을 포함하여 백그라운드 작업 인프라를 검증할 수 있다.
 * Phase 6에서 자동화 수준 설정 등 실제 설정 UI로 확장 예정.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            onClick = {
                // WorkManager 테스트: OneTimeWorkRequest로 TestWorker 실행
                val workRequest = OneTimeWorkRequestBuilder<TestWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("WorkManager 테스트")
        }

        Text(
            text = "설정 화면 - Phase 6에서 완성 예정",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
