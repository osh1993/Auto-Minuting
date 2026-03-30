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
        val entity = meetingDao.getMeetingByIdOnce(id) ?: return

        // 오디오/전사 파일만 삭제 (Minutes 파일은 FK SET_NULL로 Minutes Row가 고아로 보존되므로 유지)
        listOfNotNull(
            entity.audioFilePath.takeIf { it.isNotBlank() },
            entity.transcriptPath
        ).forEach { path ->
            try { File(path).delete() } catch (_: Exception) { }
        }

        meetingDao.delete(id)
    }
}
