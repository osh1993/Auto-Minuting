package com.autominuting.presentation.dashboard

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
import javax.inject.Inject

/**
 * 대시보드 화면의 상태를 관리하는 ViewModel.
 * 현재 진행 중인 파이프라인이 있으면 해당 회의 정보를 제공한다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    /** 진행 중인 파이프라인 상태에 해당하는 목록 */
    private val activeStatuses = listOf(
        PipelineStatus.AUDIO_RECEIVED,
        PipelineStatus.TRANSCRIBING,
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.GENERATING_MINUTES
    )

    /** 현재 진행 중인 파이프라인의 회의 정보 (없으면 null) */
    val activePipeline: StateFlow<Meeting?> = meetingRepository.getMeetings()
        .map { meetings ->
            meetings.firstOrNull { it.pipelineStatus in activeStatuses }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
}
