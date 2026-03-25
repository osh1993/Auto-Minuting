package com.autominuting.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.autominuting.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 삼성 녹음앱 파일 감지를 위한 Foreground Service 스파이크 구현.
 *
 * ContentObserver 2개를 등록하여 MediaStore.Audio와 MediaStore.Files 변경을 감시한다.
 * Foreground Service로 실행되어 앱이 백그라운드에 있어도 Observer가 유지된다.
 *
 * foregroundServiceType="specialUse"로 선언 (스파이크 목적 — 파일 모니터링).
 *
 * 이 코드는 스파이크 전용이며 임시 코드이다.
 */
class SpikeService : Service() {

    /** MediaStore.Audio 변경 감시 Observer */
    private var audioObserver: SamsungRecorderObserver? = null

    /** MediaStore.Files 변경 감시 Observer */
    private var filesObserver: SamsungRecorderObserver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SpikeService 생성됨")

        // 알림 채널 생성
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "감시 서비스 시작")

                // Foreground Service 시작 (specialUse 타입)
                val notification = createNotification("삼성 녹음앱 감시 중")
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )

                // Observer 등록
                registerObservers()

                // 감시 상태 업데이트
                _isRunning.value = true
            }

            ACTION_STOP -> {
                Log.d(TAG, "감시 서비스 중지")
                _isRunning.value = false
                stopSelf()
            }
        }

        return START_STICKY
    }

    /**
     * ContentObserver 2개를 등록한다.
     * 1) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI — 오디오 파일 감지
     * 2) MediaStore.Files.getContentUri(VOLUME_EXTERNAL) — 모든 파일 감지 (텍스트 파일 포함)
     */
    private fun registerObservers() {
        val handler = Handler(Looper.getMainLooper())

        // 오디오 파일 Observer
        audioObserver = SamsungRecorderObserver(handler, contentResolver, _detectionFlow).also {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true, // notifyForDescendants
                it
            )
            Log.d(TAG, "MediaStore.Audio Observer 등록 완료")
        }

        // 전체 파일 Observer (텍스트 파일 감지 시도)
        filesObserver = SamsungRecorderObserver(handler, contentResolver, _detectionFlow).also {
            contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                it
            )
            Log.d(TAG, "MediaStore.Files Observer 등록 완료")
        }
    }

    /**
     * 알림 채널 "spike_channel"을 생성한다.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "스파이크 감시",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "삼성 녹음앱 파일 감지 스파이크 서비스 알림"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Foreground Service용 ongoing 알림을 생성한다.
     */
    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("삼성 녹음앱 감지 스파이크")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 서비스 종료 시 Observer를 해제한다.
     */
    override fun onDestroy() {
        audioObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "MediaStore.Audio Observer 해제됨")
        }
        filesObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "MediaStore.Files Observer 해제됨")
        }
        audioObserver = null
        filesObserver = null
        _isRunning.value = false
        Log.d(TAG, "SpikeService 종료됨")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SpikeService"

        /** Foreground Service 알림 ID */
        const val NOTIFICATION_ID = 3001

        /** 알림 채널 ID */
        const val CHANNEL_ID = "spike_channel"

        /** 서비스 시작 액션 */
        const val ACTION_START = "com.autominuting.spike.ACTION_START"

        /** 서비스 중지 액션 */
        const val ACTION_STOP = "com.autominuting.spike.ACTION_STOP"

        /** 감지 결과 SharedFlow (Activity에서 collect) */
        private val _detectionFlow = MutableSharedFlow<DetectionEvent>(
            replay = 50, // 최근 50개 이벤트 유지
            extraBufferCapacity = 50
        )
        val detectionFlow: SharedFlow<DetectionEvent> = _detectionFlow.asSharedFlow()

        /** 서비스 실행 상태 */
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
