package com.autominuting.data.auth

/**
 * 인증 모드 — API 키 방식과 OAuth 방식 중 선택.
 */
enum class AuthMode {
    /** 기존 API 키 방식 */
    API_KEY,
    /** Google OAuth 방식 */
    OAUTH
}

/**
 * Google 인증 상태를 나타내는 sealed interface.
 *
 * SettingsViewModel에서 StateFlow로 노출하여 UI가 상태별로 적절한 화면을 표시한다.
 */
sealed interface AuthState {
    /** 로그인하지 않은 상태 */
    data object NotSignedIn : AuthState

    /** 로그인 완료 상태 */
    data class SignedIn(
        val displayName: String,
        val email: String,
        val photoUrl: String? = null
    ) : AuthState

    /** 로그인 진행 중 */
    data object Loading : AuthState

    /** 로그인 에러 */
    data class Error(val message: String) : AuthState
}
