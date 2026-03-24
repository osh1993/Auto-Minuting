package com.autominuting.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 화면의 상태를 관리하는 ViewModel.
 * 회의록 형식 및 자동화 모드 설정을 DataStore를 통해 읽고 쓴다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** 현재 선택된 회의록 형식 */
    val minutesFormat: StateFlow<MinutesFormat> = userPreferencesRepository.minutesFormat
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MinutesFormat.STRUCTURED
        )

    /** 현재 선택된 자동화 모드 */
    val automationMode: StateFlow<AutomationMode> = userPreferencesRepository.automationMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AutomationMode.FULL_AUTO
        )

    /** 회의록 형식을 변경한다. */
    fun setMinutesFormat(format: MinutesFormat) {
        viewModelScope.launch {
            userPreferencesRepository.setMinutesFormat(format)
        }
    }

    /** 자동화 모드를 변경한다. */
    fun setAutomationMode(mode: AutomationMode) {
        viewModelScope.launch {
            userPreferencesRepository.setAutomationMode(mode)
        }
    }
}
