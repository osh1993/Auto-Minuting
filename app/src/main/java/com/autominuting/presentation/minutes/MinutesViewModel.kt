package com.autominuting.presentation.minutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * 회의록 목록 화면의 상태를 관리하는 ViewModel.
 * 모든 회의 목록을 제공하며, 회의록이 있는 회의를 상단에 표시한다.
 */
@HiltViewModel
class MinutesViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    /**
     * 회의 목록 (회의록 있는 항목을 상단에 정렬).
     * minutesPath가 null이 아닌 회의를 먼저 표시하고,
     * 그 외 회의는 파이프라인 상태를 확인할 수 있도록 하단에 표시한다.
     */
    val meetings: StateFlow<List<Meeting>> = meetingRepository.getMeetings()
        .map { list ->
            list.sortedByDescending { meeting -> meeting.minutesPath != null }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 회의록 파일의 내용을 읽어 반환한다.
     * 파일이 존재하지 않거나 읽기 실패 시 null을 반환한다.
     *
     * @param minutesPath 회의록 파일 절대 경로
     * @return 파일 내용 문자열 또는 null
     */
    fun getMinutesContent(minutesPath: String): String? {
        return try {
            val file = File(minutesPath)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }
}
