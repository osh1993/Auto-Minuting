package com.autominuting.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.worker.MinutesGenerationWorker

/**
 * 파이프라인 알림 액션을 처리하는 BroadcastReceiver.
 *
 * PipelineNotificationHelper에서 생성한 알림의 액션 버튼을 처리한다:
 * - GENERATE_MINUTES: 하이브리드 모드에서 사용자가 "회의록 생성 시작" 버튼을 누르면 MinutesGenerationWorker를 enqueue
 * - SHARE_MINUTES: 완료 알림에서 "공유" 버튼을 누르면 공유 Intent를 시작
 */
class PipelineActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_GENERATE_MINUTES -> {
                val meetingId = intent.getLongExtra("meetingId", -1L)
                val transcriptPath = intent.getStringExtra("transcriptPath") ?: return
                val customPrompt = intent.getStringExtra("customPrompt")

                Log.d(TAG, "회의록 생성 요청: meetingId=$meetingId")

                val workRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
                    .setInputData(
                        workDataOf(
                            MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                            MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                            MinutesGenerationWorker.KEY_CUSTOM_PROMPT to customPrompt
                        )
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }

            ACTION_SHARE_MINUTES -> {
                val minutesText = intent.getStringExtra("minutesText") ?: return

                Log.d(TAG, "회의록 공유 요청")

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, minutesText)
                    type = "text/plain"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    companion object {
        /** 회의록 생성 시작 액션 (하이브리드 모드 알림에서 사용) */
        const val ACTION_GENERATE_MINUTES = "com.autominuting.action.GENERATE_MINUTES"

        /** 회의록 공유 액션 (완료 알림에서 사용) */
        const val ACTION_SHARE_MINUTES = "com.autominuting.action.SHARE_MINUTES"

        private const val TAG = "PipelineAction"
    }
}
