package com.autominuting.data.repository

import com.autominuting.data.audio.AudioFileManager
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.entity.MeetingEntity
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * [AudioRepository]의 구현체.
 *
 * 오디오 파일 수신 및 저장을 담당한다.
 * 현재 삼성 공유(ShareReceiverActivity) 경로를 통해 오디오를 수신하며,
 * 이 클래스는 저장 공간 확인 및 MeetingEntity 생성 역할을 한다.
 */
class AudioRepositoryImpl @Inject constructor(
    private val audioFileManager: AudioFileManager,
    private val meetingDao: MeetingDao
) : AudioRepository {

    /** 현재 오디오 수집 진행 상태 */
    private val _isCollecting = MutableStateFlow(false)

    /**
     * 오디오 수집을 시작하고 수신된 오디오 파일 경로를 emit한다.
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
            // 현재는 삼성 공유 경로를 통해 오디오를 수신하므로
            // 별도의 수집 로직이 필요하지 않음
        } finally {
            _isCollecting.value = false
        }
    }

    /**
     * 오디오 수집을 중지한다.
     */
    override suspend fun stopAudioCollection() {
        _isCollecting.value = false
    }

    /**
     * 현재 오디오 수집이 진행 중인지 여부를 관찰한다.
     *
     * @return 수집 진행 상태 Flow
     */
    override fun isCollecting(): Flow<Boolean> = _isCollecting.asStateFlow()

    /**
     * 오디오 파일 경로로 MeetingEntity를 생성하고 DB에 저장한다.
     *
     * @param audioFilePath 저장된 오디오 파일의 절대 경로
     * @return 생성된 Meeting의 DB ID
     */
    suspend fun saveMeetingEntity(audioFilePath: String): Long {
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
