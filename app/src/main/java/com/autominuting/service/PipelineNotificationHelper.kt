package com.autominuting.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.autominuting.R

/**
 * 파이프라인 진행 상태 알림을 관리하는 유틸리티 객체.
 *
 * 회의록 생성 파이프라인의 각 단계(전사, 회의록 생성, 완료)에서
 * 알림을 생성하고 업데이트한다.
 */
object PipelineNotificationHelper {

    /** 파이프라인 진행 알림 채널 ID */
    const val PIPELINE_CHANNEL_ID = "pipeline_progress_channel"

    /** 파이프라인 진행 알림 ID */
    const val PIPELINE_NOTIFICATION_ID = 2001

    /**
     * 파이프라인 진행 알림 채널을 생성한다.
     * Application.onCreate()에서 호출하여 앱 시작 시 채널을 등록한다.
     *
     * @param context 애플리케이션 컨텍스트
     * @return 생성된 NotificationChannel
     */
    fun createChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            PIPELINE_CHANNEL_ID,
            "파이프라인 진행",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "회의록 생성 파이프라인 진행 상태 알림"
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return channel
    }

    /**
     * 파이프라인 진행 상태 알림을 업데이트한다.
     *
     * @param context 컨텍스트
     * @param text 알림에 표시할 상태 텍스트
     * @param ongoing true이면 사용자가 스와이프로 제거할 수 없는 지속 알림
     */
    fun updateProgress(context: Context, text: String, ongoing: Boolean = true) {
        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText(text)
            .setOngoing(ongoing)
            .setSilent(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }

    /**
     * 회의록 생성 완료 알림을 표시한다.
     * 공유 액션 버튼을 포함하여 사용자가 바로 회의록을 공유할 수 있다.
     *
     * @param context 컨텍스트
     * @param meetingId 회의 ID
     * @param minutesText 생성된 회의록 텍스트
     */
    fun notifyComplete(context: Context, meetingId: Long, minutesText: String) {
        // 공유 액션 Intent
        val shareIntent = Intent().apply {
            action = "com.autominuting.action.SHARE_MINUTES"
            putExtra("meetingId", meetingId)
            putExtra("minutesText", minutesText)
        }
        val sharePendingIntent = PendingIntent.getBroadcast(
            context,
            meetingId.toInt(),
            shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText("회의록 생성이 완료되었습니다")
            .setAutoCancel(true)
            .setOngoing(false)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "공유",
                sharePendingIntent
            )
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }

    /**
     * 전사 완료 알림을 표시한다 (하이브리드 모드용).
     * 사용자에게 전사 결과를 확인하고 회의록 생성을 시작할 수 있는 액션 버튼을 제공한다.
     *
     * @param context 컨텍스트
     * @param meetingId 회의 ID
     * @param transcriptPath 전사 텍스트 파일 경로
     */
    fun notifyTranscriptionComplete(
        context: Context,
        meetingId: Long,
        transcriptPath: String,
        customPrompt: String? = null
    ) {
        // 회의록 생성 시작 액션 Intent
        val generateIntent = Intent().apply {
            action = "com.autominuting.action.GENERATE_MINUTES"
            putExtra("meetingId", meetingId)
            putExtra("transcriptPath", transcriptPath)
            customPrompt?.let { putExtra("customPrompt", it) }
        }
        val generatePendingIntent = PendingIntent.getBroadcast(
            context,
            meetingId.toInt(),
            generateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("전사 완료")
            .setContentText("전사 결과를 확인하고 회의록 생성을 시작하세요")
            .setAutoCancel(true)
            .setOngoing(false)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "회의록 생성 시작",
                generatePendingIntent
            )
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }
}
