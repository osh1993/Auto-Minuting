---
phase: 45-google-drive-auth
plan: 01
subsystem: auth
tags: [google-drive, oauth2, drive.file, settings-ui, datastore]
dependency_graph:
  requires: []
  provides: [DriveAuthState, GoogleAuthRepository.authorizeDrive, SettingsScreen.GoogleDriveSection]
  affects: [phase-46-drive-upload]
tech_stack:
  added: []
  patterns: [Identity.getAuthorizationClient, rememberLauncherForActivityResult, LaunchedEffect-NeedsConsent]
key_files:
  created:
    - app/src/main/java/com/autominuting/data/auth/DriveAuthState.kt
  modified:
    - app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
decisions:
  - "DriveAuthState를 AuthState와 별도 sealed interface로 분리 — Sign-In 상태와 Drive 스코프 승인 상태는 독립적으로 변경됨"
  - "access token은 DataStore에 저장하지 않음 — 메모리 캐시만 사용, boolean(drive_authorized)만 영속화"
  - "context as Activity 캐스팅은 GoogleDriveSection 호출부(authState is SignedIn 가드 내)에서만 사용 — 프레임워크 보장에 의존"
metrics:
  duration: "약 15분"
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 1
  files_modified: 4
requirements:
  - DRIVE-01
---

# Phase 45 Plan 01: Google Drive 인증 (DRIVE-01) Summary

**한 줄 요약:** drive.file 스코프 OAuth 2.0 인증 — DriveAuthState sealed interface, Identity.getAuthorizationClient hasResolution() 분기, DataStore boolean 영속화, SettingsScreen GoogleDriveSection UI 추가

## 완료 작업

### Task 1: 데이터 레이어 구축
- `DriveAuthState.kt` 신규 생성: 5개 상태 정의 (NotAuthorized, NeedsConsent, Authorized, Loading, Error)
- `GoogleAuthRepository.kt` 수정: Drive 전용 StateFlow, authorizeDrive(), onDriveAuthorizationResult(), onDriveAuthorizationFailed(), revokeDriveAuthorization(), getDriveAccessToken() 추가, signOut() 시 Drive 상태 초기화
- `UserPreferencesRepository.kt` 수정: DRIVE_AUTHORIZED_KEY(booleanPreferencesKey), driveAuthorized Flow<Boolean>, setDriveAuthorized(), clearDriveAuthorized() 추가

### Task 2: 프레젠테이션 레이어 구축
- `SettingsViewModel.kt` 수정: driveAuthState StateFlow 노출, authorizeDrive(), onDriveAuthorizationResult(), onDriveAuthorizationFailed(), revokeDriveAuth() 추가
- `SettingsScreen.kt` 수정: rememberLauncherForActivityResult 컴포저블 최상위 등록, LaunchedEffect(driveAuthState)로 NeedsConsent 자동 처리, GoogleDriveSection 컴포저블 추가 (5개 상태 UI), AuthState.SignedIn 상태에서만 GoogleDriveSection 표시

## DriveAuthState 5개 상태

| 상태 | 의미 | 다음 동작 |
|------|------|-----------|
| `NotAuthorized` | Drive 스코프 미승인 | "Google Drive 연결" 버튼 표시 |
| `NeedsConsent(pendingIntent)` | 사용자 동의 화면 필요 | LaunchedEffect → driveAuthLauncher.launch() |
| `Authorized(email)` | 승인 완료 | 이메일 표시 + "연결 해제" 버튼 |
| `Loading` | 승인 진행 중 | CircularProgressIndicator |
| `Error(message)` | 승인 오류 | 오류 메시지 + "Drive 다시 연결" 버튼 |

## authorizeDrive() 구현 패턴

```kotlin
// hasResolution() 분기 처리
val result = Identity.getAuthorizationClient(activity)
    .authorize(authorizationRequest).await()

if (result.hasResolution()) {
    // 최초 승인 — PendingIntent를 UI에서 실행 (NeedsConsent 상태로 전환)
    _driveAuthState.value = DriveAuthState.NeedsConsent(result.pendingIntent!!)
} else {
    // 이미 동의된 계정 — 즉시 토큰 획득
    cachedDriveAccessToken = result.accessToken
    _driveAuthState.value = DriveAuthState.Authorized(email)
}
```

## DataStore 영속화 키

```kotlin
// boolean만 저장 — access token은 저장 금지
val DRIVE_AUTHORIZED_KEY = booleanPreferencesKey("drive_authorized")
```

앱 재시작 시 `restoreAuthState()`에서 `driveAuthorized = true`이면 `DriveAuthState.Authorized(email)`로 복원.

## NeedsConsent 처리 패턴

```kotlin
// 1. 컴포저블 최상위에서 런처 등록 (unconditional)
val driveAuthLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult()
) { activityResult -> /* 결과 처리 */ }

// 2. LaunchedEffect로 NeedsConsent 상태 감지 → PendingIntent 자동 실행
LaunchedEffect(driveAuthState) {
    if (driveAuthState is DriveAuthState.NeedsConsent) {
        driveAuthLauncher.launch(
            IntentSenderRequest.Builder(driveAuthState.pendingIntent.intentSender).build()
        )
    }
}
```

## Phase 46에서 getDriveAccessToken() 사용 방법

```kotlin
// DriveUploadRepository에서
val token = googleAuthRepository.getDriveAccessToken()
    ?: return DriveUploadResult.Unauthenticated  // 토큰 없으면 재인증 필요

// Authorization 헤더에 추가
val request = Request.Builder()
    .header("Authorization", "Bearer $token")
    .build()
```

## 빌드 결과

`assembleDebug BUILD SUCCESSFUL` — 경고 없음 (기존 deprecated menuAnchor 경고는 기존 코드에서 이미 존재하던 것)

## Deviations from Plan

없음 — 플랜대로 정확히 실행됨.

## Known Stubs

없음.
