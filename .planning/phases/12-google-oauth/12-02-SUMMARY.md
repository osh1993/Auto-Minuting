---
phase: 12-google-oauth
plan: 02
subsystem: auth
tags: [google-oauth, credential-manager, retrofit, bearer-token, gemini-api, datastore]

requires:
  - phase: 12-google-oauth-01
    provides: "MinutesEngine 인터페이스, GeminiRestApiService, GeminiRestModels"
provides:
  - "GoogleAuthRepository: Google Sign-In + access token 관리"
  - "AuthState/AuthMode: 인증 상태 및 모드 모델"
  - "GeminiOAuthEngine: OAuth 기반 Gemini REST API 회의록 엔진"
  - "MinutesEngineSelector: 인증 모드에 따른 엔진 동적 선택"
  - "AuthModule: OAuth용 OkHttpClient + Retrofit Hilt 모듈"
  - "설정 화면 Google 로그인/로그아웃 UI + 인증 모드 전환"
affects: [13-plaud-ble]

tech-stack:
  added: [Credential Manager API, AuthorizationClient, BearerTokenInterceptor]
  patterns: [MinutesEngineSelector 위임 패턴, @Named qualifier로 OAuth/API 키 분리]

key-files:
  created:
    - app/src/main/java/com/autominuting/data/auth/AuthState.kt
    - app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt
    - app/src/main/java/com/autominuting/data/minutes/BearerTokenInterceptor.kt
    - app/src/main/java/com/autominuting/data/minutes/GeminiOAuthEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt
    - app/src/main/java/com/autominuting/data/minutes/MinutesPrompts.kt
    - app/src/main/java/com/autominuting/di/AuthModule.kt
  modified:
    - app/src/main/java/com/autominuting/di/RepositoryModule.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
    - app/build.gradle.kts

key-decisions:
  - "MinutesPrompts 별도 object로 프롬프트 공유 (GeminiEngine companion 수정 대신)"
  - "MinutesEngineSelector 위임 패턴으로 런타임 엔진 전환 (Provider<> + authMode Flow)"
  - "@Named(\"oauth\") qualifier로 OAuth용 OkHttpClient/Retrofit과 기존 인스턴스 분리"
  - "AuthorizationClient 스코프 우선순위: generative-language.retriever -> cloud-platform 폴백"

patterns-established:
  - "MinutesEngineSelector: authMode DataStore 값에 따라 GeminiEngine/GeminiOAuthEngine 동적 선택"
  - "@Named qualifier: 동일 타입의 다중 인스턴스 구분 (oauth vs default)"
  - "GoogleAccountSection/ApiKeySection: 설정 화면 Composable 함수 분리 패턴"

requirements-completed: [AUTH-02]

duration: 5min
completed: 2026-03-26
---

# Phase 12 Plan 02: Google OAuth Summary

**Credential Manager + AuthorizationClient 기반 Google OAuth 인증과 GeminiOAuthEngine 구현, 설정 화면에서 API 키/OAuth 인증 모드 전환 UI**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-25T21:18:04Z
- **Completed:** 2026-03-25T21:23:06Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Google Sign-In(Credential Manager) + access token 획득(AuthorizationClient) 인프라 구현
- OAuth 기반 Gemini REST API 호출 엔진(GeminiOAuthEngine) 구현
- MinutesEngineSelector로 API 키/OAuth 모드 런타임 전환 구조 완성
- 설정 화면에 인증 모드 선택(RadioButton) + Google 로그인/로그아웃 UI 추가

## Task Commits

Each task was committed atomically:

1. **Task 1: GoogleAuthRepository + AuthState + GeminiOAuthEngine + AuthModule** - `fd82193` (feat)
2. **Task 2: 설정 화면 Google 로그인 UI + 인증 모드 전환** - `c87196b` (feat)

