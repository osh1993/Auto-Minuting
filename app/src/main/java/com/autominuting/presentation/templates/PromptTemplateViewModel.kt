package com.autominuting.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.PromptTemplate
import com.autominuting.domain.repository.PromptTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * 프롬프트 템플릿 관리 화면의 상태를 관리하는 ViewModel.
 * 템플릿 목록 조회, 추가, 편집, 삭제 기능을 제공한다.
 */
@HiltViewModel
class PromptTemplateViewModel @Inject constructor(
    private val repository: PromptTemplateRepository
) : ViewModel() {

    init {
        // 화면 진입 시 기본 제공 템플릿 3종 보장
        viewModelScope.launch {
            repository.ensureDefaultTemplates()
        }
    }

    /** 전체 프롬프트 템플릿 목록 (기본 제공 먼저, 이름순) */
    val templates: StateFlow<List<PromptTemplate>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 새 프롬프트 템플릿을 추가한다.
     *
     * @param name 템플릿 이름
     * @param promptText 프롬프트 텍스트
     */
    fun addTemplate(name: String, promptText: String) {
        viewModelScope.launch {
            val now = Instant.now()
            repository.insert(
                PromptTemplate(
                    name = name,
                    promptText = promptText,
                    isBuiltIn = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * 기존 프롬프트 템플릿을 업데이트한다.
     *
     * @param template 업데이트할 템플릿 (id 기준)
     */
    fun updateTemplate(template: PromptTemplate) {
        viewModelScope.launch {
            repository.update(template.copy(updatedAt = Instant.now()))
        }
    }

    /**
     * 프롬프트 템플릿을 삭제한다 (기본 제공 템플릿은 DAO에서 보호).
     *
     * @param id 삭제할 템플릿 ID
     */
    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}
