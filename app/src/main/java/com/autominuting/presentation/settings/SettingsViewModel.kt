package com.autominuting.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.security.SecureApiKeyRepository
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * 설정 화면의 상태를 관리하는 ViewModel.
 * 회의록 형식 및 자동화 모드 설정을 DataStore를 통해 읽고 쓴다.
 */
/** API 키 검증 상태 */
sealed interface ApiKeyValidationState {
    data object Idle : ApiKeyValidationState
    data object Validating : ApiKeyValidationState
    data object Success : ApiKeyValidationState
    data class Error(val message: String) : ApiKeyValidationState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val secureApiKeyRepository: SecureApiKeyRepository
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

    /** API 키 검증 상태 */
    private val _apiKeyValidationState = MutableStateFlow<ApiKeyValidationState>(ApiKeyValidationState.Idle)
    val apiKeyValidationState: StateFlow<ApiKeyValidationState> = _apiKeyValidationState.asStateFlow()

    /** 저장된 API 키 존재 여부 */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    init {
        // 초기 로드: 저장된 API 키 존재 여부 확인
        _hasApiKey.value = secureApiKeyRepository.getGeminiApiKey() != null
    }

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

    /** API 키를 Gemini API 테스트 호출로 검증 후 암호화 저장한다. */
    fun validateAndSaveApiKey(apiKey: String) {
        viewModelScope.launch {
            _apiKeyValidationState.value = ApiKeyValidationState.Validating
            try {
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey
                )
                // 최소 비용 테스트 호출
                val response = withTimeout(10_000) {
                    model.generateContent("Hello")
                }
                if (response.text != null) {
                    secureApiKeyRepository.saveGeminiApiKey(apiKey)
                    _hasApiKey.value = true
                    _apiKeyValidationState.value = ApiKeyValidationState.Success
                } else {
                    _apiKeyValidationState.value = ApiKeyValidationState.Error("빈 응답이 반환되었습니다")
                }
            } catch (e: Exception) {
                _apiKeyValidationState.value = ApiKeyValidationState.Error(
                    e.message ?: "API 키 검증에 실패했습니다"
                )
            }
        }
    }

    /** API 키 검증 상태를 초기화한다. */
    fun resetApiKeyValidationState() {
        _apiKeyValidationState.value = ApiKeyValidationState.Idle
    }

    /** 저장된 API 키를 삭제한다. */
    fun clearApiKey() {
        secureApiKeyRepository.clearGeminiApiKey()
        _hasApiKey.value = false
    }
}