## Files Created/Modified
- `data/auth/AuthState.kt` - AuthMode enum + AuthState sealed interface
- `data/auth/GoogleAuthRepository.kt` - Google Sign-In + access token 관리
- `data/minutes/BearerTokenInterceptor.kt` - OkHttp Bearer 토큰 인터셉터
- `data/minutes/GeminiOAuthEngine.kt` - OAuth 기반 Gemini REST API 회의록 엔진
- `data/minutes/MinutesEngineSelector.kt` - 인증 모드별 엔진 동적 선택 위임
- `data/minutes/MinutesPrompts.kt` - 두 엔진 공유 프롬프트 상수
- `di/AuthModule.kt` - OAuth용 OkHttpClient + Retrofit Hilt 모듈
- `di/RepositoryModule.kt` - MinutesEngine 바인딩을 MinutesEngineSelector로 변경
- `data/preferences/UserPreferencesRepository.kt` - authMode, Google 계정 관리 추가
- `presentation/settings/SettingsViewModel.kt` - GoogleAuthRepository 주입, 인증 액션 추가
- `presentation/settings/SettingsScreen.kt` - 인증 모드 선택 + Google 로그인 UI
- `app/build.gradle.kts` - GOOGLE_OAUTH_WEB_CLIENT_ID BuildConfig 필드 추가

## Decisions Made
- MinutesPrompts 별도 object로 프롬프트를 공유하여 GeminiEngine의 companion object 수정을 최소화
- MinutesEngineSelector 위임 패턴: Provider<GeminiEngine> + Provider<GeminiOAuthEngine>으로 lazy 주입
- @Named("oauth") qualifier로 OAuth전용 OkHttpClient/GeminiRestApiService를 기존 인스턴스와 분리
- deprecated MenuAnchorType을 ExposedDropdownMenuAnchorType으로 수정

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] MinutesPrompts 별도 객체 생성**
- **Found during:** Task 1
- **Issue:** 플랜에서는 GeminiEngine의 프롬프트를 internal로 공개하거나 MinutesPrompts object로 추출하라고 명시
- **Fix:** MinutesPrompts object를 별도 파일로 생성하여 두 엔진이 공유 (GeminiEngine 기존 코드 변경 최소화)
- **Files modified:** app/src/main/java/com/autominuting/data/minutes/MinutesPrompts.kt
- **Verification:** 빌드 성공
- **Committed in:** fd82193

**2. [Rule 2 - Missing Critical] MinutesEngineSelector 생성**
- **Found during:** Task 1
- **Issue:** RepositoryModule에서 단순 @Binds로는 런타임 authMode 전환 불가
- **Fix:** MinutesEngineSelector 클래스를 생성하여 MinutesEngine을 구현하고 내부에서 authMode에 따라 위임
- **Files modified:** app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt
- **Verification:** 빌드 성공, Provider 패턴으로 순환 의존성 방지
- **Committed in:** fd82193

---

**Total deviations:** 2 auto-fixed (2 missing critical)
**Impact on plan:** 플랜에서 제안한 구조를 구체화한 것으로 스코프 확장 없음.

## Issues Encountered
None

## User Setup Required

**External services require manual configuration.** Google Cloud Console에서:
- Generative Language API 활성화
- OAuth 동의 화면 구성 (External, 테스트 모드)
- Android 클라이언트 ID 생성 (패키지명: com.autominuting, SHA-1 지문)
- Web 클라이언트 ID 생성 (serverClientId용)
- `local.properties`에 `GOOGLE_OAUTH_WEB_CLIENT_ID=<Web Client ID>` 추가

## Next Phase Readiness
- Google OAuth 인프라 완성, API 키/OAuth 모드 전환 가능
- 실기기에서 Google Cloud 프로젝트 설정 후 실제 로그인/토큰 검증 필요
- Gemini REST API OAuth 스코프 검증은 실기기 테스트 필수

## Self-Check: PASSED

---
*Phase: 12-google-oauth*
*Completed: 2026-03-26*
