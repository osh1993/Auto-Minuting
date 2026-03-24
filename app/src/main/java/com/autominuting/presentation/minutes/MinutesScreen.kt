package com.autominuting.presentation.minutes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 회의록 목록 화면.
 * 생성된 회의록 목록을 표시할 예정.
 */
@Composable
fun MinutesScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("회의록 목록")
    }
}
