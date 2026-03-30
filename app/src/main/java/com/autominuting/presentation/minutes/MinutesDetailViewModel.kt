package com.autominuting.presentation.minutes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.domain.model.Minutes
import com.autominuting.domain.repository.MinutesDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * 회의록 상세 읽기 화면의 상태를 관리하는 ViewModel.
 * Navigation argument로 전달된 minutesId를 기반으로 Minutes 정보를 로드하고,
 * 회의록 Markdown 파일의 내용을 읽어 제공한다.
 */
@HiltViewModel
class MinutesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val minutesDataRepository: MinutesDataRepository
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

    /**
     * 회의록 파일 내용.
     * Minutes의 minutesPath를 감시하여 파일 내용을 읽는다.
     * minutesPath가 null이거나 파일을 읽을 수 없으면 빈 문자열을 반환한다.
     */
    @Suppress("OPT_IN_USAGE")
    val minutesContent: StateFlow<String> = minutesDataRepository.getMinutesById(minutesId)
        .flatMapLatest { minutes ->
            flow {
                val path = minutes?.minutesPath
                if (path != null) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            emit(file.readText())
                        } else {
                            emit("")
                        }
                    } catch (e: Exception) {
                        emit("")
                    }
                } else {
                    emit("")
                }
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )
}
