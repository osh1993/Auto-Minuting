package com.autominuting.presentation.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.service.PipelineNotificationHelper
import com.autominuting.worker.MinutesGenerationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 외부 앱(삼성 녹음앱 등)에서 ACTION_SEND intent로 공유된 텍스트를 수신하여
 * STT 단계를 건너뛰고 회의록 생성 파이프라인에 자동 진입시키는 Activity.
 *
 * UI 없이 투명하게 동작하며, 공유 텍스트를 파일로 저장하고
 * MeetingEntity(source=SAMSUNG_SHARE)를 생성한 뒤 MinutesGenerationWorker를 enqueue한다.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var meetingRepository: MeetingRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ACTION_SEND + text/plain 검증
        if (intent?.action != Intent.ACTION_SEND || intent?.type != "text/plain") {
            Log.w(TAG, "지원하지 않는 intent: action=${intent?.action}, type=${intent?.type}")
            finish()
            return
        }

        // 공유 텍스트 추출
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrBlank() || sharedText.length < 10) {
            Toast.makeText(this, "공유된 텍스트가 너무 짧습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 제목 추출: 첫 줄 사용, 30자 초과 시 자르기
        val title = extractTitle(sharedText)

        lifecycleScope.launch {
            try {
                processSharedText(sharedText, title)
            } catch (e: Exception) {
                Log.e(TAG, "공유 텍스트 처리 실패", e)
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "처리 중 오류가 발생했습니다",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                finish()
            }
        }
    }

    /**
     * 공유된 텍스트를 파일로 저장하고 파이프라인에 진입시킨다.
     */
    private suspend fun processSharedText(sharedText: String, title: String) {
        val now = System.currentTimeMillis()

        // 1. 전사 텍스트를 임시 파일로 저장
        val transcriptsDir = File(filesDir, "transcripts")
        transcriptsDir.mkdirs()
        val tempFile = File(transcriptsDir, "share_${now}.txt")
        tempFile.writeText(sharedText)

        // 2. MeetingEntity 생성 및 DB insert
        val meeting = Meeting(
            title = title,
            recordedAt = Instant.ofEpochMilli(now),
            audioFilePath = "", // 공유 방식은 오디오 파일 없음 (Room non-null 필드)
            transcriptPath = tempFile.absolutePath,
            pipelineStatus = PipelineStatus.TRANSCRIBED, // STT 단계 건너뜀
            source = "SAMSUNG_SHARE",
            createdAt = Instant.ofEpochMilli(now),
            updatedAt = Instant.ofEpochMilli(now)
        )
        val meetingId = meetingRepository.insertMeeting(meeting)

        // 3. 파일명을 meetingId 기반으로 rename
        val finalFile = File(transcriptsDir, "${meetingId}.txt")
        if (tempFile.renameTo(finalFile)) {
            // transcriptPath 업데이트를 위해 meeting 업데이트
            meetingRepository.updateMeeting(
                meeting.copy(
                    id = meetingId,
                    transcriptPath = finalFile.absolutePath,
                    updatedAt = Instant.now()
                )
            )
        }

        val transcriptPath = if (finalFile.exists()) finalFile.absolutePath else tempFile.absolutePath

        // 4. 회의록 형식 조회
        val minutesFormat = userPreferencesRepository.getMinutesFormatOnce()

        // 5. MinutesGenerationWorker enqueue
        val workRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
            .setInputData(
                workDataOf(
                    MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                    MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                    MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat.name
                )
            )
            .build()
        WorkManager.getInstance(this@ShareReceiverActivity).enqueue(workRequest)

        Log.d(TAG, "회의록 생성 파이프라인 진입: meetingId=$meetingId, source=SAMSUNG_SHARE")

        // 6. 알림 및 Toast
        PipelineNotificationHelper.updateProgress(this@ShareReceiverActivity, "회의록 생성 중...")
        Toast.makeText(this@ShareReceiverActivity, "회의록 생성 중...", Toast.LENGTH_SHORT).show()
    }

    /**
     * 공유 텍스트에서 제목을 추출한다.
     * 첫 줄을 제목으로 사용하되, 30자 초과 시 잘라내고 "..." 추가.
     * 첫 줄이 비어있으면 기본 제목을 사용한다.
     */
    private fun extractTitle(text: String): String {
        val firstLine = text.lines().firstOrNull()?.trim()
        if (firstLine.isNullOrBlank()) return DEFAULT_TITLE
        return if (firstLine.length > 30) {
            firstLine.substring(0, 30) + "..."
        } else {
            firstLine
        }
    }

    companion object {
        private const val TAG = "ShareReceiver"
        private const val DEFAULT_TITLE = "삼성 공유 회의록"
    }
}
