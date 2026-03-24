package com.autominuting.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 앱의 화면 경로를 정의하는 sealed class.
 * Bottom Navigation에서 사용되는 4개 메인 화면을 포함한다.
 *
 * @param route Navigation 경로 문자열
 * @param title 화면 제목 (Bottom Navigation 라벨)
 * @param icon 화면 아이콘 (Bottom Navigation 아이콘)
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    /** 대시보드 - 앱의 메인 홈 화면 */
    data object Dashboard : Screen("dashboard", "대시보드", Icons.Default.Home)

    /** 전사 목록 - 음성 전사 결과 목록 화면 */
    data object Transcripts : Screen("transcripts", "전사 목록", Icons.Default.Description)

    /** 회의록 - 생성된 회의록 목록 화면 */
    data object Minutes : Screen("minutes", "회의록", Icons.Default.List)

    /** 설정 - 앱 설정 화면 */
    data object Settings : Screen("settings", "설정", Icons.Default.Settings)

    companion object {
        /** Bottom Navigation에 표시될 화면 목록 */
        val bottomNavItems = listOf(Dashboard, Transcripts, Minutes, Settings)
    }
}
