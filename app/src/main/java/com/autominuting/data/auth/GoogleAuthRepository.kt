package com.autominuting.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.autominuting.BuildConfig
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.security.SecureApiKeyRepository
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google 인증을 관리하는 Repository.
 *
 * Credential Manager API로 Google Sign-In을 수행하고,
 * AuthorizationClient로 Gemini API 호출에 필요한 access token을 획득한다.
 *
 * 로그인 상태는 DataStore에 persist하여 앱 재시작 후에도 유지된다.
 */
@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val secureApiKeyRepository: SecureApiKeyRepository
) {
    companion object {
        private const val TAG = "GoogleAuthRepository"

        /** Gemini API 호출에 필요한 OAuth 스코프 (우선) */
        private const val SCOPE_GENERATIVE_LANGUAGE =
            "https://www.googleapis.com/auth/generative-language.retriever"

        /** 폴백 OAuth 스코프 */
        private const val SCOPE_CLOUD_PLATFORM =
            "https://www.googleapis.com/auth/cloud-platform"

        /** Google Drive 파일 접근 스코프 (앱이 생성한 파일만 — Non-sensitive) */
        private const val SCOPE_DRIVE_FILE =
            "https://www.googleapis.com/auth/drive.file"
    }

    private val credentialManager = CredentialManager.create(context)

    /** 현재 인증 상태 */
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotSignedIn)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** 캐시된 access token (메모리에만 보관) */
    @Volatile
    private var cachedAccessToken: String? = null

    /** Drive 전용 인증 상태 */
    private val _driveAuthState = MutableStateFlow<DriveAuthState>(DriveAuthState.NotAuthorized)
    val driveAuthState: StateFlow<DriveAuthState> = _driveAuthState.asStateFlow()

    /** 캐시된 Drive access token (메모리에만 보관 — 민감 정보) */
    @Volatile
    private var cachedDriveAccessToken: String? = null

    /**
     * 초기화 시 DataStore에서 저장된 로그인 상태를 복원한다.
     */
    suspend fun restoreAuthState() {
        val savedEmail = userPreferencesRepository.googleEmail.first()
        val savedName = userPreferencesRepository.googleDisplayName.first()
        if (!savedEmail.isNullOrBlank()) {
            _authState.value = AuthState.SignedIn(
                displayName = savedName ?: "",
                email = savedEmail
            )
        }

        // Drive 인증 여부 복원
        val driveAuthorized = userPreferencesRepository.driveAuthorized.first()
        if (driveAuthorized && !savedEmail.isNullOrBlank()) {
            // 토큰은 만료됐을 수 있으므로 Authorized 상태로만 복원
            // 실제 토큰은 authorizeDrive() 재호출 시 획득
            _driveAuthState.value = DriveAuthState.Authorized(email = savedEmail)
        }
    }

    /**
     * Credential Manager API로 Google Sign-In을 실행한다.
     *
     * @param activityContext Activity 컨텍스트 (Credential Manager 필수)
     */
    suspend fun signIn(activityContext: Context) {
        _authState.value = AuthState.Loading

        try {
            // SecureApiKeyRepository 우선 조회, 없으면 BuildConfig 폴백
            val storedClientId = secureApiKeyRepository.getGoogleOAuthClientId()
            val webClientId = if (!storedClientId.isNullOrBlank()) {
                storedClientId
            } else {
                BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID
            }
            if (webClientId.isBlank()) {
                _authState.value = AuthState.Error(
                    "Google OAuth Web Client ID가 설정되지 않았습니다. 설정 화면에서 입력해주세요."
                )
                return
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val displayName = googleIdTokenCredential.displayName ?: ""
            val email = googleIdTokenCredential.id

            // DataStore에 로그인 정보 저장
            userPreferencesRepository.setGoogleAccount(displayName, email)

            _authState.value = AuthState.SignedIn(
                displayName = displayName,
                email = email,
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
            )

            Log.d(TAG, "Google 로그인 성공: $email")

        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google 로그인 취소됨")
            _authState.value = AuthState.NotSignedIn
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google 로그인 실패: ${e.message}", e)
            _authState.value = AuthState.Error("로그인 실패: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 중 예외 발생: ${e.message}", e)
            _authState.value = AuthState.Error("로그인 중 오류: ${e.message}")
        }
    }

    /**
     * AuthorizationClient로 Gemini API 접근 권한(access token)을 획득한다.
     *
     * @param activity Activity 인스턴스 (AuthorizationClient 필수)
     * @return access token 획득 성공 시 true
     */
    suspend fun authorize(activity: Activity): Boolean {
        return try {
            // 우선 generative-language 스코프 시도
            val token = requestAccessToken(activity, SCOPE_GENERATIVE_LANGUAGE)
            if (token != null) {
                cachedAccessToken = token
                Log.d(TAG, "access token 획득 성공 (generative-language 스코프)")
                return true
            }

            // 폴백: cloud-platform 스코프
            val fallbackToken = requestAccessToken(activity, SCOPE_CLOUD_PLATFORM)
            if (fallbackToken != null) {
                cachedAccessToken = fallbackToken
                Log.d(TAG, "access token 획득 성공 (cloud-platform 스코프, 폴백)")
                return true
            }

            Log.w(TAG, "access token 획득 실패: 두 스코프 모두 실패")
            false
        } catch (e: Exception) {
            Log.e(TAG, "권한 부여 실패: ${e.message}", e)
            false
        }
    }

    /**
     * 특정 스코프로 access token을 요청한다.
     */
    private suspend fun requestAccessToken(activity: Activity, scope: String): String? {
        return try {
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(scope)))
                .build()

            val authorizationClient: AuthorizationClient =
                Identity.getAuthorizationClient(activity)

            val result = authorizationClient.authorize(authorizationRequest).await()

            if (result.hasResolution()) {
                // PendingIntent가 필요한 경우 — 현재는 null 반환 (향후 UI 연동)
                Log.d(TAG, "권한 부여에 사용자 동의 필요 (스코프: $scope)")
                null
            } else {
                result.accessToken
            }
        } catch (e: Exception) {
            Log.w(TAG, "access token 요청 실패 (스코프: $scope): ${e.message}")
            null
        }
    }

    /**
     * 캐시된 access token을 반환한다.
     *
     * @return access token 또는 null
     */
    fun getAccessToken(): String? = cachedAccessToken

    /**
     * Google Drive API drive.file 스코프 권한을 요청한다.
     *
     * hasResolution() == true 이면 사용자 동의 화면이 필요하며 NeedsConsent 상태를 반환한다.
     * Activity에서 rememberLauncherForActivityResult로 PendingIntent를 실행해야 한다.
     *
     * hasResolution() == false 이면 이미 동의된 상태이므로 즉시 Authorized를 반환한다.
     *
     * @param activity Activity 인스턴스 (AuthorizationClient 필수)
     */
    suspend fun authorizeDrive(activity: Activity) {
        _driveAuthState.value = DriveAuthState.Loading
        try {
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
                .build()

            val result = Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest)
                .await()

            if (result.hasResolution()) {
                // 최초 승인 요청 — PendingIntent를 UI에서 실행해야 함
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    _driveAuthState.value = DriveAuthState.NeedsConsent(pendingIntent)
                    Log.d(TAG, "Drive 인증: 사용자 동의 화면 필요")
                } else {
                    _driveAuthState.value = DriveAuthState.Error("동의 화면 PendingIntent가 null입니다")
                }
            } else {
                // 이미 동의된 계정 — 즉시 token 획득
                val token = result.accessToken
                cachedDriveAccessToken = token
                val email = (authState.value as? AuthState.SignedIn)?.email ?: ""
                userPreferencesRepository.setDriveAuthorized(true)
                _driveAuthState.value = DriveAuthState.Authorized(email = email)
                Log.d(TAG, "Drive 인증 성공 (즉시): $email")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive 인증 실패: ${e.message}", e)
            _driveAuthState.value = DriveAuthState.Error("Drive 인증 실패: ${e.message}")
        }
    }

    /**
     * Activity에서 사용자 동의 결과를 수신한 후 호출한다.
     * getAuthorizationResultFromIntent()로 access token을 추출하여 Authorized 상태로 전환한다.
     *
     * @param accessToken AuthorizationResult에서 추출한 access token
     */
    suspend fun onDriveAuthorizationResult(accessToken: String) {
        cachedDriveAccessToken = accessToken
        val email = (authState.value as? AuthState.SignedIn)?.email ?: ""
        userPreferencesRepository.setDriveAuthorized(true)
        _driveAuthState.value = DriveAuthState.Authorized(email = email)
        Log.d(TAG, "Drive 인증 성공 (동의 후): $email")
    }

    /**
     * Drive 인증 실패 처리.
     */
    fun onDriveAuthorizationFailed(errorMessage: String?) {
        _driveAuthState.value = DriveAuthState.Error(
            errorMessage ?: "Drive 인증이 취소되었습니다"
        )
        Log.w(TAG, "Drive 인증 실패: $errorMessage")
    }

    /**
     * Drive 인증을 해제한다. DataStore에서 드라이브 인증 여부를 삭제하고 상태를 초기화한다.
     */
    suspend fun revokeDriveAuthorization() {
        cachedDriveAccessToken = null
        userPreferencesRepository.setDriveAuthorized(false)
        _driveAuthState.value = DriveAuthState.NotAuthorized
        Log.d(TAG, "Drive 인증 해제 완료")
    }

    /**
     * 캐시된 Drive access token을 반환한다. null이면 authorizeDrive() 재호출 필요.
     */
    fun getDriveAccessToken(): String? = cachedDriveAccessToken

    /**
     * Google 로그아웃을 수행한다.
     * DataStore에서 사용자 정보를 삭제하고 캐시된 토큰을 초기화한다.
     */
    suspend fun signOut() {
        cachedAccessToken = null
        // Drive 인증 상태도 함께 초기화
        cachedDriveAccessToken = null
        userPreferencesRepository.setDriveAuthorized(false)
        _driveAuthState.value = DriveAuthState.NotAuthorized
        userPreferencesRepository.clearGoogleAccount()
        _authState.value = AuthState.NotSignedIn
        Log.d(TAG, "Google 로그아웃 완료")
    }

    /**
     * 현재 로그인 상태를 반환한다.
     */
    fun isSignedIn(): Boolean = _authState.value is AuthState.SignedIn
}
