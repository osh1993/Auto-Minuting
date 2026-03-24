package com.autominuting.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Plaud Cloud API Retrofit 인터페이스.
 *
 * SDK BLE 연결 실패 시 2차 폴백 경로로 사용한다.
 * JWT 인증 토큰이 필요하다.
 */
interface PlaudCloudApi {

    /**
     * 녹음 목록을 조회한다.
     *
     * @param token JWT 인증 토큰 (Bearer 형식)
     * @return 녹음 목록
     */
    @GET("recordings")
    suspend fun getRecordings(
        @Header("Authorization") token: String
    ): List<RecordingResponse>

    /**
     * 특정 녹음 파일을 다운로드한다.
     *
     * @param id 녹음 ID
     * @param token JWT 인증 토큰 (Bearer 형식)
     * @return 오디오 파일 바이너리
     */
    @GET("recordings/{id}/download")
    @Streaming
    suspend fun downloadRecording(
        @Path("id") id: String,
        @Header("Authorization") token: String
    ): ResponseBody
}

/**
 * Cloud API 녹음 응답 데이터.
 *
 * @property id 녹음 고유 ID
 * @property name 녹음 파일명
 * @property createdAt 녹음 생성 시각 (ISO 8601)
 * @property size 파일 크기 (바이트)
 */
data class RecordingResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val size: Long
)

/**
 * Plaud Cloud API 서비스.
 *
 * SDK BLE 경로 실패 시 Cloud API를 통해 녹음 파일을 다운로드한다.
 * JWT 인증이 필요하며, 녹음 목록 조회 후 각 파일을 순차적으로 다운로드한다.
 */
class PlaudCloudApiService @Inject constructor(
    private val api: PlaudCloudApi,
    private val audioFileManager: AudioFileManager
) {

    /**
     * 최신 녹음 파일을 다운로드하고 저장된 파일 경로를 emit한다.
     *
     * @param jwtToken JWT 인증 토큰
     * @return 저장된 오디오 파일의 절대 경로를 방출하는 Flow
     */
    fun downloadLatestRecordings(jwtToken: String): Flow<String> = flow {
        val bearerToken = "Bearer $jwtToken"
        val recordings = api.getRecordings(bearerToken)
        val outputDir = audioFileManager.getAudioDirectory()

        for (recording in recordings) {
            val responseBody = api.downloadRecording(recording.id, bearerToken)
            val fileName = recording.name.ifBlank {
                audioFileManager.generateFileName()
            }
            val outputFile = File(outputDir, fileName)

            // 파일을 디스크에 스트리밍 저장
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 저장된 파일 경로 emit
            if (audioFileManager.validateAudioFile(outputFile)) {
                emit(outputFile.absolutePath)
            }
        }
    }.flowOn(Dispatchers.IO)
}
