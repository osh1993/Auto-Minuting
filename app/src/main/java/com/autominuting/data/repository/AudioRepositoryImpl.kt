package com.autominuting.data.repository

import android.util.Log
import com.autominuting.data.audio.AudioFileManager
import com.autominuting.data.audio.BleConnectionState
import com.autominuting.data.audio.PlaudCloudApiService
import com.autominuting.data.audio.PlaudSdkException
import com.autominuting.data.audio.PlaudSdkManager
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.entity.MeetingEntity
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * [AudioRepository]의 구현체.
 *
 * Plaud SDK(1차)와 Cloud API(2차)의 이중 경로를 지원한다.
 * SDK BLE 연결이 실패하면 자동으로 Cloud API 폴백으로 전환된다.
 *
 * getFileList()로 실제 세션 목록을 조회하여 모든 세션의 오디오를 수집하고,
 * 각 세션 실패 시에도 나머지 세션은 계속 처리한다.
 */
class AudioRepositoryImpl @Inject constructor(
    private val plaudSdkManager: PlaudSdkManager,
    private val cloudApiService: PlaudCloudApiService,
    private val audioFileManager: AudioFileManager,
    private val meetingDao: MeetingDao
) : AudioRepository {

    companion object {
        private const val TAG = "AudioRepo"
    }

    /** 현재 오디오 수집 진행 상태 */
    private val _isCollecting = MutableStateFlow(false)

    /**
     * 오디오 수집을 시작하고 수신된 오디오 파일 경로를 emit한다.
     *
     * 실행 흐름:
     * 1. 저장 공간 확인
     * 2. SDK BLE 경로로 스캔/연결 시도 (1차)
     * 3. getFileList()로 세션 목록 조회 후 각 세션별 exportAudio 호출
     * 4. SDK 실패 시 Cloud API 폴백으로 전환 (2차)
     * 5. 파일 저장 후 MeetingEntity를 AUDIO_RECEIVED 상태로 DB에 기록
     *
     * @return 저장된 오디오 파일의 절대 경로를 방출하는 Flow
     * @throws IllegalStateException 저장 공간 부족 시
     */
    override suspend fun startAudioCollection(): Flow<String> = channelFlow {
        _isCollecting.value = true
        try {
            // 저장 공간 확인
            if (!audioFileManager.hasEnoughSpace()) {
                throw IllegalStateException(
                    "저장 공간이 부족합니다. 최소 100MB의 여유 공간이 필요합니다."
                )
            }

            try {
                // 1차: SDK BLE 경로
                plaudSdkManager.scanAndConnect()

                // 세션 목록 조회
                val sessions = plaudSdkManager.getFileList()
                if (sessions.isEmpty()) {
                    throw PlaudSdkException("녹음기에 다운로드 가능한 파일이 없습니다")
                }

                Log.d(TAG, "세션 목록 조회 완료: ${sessions.size}개 세션 발견")

                val outputDir = audioFileManager.getAudioDirectory()

                // 모든 세션을 순회하여 각각 exportAudio 호출
                for (session in sessions) {
                    try {
                        val filePath = plaudSdkManager.exportAudio(
                            sessionId = session,
                            outputDir = outputDir,
                            format = "WAV"  // 명시적으로 WAV 포맷 지정
                        )

                        // 파일 저장 성공 -> MeetingEntity 생성 및 DB 기록
                        saveMeetingEntity(filePath)
                        send(filePath)
                        Log.d(TAG, "세션 수집 완료: $session -> $filePath")
                    } catch (e: Exception) {
                        // 개별 세션 실패 시에도 나머지 세션은 계속 처리
                        Log.e(TAG, "세션 수집 실패 (계속 진행): $session, 오류: ${e.message}")
                    }
                }

            } catch (e: PlaudSdkException) {
                // 2차: Cloud API 폴백
                Log.w(TAG, "SDK BLE 실패, Cloud API 폴백 전환: ${e.message}")
                // TODO: JWT 토큰은 사용자 설정에서 가져와야 함
                val jwtToken = "" // 현재는 빈 값 (사용자 설정 연동 후 교체)
                cloudApiService.downloadLatestRecordings(jwtToken).collect { filePath ->
                    saveMeetingEntity(filePath)
                    send(filePath)
                }
            }
        } finally {
            _isCollecting.value = false
        }
    }

    /**
     * 오디오 수집을 중지한다.
     * SDK BLE 연결을 해제하고 수집 상태를 false로 전환한다.
     */
    override suspend fun stopAudioCollection() {
        _isCollecting.value = false
        plaudSdkManager.disconnect()
    }

    /**
     * 현재 오디오 수집이 진행 중인지 여부를 관찰한다.
     *
     * @return 수집 진행 상태 Flow
     */
    override fun isCollecting(): Flow<Boolean> = _isCollecting.asStateFlow()

    /**
     * BLE 연결 상태를 StateFlow로 반환한다.
     * PlaudSdkManager의 connectionState를 직접 위임한다.
     *
     * @return BLE 연결 상태 StateFlow
     */
    override fun getBleConnectionState(): StateFlow<BleConnectionState> =
        plaudSdkManager.connectionState

    /**
     * 오디오 파일 경로로 MeetingEntity를 생성하고 DB에 저장한다.
     *
     * @param audioFilePath 저장된 오디오 파일의 절대 경로
     * @return 생성된 Meeting의 DB ID
     */
    private suspend fun saveMeetingEntity(audioFilePath: String): Long {
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(now))

        val entity = MeetingEntity(
            title = "회의 $formattedDate",
            recordedAt = now,
            audioFilePath = audioFilePath,
            pipelineStatus = PipelineStatus.AUDIO_RECEIVED.name,
            createdAt = now,
            updatedAt = now
        )

        return meetingDao.insert(entity)
    }
}
