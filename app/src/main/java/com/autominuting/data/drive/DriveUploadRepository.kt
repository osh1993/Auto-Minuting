package com.autominuting.data.drive

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Drive REST API 업로드 실패 중 401 응답 시 발생하는 예외.
 * 토큰 만료 또는 권한 부족을 나타낸다.
 */
class UnauthorizedException(message: String) : Exception(message)

/**
 * Google Drive REST API multipart/related 업로드를 담당하는 Repository.
 *
 * OkHttp 클라이언트를 직접 사용하여 Drive v3 Files.create 엔드포인트에
 * 메타데이터 파트 + 파일 파트를 multipart/related 형식으로 전송한다.
 *
 * 401 응답 → [UnauthorizedException] (재시도 불가)
 * 그 외 HTTP 오류 → [IOException] (WorkManager retry 가능)
 *
 * Hilt 바인딩: RepositoryModule.provideDriveUploadRepository()
 */
class DriveUploadRepository(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "DriveUploadRepository"

        /** Drive Files.create multipart 업로드 엔드포인트 */
        private const val UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    }

    /**
     * Drive REST API multipart/related 업로드.
     *
     * @param accessToken Bearer 토큰 (OAuth Drive scope)
     * @param fileName Drive에 저장될 파일명
     * @param mimeType 파일 MIME 타입 ("text/plain" for txt/md)
     * @param fileContent 파일 바이트 배열
     * @param parentFolderId 대상 폴더 ID ("root" 또는 실제 폴더 ID)
     * @return Result.success(fileId) | Result.failure(UnauthorizedException) | Result.failure(IOException)
     */
    suspend fun uploadFile(
        accessToken: String,
        fileName: String,
        mimeType: String,
        fileContent: ByteArray,
        parentFolderId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // boundary 생성: 타임스탬프 기반으로 유일성 보장
            val boundary = "auto_minuting_boundary_${System.currentTimeMillis()}"
            val body = buildMultipartBody(boundary, fileName, mimeType, fileContent, parentFolderId)

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "multipart/related; boundary=$boundary")
                .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.use { res ->
                when {
                    res.isSuccessful -> {
                        // 응답 바디에서 업로드된 파일의 ID 추출
                        val responseBody = res.body?.string() ?: ""
                        val fileId = parseFileId(responseBody)
                        Log.d(TAG, "Drive 업로드 성공: fileName=$fileName, fileId=$fileId")
                        Result.success(fileId ?: "")
                    }
                    res.code == 401 -> {
                        // 토큰 만료 또는 권한 부족 — 재시도로 해결 불가
                        Log.w(TAG, "Drive 업로드 401: 토큰 만료 또는 권한 부족")
                        Result.failure(UnauthorizedException("Drive access token 만료 — 재인증 필요"))
                    }
                    else -> {
                        // 그 외 HTTP 오류 — WorkManager retry 가능
                        Log.w(TAG, "Drive 업로드 실패: HTTP ${res.code}")
                        Result.failure(IOException("Drive 업로드 실패: HTTP ${res.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive 업로드 예외: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * multipart/related 요청 바디를 구성한다.
     *
     * 구성:
     * --boundary
     * Content-Type: application/json; charset=UTF-8
     * {"name":"...","parents":["..."]}
     * --boundary
     * Content-Type: <mimeType>
     * <파일 바이트>
     * --boundary--
     */
    private fun buildMultipartBody(
        boundary: String,
        fileName: String,
        mimeType: String,
        fileContent: ByteArray,
        parentFolderId: String
    ): ByteArray {
        // 메타데이터 JSON 구성
        val metadataJson = """{"name":"$fileName","parents":["$parentFolderId"]}"""

        // 각 파트를 ByteArray 리스트로 구성한 후 하나로 합산
        val parts = listOf(
            "--$boundary\r\n".toByteArray(Charsets.UTF_8),
            "Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8),
            metadataJson.toByteArray(Charsets.UTF_8),
            "\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8),
            "Content-Type: $mimeType\r\n\r\n".toByteArray(Charsets.UTF_8),
            fileContent,
            "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        )

        // fold로 모든 파트 ByteArray 병합
        return parts.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
    }

    /**
     * Drive API 응답 JSON에서 파일 ID를 추출한다.
     *
     * @param json Drive Files.create 응답 JSON 문자열
     * @return 파일 ID 또는 null (파싱 실패 시)
     */
    private fun parseFileId(json: String): String? {
        return try {
            // Android 플랫폼 내장 org.json 사용 — 추가 의존성 불필요
            JSONObject(json).optString("id").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Drive 응답 JSON 파싱 실패: ${e.message}")
            null
        }
    }
}
