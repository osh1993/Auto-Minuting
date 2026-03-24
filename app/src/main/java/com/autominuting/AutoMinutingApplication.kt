package com.autominuting

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.autominuting.data.audio.PlaudSdkManager
import com.autominuting.service.PipelineNotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Auto Minuting 애플리케이션 클래스.
 *
 * Hilt 의존성 주입 그래프의 루트 컨테이너 역할을 하며,
 * 앱 시작 시 Plaud SDK를 초기화한다.
 *
 * WorkManager의 커스텀 Worker 팩토리를 설정하여
 * @HiltWorker 어노테이션이 적용된 Worker에 의존성을 주입한다.
 */
@HiltAndroidApp
class AutoMinutingApplication : Application(), Configuration.Provider {

    /** Hilt Worker 팩토리: @HiltWorker Worker에 의존성 주입 */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Hilt EntryPoint: Application에서 싱글톤 컴포넌트의 의존성에 접근하기 위한 인터페이스.
     * Application 자체는 Hilt 컴포넌트 루트이므로 직접 @Inject를 사용할 수 없어
     * EntryPoint 패턴을 사용한다.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        /** PlaudSdkManager 인스턴스를 반환한다 */
        fun plaudSdkManager(): PlaudSdkManager
    }

    /**
     * 앱 시작 시 Plaud SDK를 초기화한다.
     *
     * BuildConfig에서 appKey/appSecret을 읽어 SDK를 초기화하며,
     * 키가 설정되지 않은 경우(빈 문자열) 초기화를 건너뛰고
     * Cloud API 폴백 모드로 동작한다.
     */
    override fun onCreate() {
        super.onCreate()

        // EntryPoint를 통해 PlaudSdkManager 획득
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val sdkManager = entryPoint.plaudSdkManager()

        // Plaud SDK 초기화 (appKey 미설정 시 Cloud API 폴백)
        val appKey = BuildConfig.PLAUD_APP_KEY
        val appSecret = BuildConfig.PLAUD_APP_SECRET

        // 파이프라인 진행 알림 채널 등록
        PipelineNotificationHelper.createChannel(this)

        if (appKey.isNotBlank() && appSecret.isNotBlank()) {
            sdkManager.initialize(appKey, appSecret)
            Log.d(TAG, "Plaud SDK 초기화 완료")
        } else {
            // appKey가 설정되지 않으면 Cloud API 폴백 모드로 동작
            // local.properties에 PLAUD_APP_KEY, PLAUD_APP_SECRET을 설정하면 SDK 모드로 전환
            Log.w(TAG, "Plaud SDK appKey 미설정 - Cloud API 폴백 모드로 동작")
        }
    }

    /**
     * WorkManager 커스텀 설정을 제공한다.
     * HiltWorkerFactory를 사용하여 @HiltWorker Worker에 의존성을 주입한다.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "AutoMinutingApp"
    }
}
