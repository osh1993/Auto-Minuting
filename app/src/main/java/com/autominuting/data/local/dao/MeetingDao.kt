package com.autominuting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autominuting.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow

/**
 * meetings 테이블에 대한 데이터 접근 객체(DAO).
 * Room이 컴파일 시점에 구현체를 자동 생성한다.
 */
@Dao
interface MeetingDao {

    /** 모든 회의를 녹음 시각 역순으로 조회한다. */
    @Query("SELECT * FROM meetings ORDER BY recordedAt DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    /** 특정 회의를 ID로 조회한다. */
    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingById(id: Long): Flow<MeetingEntity?>

    /** 삭제 전 파일 경로 조회용 일회성 조회 */
    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingByIdOnce(id: Long): MeetingEntity?

    /** 회의를 삽입하고 생성된 행 ID를 반환한다. 충돌 시 교체한다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MeetingEntity): Long

    /** 회의 정보를 업데이트한다. */
    @Update
    suspend fun update(entity: MeetingEntity)

    /** 회의의 파이프라인 상태를 업데이트한다. */
    @Query("UPDATE meetings SET pipelineStatus = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePipelineStatus(
        id: Long,
        status: String,
        errorMessage: String?,
        updatedAt: Long
    )

    /** 전사 완료 후 transcriptPath와 파이프라인 상태를 업데이트한다. */
    @Query("UPDATE meetings SET transcriptPath = :transcriptPath, pipelineStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTranscript(
        id: Long,
        transcriptPath: String,
        status: String,
        updatedAt: Long
    )

    /** 회의록 생성 완료 후 minutesPath와 파이프라인 상태를 업데이트한다. */
    @Query("UPDATE meetings SET minutesPath = :minutesPath, pipelineStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMinutes(
        id: Long,
        minutesPath: String,
        status: String,
        updatedAt: Long
    )

    /** 오디오 파일 경로로 회의를 조회한다. */
    @Query("SELECT * FROM meetings WHERE audioFilePath = :audioFilePath LIMIT 1")
    suspend fun getMeetingByAudioPath(audioFilePath: String): MeetingEntity?

    /** 제목에 검색어를 포함하는 회의를 녹음 시각 역순으로 조회한다. */
    @Query("SELECT * FROM meetings WHERE title LIKE '%' || :query || '%' ORDER BY recordedAt DESC")
    fun searchMeetings(query: String): Flow<List<MeetingEntity>>

    /** 회의록 경로만 초기화하고 상태를 TRANSCRIBED로 되돌린다 (전사 파일 보존). */
    @Query("UPDATE meetings SET minutesPath = NULL, pipelineStatus = 'TRANSCRIBED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearMinutesPath(id: Long, updatedAt: Long)

    /** 전사 경로와 회의록 경로를 모두 초기화하고 상태를 AUDIO_RECEIVED로 되돌린다. */
    @Query("UPDATE meetings SET transcriptPath = NULL, minutesPath = NULL, pipelineStatus = 'AUDIO_RECEIVED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearTranscriptPath(id: Long, updatedAt: Long)

    /** 회의를 삭제한다. */
    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Long)
}
