package com.autominuting.presentation.transcripts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 전사 텍스트 편집 화면의 상태를 관리하는 ViewModel.
 * Navigation argument로 전달된 meetingId를 기반으로 회의 정보를 로드하고,
 * 전사 텍스트를 파일에서 읽어와 편집/저장 기능을 제공한다.
 */
@HiltViewModel
class TranscriptEditViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Navigation argument에서 추출한 회의 ID */
    private val meetingId: Long = checkNotNull(savedStateHandle["meetingId"])

    /** 현재 회의 정보 */
    val meeting: StateFlow<Meeting?> = meetingRepository.getMeetingById(meetingId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** 편집 가능한 전사 텍스트 */
    private val _transcriptText = MutableStateFlow("")
    val transcriptText: StateFlow<String> = _transcriptText.asStateFlow()

    /** 원본 전사 텍스트 (변경 감지용) */
    private var originalText: String = ""

    /** 저장 중 상태 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 저장 완료 이벤트 (true: 성공, false: 실패) */
    private val _saveSuccess = MutableSharedFlow<Boolean>()
    val saveSuccess: SharedFlow<Boolean> = _saveSuccess.asSharedFlow()

    init {
        // 회의 정보가 로드되면 전사 파일에서 텍스트 읽기
        viewModelScope.launch {
            val loadedMeeting = meetingRepository.getMeetingById(meetingId)
                .filterNotNull()
                .first()

            val transcriptPath = loadedMeeting.transcriptPath
            if (transcriptPath != null) {
                try {
                    val file = File(transcriptPath)
                    if (file.exists()) {
                        val text = file.readText()
                        originalText = text
                        _transcriptText.value = text
                    }
                } catch (e: Exception) {
                    // 파일 읽기 실패 시 빈 텍스트로 유지
                    originalText = ""
                    _transcriptText.value = ""
                }
            }
        }
    }

    /** 전사 텍스트를 업데이트한다. */
    fun updateText(newText: String) {
        _transcriptText.value = newText
    }

    /** 현재 텍스트가 원본과 다른지 확인한다 (변경 사항 존재 여부). */
    fun hasChanges(): Boolean = _transcriptText.value != originalText

    /**
     * 전사 텍스트를 파일에 저장하고 회의 정보를 업데이트한다.
     * 1. transcriptPath 파일에 텍스트 쓰기
     * 2. meeting.updatedAt 업데이트
     * 3. saveSuccess 이벤트 발행
     */
    fun saveTranscript() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val currentMeeting = meetingRepository.getMeetingById(meetingId)
                    .filterNotNull()
                    .first()

                val transcriptPath = currentMeeting.transcriptPath
                if (transcriptPath != null) {
                    // 파일에 텍스트 저장
                    File(transcriptPath).writeText(_transcriptText.value)

                    // 회의 정보 업데이트 (updatedAt 갱신)
                    meetingRepository.updateMeeting(
                        currentMeeting.copy(updatedAt = Instant.now())
                    )

                    // 원본 텍스트 갱신 (저장 후에는 현재 텍스트가 새로운 원본)
                    originalText = _transcriptText.value

                    _saveSuccess.emit(true)
                } else {
                    _saveSuccess.emit(false)
                }
            } catch (e: Exception) {
                _saveSuccess.emit(false)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
