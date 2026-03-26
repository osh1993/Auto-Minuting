package com.autominuting.presentation.minutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * 회의록 목록 화면의 상태를 관리하는 ViewModel.
 * 모든 회의 목록을 제공하며, 검색 기능과 회의록 정렬을 지원한다.
 */
@HiltViewModel
class MinutesViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    /** 검색어 내부 상태. */
    private val _searchQuery = MutableStateFlow("")

    /** 검색어 공개 상태. UI에서 양방향 바인딩에 사용한다. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 검색어를 변경한다. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * 회의 목록 (검색어 기반 필터링 + 회의록 있는 항목을 상단에 정렬).
     * 검색어가 비어있으면 전체 목록을, 있으면 제목 LIKE 검색 결과를 표시한다.
     * 300ms debounce로 불필요한 쿼리를 방지한다.
     */
    @OptIn(FlowPreview::class)
    val meetings: StateFlow<List<Meeting>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                meetingRepository.getMeetings()
            } else {
                meetingRepository.searchMeetings(query.trim())
            }
        }
        .map { list ->
            list.sortedByDescending { meeting -> meeting.minutesPath != null }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 회의록만 삭제한다 (전사 파일 보존). */
    fun deleteMeeting(id: Long) {
        viewModelScope.launch {
            meetingRepository.deleteMinutesOnly(id)
        }
    }

    /** 선택된 회의록을 일괄 삭제한다 (전사 파일 보존). */
    fun deleteSelectedMinutes(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { meetingRepository.deleteMinutesOnly(it) }
        }
    }

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
