package com.autominuting.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.repository.TranscriptionRepositoryImpl
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat
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
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(appContext, workerParams) {

    /**
     * 전사 파이프라인 진입점.
     *
     * inputData에서 오디오 파일 경로와 회의 ID를 읽어 전사를 수행한다.
     * 성공 시 전사 텍스트를 파일로 저장하고 PipelineStatus를 TRANSCRIBED로 업데이트한다.
     * 실패 시 PipelineStatus를 FAILED로 업데이트한다.
     *
     * @return Result.success() 또는 Result.failure()
     */
    override suspend fun doWork(): Result {
        val audioFilePath = inputData.getString(KEY_AUDIO_FILE_PATH)
            ?: run {
                Log.e(TAG, "오디오 파일 경로가 전달되지 않았습니다")
                return Result.failure()
            }

        // inputData에서 설정값 읽기
        val automationMode = inputData.getString(KEY_AUTOMATION_MODE)
            ?: AutomationMode.FULL_AUTO.name
        val minutesFormat = inputData.getString(KEY_MINUTES_FORMAT)
            ?: MinutesFormat.STRUCTURED.name
        val templateId = inputData.getLong(KEY_TEMPLATE_ID, 0L)

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

        // 전사 시작 알림 (indeterminate 프로그레스바)
        PipelineNotificationHelper.updateProgress(applicationContext, "전사 중...", progress = -1)

        // 전사 수행 (Whisper 1차 -> ML Kit 2차 폴백) — 진행률 콜백 연결
        val transcribeResult = transcriptionRepository.transcribe(audioFilePath) { progress ->
            // progress: Float 0.0~1.0
            val percent = (progress * 100).toInt()
            // Worker progress 업데이트 (WorkManager WorkInfo에서 관찰 가능)
            setProgress(workDataOf(KEY_PROGRESS to percent))
            // 알림 업데이트
            PipelineNotificationHelper.updateProgress(
                applicationContext,
                "전사 중 $percent%",
                ongoing = true,
                progress = percent
            )
        }

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

            // 자동화 모드에 따라 분기
            if (automationMode == AutomationMode.FULL_AUTO.name) {
                // 완전 자동: 회의록 생성 Worker 자동 체이닝
                val minutesWorkRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
                    .setInputData(
                        workDataOf(
                            MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                            MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                            MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat,
                            MinutesGenerationWorker.KEY_TEMPLATE_ID to templateId
                        )
                    )
                    .build()
                WorkManager.getInstance(applicationContext)
                    .enqueue(minutesWorkRequest)
                Log.d(TAG, "완전 자동 모드: 회의록 생성 Worker 체이닝")
            } else {
                // 하이브리드: 알림만 표시, 사용자 확인 대기
                PipelineNotificationHelper.notifyTranscriptionComplete(
                    applicationContext, meetingId, transcriptPath, minutesFormat
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
        const val KEY_MINUTES_FORMAT = "minutesFormat"
        /** 프롬프트 템플릿 ID (FULL_AUTO 체이닝 시 MinutesGenerationWorker에 전달) */
        const val KEY_TEMPLATE_ID = "templateId"
        /** 전사 진행률 (0~100). WorkInfo.progress에서 관찰 가능. */
        const val KEY_PROGRESS = "progress"
        private const val TAG = "TranscriptionTrigger"
    }
}
