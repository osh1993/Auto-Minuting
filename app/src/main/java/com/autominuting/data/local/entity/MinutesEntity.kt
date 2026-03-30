package com.autominuting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autominuting.domain.model.Minutes
import java.time.Instant

/**
 * Room 데이터베이스의 minutes 테이블에 대응하는 Entity.
 * Meeting과 N:1 관계로, meetingId FK를 통해 연결된다.
 * Meeting 삭제 시 SET_NULL로 회의록은 보존된다.
 */
@Entity(
    tableName = "minutes",
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index(value = ["meetingId"])]
)
data class MinutesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** FK -> meetings.id, nullable (Meeting 삭제 시 SET_NULL) */
    val meetingId: Long?,
    /** 회의록 파일 경로 (NOT NULL - 회의록이 있어야 Row 존재) */
    val minutesPath: String,
    /** 자동 추출 제목 (Gemini 응답 첫 줄에서 추출) */
    val minutesTitle: String?,
    /** 생성에 사용된 프롬프트 템플릿 ID (추적용) */
    val templateId: Long?,
    /** 생성 시각 (epoch millis) */
    val createdAt: Long,
    /** 수정 시각 (epoch millis) */
    val updatedAt: Long
) {
    /**
     * Entity를 도메인 모델로 변환한다.
     */
    fun toDomain(): Minutes = Minutes(
        id = id,
        meetingId = meetingId,
        minutesPath = minutesPath,
        minutesTitle = minutesTitle,
        templateId = templateId,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    companion object {
        /**
         * 도메인 모델을 Entity로 변환한다.
         */
        fun fromDomain(minutes: Minutes): MinutesEntity = MinutesEntity(
            id = minutes.id,
            meetingId = minutes.meetingId,
            minutesPath = minutes.minutesPath,
            minutesTitle = minutes.minutesTitle,
            templateId = minutes.templateId,
            createdAt = minutes.createdAt.toEpochMilli(),
            updatedAt = minutes.updatedAt.toEpochMilli()
        )
    }
}
