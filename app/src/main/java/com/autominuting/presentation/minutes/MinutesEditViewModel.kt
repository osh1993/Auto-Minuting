package com.autominuting.presentation.minutes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Minutes
import com.autominuting.domain.repository.MinutesDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 회의록 텍스트 편집 화면의 상태를 관리하는 ViewModel.
 * Navigation argument로 전달된 minutesId를 기반으로 회의록 정보를 로드하고,
 * 회의록 텍스트를 파일에서 읽어와 편집/저장 기능을 제공한다.
 */
@HiltViewModel
class MinutesEditViewModel @Inject constructor(
    private val minutesDataRepository: MinutesDataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Navigation argument에서 추출한 회의록 ID */
    private val minutesId: Long = checkNotNull(savedStateHandle["minutesId"])

    /** 현재 회의록 정보 */
    val minutes: StateFlow<Minutes?> = minutesDataRepository.getMinutesById(minutesId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** 편집 가능한 회의록 텍스트 */
    private val _editText = MutableStateFlow("")
    val editText: StateFlow<String> = _editText.asStateFlow()

    /** 원본 회의록 텍스트 (변경 감지용) */
    private var originalText: String = ""

    /** 저장 중 상태 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 저장 완료 이벤트 (true: 성공, false: 실패) */
    private val _saveSuccess = MutableSharedFlow<Boolean>()
    val saveSuccess: SharedFlow<Boolean> = _saveSuccess.asSharedFlow()

    init {
        // 회의록 정보가 로드되면 회의록 파일에서 텍스트 읽기
        viewModelScope.launch {
            val loadedMinutes = minutesDataRepository.getMinutesById(minutesId)
                .filterNotNull()
                .first()

            try {
                val file = File(loadedMinutes.minutesPath)
                if (file.exists()) {
                    val text = withContext(Dispatchers.IO) { file.readText() }
                    originalText = text
                    _editText.value = text
                }
            } catch (e: Exception) {
                // 파일 읽기 실패 시 빈 텍스트로 유지
                originalText = ""
                _editText.value = ""
            }
        }
    }

    /** 회의록 텍스트를 업데이트한다. */
    fun updateText(newText: String) {
        _editText.value = newText
    }

    /** 현재 텍스트가 원본과 다른지 확인한다 (변경 사항 존재 여부). */
    fun hasChanges(): Boolean = _editText.value != originalText

    /**
     * 회의록 텍스트를 파일에 저장하고 회의록 정보를 업데이트한다.
     * 1. minutesPath 파일에 텍스트 쓰기 (Dispatchers.IO)
     * 2. minutes.updatedAt 업데이트 (updateMinutesUpdatedAt 호출)
     * 3. saveSuccess 이벤트 발행
     */
    fun saveMinutes() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val currentMinutes = minutesDataRepository.getMinutesById(minutesId)
                    .filterNotNull()
                    .first()

                withContext(Dispatchers.IO) {
                    File(currentMinutes.minutesPath).writeText(_editText.value)
                }

                minutesDataRepository.updateMinutesUpdatedAt(
                    id = minutesId,
                    updatedAt = Instant.now().toEpochMilli()
                )

                // 저장 후 원본 텍스트를 현재 텍스트로 갱신
                originalText = _editText.value

                _saveSuccess.emit(true)
            } catch (e: Exception) {
                _saveSuccess.emit(false)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
