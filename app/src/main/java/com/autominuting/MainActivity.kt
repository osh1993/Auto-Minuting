package com.autominuting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 메인 액티비티.
 * Hilt 의존성 주입을 위해 @AndroidEntryPoint 어노테이션 사용.
 * 향후 Plan 03에서 Navigation 구조로 교체 예정.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Auto Minuting")
                }
            }
        }
    }
}
