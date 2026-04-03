package com.autominuting.data.auth

import android.app.PendingIntent

/**
 * Google Drive 전용 인증 상태 — AuthState(Sign-In)와 독립적으로 관리된다.
 *
 * Drive 스코프 승인은 Sign-In 이후 별도 단계이므로 별도 sealed interface로 분리한다.
 * 앱 재시작 시 DataStore의 driveAuthorized(boolean)를 읽어 NotAuthorized 또는
 * Authorized 상태로 초기화한다. access token은 메모리 캐시에만 유지된다.
 */
sealed interface DriveAuthState {
    /** Drive 스코프 미승인 상태 */
    data object NotAuthorized : DriveAuthState

    /** 사용자 동의 화면 표시 필요 — PendingIntent를 Activity에서 실행해야 한다 */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthState

    /** Drive 스코프 승인 완료 — access token이 메모리 캐시에 보관된다 */
    data class Authorized(val email: String) : DriveAuthState

    /** 승인 진행 중 */
    data object Loading : DriveAuthState

    /** 승인 오류 */
    data class Error(val message: String) : DriveAuthState
}
