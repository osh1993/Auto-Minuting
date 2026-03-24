package com.autominuting.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autominuting.data.local.dao.MeetingDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 전사 파이프라인 트리거 Worker.
 *
 * AudioCollectionService에서 오디오 파일 저장이 완료되면
 * WorkManager를 통해 자동으로 enqueue되어 전사 파이프라인을 시작한다.
 *
 * Phase 4에서 실제 전사 로직 구현 예정.
 * 현재는 파이프라인 연결 포인트 역할만 수행하며 로그를 남기고 성공을 반환한다.
 */
@HiltWorker
class TranscriptionTriggerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meetingDao: MeetingDao
) : CoroutineWorker(appContext, workerParams) {

    /**
     * 전사 파이프라인 진입점.
     *
     * inputData에서 오디오 파일 경로를 읽어 전사를 시작한다.
     * Phase 4(STT-01)에서 Whisper 온디바이스 전사 로직으로 교체 예정.
     *
     * @return Result.success() - 현재는 항상 성공 반환
     */
    override suspend fun doWork(): Result {
        val audioFilePath = inputData.getString("audioFilePath")
            ?: run {
                Log.e(TAG, "오디오 파일 경로가 전달되지 않았습니다")
                return Result.failure()
            }

        Log.d(TAG, "전사 파이프라인 시작: $audioFilePath")

        // Phase 4에서 실제 전사 로직 구현 예정:
        // 1. audioFilePath로 오디오 파일 로드
        // 2. Whisper 온디바이스 STT로 전사
        // 3. 전사 결과를 MeetingEntity에 저장
        // 4. PipelineStatus를 TRANSCRIBED로 업데이트

        Log.d(TAG, "전사 파이프라인 완료 (스텁): $audioFilePath")
        return Result.success()
    }

    companion object {
        private const val TAG = "TranscriptionTrigger"
    }
}
