package com.autominuting.presentation.transcripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 전사 목록 화면의 상태를 관리하는 ViewModel.
 * 전사가 진행 중이거나 완료된 회의 목록을 필터링하여 제공한다.
 */
@HiltViewModel
class TranscriptsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    /** 전사 관련 상태의 회의 목록 (전사 중, 전사 완료, 회의록 생성 중, 완료, 실패) */
    val meetings: StateFlow<List<Meeting>> = meetingRepository.getMeetings()
        .map { list ->
            list.filter { meeting ->
                meeting.pipelineStatus in TRANSCRIPT_VISIBLE_STATUSES
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 전사 파일(+ 연관 회의록)을 삭제한다. */
    fun deleteTranscript(id: Long) {
        viewModelScope.launch {
            meetingRepository.deleteTranscript(id)
        }
    }

    companion object {
        /** 전사 목록에 표시할 파이프라인 상태 목록 */
        private val TRANSCRIPT_VISIBLE_STATUSES = setOf(
            PipelineStatus.TRANSCRIBING,
            PipelineStatus.TRANSCRIBED,
            PipelineStatus.GENERATING_MINUTES,
            PipelineStatus.COMPLETED,
            PipelineStatus.FAILED
        )
    }
}
