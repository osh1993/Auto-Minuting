package com.autominuting.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autominuting.presentation.dashboard.DashboardScreen
import com.autominuting.presentation.minutes.MinutesScreen
import com.autominuting.presentation.settings.SettingsScreen
import com.autominuting.presentation.transcripts.TranscriptsScreen

/**
 * 앱의 메인 네비게이션 컴포저블.
 * Bottom Navigation Bar와 NavHost를 포함하여 4개 메인 화면 간 전환을 관리한다.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // 백스택에 시작 화면까지만 유지하여 메모리 절약
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // 같은 화면 중복 생성 방지
                                launchSingleTop = true
                                // 이전 상태 복원
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Transcripts.route) { TranscriptsScreen() }
            composable(Screen.Minutes.route) { MinutesScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
