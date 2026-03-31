package com.autominuting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.autominuting.data.local.entity.MinutesEntity
import kotlinx.coroutines.flow.Flow

/**
 * Minutes + Meeting.title LEFT JOIN 결과를 담는 POJO.
 * Room이 쿼리 결과를 이 클래스에 매핑한다.
 */
data class MinutesWithMeetingTitle(
    val id: Long,
    val meetingId: Long?,
    val minutesPath: String,
    val minutesTitle: String?,
    val templateId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val meetingTitle: String?  // LEFT JOIN으로 가져옴
)

/**
 * meetingId별 회의록 수를 담는 POJO.
 * GROUP BY count 쿼리 결과를 매핑한다.
 */
data class MinutesCountPerMeeting(
    val meetingId: Long,
    val count: Int
)

/**
 * minutes 테이블에 대한 데이터 접근 객체(DAO).
 * Room이 컴파일 시점에 구현체를 자동 생성한다.
 */
@Dao
interface MinutesDao {

    /** 모든 회의록을 생성 시각 역순으로 조회한다. */
    @Query("SELECT * FROM minutes ORDER BY createdAt DESC")
    fun getAllMinutes(): Flow<List<MinutesEntity>>

    /** 특정 Meeting에 연결된 회의록 목록을 조회한다. */
    @Query("SELECT * FROM minutes WHERE meetingId = :meetingId ORDER BY createdAt DESC")
    fun getMinutesByMeetingId(meetingId: Long): Flow<List<MinutesEntity>>

    /** 특정 회의록을 ID로 조회한다. */
    @Query("SELECT * FROM minutes WHERE id = :id")
    fun getMinutesById(id: Long): Flow<MinutesEntity?>

    /** 특정 회의록을 ID로 일회성 조회한다. */
    @Query("SELECT * FROM minutes WHERE id = :id")
    suspend fun getMinutesByIdOnce(id: Long): MinutesEntity?

    /** 특정 Meeting에 연결된 회의록 수를 조회한다. */
    @Query("SELECT COUNT(*) FROM minutes WHERE meetingId = :meetingId")
    fun getMinutesCountByMeetingId(meetingId: Long): Flow<Int>

    /** 모든 회의록을 출처 Meeting 제목과 함께 조회한다 (LEFT JOIN). */
    @Query("""
        SELECT m.*, mt.title AS meetingTitle
        FROM minutes m
        LEFT JOIN meetings mt ON m.meetingId = mt.id
        ORDER BY m.createdAt DESC
    """)
    fun getAllMinutesWithMeetingTitle(): Flow<List<MinutesWithMeetingTitle>>

    /** 모든 Meeting별 회의록 수를 일괄 조회한다 (GROUP BY). */
    @Query("SELECT meetingId, COUNT(*) AS count FROM minutes WHERE meetingId IS NOT NULL GROUP BY meetingId")
    fun getMinutesCountPerMeeting(): Flow<List<MinutesCountPerMeeting>>

    /** 회의록을 삽입하고 ID를 반환한다. */
    @Insert
    suspend fun insert(entity: MinutesEntity): Long

    /** 회의록 제목을 업데이트한다. */
    @Query("UPDATE minutes SET minutesTitle = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    /** 회의록을 삭제한다. */
    @Query("DELETE FROM minutes WHERE id = :id")
    suspend fun delete(id: Long)

    /** 특정 Meeting의 모든 회의록을 삭제한다. */
    @Query("DELETE FROM minutes WHERE meetingId = :meetingId")
    suspend fun deleteByMeetingId(meetingId: Long)

    /** 특정 Meeting에 연결된 회의록을 일회성으로 조회한다 (삭제 시 파일 경로 조회용). */
    @Query("SELECT * FROM minutes WHERE meetingId = :meetingId")
    suspend fun getMinutesByMeetingIdOnce(meetingId: Long): List<MinutesEntity>
}
