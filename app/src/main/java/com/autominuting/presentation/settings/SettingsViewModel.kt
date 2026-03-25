package com.autominuting.presentation.settings

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.auth.AuthMode
import com.autominuting.data.auth.AuthState
import com.autominuting.data.auth.GoogleAuthRepository
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
 * 회의록 형식, 자동화 모드, Gemini 인증(API 키/OAuth) 설정을 DataStore를 통해 관리한다.
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
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val googleAuthRepository: GoogleAuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

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

    /** 현재 인증 모드 (API 키 / OAuth) */
    val authMode: StateFlow<AuthMode> = userPreferencesRepository.authMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthMode.API_KEY
        )

    /** Google 인증 상태 */
    val authState: StateFlow<AuthState> = googleAuthRepository.authState

    /** API 키 검증 상태 */
    private val _apiKeyValidationState = MutableStateFlow<ApiKeyValidationState>(ApiKeyValidationState.Idle)
    val apiKeyValidationState: StateFlow<ApiKeyValidationState> = _apiKeyValidationState.asStateFlow()

    /** 저장된 API 키 존재 여부 */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    init {
        // 초기 로드: 저장된 API 키 존재 여부 확인
        _hasApiKey.value = secureApiKeyRepository.getGeminiApiKey() != null

        // 저장된 Google 인증 상태 복원
        viewModelScope.launch {
            googleAuthRepository.restoreAuthState()
        }
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

    /** 인증 모드를 변경한다. */
    fun setAuthMode(mode: AuthMode) {
        viewModelScope.launch {
            userPreferencesRepository.setAuthMode(mode)
        }
    }

    /**
     * Google 계정으로 로그인한다.
     * Credential Manager는 Activity Context가 필요하므로 파라미터로 받는다.
     *
     * @param activityContext Activity 컨텍스트
     */
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            googleAuthRepository.signIn(activityContext)

            // 로그인 성공 시 자동으로 access token 권한 부여 시도
            if (googleAuthRepository.isSignedIn()) {
                val activity = activityContext as? Activity
                if (activity != null) {
                    googleAuthRepository.authorize(activity)
                } else {
                    Log.w(TAG, "Activity 컨텍스트가 아니어서 자동 권한 부여 건너뜀")
                }
            }
        }
    }

    /**
     * Gemini API 접근 권한을 수동으로 요청한다.
     *
     * @param activity Activity 인스턴스
     */
    fun authorizeGeminiAccess(activity: Activity) {
        viewModelScope.launch {
            googleAuthRepository.authorize(activity)
        }
    }

    /**
     * Google 로그아웃을 수행한다.
     * 인증 모드를 API 키로 리셋한다.
     */
    fun signOut() {
        viewModelScope.launch {
            googleAuthRepository.signOut()
            userPreferencesRepository.setAuthMode(AuthMode.API_KEY)
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
