package com.autominuting.data.repository

import com.autominuting.data.local.dao.MinutesDao
import com.autominuting.data.local.dao.MinutesWithMeetingTitle
import com.autominuting.data.local.entity.MinutesEntity
import com.autominuting.domain.model.Minutes
import com.autominuting.domain.repository.MinutesDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * [MinutesDataRepository]의 구현체.
 * MinutesDao를 통해 데이터베이스에 접근하고, Entity-Domain 간 매핑을 수행한다.
 * 삭제 시 연관된 회의록 파일도 함께 삭제한다.
 */
class MinutesDataRepositoryImpl @Inject constructor(
    private val minutesDao: MinutesDao
) : MinutesDataRepository {

    override fun getAllMinutes(): Flow<List<Minutes>> =
        minutesDao.getAllMinutes().map { list ->
            list.map { it.toDomain() }
        }

    override fun getMinutesByMeetingId(meetingId: Long): Flow<List<Minutes>> =
        minutesDao.getMinutesByMeetingId(meetingId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getMinutesById(id: Long): Flow<Minutes?> =
        minutesDao.getMinutesById(id).map { it?.toDomain() }

    override suspend fun getMinutesByIdOnce(id: Long): Minutes? =
        minutesDao.getMinutesByIdOnce(id)?.toDomain()

    override fun getMinutesCountByMeetingId(meetingId: Long): Flow<Int> =
        minutesDao.getMinutesCountByMeetingId(meetingId)

    override fun getAllMinutesWithMeetingTitle(): Flow<List<Pair<Minutes, String?>>> =
        minutesDao.getAllMinutesWithMeetingTitle().map { list ->
            list.map { row ->
                Pair(
                    Minutes(
                        id = row.id,
                        meetingId = row.meetingId,
                        minutesPath = row.minutesPath,
                        minutesTitle = row.minutesTitle,
                        templateId = row.templateId,
                        createdAt = Instant.ofEpochMilli(row.createdAt),
                        updatedAt = Instant.ofEpochMilli(row.updatedAt)
                    ),
                    row.meetingTitle
                )
            }
        }

    override fun getMinutesCountPerMeeting(): Flow<Map<Long, Int>> =
        minutesDao.getMinutesCountPerMeeting().map { list ->
            list.associate { it.meetingId to it.count }
        }

    override suspend fun insertMinutes(minutes: Minutes): Long =
        minutesDao.insert(MinutesEntity.fromDomain(minutes))

    override suspend fun updateMinutesTitle(id: Long, title: String) {
        minutesDao.updateTitle(id, title, Instant.now().toEpochMilli())
    }

    override suspend fun updateMinutesUpdatedAt(id: Long, updatedAt: Long) {
        minutesDao.updateUpdatedAt(id, updatedAt)
    }

    override suspend fun deleteMinutes(id: Long) {
        // 파일 경로 조회 후 파일 삭제, 이후 DB 레코드 삭제
        val entity = minutesDao.getMinutesByIdOnce(id)
        entity?.minutesPath?.let { path ->
            try { File(path).delete() } catch (_: Exception) { }
        }
        minutesDao.delete(id)
    }

    override suspend fun deleteMinutesByMeetingId(meetingId: Long) {
        // 모든 연관 회의록 파일 삭제 후 DB 레코드 삭제
        val entities = minutesDao.getMinutesByMeetingIdOnce(meetingId)
        entities.forEach { entity ->
            try { File(entity.minutesPath).delete() } catch (_: Exception) { }
        }
        minutesDao.deleteByMeetingId(meetingId)
    }
}
