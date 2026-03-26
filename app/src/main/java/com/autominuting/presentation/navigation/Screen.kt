package com.autominuting.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
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

    /** 전사 편집 - 전사 텍스트 편집 화면 (Bottom Navigation에 표시되지 않음) */
    data object TranscriptEdit : Screen(
        "transcripts/{meetingId}/edit",
        "전사 편집",
        Icons.Default.Edit
    ) {
        /** meetingId를 포함한 실제 Navigation 경로를 생성한다. */
        fun createRoute(meetingId: Long) = "transcripts/$meetingId/edit"
    }

    /** 프롬프트 템플릿 - 설정 하위 화면 (Bottom Navigation에 표시되지 않음) */
    data object PromptTemplates : Screen(
        "settings/templates",
        "프롬프트 템플릿",
        Icons.Default.Edit
    )

    /** 회의록 상세 - 회의록 내용 읽기 화면 (Bottom Navigation에 표시되지 않음) */
    data object MinutesDetail : Screen(
        "minutes/{meetingId}",
        "회의록 상세",
        Icons.Default.Description
    ) {
        /** meetingId를 포함한 실제 Navigation 경로를 생성한다. */
        fun createRoute(meetingId: Long) = "minutes/$meetingId"
    }

    companion object {
        /** Bottom Navigation에 표시될 화면 목록 */
        val bottomNavItems = listOf(Dashboard, Transcripts, Minutes, Settings)
    }
}
