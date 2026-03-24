package com.autominuting.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.R
import com.autominuting.domain.repository.AudioRepository
import com.autominuting.worker.TranscriptionTriggerWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 오디오 수집 Foreground Service.
 *
 * connectedDevice 타입으로 선언되어 백그라운드에서 BLE 연결을 유지하며
 * Plaud 녹음기에서 오디오 파일을 수집한다.
 * 파일 저장 완료 시 WorkManager를 통해 전사 파이프라인을 자동 트리거한다.
 */
@AndroidEntryPoint
class AudioCollectionService : Service() {

    /** AudioRepository: Plaud SDK/Cloud API를 통한 오디오 수집 */
    @Inject
    lateinit var audioRepository: AudioRepository

    /** WorkManager: 전사 파이프라인 Worker 예약 */
    @Inject
    lateinit var workManager: WorkManager

    /** 서비스 전용 코루틴 스코프 (SupervisorJob으로 개별 실패 격리) */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 현재 실행 중인 오디오 수집 작업 */
    private var collectionJob: Job? = null

    /**
     * 서비스 생성 시 알림 채널을 등록한다.
     * Android 8.0(API 26) 이상에서 Foreground Service 알림에 필수.
     */
    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "오디오 수집",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Plaud 녹음기에서 오디오를 수집하는 동안 표시되는 알림"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 서비스 시작 명령을 처리한다.
     *
     * ACTION_START: Foreground Service를 시작하고 오디오 수집을 개시한다.
     * ACTION_STOP: 서비스를 중지한다.
     *
     * @return START_STICKY - 시스템이 서비스를 종료해도 자동 재시작
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Foreground Service 시작 (connectedDevice 타입)
                val notification = createNotification("오디오 수집 대기 중...")
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )

                // 오디오 수집 코루틴 시작
                collectionJob = serviceScope.launch {
                    collectAudio()
                }
            }

            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    /**
     * AudioRepository를 통해 오디오를 수집한다.
     *
     * 수집된 파일 경로를 받을 때마다:
     * 1. 알림을 업데이트하여 저장 완료를 표시
     * 2. WorkManager로 전사 파이프라인을 트리거 (AUD-03)
     */
    private suspend fun collectAudio() {
        audioRepository.startAudioCollection().collect { filePath ->
            updateNotification("오디오 저장 완료: ${File(filePath).name}")
            triggerTranscriptionPipeline(filePath)
        }
    }

    /**
     * 전사 파이프라인을 WorkManager로 트리거한다.
     *
     * 오디오 파일 경로를 inputData로 전달하여
     * TranscriptionTriggerWorker가 전사를 시작하도록 한다.
     *
     * @param filePath 저장된 오디오 파일의 절대 경로
     */
    private fun triggerTranscriptionPipeline(filePath: String) {
        val inputData = workDataOf("audioFilePath" to filePath)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "transcription_${System.currentTimeMillis()}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Foreground Service 알림을 생성한다.
     *
     * @param text 알림에 표시할 텍스트
     * @return 생성된 Notification 객체
     */
    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    /**
     * 알림 텍스트를 업데이트한다.
     *
     * @param text 새로 표시할 알림 텍스트
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 서비스 종료 시 리소스를 정리한다.
     * 수집 작업을 취소하고 코루틴 스코프를 해제한다.
     */
    override fun onDestroy() {
        collectionJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 바인딩 미지원 (Started Service 패턴) */
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Foreground Service 알림 ID */
        const val NOTIFICATION_ID = 1001

        /** 알림 채널 ID */
        const val CHANNEL_ID = "audio_collection_channel"

        /** 오디오 수집 시작 액션 */
        const val ACTION_START = "com.autominuting.action.START_COLLECTION"

        /** 오디오 수집 중지 액션 */
        const val ACTION_STOP = "com.autominuting.action.STOP_COLLECTION"
    }
}
