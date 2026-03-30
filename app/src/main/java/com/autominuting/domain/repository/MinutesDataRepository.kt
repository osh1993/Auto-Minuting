package com.autominuting.domain.repository

import com.autominuting.domain.model.Minutes
import kotlinx.coroutines.flow.Flow

/**
 * 회의록 데이터 CRUD를 위한 Repository 인터페이스.
 * 회의록 생성(Gemini API 호출)은 [MinutesRepository]가 담당하고,
 * 이 인터페이스는 생성된 회의록의 저장/조회/삭제/수정만 담당한다.
 */
interface MinutesDataRepository {

    /** 모든 회의록을 생성 시각 역순으로 조회한다. */
    fun getAllMinutes(): Flow<List<Minutes>>

    /** 특정 Meeting에 연결된 회의록 목록을 조회한다. */
    fun getMinutesByMeetingId(meetingId: Long): Flow<List<Minutes>>

    /** 특정 회의록을 ID로 조회한다. */
    fun getMinutesById(id: Long): Flow<Minutes?>

    /** 특정 회의록을 ID로 일회성 조회한다. */
    suspend fun getMinutesByIdOnce(id: Long): Minutes?

    /** 특정 Meeting에 연결된 회의록 수를 조회한다. */
    fun getMinutesCountByMeetingId(meetingId: Long): Flow<Int>

    /** 회의록을 삽입하고 ID를 반환한다. */
    suspend fun insertMinutes(minutes: Minutes): Long

    /** 회의록 제목을 업데이트한다. */
    suspend fun updateMinutesTitle(id: Long, title: String)

    /** 회의록을 삭제한다 (파일도 삭제). */
    suspend fun deleteMinutes(id: Long)

    /** 특정 Meeting의 모든 회의록을 삭제한다 (파일도 삭제). */
    suspend fun deleteMinutesByMeetingId(meetingId: Long)
}
