package com.autominuting.presentation.settings

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.auth.AuthMode
import com.autominuting.data.auth.AuthState
import com.autominuting.data.auth.DriveAuthState
import com.autominuting.data.auth.GoogleAuthRepository
import com.autominuting.data.drive.DriveFolder
import com.autominuting.data.drive.DriveUploadRepository
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.security.GeminiApiKeyEntry
import com.autominuting.data.security.SecureApiKeyRepository
import com.autominuting.data.stt.WhisperModelManager
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesEngineType
import com.autominuting.domain.model.PromptTemplate
import com.autominuting.domain.model.SttEngineType
import com.autominuting.domain.repository.PromptTemplateRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    data class Success(val addedLabel: String = "") : ApiKeyValidationState
    data class Error(val message: String) : ApiKeyValidationState
}

/** Drive 폴더 피커 상태 */
sealed interface DriveFolderPickerState {
    data object Idle : DriveFolderPickerState
    data object Loading : DriveFolderPickerState
    /**
     * @param folders 현재 위치의 하위 폴더 목록
     * @param currentFolder 현재 위치 폴더 (null이면 root)
     * @param navStack 탐색 경로 스택 (root→현재까지의 조상 폴더들, 뒤로가기에 사용)
     */
    data class Loaded(
        val folders: List<DriveFolder>,
        val currentFolder: DriveFolder?,
        val navStack: List<DriveFolder> = emptyList()
    ) : DriveFolderPickerState
    data class Error(val message: String) : DriveFolderPickerState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val googleAuthRepository: GoogleAuthRepository,
    private val driveUploadRepository: DriveUploadRepository,
    private val whisperModelManager: WhisperModelManager,
    private val promptTemplateRepository: PromptTemplateRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    /** 현재 선택된 STT 엔진 유형 */
    val sttEngineType: StateFlow<SttEngineType> = userPreferencesRepository.sttEngineType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SttEngineType.GEMINI
        )

    /** 현재 선택된 회의록 엔진 유형 */
    val minutesEngineType: StateFlow<MinutesEngineType> = userPreferencesRepository.minutesEngineType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MinutesEngineType.GEMINI
        )

    /** Whisper 모델 다운로드 상태 */
    val whisperModelState: StateFlow<WhisperModelManager.ModelState> =
        whisperModelManager.modelState

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

    /** 프롬프트 템플릿 목록 */
    val templates: StateFlow<List<PromptTemplate>> = promptTemplateRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 기본 프롬프트 템플릿 ID (0 = 미설정, 매번 선택) */
    val defaultTemplateId: StateFlow<Long> = userPreferencesRepository.defaultTemplateId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L
        )

    /** 기본 커스텀 프롬프트 텍스트 */
    val defaultCustomPrompt: StateFlow<String> = userPreferencesRepository.defaultCustomPrompt
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    /** Google 인증 상태 */
    val authState: StateFlow<AuthState> = googleAuthRepository.authState

    /** Google Drive 인증 상태 */
    val driveAuthState: StateFlow<DriveAuthState> = googleAuthRepository.driveAuthState

    /** 전사 Drive 폴더 ID (per DRIVE-04) */
    val driveTranscriptFolderId: StateFlow<String> = userPreferencesRepository.driveTranscriptFolderId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    /** 회의록 Drive 폴더 ID (per DRIVE-04) */
    val driveMinutesFolderId: StateFlow<String> = userPreferencesRepository.driveMinutesFolderId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    /** Drive 자동 업로드 활성화 여부 */
    val driveAutoUploadEnabled: StateFlow<Boolean> = userPreferencesRepository.driveAutoUploadEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    /** Drive 폴더 피커 상태 — 전사/회의록 공용 */
    private val _driveFolderPickerState = MutableStateFlow<DriveFolderPickerState>(DriveFolderPickerState.Idle)
    val driveFolderPickerState: StateFlow<DriveFolderPickerState> = _driveFolderPickerState.asStateFlow()

    /** API 키 검증 상태 */
    private val _apiKeyValidationState = MutableStateFlow<ApiKeyValidationState>(ApiKeyValidationState.Idle)
    val apiKeyValidationState: StateFlow<ApiKeyValidationState> = _apiKeyValidationState.asStateFlow()

    /** 등록된 Gemini API 키 목록 */
    private val _geminiApiKeys = MutableStateFlow<List<GeminiApiKeyEntry>>(emptyList())
    val geminiApiKeys: StateFlow<List<GeminiApiKeyEntry>> = _geminiApiKeys.asStateFlow()

    /** 저장된 OAuth Client ID 존재 여부 */
    private val _hasOAuthClientId = MutableStateFlow(false)
    val hasOAuthClientId: StateFlow<Boolean> = _hasOAuthClientId.asStateFlow()

    /** OAuth Client ID 저장 완료 메시지 */
    private val _oauthClientIdSaved = MutableStateFlow(false)
    val oauthClientIdSaved: StateFlow<Boolean> = _oauthClientIdSaved.asStateFlow()

    /** Groq API 키 존재 여부 */
    private val _hasGroqApiKey = MutableStateFlow(false)
    val hasGroqApiKey: StateFlow<Boolean> = _hasGroqApiKey.asStateFlow()

    /** Deepgram API 키 존재 여부 */
    private val _hasDeepgramApiKey = MutableStateFlow(false)
    val hasDeepgramApiKey: StateFlow<Boolean> = _hasDeepgramApiKey.asStateFlow()

    /** CLOVA Speech Invoke URL 존재 여부 */
    private val _hasClovaInvokeUrl = MutableStateFlow(false)
    val hasClovaInvokeUrl: StateFlow<Boolean> = _hasClovaInvokeUrl.asStateFlow()

    /** CLOVA Speech Secret Key 존재 여부 */
    private val _hasClovaSecretKey = MutableStateFlow(false)
    val hasClovaSecretKey: StateFlow<Boolean> = _hasClovaSecretKey.asStateFlow()

    /** CLOVA Summary Client ID 존재 여부 */
    private val _hasClovaSummaryClientId = MutableStateFlow(false)
    val hasClovaSummaryClientId: StateFlow<Boolean> = _hasClovaSummaryClientId.asStateFlow()

    /** CLOVA Summary Client Secret 존재 여부 */
    private val _hasClovaSummaryClientSecret = MutableStateFlow(false)
    val hasClovaSummaryClientSecret: StateFlow<Boolean> = _hasClovaSummaryClientSecret.asStateFlow()

    init {
        // 레거시 단일 키 → 다중 키 구조 마이그레이션 (GEMINI-04)
        secureApiKeyRepository.migrateGeminiApiKeyIfNeeded()
        // 초기 로드
        _geminiApiKeys.value = secureApiKeyRepository.getGeminiApiKeys()
        _hasOAuthClientId.value = !secureApiKeyRepository.getGoogleOAuthClientId().isNullOrBlank()
        _hasGroqApiKey.value = !secureApiKeyRepository.getGroqApiKey().isNullOrBlank()
        _hasDeepgramApiKey.value = !secureApiKeyRepository.getDeepgramApiKey().isNullOrBlank()
        _hasClovaInvokeUrl.value = !secureApiKeyRepository.getClovaInvokeUrl().isNullOrBlank()
        _hasClovaSecretKey.value = !secureApiKeyRepository.getClovaSecretKey().isNullOrBlank()
        _hasClovaSummaryClientId.value = !secureApiKeyRepository.getClovaSummaryClientId().isNullOrBlank()
        _hasClovaSummaryClientSecret.value = !secureApiKeyRepository.getClovaSummaryClientSecret().isNullOrBlank()

        // 저장된 Google 인증 상태 복원
        viewModelScope.launch {
            googleAuthRepository.restoreAuthState()
        }
    }

    /** 회의록 엔진 유형을 변경한다. */
    fun setMinutesEngineType(type: MinutesEngineType) {
        viewModelScope.launch {
            userPreferencesRepository.setMinutesEngineType(type)
        }
    }

    /** STT 엔진 유형을 변경한다. */
    fun setSttEngineType(type: SttEngineType) {
        viewModelScope.launch {
            userPreferencesRepository.setSttEngineType(type)
        }
    }

    /** Whisper 모델 다운로드를 시작한다. */
    fun downloadWhisperModel() {
        whisperModelManager.downloadModel()
    }

    /** Whisper 모델을 삭제한다. */
    fun deleteWhisperModel() {
        whisperModelManager.deleteModel()
    }

    /** 기본 커스텀 프롬프트를 저장한다. */
    fun setDefaultCustomPrompt(prompt: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultCustomPrompt(prompt)
        }
    }

    /** 기본 프롬프트 템플릿을 변경한다. */
    fun setDefaultTemplateId(id: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultTemplateId(id)
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
     * Google Drive drive.file 스코프 권한을 요청한다.
     * hasResolution() == true 이면 NeedsConsent 상태로 전환되며,
     * SettingsScreen에서 rememberLauncherForActivityResult로 PendingIntent를 실행해야 한다.
     *
     * @param activity Activity 인스턴스
     */
    fun authorizeDrive(activity: Activity) {
        viewModelScope.launch {
            googleAuthRepository.authorizeDrive(activity)
        }
    }

    /**
     * Activity Result에서 Drive 동의 결과를 수신한 후 호출한다.
     * @param accessToken 동의 후 획득한 access token
     */
    fun onDriveAuthorizationResult(accessToken: String) {
        viewModelScope.launch {
            googleAuthRepository.onDriveAuthorizationResult(accessToken)
        }
    }

    /**
     * Drive 인증 실패 처리.
     * @param errorMessage 오류 메시지 (null이면 취소로 간주)
     */
    fun onDriveAuthorizationFailed(errorMessage: String?) {
        googleAuthRepository.onDriveAuthorizationFailed(errorMessage)
    }

    /** 전사 Drive 폴더 ID를 저장한다 (per DRIVE-04). 빈 문자열 저장 시 업로드 비활성 */
    fun setDriveTranscriptFolderId(folderId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDriveTranscriptFolderId(folderId)
        }
    }

    /** 회의록 Drive 폴더 ID를 저장한다 (per DRIVE-04). 빈 문자열 저장 시 업로드 비활성 */
    fun setDriveMinutesFolderId(folderId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDriveMinutesFolderId(folderId)
        }
    }

    /** Drive 자동 업로드 활성화 여부를 변경한다. */
    fun setDriveAutoUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDriveAutoUploadEnabled(enabled)
        }
    }

    /**
     * Drive 폴더 목록을 로드한다. root에서 시작하는 새 탐색을 시작한다.
     */
    fun loadDriveFolders(parentId: String? = null) {
        viewModelScope.launch {
            _driveFolderPickerState.value = DriveFolderPickerState.Loading
            val token = getFreshDriveToken()
            if (token.isNullOrBlank()) {
                _driveFolderPickerState.value = DriveFolderPickerState.Error("Drive 인증이 필요합니다")
                return@launch
            }
            val result = driveUploadRepository.listFolders(token, parentId)
            _driveFolderPickerState.value = if (result.isSuccess) {
                DriveFolderPickerState.Loaded(
                    folders = result.getOrDefault(emptyList()),
                    currentFolder = null,
                    navStack = emptyList()
                )
            } else {
                DriveFolderPickerState.Error(result.exceptionOrNull()?.message ?: "폴더 목록 조회 실패")
            }
        }
    }

    /**
     * 하위 폴더로 탐색한다. 현재 상태를 navStack에 쌓는다.
     */
    fun navigateIntoFolder(folder: DriveFolder) {
        val currentState = _driveFolderPickerState.value as? DriveFolderPickerState.Loaded ?: return
        viewModelScope.launch {
            _driveFolderPickerState.value = DriveFolderPickerState.Loading
            val token = getFreshDriveToken()
            if (token.isNullOrBlank()) {
                _driveFolderPickerState.value = DriveFolderPickerState.Error("Drive 인증이 필요합니다")
                return@launch
            }
            val result = driveUploadRepository.listFolders(token, folder.id)
            _driveFolderPickerState.value = if (result.isSuccess) {
                DriveFolderPickerState.Loaded(
                    folders = result.getOrDefault(emptyList()),
                    currentFolder = folder,
                    navStack = currentState.navStack + listOfNotNull(currentState.currentFolder)
                )
            } else {
                DriveFolderPickerState.Error(result.exceptionOrNull()?.message ?: "폴더 목록 조회 실패")
            }
        }
    }

    /**
     * 상위 폴더로 이동한다. navStack의 마지막 항목으로 복귀한다.
     */
    fun navigateUpFolder() {
        val currentState = _driveFolderPickerState.value as? DriveFolderPickerState.Loaded ?: return
        val stack = currentState.navStack
        val parentFolder = stack.lastOrNull() // null이면 root
        viewModelScope.launch {
            _driveFolderPickerState.value = DriveFolderPickerState.Loading
            val token = getFreshDriveToken()
            if (token.isNullOrBlank()) {
                _driveFolderPickerState.value = DriveFolderPickerState.Error("Drive 인증이 필요합니다")
                return@launch
            }
            val result = driveUploadRepository.listFolders(token, parentFolder?.id)
            _driveFolderPickerState.value = if (result.isSuccess) {
                DriveFolderPickerState.Loaded(
                    folders = result.getOrDefault(emptyList()),
                    currentFolder = parentFolder,
                    navStack = stack.dropLast(1)
                )
            } else {
                DriveFolderPickerState.Error(result.exceptionOrNull()?.message ?: "폴더 목록 조회 실패")
            }
        }
    }

    /**
     * Drive에 새 폴더를 생성한다. 생성 후 현재 위치의 목록을 새로 고침한다.
     *
     * @param folderName 생성할 폴더 이름
     * @param onCreated 생성된 폴더를 전달하는 콜백 (자동 선택 여부는 호출부에서 결정)
     */
    fun createDriveFolder(
        folderName: String,
        onCreated: (DriveFolder) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentState = _driveFolderPickerState.value as? DriveFolderPickerState.Loaded
        val parentId = currentState?.currentFolder?.id  // null이면 root
        viewModelScope.launch {
            val token = getFreshDriveToken()
            if (token.isNullOrBlank()) {
                onError("Drive 인증이 필요합니다")
                return@launch
            }
            val result = driveUploadRepository.createFolder(token, folderName, parentId)
            if (result.isSuccess) {
                val folder = result.getOrThrow()
                onCreated(folder)
                // 생성 후 현재 위치 목록 새로 고침 (새 폴더 반영)
                val refreshResult = driveUploadRepository.listFolders(token, parentId)
                if (refreshResult.isSuccess && currentState != null) {
                    _driveFolderPickerState.value = currentState.copy(
                        folders = refreshResult.getOrDefault(emptyList())
                    )
                }
            } else {
                onError(result.exceptionOrNull()?.message ?: "폴더 생성 실패")
            }
        }
    }

    /** Drive 폴더 피커 상태를 초기화한다. */
    fun dismissDriveFolderPicker() {
        _driveFolderPickerState.value = DriveFolderPickerState.Idle
    }

    /**
     * Drive fresh token을 획득한다.
     * 이미 승인된 스코프는 Activity 없이 즉시 발급 가능하다.
     */
    private suspend fun getFreshDriveToken(): String? {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(authRequest).await()
            if (!result.hasResolution()) {
                val token = result.accessToken
                if (!token.isNullOrBlank()) return token
            }
            googleAuthRepository.getDriveAccessToken()
        } catch (e: Exception) {
            Log.w(TAG, "Drive fresh token 획득 실패 — 캐시 폴백: ${e.message}")
            googleAuthRepository.getDriveAccessToken()
        }
    }

    /**
     * Drive 인증을 해제한다.
     */
    fun revokeDriveAuth() {
        viewModelScope.launch {
            googleAuthRepository.revokeDriveAuthorization()
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

    /**
     * 새 Gemini API 키를 검증한 후 다중 키 목록에 추가한다 (GEMINI-01, GEMINI-04).
     * 검증: generateContent("Hello") 호출 성공 여부로 판단.
     * 중복 키는 addGeminiApiKey에서 IllegalArgumentException으로 거부된다.
     *
     * @param label 사용자 입력 별명
     * @param apiKey 추가할 API 키 값
     */
    fun validateAndAddApiKey(label: String, apiKey: String) {
        viewModelScope.launch {
            _apiKeyValidationState.value = ApiKeyValidationState.Validating
            try {
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey
                )
                val response = withTimeout(10_000) {
                    model.generateContent("Hello")
                }
                if (response.text != null) {
                    secureApiKeyRepository.addGeminiApiKey(label, apiKey)
                    _geminiApiKeys.value = secureApiKeyRepository.getGeminiApiKeys()
                    _apiKeyValidationState.value = ApiKeyValidationState.Success(addedLabel = label)
                } else {
                    _apiKeyValidationState.value = ApiKeyValidationState.Error(
                        "빈 응답이 반환되었습니다. 키 값을 다시 확인해주세요."
                    )
                }
            } catch (e: IllegalArgumentException) {
                // 중복 키
                _apiKeyValidationState.value = ApiKeyValidationState.Error(
                    e.message ?: "이미 등록된 API 키입니다."
                )
            } catch (e: Exception) {
                val msg = e.message ?: ""
                _apiKeyValidationState.value = ApiKeyValidationState.Error(
                    when {
                        msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ->
                            "네트워크 오류로 검증하지 못했습니다. 연결 상태를 확인 후 다시 시도하세요."
                        else -> "API 키 검증에 실패했습니다. 키 값을 다시 확인해주세요."
                    }
                )
            }
        }
    }

    /**
     * 지정 인덱스의 Gemini API 키를 삭제하고 목록을 갱신한다.
     * @param index 삭제할 키의 인덱스 (0-based, GeminiApiKeyEntry.index 값)
     */
    fun removeGeminiApiKey(index: Int) {
        secureApiKeyRepository.removeGeminiApiKey(index)
        _geminiApiKeys.value = secureApiKeyRepository.getGeminiApiKeys()
    }

    /** API 키 검증 상태를 초기화한다. */
    fun resetApiKeyValidationState() {
        _apiKeyValidationState.value = ApiKeyValidationState.Idle
    }

    /** Google OAuth Web Client ID를 저장한다. */
    fun saveOAuthClientId(clientId: String) {
        secureApiKeyRepository.saveGoogleOAuthClientId(clientId)
        _hasOAuthClientId.value = true
        _oauthClientIdSaved.value = true
    }

    /** 저장된 OAuth Client ID를 삭제한다. */
    fun clearOAuthClientId() {
        secureApiKeyRepository.clearGoogleOAuthClientId()
        _hasOAuthClientId.value = false
        _oauthClientIdSaved.value = false
    }

    /** OAuth Client ID 저장 상태를 초기화한다. */
    fun resetOAuthClientIdSaved() {
        _oauthClientIdSaved.value = false
    }

    /** Groq API 키를 저장한다. */
    fun saveGroqApiKey(apiKey: String) {
        secureApiKeyRepository.saveGroqApiKey(apiKey)
        _hasGroqApiKey.value = true
    }

    /** Groq API 키를 삭제한다. */
    fun clearGroqApiKey() {
        secureApiKeyRepository.clearGroqApiKey()
        _hasGroqApiKey.value = false
    }

    /** Deepgram API 키를 저장한다. */
    fun saveDeepgramApiKey(apiKey: String) {
        secureApiKeyRepository.saveDeepgramApiKey(apiKey)
        _hasDeepgramApiKey.value = true
    }

    /** Deepgram API 키를 삭제한다. */
    fun clearDeepgramApiKey() {
        secureApiKeyRepository.clearDeepgramApiKey()
        _hasDeepgramApiKey.value = false
    }

    /** CLOVA Speech Invoke URL을 저장한다. */
    fun saveClovaInvokeUrl(url: String) {
        secureApiKeyRepository.saveClovaInvokeUrl(url)
        _hasClovaInvokeUrl.value = true
    }

    /** CLOVA Speech Invoke URL을 삭제한다. */
    fun clearClovaInvokeUrl() {
        secureApiKeyRepository.clearClovaInvokeUrl()
        _hasClovaInvokeUrl.value = false
    }

    /** CLOVA Speech Secret Key를 저장한다. */
    fun saveClovaSecretKey(secretKey: String) {
        secureApiKeyRepository.saveClovaSecretKey(secretKey)
        _hasClovaSecretKey.value = true
    }

    /** CLOVA Speech Secret Key를 삭제한다. */
    fun clearClovaSecretKey() {
        secureApiKeyRepository.clearClovaSecretKey()
        _hasClovaSecretKey.value = false
    }

    /** CLOVA Summary Client ID를 저장한다. */
    fun saveClovaSummaryClientId(clientId: String) {
        secureApiKeyRepository.saveClovaSummaryClientId(clientId)
        _hasClovaSummaryClientId.value = true
    }

    /** CLOVA Summary Client ID를 삭제한다. */
    fun clearClovaSummaryClientId() {
        secureApiKeyRepository.clearClovaSummaryClientId()
        _hasClovaSummaryClientId.value = false
    }

    /** CLOVA Summary Client Secret을 저장한다. */
    fun saveClovaSummaryClientSecret(secret: String) {
        secureApiKeyRepository.saveClovaSummaryClientSecret(secret)
        _hasClovaSummaryClientSecret.value = true
    }

    /** CLOVA Summary Client Secret을 삭제한다. */
    fun clearClovaSummaryClientSecret() {
        secureApiKeyRepository.clearClovaSummaryClientSecret()
        _hasClovaSummaryClientSecret.value = false
    }
}
