package com.autominuting.domain.repository

import com.autominuting.domain.model.PromptTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 프롬프트 템플릿 Repository 인터페이스.
 * 템플릿의 CRUD 및 기본 템플릿 자동 생성을 제공한다.
 */
interface PromptTemplateRepository {

    /** 모든 프롬프트 템플릿을 조회한다. */
    fun getAll(): Flow<List<PromptTemplate>>

    /** 특정 프롬프트 템플릿을 ID로 조회한다. */
    suspend fun getById(id: Long): PromptTemplate?

    /** 프롬프트 템플릿을 삽입하고 생성된 ID를 반환한다. */
    suspend fun insert(template: PromptTemplate): Long

    /** 프롬프트 템플릿을 업데이트한다. */
    suspend fun update(template: PromptTemplate)

    /** 프롬프트 템플릿을 삭제한다 (기본 제공 템플릿은 삭제 불가). */
    suspend fun delete(id: Long)

    /** 기본 제공 템플릿 3종이 없으면 자동 생성한다. */
    suspend fun ensureDefaultTemplates()
}
