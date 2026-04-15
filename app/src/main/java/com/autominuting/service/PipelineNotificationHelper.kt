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
     * Gemini API 쿼터 초과 알림을 표시한다.
     * GeminiEngine 내부 재시도 소진 후 Worker가 retry할 때 사용자에게 상황을 알린다.
     *
     * @param context 컨텍스트
     */
    fun notifyQuotaExceeded(context: Context) {
        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText("Gemini API 쿼터 초과 — 잠시 후 자동 재시도합니다")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Gemini API 무료 쿼터가 초과되었습니다.\n잠시 후 자동으로 재시도합니다.")
            )
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(false)
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
    /**
     * 전사 파일 크기 초과 알림을 표시한다.
     * 선택된 STT 엔진의 파일 크기 제한을 초과했을 때 사용자에게 엔진 변경을 안내한다.
     *
     * @param context 컨텍스트
     * @param engineName 초과된 엔진 이름 (예: "Groq Whisper (Cloud)")
     * @param fileSizeMb 실제 파일 크기 (MB)
     * @param limitMb 엔진 제한 크기 (MB)
     */
    fun notifyFileTooLarge(context: Context, engineName: String, fileSizeMb: Long, limitMb: Long) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("전사 엔진 변경 필요")
            .setContentText("파일(${fileSizeMb}MB)이 ${engineName} 제한(${limitMb}MB)을 초과합니다")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "합쳐진 오디오 파일(${fileSizeMb}MB)이 ${engineName}의 파일 크기 제한(${limitMb}MB)을 초과합니다.\n\n" +
                        "설정 화면에서 다른 STT 엔진(Whisper 온디바이스, Gemini, Deepgram 등)으로 변경하면 파일 크기 제한 없이 전사할 수 있습니다."
                    )
            )
            .setAutoCancel(true)
            .setOngoing(false)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }

    /**
     * Gemini API 키 오류 발생 + 자동 전환 알림을 표시한다 (GEMINI-03).
     * 동일 PIPELINE_NOTIFICATION_ID를 재사용하여 알림이 쌓이지 않는다.
     *
     * @param context 컨텍스트
     * @param keyLabel 오류 발생 키의 레이블
     * @param reason 오류 원인 요약 ("할당량 초과", "권한 오류" 등)
     * @param nextKeyLabel 다음 전환 키 레이블. null이면 마지막 키였음을 의미
     */
    fun notifyApiKeyError(
        context: Context,
        keyLabel: String,
        reason: String,
        nextKeyLabel: String?
    ) {
        val bodyText = if (nextKeyLabel != null) {
            "'$keyLabel' 키 $reason — '$nextKeyLabel' 키로 자동 전환합니다"
        } else {
            "'$keyLabel' 키 $reason — 마지막 키입니다. 전환 후 파이프라인을 계속합니다"
        }
        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Gemini API 키 오류")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setOngoing(false)
            .setSilent(false)
            .build()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }

    /**
     * 모든 Gemini API 키가 오류를 반환하여 파이프라인이 중단되었음을 알린다 (GEMINI-03).
     *
     * @param context 컨텍스트
     * @param keyCount 시도한 키 총 개수
     */
    fun notifyAllKeysExhausted(context: Context, keyCount: Int) {
        val bodyText = "등록된 Gemini API 키 ${keyCount}개 모두 오류 — 파이프라인이 중단되었습니다.\n설정에서 유효한 API 키를 확인해주세요."
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Gemini API 오류")
            .setContentText("모든 API 키 오류 — 파이프라인 중단")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setOngoing(false)
            .setSilent(false)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
    }

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
