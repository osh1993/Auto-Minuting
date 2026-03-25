package com.autominuting.data.repository

import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.entity.MeetingEntity
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * [MeetingRepository]의 구현체.
 * Room DAO를 통해 데이터베이스에 접근하고, Entity-Domain 간 매핑을 수행한다.
 */
class MeetingRepositoryImpl @Inject constructor(
    private val meetingDao: MeetingDao
) : MeetingRepository {

    override fun getMeetings(): Flow<List<Meeting>> =
        meetingDao.getAllMeetings().map { list ->
            list.map { it.toDomain() }
        }

    override fun getMeetingById(id: Long): Flow<Meeting?> =
        meetingDao.getMeetingById(id).map { it?.toDomain() }

    override suspend fun insertMeeting(meeting: Meeting): Long =
        meetingDao.insert(MeetingEntity.fromDomain(meeting))

    override suspend fun updateMeeting(meeting: Meeting) =
        meetingDao.update(MeetingEntity.fromDomain(meeting))

    override suspend fun updatePipelineStatus(
        id: Long,
        status: PipelineStatus,
        errorMessage: String?
    ) = meetingDao.updatePipelineStatus(
        id = id,
        status = status.name,
        errorMessage = errorMessage,
        updatedAt = Instant.now().toEpochMilli()
    )

    override fun searchMeetings(query: String): Flow<List<Meeting>> =
        meetingDao.searchMeetings(query).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun deleteMeeting(id: Long) {
        // 1. 파일 경로 조회 (삭제 전에 Entity에서 가져와야 함)
        val entity = meetingDao.getMeetingByIdOnce(id) ?: return

        // 2. 연관 파일 삭제 (실패해도 DB 삭제 진행 -- 고아 파일 > 고아 레코드)
        listOfNotNull(
            entity.audioFilePath,
            entity.transcriptPath,
            entity.minutesPath
        ).forEach { path ->
            try { File(path).delete() } catch (_: Exception) { }
        }

        // 3. DB 레코드 삭제
        meetingDao.delete(id)
    }
}
