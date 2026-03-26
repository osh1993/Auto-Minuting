---
phase: "18"
plan: "01"
subsystem: auth, settings
tags: [oauth, settings-ui, security]
dependency_graph:
  requires: []
  provides: [google-oauth-client-id-settings]
  affects: [google-auth-flow]
tech_stack:
  added: []
  patterns: [encrypted-storage-for-credentials]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt
    - app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
decisions:
  - OAuth Client ID는 Gemini API 키와 동일하게 EncryptedSharedPreferences에 암호화 저장
  - BuildConfig 폴백 유지하여 기존 local.properties 설정도 계속 동작
metrics:
  duration: 2min
  completed: "2026-03-26T08:38:49Z"
---

# Phase 18 Plan 01: Google OAuth Web Client ID 설정 UI 추가 Summary

설정 화면에서 Google OAuth Web Client ID를 입력/암호화 저장하고, GoogleAuthRepository가 이를 우선 참조하도록 수정

## What Was Done

### Task 1: SecureApiKeyRepository 확장 (fa18b40)
- `KEY_GOOGLE_OAUTH_CLIENT_ID` 상수 추가
- `getGoogleOAuthClientId()`, `saveGoogleOAuthClientId()`, `clearGoogleOAuthClientId()` 3개 메서드 추가
- 기존 Gemini API 키와 동일한 EncryptedSharedPreferences 패턴 적용

### Task 2: GoogleAuthRepository 우선 참조 변경 (abc7eb1)
- 생성자에 `SecureApiKeyRepository` 의존성 주입 추가
- `signIn()` 메서드에서 `SecureApiKeyRepository.getGoogleOAuthClientId()` 우선 조회
- 저장된 Client ID가 없을 때만 `BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID`로 폴백
- 에러 메시지에 "설정 화면에서 입력해주세요" 안내 추가

### Task 3: SettingsScreen UI 추가 (3e666d9)
- `SettingsViewModel`에 `saveOAuthClientId()`, `clearOAuthClientId()`, `resetOAuthClientIdSaved()` 메서드 추가
- `hasOAuthClientId`, `oauthClientIdSaved` StateFlow 추가
- `OAuthClientIdSection` 컴포저블 생성: 비밀번호 마스킹 입력, 저장/삭제 버튼, 상태 메시지
- OAuth 인증 모드 선택 시 Google 로그인 버튼 위에 Client ID 입력 섹션 표시

## Deviations from Plan

None - 계획대로 정확히 실행됨.

## Known Stubs

None - 모든 기능이 완전히 연결됨.
