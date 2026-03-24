package com.autominuting.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.repository.MinutesRepositoryImpl
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MinutesRepository
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
    private val minutesRepository: MinutesRepository
) : CoroutineWorker(appContext, workerParams) {

    /**
     * 회의록 생성 파이프라인 진입점.
     *
     * inputData에서 회의 ID와 전사 파일 경로를 읽어 회의록 생성을 수행한다.
     * 성공 시 회의록을 Markdown 파일로 저장하고 PipelineStatus를 COMPLETED로 업데이트한다.
     * 실패 시 PipelineStatus를 FAILED로 업데이트한다.
     *
     * @return Result.success() 또는 Result.failure()
     */
    override suspend fun doWork(): Result {
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

        Log.d(TAG, "회의록 생성 시작: meetingId=$meetingId, transcriptPath=$transcriptPath")

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

        // 회의록 생성 (Gemini API 1차)
        val generateResult = minutesRepository.generateMinutes(transcriptText)

        return if (generateResult.isSuccess) {
            val minutesText = generateResult.getOrThrow()
            Log.d(TAG, "회의록 생성 성공: ${minutesText.length}자")

            // 회의록을 파일로 저장
            val minutesPath = (minutesRepository as? MinutesRepositoryImpl)
                ?.saveMinutesToFile(meetingId, minutesText)
                ?: run {
                    // MinutesRepositoryImpl이 아닌 경우 직접 저장
                    val file = File(
                        applicationContext.filesDir,
                        "minutes/${meetingId}.md"
                    )
                    file.parentFile?.mkdirs()
                    file.writeText(minutesText)
                    file.absolutePath
                }

            // DB 업데이트: minutesPath + COMPLETED 상태
            val completedAt = System.currentTimeMillis()
            meetingDao.updateMinutes(
                id = meetingId,
                minutesPath = minutesPath,
                status = PipelineStatus.COMPLETED.name,
                updatedAt = completedAt
            )

            Log.d(TAG, "회의록 파이프라인 완료: $minutesPath")

            // outputData에 minutesPath 포함
            Result.success(
                workDataOf(KEY_MINUTES_PATH to minutesPath)
            )
        } else {
            val error = generateResult.exceptionOrNull()
            val errorMessage = error?.message ?: "알 수 없는 회의록 생성 오류"
            Log.e(TAG, "회의록 생성 실패: $errorMessage", error)

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
        private const val TAG = "MinutesGeneration"
    }
}
