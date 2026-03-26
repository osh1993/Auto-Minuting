package com.autominuting.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.autominuting.data.local.entity.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * prompt_templates 테이블에 대한 데이터 접근 객체(DAO).
 * Room이 컴파일 시점에 구현체를 자동 생성한다.
 */
@Dao
interface PromptTemplateDao {

    /** 모든 템플릿을 조회한다. 기본 제공 템플릿이 먼저, 그 다음 이름순. */
    @Query("SELECT * FROM prompt_templates ORDER BY isBuiltIn DESC, name ASC")
    fun getAllTemplates(): Flow<List<PromptTemplateEntity>>

    /** 특정 템플릿을 ID로 조회한다. */
    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun getById(id: Long): PromptTemplateEntity?

    /** 템플릿을 삽입하고 생성된 행 ID를 반환한다. */
    @Insert
    suspend fun insert(entity: PromptTemplateEntity): Long

    /** 템플릿 정보를 업데이트한다. */
    @Update
    suspend fun update(entity: PromptTemplateEntity)

    /** 사용자 정의 템플릿만 삭제한다 (isBuiltIn = 0인 경우만). */
    @Query("DELETE FROM prompt_templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun delete(id: Long)

    /** 전체 템플릿 개수를 반환한다. */
    @Query("SELECT COUNT(*) FROM prompt_templates")
    suspend fun count(): Int
}
