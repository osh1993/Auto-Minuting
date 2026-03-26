package com.autominuting

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.autominuting.service.PipelineNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Auto Minuting 애플리케이션 클래스.
 *
 * Hilt 의존성 주입 그래프의 루트 컨테이너 역할을 하며,
 * WorkManager의 커스텀 Worker 팩토리를 설정하여
 * @HiltWorker 어노테이션이 적용된 Worker에 의존성을 주입한다.
 */
@HiltAndroidApp
class AutoMinutingApplication : Application(), Configuration.Provider {

    /** Hilt Worker 팩토리: @HiltWorker Worker에 의존성 주입 */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // 파이프라인 진행 알림 채널 등록
        PipelineNotificationHelper.createChannel(this)
    }

    /**
     * WorkManager 커스텀 설정을 제공한다.
     * HiltWorkerFactory를 사용하여 @HiltWorker Worker에 의존성을 주입한다.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
