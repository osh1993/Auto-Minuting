package com.autominuting.domain.repository

import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import kotlinx.coroutines.flow.Flow

/**
 * 회의 데이터 접근을 위한 Repository 인터페이스.
 * Data 레이어에서 구현체를 제공하며, Domain 레이어는 이 인터페이스에만 의존한다.
 */
interface MeetingRepository {

    /** 모든 회의 목록을 녹음 시각 역순으로 조회한다. */
    fun getMeetings(): Flow<List<Meeting>>

    /** 특정 회의를 ID로 조회한다. */
    fun getMeetingById(id: Long): Flow<Meeting?>

    /** 새 회의를 삽입하고 생성된 ID를 반환한다. */
    suspend fun insertMeeting(meeting: Meeting): Long

    /** 회의 정보를 업데이트한다. */
    suspend fun updateMeeting(meeting: Meeting)

    /** 회의의 파이프라인 상태를 업데이트한다. */
    suspend fun updatePipelineStatus(
        id: Long,
        status: PipelineStatus,
        errorMessage: String? = null
    )

    /** 제목에 검색어를 포함하는 회의를 조회한다. */
    fun searchMeetings(query: String): Flow<List<Meeting>>

    /** 회의를 삭제한다. */
    suspend fun deleteMeeting(id: Long)

    /** 회의록 파일만 삭제하고 전사 파일을 보존한다. */
    suspend fun deleteMinutesOnly(id: Long)

    /** 전사 파일(+ 연관 회의록 파일)을 삭제한다. */
    suspend fun deleteTranscript(id: Long)
}
