package com.autominuting.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * WorkManager 초기화 검증용 테스트 Worker.
 * Phase 6에서 실제 파이프라인 Worker(전사, 회의록 생성 등)로 교체 예정.
 *
 * 1초 대기 후 성공을 반환하여 WorkManager가 정상 작동하는지 확인한다.
 */
class TestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "백그라운드 작업 실행 시작")
        // 간단한 대기로 백그라운드 작업 시뮬레이션
        delay(1000)
        Log.d(TAG, "백그라운드 작업 실행 완료")
        return Result.success()
    }

    companion object {
        private const val TAG = "TestWorker"
    }
}
