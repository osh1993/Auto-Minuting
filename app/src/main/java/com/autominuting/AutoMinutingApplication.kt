package com.autominuting

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.service.PipelineNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    companion object {
        private const val TAG = "AutoMinutingApp"
    }

    /** Hilt Worker 팩토리: @HiltWorker Worker에 의존성 주입 */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** MeetingDao: 앱 시작 시 stale 상태 복구용 */
    @Inject
    lateinit var meetingDao: MeetingDao

    /** Application 범위 코루틴 스코프 (앱 종료 시까지 유지) */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 파이프라인 진행 알림 채널 등록
        PipelineNotificationHelper.createChannel(this)

        // Worker 비정상 종료로 인한 stale 상태 복구
        // TRANSCRIBING, GENERATING_MINUTES 상태로 남아있는 회의를 FAILED로 전환
        recoverStalePipelineStates()
    }

    /**
     * 앱 시작 시 진행 중 상태로 남아있는 회의를 FAILED로 복구한다.
     *
     * Worker가 비정상 종료(WorkManager kill, 프로세스 종료 등)되면
     * TRANSCRIBING/GENERATING_MINUTES 상태에서 영구 hang이 발생할 수 있다.
     * 이를 방지하기 위해 앱 시작마다 stale 상태를 정리한다.
     */
    private fun recoverStalePipelineStates() {
        appScope.launch {
            try {
                val recovered = meetingDao.recoverStaleInProgressMeetings(
                    errorMessage = "앱 재시작으로 인한 자동 복구 (이전 작업이 비정상 종료됨)",
                    updatedAt = System.currentTimeMillis()
                )
                if (recovered > 0) {
                    Log.w(TAG, "stale 파이프라인 상태 복구: ${recovered}건 (TRANSCRIBING/GENERATING_MINUTES → FAILED)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "stale 파이프라인 상태 복구 실패: ${e.message}", e)
            }
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
}
