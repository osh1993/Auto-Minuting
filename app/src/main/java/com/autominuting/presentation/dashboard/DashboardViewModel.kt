package com.autominuting.presentation.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.service.AudioCollectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 대시보드 화면의 상태를 관리하는 ViewModel.
 * 현재 진행 중인 파이프라인이 있으면 해당 회의 정보를 제공한다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 진행 중인 파이프라인 상태에 해당하는 목록 */
    private val activeStatuses = listOf(
        PipelineStatus.AUDIO_RECEIVED,
        PipelineStatus.TRANSCRIBING,
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.GENERATING_MINUTES
    )

    /** 오디오 수집 서비스 실행 상태 */
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    /**
     * AudioCollectionService를 시작/중지하는 토글 함수.
     * 수집 중이면 중지하고, 미수집이면 시작한다.
     */
    fun toggleCollection() {
        val intent = Intent(context, AudioCollectionService::class.java)
        if (_isCollecting.value) {
            intent.action = AudioCollectionService.ACTION_STOP
            context.startService(intent)
        } else {
            intent.action = AudioCollectionService.ACTION_START
            context.startForegroundService(intent)
        }
        _isCollecting.value = !_isCollecting.value
    }

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
