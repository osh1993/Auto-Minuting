package com.autominuting.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autominuting.R
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.repository.TranscriptionRepositoryImpl
import com.autominuting.domain.model.AutomationMode
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.TranscriptionRepository
import com.autominuting.service.PipelineNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 전사 파이프라인 트리거 Worker.
 *
 * 오디오 파일 저장이 완료되면
 * WorkManager를 통해 자동으로 enqueue되어 전사 파이프라인을 시작한다.
 *
 * TranscriptionRepository를 주입받아 Whisper(1차) + ML Kit(2차) 이중 경로로
 * 실제 전사를 수행하고, 결과를 파일로 저장한 뒤 MeetingEntity를 업데이트한다.
 */
@HiltWorker
class TranscriptionTriggerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meetingDao: MeetingDao,
    private val transcriptionRepository: TranscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Foreground Service 알림 정보를 생성한다.
     * Long-running Worker로 동작하기 위해 필요하며,
     * WorkManager 10분 실행 제한을 우회한다.
     */
    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            PipelineNotificationHelper.PIPELINE_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText(progressText)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    /**
     * 전사 파이프라인 진입점.
     *
     * Foreground Worker로 승격하여 WorkManager 10분 실행 제한을 우회한 뒤,
     * inputData에서 오디오 파일 경로와 회의 ID를 읽어 전사를 수행한다.
     * 성공 시 전사 텍스트를 파일로 저장하고 PipelineStatus를 TRANSCRIBED로 업데이트한다.
     * 실패 시 PipelineStatus를 FAILED로 업데이트한다.
     *
     * @return Result.success() 또는 Result.failure()
     */
    override suspend fun doWork(): Result {
        // Foreground Worker로 승격 — 10분 실행 제한 해제
        try {
            setForeground(createForegroundInfo("전사 준비 중..."))
        } catch (e: Exception) {
            // Foreground 승격 실패 시에도 전사는 시도 (10분 제한 내에 완료될 수 있음)
            Log.w(TAG, "Foreground 승격 실패 (전사는 계속 진행): ${e.message}")
        }

        val audioFilePath = inputData.getString(KEY_AUDIO_FILE_PATH)
            ?: run {
                Log.e(TAG, "오디오 파일 경로가 전달되지 않았습니다")
                return Result.failure()
            }

        // inputData에서 설정값 읽기
        val automationMode = inputData.getString(KEY_AUTOMATION_MODE)
            ?: AutomationMode.FULL_AUTO.name
        // templateId: inputData에서 전달되지 않으면 UserPreferences에서 기본값 조회
        val templateId = inputData.getLong(KEY_TEMPLATE_ID, 0L).let { id ->
            if (id == 0L) userPreferencesRepository.getDefaultTemplateIdOnce() else id
        }

        // meetingId가 없으면 audioFilePath로 DB에서 조회 (기존 호환성)
        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L).let { id ->
            if (id > 0) id
            else {
                val entity = meetingDao.getMeetingByAudioPath(audioFilePath)
                entity?.id ?: run {
                    Log.e(TAG, "회의 정보를 찾을 수 없습니다: $audioFilePath")
                    return Result.failure()
                }
            }
        }

        val now = System.currentTimeMillis()

        // 재전사 시 기존 전사 파일 보존을 위해 이전 상태 저장
        val previousEntity = meetingDao.getMeetingByIdOnce(meetingId)
        val previousTranscriptPath = previousEntity?.transcriptPath
        val previousStatus = previousEntity?.pipelineStatus

        Log.d(TAG, "전사 파이프라인 시작: meetingId=$meetingId, audioPath=$audioFilePath, mode=$automationMode, 이전상태=$previousStatus")

        // 파이프라인 상태를 TRANSCRIBING으로 업데이트
        meetingDao.updatePipelineStatus(
            id = meetingId,
            status = PipelineStatus.TRANSCRIBING.name,
            errorMessage = null,
            updatedAt = now
        )

        // 전사 시작 알림
        PipelineNotificationHelper.updateProgress(applicationContext, "전사 중...")

        // 전사 수행 (Whisper 1차 -> ML Kit 2차 폴백)
        val transcribeResult = transcriptionRepository.transcribe(audioFilePath)

        return if (transcribeResult.isSuccess) {
            val transcriptText = transcribeResult.getOrThrow()
            Log.d(TAG, "전사 성공: ${transcriptText.length}자")

            // 전사 텍스트를 파일로 저장
            val transcriptPath = (transcriptionRepository as? TranscriptionRepositoryImpl)
                ?.saveTranscriptToFile(meetingId, transcriptText)
                ?: run {
                    // TranscriptionRepositoryImpl이 아닌 경우 직접 저장
                    val file = java.io.File(
                        applicationContext.filesDir,
                        "transcripts/${meetingId}.txt"
                    )
                    file.parentFile?.mkdirs()
                    file.writeText(transcriptText)
                    file.absolutePath
                }

            // DB 업데이트: transcriptPath + TRANSCRIBED 상태
            val completedAt = System.currentTimeMillis()
            meetingDao.updateTranscript(
                id = meetingId,
                transcriptPath = transcriptPath,
                status = PipelineStatus.TRANSCRIBED.name,
                updatedAt = completedAt
            )

            Log.d(TAG, "전사 파이프라인 완료: $transcriptPath")

            // DRIVE-02: Drive 자동 업로드 활성화 시 독립 enqueue (파이프라인 체인과 분리)
            val driveAutoUploadEnabled = userPreferencesRepository.getDriveAutoUploadEnabledOnce()
            if (driveAutoUploadEnabled) {
                val driveUploadRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                    .setInputData(workDataOf(
                        DriveUploadWorker.KEY_FILE_PATH to transcriptPath,
                        DriveUploadWorker.KEY_FILE_TYPE to DriveUploadWorker.TYPE_TRANSCRIPT,
                        DriveUploadWorker.KEY_MEETING_ID to meetingId
                    ))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(driveUploadRequest)
                Log.d(TAG, "Drive 자동 업로드 Worker 독립 enqueue: TYPE_TRANSCRIPT, meetingId=$meetingId")
            } else {
                Log.d(TAG, "Drive 자동 업로드 비활성 — 건너뜀: TYPE_TRANSCRIPT, meetingId=$meetingId")
            }

            // 직접 입력 모드인 경우 커스텀 프롬프트 조회
            val resolvedCustomPrompt: String? = if (templateId == UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID) {
                userPreferencesRepository.getDefaultCustomPromptOnce().ifBlank { null }
            } else null

            // 자동화 모드에 따라 분기
            if (automationMode == AutomationMode.FULL_AUTO.name) {
                // 완전 자동: 회의록 생성 Worker 자동 체이닝
                val minutesWorkRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
                    .setInputData(
                        workDataOf(
                            MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                            MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                            MinutesGenerationWorker.KEY_TEMPLATE_ID to templateId,
                            MinutesGenerationWorker.KEY_CUSTOM_PROMPT to resolvedCustomPrompt
                        )
                    )
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 60L, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(applicationContext)
                    .enqueue(minutesWorkRequest)
                Log.d(TAG, "완전 자동 모드: 회의록 생성 Worker 체이닝")
            } else {
                // 하이브리드: 알림만 표시, 사용자 확인 대기
                PipelineNotificationHelper.notifyTranscriptionComplete(
                    applicationContext, meetingId, transcriptPath,
                    customPrompt = resolvedCustomPrompt
                )
                Log.d(TAG, "하이브리드 모드: 전사 완료 알림 표시, 사용자 확인 대기")
            }

            // outputData에 transcriptPath 포함
            Result.success(
                workDataOf(KEY_TRANSCRIPT_PATH to transcriptPath)
            )
        } else {
            val error = transcribeResult.exceptionOrNull()
            val errorMessage = error?.message ?: "알 수 없는 전사 오류"
            Log.e(TAG, "전사 실패: $errorMessage", error)

            // 재전사 실패 시: 기존 전사 파일이 있으면 안정 상태로 복원
            if (!previousTranscriptPath.isNullOrBlank() && java.io.File(previousTranscriptPath).exists()) {
                // 진행 중 상태(TRANSCRIBING, GENERATING_MINUTES)로는 복원하지 않음
                val safeStatus = when (previousStatus) {
                    PipelineStatus.TRANSCRIBING.name,
                    PipelineStatus.GENERATING_MINUTES.name -> PipelineStatus.TRANSCRIBED.name
                    null -> PipelineStatus.TRANSCRIBED.name
                    else -> previousStatus
                }
                Log.d(TAG, "재전사 실패 — 기존 전사 파일 보존, 상태 복원: $safeStatus")
                meetingDao.updatePipelineStatus(
                    id = meetingId,
                    status = safeStatus,
                    errorMessage = "재전사 실패: $errorMessage",
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                // 최초 전사 실패: FAILED로 업데이트
                meetingDao.updatePipelineStatus(
                    id = meetingId,
                    status = PipelineStatus.FAILED.name,
                    errorMessage = errorMessage,
                    updatedAt = System.currentTimeMillis()
                )
            }

            Result.failure()
        }
    }

    companion object {
        const val KEY_AUDIO_FILE_PATH = "audioFilePath"
        const val KEY_MEETING_ID = "meetingId"
        const val KEY_TRANSCRIPT_PATH = "transcriptPath"
        const val KEY_AUTOMATION_MODE = "automationMode"
        /** 프롬프트 템플릿 ID (FULL_AUTO 체이닝 시 MinutesGenerationWorker에 전달) */
        const val KEY_TEMPLATE_ID = "templateId"
        /** 전사 진행률 키 (0-100 정수, DashboardViewModel에서 관찰) */
        const val KEY_PROGRESS = "progress"
        /** Foreground Service 알림 ID (PipelineNotificationHelper와 별도) */
        private const val FOREGROUND_NOTIFICATION_ID = 3001
        private const val TAG = "TranscriptionTrigger"
    }
}
