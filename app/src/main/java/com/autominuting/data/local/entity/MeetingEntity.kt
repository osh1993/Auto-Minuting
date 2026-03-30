package com.autominuting.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import java.time.Instant

/**
 * Room 데이터베이스의 meetings 테이블에 대응하는 Entity.
 * 도메인 모델 [Meeting]과의 매핑 함수를 제공한다.
 */
@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** 녹음 시각 (epoch millis) */
    val recordedAt: Long,
    val audioFilePath: String,
    val transcriptPath: String? = null,
    /** 파이프라인 상태 (enum name으로 저장) */
    val pipelineStatus: String,
    val errorMessage: String? = null,
    /** 레코드 생성 시각 (epoch millis) */
    val createdAt: Long,
    /** 레코드 최종 수정 시각 (epoch millis) */
    val updatedAt: Long,
    /** 데이터 소스 (PLAUD_BLE, SAMSUNG_SHARE 등) */
    val source: String = "PLAUD_BLE"
) {
    /**
     * Entity를 도메인 모델로 변환한다.
     */
    fun toDomain(): Meeting = Meeting(
        id = id,
        title = title,
        recordedAt = Instant.ofEpochMilli(recordedAt),
        audioFilePath = audioFilePath,
        transcriptPath = transcriptPath,
        pipelineStatus = PipelineStatus.valueOf(pipelineStatus),
        errorMessage = errorMessage,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        source = source
    )

    companion object {
        /**
         * 도메인 모델을 Entity로 변환한다.
         */
        fun fromDomain(meeting: Meeting): MeetingEntity = MeetingEntity(
            id = meeting.id,
            title = meeting.title,
            recordedAt = meeting.recordedAt.toEpochMilli(),
            audioFilePath = meeting.audioFilePath,
            transcriptPath = meeting.transcriptPath,
            pipelineStatus = meeting.pipelineStatus.name,
            errorMessage = meeting.errorMessage,
            createdAt = meeting.createdAt.toEpochMilli(),
            updatedAt = meeting.updatedAt.toEpochMilli(),
            source = meeting.source
        )
    }
}
