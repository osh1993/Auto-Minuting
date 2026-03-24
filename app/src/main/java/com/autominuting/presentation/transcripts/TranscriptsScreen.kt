package com.autominuting.presentation.transcripts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 전사 목록 화면.
 * 음성 파일에서 변환된 전사 결과 목록을 표시할 예정.
 */
@Composable
fun TranscriptsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("전사 목록")
    }
}
