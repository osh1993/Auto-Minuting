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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
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

        // 업로드 전 fresh token 획득 시도
        // drive.file 스코프로 authorize()를 재호출하면 이미 승인된 경우 Activity 없이 즉시 발급됨
        val accessToken = getFreshDriveToken()
        if (accessToken.isNullOrBlank()) {
            Log.w(TAG, "Drive access token 없음 — 재인증 필요")
            return Result.failure(workDataOf(KEY_ERROR to "Drive access token 없음. 설정에서 Drive 연동 필요."))
        }

        // 파일 유형에 따라 대상 폴더 ID 조회
        // 폴더 미설정 시 "root" 사용 — 내 드라이브 최상위에 업로드
        val savedFolderId = when (fileType) {
            TYPE_TRANSCRIPT -> userPreferencesRepository.getDriveTranscriptFolderIdOnce()
            TYPE_MINUTES -> userPreferencesRepository.getDriveMinutesFolderIdOnce()
            else -> {
                Log.w(TAG, "알 수 없는 파일 유형: $fileType")
                return Result.failure(workDataOf(KEY_ERROR to "알 수 없는 파일 유형: $fileType"))
            }
        }
        val folderId = savedFolderId.ifBlank {
            Log.d(TAG, "Drive 폴더 미설정 — root에 업로드 (fileType=$fileType)")
            "root"
        }

        // 파일 존재 확인
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "업로드 대상 파일 없음: $filePath")
            return Result.failure(workDataOf(KEY_ERROR to "파일 없음: $filePath"))
        }

        // Drive에 저장될 파일명: 공유 파일명(title) + 유형 접미사 + 원본 확장자
        val title = inputData.getString(KEY_TITLE)?.ifBlank { null }
        val ext = file.extension.ifBlank { "txt" }
        val driveFileName = if (title != null) {
            val suffix = when (fileType) {
                TYPE_TRANSCRIPT -> "_전사"
                TYPE_MINUTES -> "_회의록"
                else -> ""
            }
            "${title}${suffix}.${ext}"
        } else {
            file.name
        }

        // Drive 업로드 실행 (mimeType은 txt/md 모두 text/plain)
        val mimeType = "text/plain"
        val uploadResult = driveUploadRepository.uploadFile(
            accessToken = accessToken,
            fileName = driveFileName,
            mimeType = mimeType,
            fileContent = file.readBytes(),
            parentFolderId = folderId
        )

        return when {
            uploadResult.isSuccess -> {
                val fileId = uploadResult.getOrNull() ?: ""
                Log.d(TAG, "Drive 업로드 성공: fileName=$driveFileName, fileId=$fileId")
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

    /**
     * Drive access token을 fresh하게 획득한다.
     *
     * Google Identity Services의 authorize()는 이미 승인된 스코프에 대해
     * Activity 없이도 즉시 새 access token을 발급할 수 있다.
     * hasResolution() == false이면 즉시 token 반환.
     * hasResolution() == true이면 사용자 동의가 필요하므로 저장된 token으로 폴백.
     */
    private suspend fun getFreshDriveToken(): String? {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
                .build()
            val result = Identity.getAuthorizationClient(applicationContext)
                .authorize(authRequest)
                .await()

            if (!result.hasResolution()) {
                val freshToken = result.accessToken
                if (!freshToken.isNullOrBlank()) {
                    Log.d(TAG, "Drive fresh token 획득 성공")
                    return freshToken
                }
            }
            // hasResolution() == true이거나 token null → 저장된 token 폴백
            val cached = googleAuthRepository.getDriveAccessToken()
            Log.d(TAG, "Drive fresh token 획득 실패 → 캐시 폴백: ${if (cached.isNullOrBlank()) "없음" else "있음"}")
            cached
        } catch (e: Exception) {
            Log.w(TAG, "Drive fresh token 획득 중 예외 — 캐시 폴백: ${e.message}")
            googleAuthRepository.getDriveAccessToken()
        }
    }

    companion object {
        /** inputData: 업로드할 파일의 절대 경로 */
        const val KEY_FILE_PATH = "driveFilePath"

        /** inputData: 파일 유형 (TYPE_TRANSCRIPT 또는 TYPE_MINUTES) */
        const val KEY_FILE_TYPE = "driveFileType"

        /** inputData: 공유 파일명(meeting.title) — Drive 저장 파일명 생성에 사용 */
        const val KEY_TITLE = "driveTitle"

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
