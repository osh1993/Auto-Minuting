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
    }

    private val credentialManager = CredentialManager.create(context)

    /** 현재 인증 상태 */
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotSignedIn)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** 캐시된 access token (메모리에만 보관) */
    @Volatile
    private var cachedAccessToken: String? = null

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
     * Google 로그아웃을 수행한다.
     * DataStore에서 사용자 정보를 삭제하고 캐시된 토큰을 초기화한다.
     */
    suspend fun signOut() {
        cachedAccessToken = null
        userPreferencesRepository.clearGoogleAccount()
        _authState.value = AuthState.NotSignedIn
        Log.d(TAG, "Google 로그아웃 완료")
    }

    /**
     * 현재 로그인 상태를 반환한다.
     */
    fun isSignedIn(): Boolean = _authState.value is AuthState.SignedIn
}
