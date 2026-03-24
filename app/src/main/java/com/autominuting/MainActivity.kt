package com.autominuting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.autominuting.presentation.navigation.AppNavigation
import com.autominuting.presentation.theme.AutoMinutingTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 메인 액티비티.
 * Hilt 의존성 주입을 위해 @AndroidEntryPoint 어노테이션 사용.
 * AutoMinutingTheme과 AppNavigation으로 4개 화면을 Bottom Navigation으로 연결한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoMinutingTheme {
                AppNavigation()
            }
        }
    }
}
