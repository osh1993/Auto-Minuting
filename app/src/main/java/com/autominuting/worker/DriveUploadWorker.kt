package com.autominuting.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autominuting.data.auth.GoogleAuthRepository
import com.autominuting.data.drive.DriveUploadRepository
import com.autominuting.data.drive.UnauthorizedException
import com.autominuting.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Google Drive 파일 업로드 Worker.
 *
 * TranscriptionTriggerWorker 또는 MinutesGenerationWorker 완료 후
 * 독립 enqueue 방식으로 실행된다 (.then() 체인 아님).
 *
 * 핵심 설계 원칙:
 * - MeetingDao를 주입받지 않는다 — PipelineStatus 절대 변경 금지
 * - 폴더 ID 빈 문자열 = Result.success() 조기 반환 (업로드 비활성, 오류 아님)
 * - Drive access token null/blank = Result.failure() 즉시 반환 (재시도 무의미)
 * - runAttemptCount >= MAX_ATTEMPTS = Result.failure() (무한 재시도 방지)
 */
@HiltWorker
class DriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleAuthRepository: GoogleAuthRepository,
    private val driveUploadRepository: DriveUploadRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 최대 재시도 횟수 초과 시 즉시 실패 — 무한 재시도 방지
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.w(TAG, "최대 재시도 횟수 초과 (runAttemptCount=$runAttemptCount) — 업로드 중단")
            return Result.failure(workDataOf(KEY_ERROR to "최대 재시도 초과"))
        }

        // inputData에서 파일 경로와 파일 유형 읽기
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure(workDataOf(KEY_ERROR to "파일 경로가 전달되지 않았습니다"))
        val fileType = inputData.getString(KEY_FILE_TYPE)
            ?: return Result.failure(workDataOf(KEY_ERROR to "파일 유형이 전달되지 않았습니다"))

        Log.d(TAG, "Drive 업로드 시작: fileType=$fileType, filePath=$filePath, attempt=$runAttemptCount")

        // Drive access token 확인 — null/blank 이면 재시도로 해결 불가
        val accessToken = googleAuthRepository.getDriveAccessToken()
        if (accessToken.isNullOrBlank()) {
            Log.w(TAG, "Drive access token 없음 — 재인증 필요")
            return Result.failure(workDataOf(KEY_ERROR to "Drive access token 없음. 설정에서 Drive 연동 필요."))
        }

        // 파일 유형에 따라 대상 폴더 ID 조회
        val folderId = when (fileType) {
            TYPE_TRANSCRIPT -> userPreferencesRepository.getDriveTranscriptFolderIdOnce()
            TYPE_MINUTES -> userPreferencesRepository.getDriveMinutesFolderIdOnce()
            else -> {
                Log.w(TAG, "알 수 없는 파일 유형: $fileType")
                return Result.failure(workDataOf(KEY_ERROR to "알 수 없는 파일 유형: $fileType"))
            }
        }

        // 폴더 ID 빈 문자열 = 업로드 비활성 (사용자가 폴더를 설정하지 않은 정상 상태)
        if (folderId.isBlank()) {
            Log.d(TAG, "Drive 폴더 미설정 — 건너뜀 (fileType=$fileType)")
            return Result.success()
        }

        // 파일 존재 확인
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "업로드 대상 파일 없음: $filePath")
            return Result.failure(workDataOf(KEY_ERROR to "파일 없음: $filePath"))
        }

        // Drive 업로드 실행 (mimeType은 txt/md 모두 text/plain)
        val mimeType = "text/plain"
        val uploadResult = driveUploadRepository.uploadFile(
            accessToken = accessToken,
            fileName = file.name,
            mimeType = mimeType,
            fileContent = file.readBytes(),
            parentFolderId = folderId
        )

        return when {
            uploadResult.isSuccess -> {
                val fileId = uploadResult.getOrNull() ?: ""
                Log.d(TAG, "Drive 업로드 성공: fileName=${file.name}, fileId=$fileId")
                Result.success()
            }
            uploadResult.exceptionOrNull() is UnauthorizedException -> {
                // 토큰 만료 — 재시도 불가
                Log.w(TAG, "Drive 토큰 만료 — 재인증 필요")
                Result.failure(workDataOf(KEY_ERROR to "Drive 토큰 만료 — 재인증 필요"))
            }
            else -> {
                // 일시적 오류 — WorkManager BackoffPolicy로 재시도
                val errorMessage = uploadResult.exceptionOrNull()?.message ?: "알 수 없는 오류"
                Log.w(TAG, "Drive 업로드 실패 (재시도 예정): $errorMessage")
                Result.retry()
            }
        }
    }

    companion object {
        /** inputData: 업로드할 파일의 절대 경로 */
        const val KEY_FILE_PATH = "driveFilePath"

        /** inputData: 파일 유형 (TYPE_TRANSCRIPT 또는 TYPE_MINUTES) */
        const val KEY_FILE_TYPE = "driveFileType"

        /** inputData: 관련 회의 ID (추적 로깅용) */
        const val KEY_MEETING_ID = "meetingId"

        /** outputData: 오류 메시지 */
        const val KEY_ERROR = "driveError"

        /** 전사 파일 업로드 타입 식별자 */
        const val TYPE_TRANSCRIPT = "transcript"

        /** 회의록 파일 업로드 타입 식별자 */
        const val TYPE_MINUTES = "minutes"

        /** 최대 재시도 횟수 — 이 값 이상이면 Result.failure() 반환 */
        private const val MAX_ATTEMPTS = 3

        private const val TAG = "DriveUploadWorker"
    }
}
