package com.autominuting.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.autominuting.R
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.dao.MinutesDao
import com.autominuting.data.local.entity.MinutesEntity
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.repository.MinutesRepositoryImpl
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.domain.repository.PromptTemplateRepository
import com.autominuting.service.PipelineNotificationHelper
import com.google.ai.client.generativeai.type.QuotaExceededException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * 회의록 생성 파이프라인 Worker.
 *
 * TranscriptionTriggerWorker에서 전사 완료 후 자동으로 enqueue되어
 * 전사 텍스트를 Gemini API에 전달하여 구조화된 회의록을 생성한다.
 *
 * MinutesRepository를 주입받아 Gemini API(1차)로 회의록을 생성하고,
 * 결과를 Markdown 파일로 저장한 뒤 MeetingEntity를 업데이트한다.
 */
@HiltWorker
class MinutesGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meetingDao: MeetingDao,
    private val minutesDao: MinutesDao,
    private val minutesRepository: MinutesRepository,
    private val promptTemplateRepository: PromptTemplateRepository,
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
     * 회의록 생성 파이프라인 진입점.
     *
     * Foreground Worker로 승격하여 WorkManager 10분 실행 제한을 우회한 뒤,
     * inputData에서 회의 ID와 전사 파일 경로를 읽어 회의록 생성을 수행한다.
     * 성공 시 회의록을 Markdown 파일로 저장하고 PipelineStatus를 COMPLETED로 업데이트한다.
     * 실패 시 PipelineStatus를 FAILED로 업데이트한다.
     *
     * @return Result.success() 또는 Result.failure()
     */
    override suspend fun doWork(): Result {
        // Foreground Worker로 승격 — 10분 실행 제한 해제
        try {
            setForeground(createForegroundInfo("회의록 생성 준비 중..."))
        } catch (e: Exception) {
            Log.w(TAG, "Foreground 승격 실패 (회의록 생성은 계속 진행): ${e.message}")
        }

        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L)
        if (meetingId <= 0) {
            Log.e(TAG, "회의 ID가 전달되지 않았습니다")
            return Result.failure()
        }

        // transcriptPath: inputData에서 직접 가져오거나, DB에서 조회
        val transcriptPath = inputData.getString(KEY_TRANSCRIPT_PATH)
            ?: run {
                // DB에서 meetingId로 조회하여 transcriptPath 가져오기
                // getMeetingById는 Flow이므로 직접 쿼리가 필요 — getMeetingByAudioPath 패턴 참조
                Log.w(TAG, "transcriptPath가 inputData에 없음, 기본 경로 사용: meetingId=$meetingId")
                File(applicationContext.filesDir, "transcripts/${meetingId}.txt").absolutePath
            }

        // inputData에서 템플릿 ID와 커스텀 프롬프트 읽기
        val templateId = inputData.getLong(KEY_TEMPLATE_ID, 0L)
        val customPrompt = inputData.getString(KEY_CUSTOM_PROMPT)

        // 프롬프트 해결: customPrompt > templateId > STRUCTURED 기본 폴백
        val resolvedPrompt: String? = when {
            customPrompt != null -> customPrompt
            templateId > 0 -> {
                val template = promptTemplateRepository.getById(templateId)
                template?.promptText
            }
            else -> null
        }

        Log.d(TAG, "회의록 생성 시작: meetingId=$meetingId, transcriptPath=$transcriptPath, templateId=$templateId")

        // 전사 텍스트 파일 읽기
        val transcriptFile = File(transcriptPath)
        if (!transcriptFile.exists()) {
            Log.e(TAG, "전사 텍스트 파일을 찾을 수 없습니다: $transcriptPath")
            meetingDao.updatePipelineStatus(
                id = meetingId,
                status = PipelineStatus.FAILED.name,
                errorMessage = "전사 텍스트 파일을 찾을 수 없습니다: $transcriptPath",
                updatedAt = System.currentTimeMillis()
            )
            return Result.failure()
        }

        val transcriptText = transcriptFile.readText()
        if (transcriptText.isBlank()) {
            Log.e(TAG, "전사 텍스트가 비어 있습니다: $transcriptPath")
            meetingDao.updatePipelineStatus(
                id = meetingId,
                status = PipelineStatus.FAILED.name,
                errorMessage = "전사 텍스트가 비어 있습니다",
                updatedAt = System.currentTimeMillis()
            )
            return Result.failure()
        }

        // 파이프라인 상태를 GENERATING_MINUTES로 업데이트
        meetingDao.updatePipelineStatus(
            id = meetingId,
            status = PipelineStatus.GENERATING_MINUTES.name,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        // 회의록 생성 중 알림
        PipelineNotificationHelper.updateProgress(applicationContext, "회의록 생성 중...")

        // 회의록 생성 (Gemini API 1차) — 템플릿 프롬프트 우선, 없으면 기본 프롬프트
        val generateResult = minutesRepository.generateMinutes(transcriptText, customPrompt = resolvedPrompt)

        return if (generateResult.isSuccess) {
            val minutesText = generateResult.getOrThrow()
            Log.d(TAG, "회의록 생성 성공: ${minutesText.length}자")

            // 회의록을 파일로 저장
            val minutesPath = (minutesRepository as? MinutesRepositoryImpl)
                ?.saveMinutesToFile(meetingId, minutesText)
                ?: run {
                    // MinutesRepositoryImpl이 아닌 경우 직접 저장
                    // 타임스탬프 기반 파일명으로 재생성 시 덮어쓰기 방지
                    val file = File(
                        applicationContext.filesDir,
                        "minutes/${meetingId}_${System.currentTimeMillis()}.md"
                    )
                    file.parentFile?.mkdirs()
                    file.writeText(minutesText)
                    file.absolutePath
                }

            // Gemini 응답의 첫 줄에서 제목 추출 (# 마크다운 헤더 제거)
            val minutesTitle = minutesText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.removePrefix("#")
                ?.trim()
                ?.take(100) // 제목 최대 100자 제한
            Log.d(TAG, "회의록 제목 추출: $minutesTitle")

            // Minutes 테이블에 새 Row 삽입 + Meeting 상태를 COMPLETED로 업데이트
            val completedAt = System.currentTimeMillis()
            val minutesEntity = MinutesEntity(
                meetingId = meetingId,
                minutesPath = minutesPath,
                minutesTitle = minutesTitle,
                templateId = templateId.takeIf { it > 0 },
                createdAt = completedAt,
                updatedAt = completedAt
            )
            minutesDao.insert(minutesEntity)
            meetingDao.updatePipelineStatus(
                id = meetingId,
                status = PipelineStatus.COMPLETED.name,
                errorMessage = null,
                updatedAt = completedAt
            )

            Log.d(TAG, "회의록 파이프라인 완료: $minutesPath")

            // 완료 알림 (공유 액션 포함)
            PipelineNotificationHelper.notifyComplete(applicationContext, meetingId, minutesText)

            // DRIVE-03: Drive 자동 업로드 활성화 시 독립 enqueue (파이프라인 체인과 분리)
            val driveAutoUploadEnabled = userPreferencesRepository.getDriveAutoUploadEnabledOnce()
            if (driveAutoUploadEnabled) {
                val meetingTitle = meetingDao.getMeetingByIdOnce(meetingId)?.title ?: ""
                val driveUploadRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                    .setInputData(workDataOf(
                        DriveUploadWorker.KEY_FILE_PATH to minutesPath,
                        DriveUploadWorker.KEY_FILE_TYPE to DriveUploadWorker.TYPE_MINUTES,
                        DriveUploadWorker.KEY_MEETING_ID to meetingId,
                        DriveUploadWorker.KEY_TITLE to meetingTitle
                    ))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(driveUploadRequest)
                Log.d(TAG, "Drive 자동 업로드 Worker 독립 enqueue: TYPE_MINUTES, meetingId=$meetingId")
            } else {
                Log.d(TAG, "Drive 자동 업로드 비활성 — 건너뜀: TYPE_MINUTES, meetingId=$meetingId")
            }

            // outputData에 minutesPath 포함
            Result.success(
                workDataOf(KEY_MINUTES_PATH to minutesPath)
            )
        } else {
            val error = generateResult.exceptionOrNull()
            val errorMessage = error?.message ?: "알 수 없는 회의록 생성 오류"
            Log.e(TAG, "회의록 생성 실패: $errorMessage", error)

            // 쿼터 초과인 경우: 알림 후 WorkManager retry
            val isQuotaExceeded = error is QuotaExceededException
                || error?.cause is QuotaExceededException
            if (isQuotaExceeded) {
                PipelineNotificationHelper.notifyQuotaExceeded(applicationContext)
                // 상태는 GENERATING_MINUTES 유지 (retry 후 재개)
                return Result.retry()
            }

            // 일반 실패 알림
            PipelineNotificationHelper.updateProgress(applicationContext, "회의록 생성 실패", ongoing = false)

            // 파이프라인 상태를 FAILED로 업데이트
            meetingDao.updatePipelineStatus(
                id = meetingId,
                status = PipelineStatus.FAILED.name,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )

            Result.failure()
        }
    }

    companion object {
        const val KEY_MEETING_ID = "meetingId"
        const val KEY_TRANSCRIPT_PATH = "transcriptPath"
        const val KEY_MINUTES_PATH = "minutesPath"
        /** 프롬프트 템플릿 ID (0이면 미지정 → 기본 STRUCTURED 프롬프트) */
        const val KEY_TEMPLATE_ID = "templateId"
        /** 커스텀 프롬프트 텍스트 (templateId보다 우선) */
        const val KEY_CUSTOM_PROMPT = "customPrompt"
        /** Foreground Service 알림 ID (TranscriptionTriggerWorker와 별도) */
        private const val FOREGROUND_NOTIFICATION_ID = 3002
        private const val TAG = "MinutesGeneration"
    }
}
