package com.autominuting.data.repository

import com.autominuting.data.local.dao.PromptTemplateDao
import com.autominuting.data.local.entity.PromptTemplateEntity
import com.autominuting.data.minutes.MinutesPrompts
import com.autominuting.domain.model.PromptTemplate
import com.autominuting.domain.repository.PromptTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PromptTemplateRepository]의 구현체.
 * Room DAO를 통해 프롬프트 템플릿을 영속 관리한다.
 */
@Singleton
class PromptTemplateRepositoryImpl @Inject constructor(
    private val dao: PromptTemplateDao
) : PromptTemplateRepository {

    override fun getAll(): Flow<List<PromptTemplate>> =
        dao.getAllTemplates().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getById(id: Long): PromptTemplate? =
        dao.getById(id)?.toDomain()

    override suspend fun insert(template: PromptTemplate): Long =
        dao.insert(PromptTemplateEntity.fromDomain(template))

    override suspend fun update(template: PromptTemplate) =
        dao.update(PromptTemplateEntity.fromDomain(template))

    override suspend fun delete(id: Long) =
        dao.delete(id)

    /**
     * 기본 제공 템플릿 3종이 없으면 자동 삽입한다.
     * - 구조화된 회의록 (MinutesPrompts.STRUCTURED)
     * - 요약 (MinutesPrompts.SUMMARY)
     * - 액션 아이템 (MinutesPrompts.ACTION_ITEMS)
     */
    override suspend fun ensureDefaultTemplates() {
        if (dao.count() > 0) return

        val now = System.currentTimeMillis()
        val defaults = listOf(
            PromptTemplateEntity(
                name = "구조화된 회의록",
                promptText = MinutesPrompts.STRUCTURED,
                isBuiltIn = true,
                createdAt = now,
                updatedAt = now
            ),
            PromptTemplateEntity(
                name = "요약",
                promptText = MinutesPrompts.SUMMARY,
                isBuiltIn = true,
                createdAt = now,
                updatedAt = now
            ),
            PromptTemplateEntity(
                name = "액션 아이템",
                promptText = MinutesPrompts.ACTION_ITEMS,
                isBuiltIn = true,
                createdAt = now,
                updatedAt = now
            )
        )
        defaults.forEach { dao.insert(it) }
    }
}
