package com.autominuting.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autominuting.domain.model.PromptTemplate
import java.time.Instant

/**
 * Room 데이터베이스의 prompt_templates 테이블에 대응하는 Entity.
 * 도메인 모델 [PromptTemplate]과의 매핑 함수를 제공한다.
 */
@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 템플릿 이름 */
    val name: String,
    /** 프롬프트 텍스트 */
    val promptText: String,
    /** 기본 제공 템플릿 여부 (true이면 삭제 불가) */
    val isBuiltIn: Boolean = false,
    /** 레코드 생성 시각 (epoch millis) */
    val createdAt: Long,
    /** 레코드 최종 수정 시각 (epoch millis) */
    val updatedAt: Long
) {
    /**
     * Entity를 도메인 모델로 변환한다.
     */
    fun toDomain(): PromptTemplate = PromptTemplate(
        id = id,
        name = name,
        promptText = promptText,
        isBuiltIn = isBuiltIn,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    companion object {
        /**
         * 도메인 모델을 Entity로 변환한다.
         */
        fun fromDomain(template: PromptTemplate): PromptTemplateEntity = PromptTemplateEntity(
            id = template.id,
            name = template.name,
            promptText = template.promptText,
            isBuiltIn = template.isBuiltIn,
            createdAt = template.createdAt.toEpochMilli(),
            updatedAt = template.updatedAt.toEpochMilli()
        )
    }
}
