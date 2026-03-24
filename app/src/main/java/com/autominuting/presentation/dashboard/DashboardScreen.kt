package com.autominuting.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 대시보드 화면.
 * 앱의 메인 홈 화면으로, 향후 녹음 상태와 최근 회의록 요약을 표시할 예정.
 */
@Composable
fun DashboardScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("대시보드")
    }
}
