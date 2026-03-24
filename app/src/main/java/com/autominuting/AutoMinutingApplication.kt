package com.autominuting

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Auto Minuting 애플리케이션 클래스.
 * Hilt 의존성 주입 그래프의 루트 컨테이너 역할을 한다.
 */
@HiltAndroidApp
class AutoMinutingApplication : Application()
