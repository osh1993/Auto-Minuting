# Phase 45: Google Drive 인증 (DRIVE-01) - Research

**Researched:** 2026-04-03
**Domain:** Android OAuth 2.0 / Google Drive API v3 / Identity API
**Confidence:** HIGH

---

## Summary

Phase 45는 Google Drive API v3 접근을 위한 OAuth 2.0 인증 레이어를 추가하는 작업이다.
프로젝트에는 이미 Gemini API용 `GoogleAuthRepository`가 구현되어 있으며, `play-services-auth`,
`credentials`, `googleid` 라이브러리가 이미 추가된 상태다. Drive 전용 추가 라이브러리 없이
동일한 `Identity.getAuthorizationClient()` 패턴으로 `drive.file` 스코프를 요청하는 것이 가장
적합한 접근법이다.

핵심 설계 결정은 두 가지다. 첫째, `GoogleAuthRepository`를 **Drive 스코프로 확장** (별도 클래스
생성 금지) — 이미 계정 Sign-In 흐름, DataStore 영속화, AuthState StateFlow가 모두 구현되어
있으므로 중복을 만들지 않는다. 둘째, `google-api-client-android` + `google-api-services-drive`
의존성은 **추가하지 않는다** — 이미 보유한 OkHttp/Retrofit + 직접 REST 호출로 Drive 파일 업로드가
가능하며, 기존 라이브러리 추가는 불필요한 의존성 증가를 초래한다.

**Primary recommendation:** `Identity.getAuthorizationClient`를 통해 `drive.file` 스코프를
요청하고, `GoogleAuthRepository`에 Drive 전용 authorization 메서드를 추가하여 access token을
DriveAuthState로 노출한다. 신규 의존성 불필요.

---

## Project Constraints (from CLAUDE.md)

### 기술 스택 제약
- Kotlin + Android 네이티브 (Min SDK 31, Target SDK 36)
- Jetpack Compose, Hilt, DataStore, Retrofit/OkHttp 기반 프로젝트
- 이미 추가된 인증 라이브러리: `credentials:1.5.0`, `credentials-play-services:1.5.0`,
  `googleid:1.1.1`, `play-services-auth:21.5.1`
- 최소 의존성 추가 원칙 — 기존 스택으로 해결 가능하면 신규 의존성 금지
- GSD 워크플로 없이 직접 파일 수정 금지

### 기존 인증 구조
- `GoogleAuthRepository` — Credential Manager 기반 Sign-In + AuthorizationClient 기반 access token
- `AuthState` sealed interface — NotSignedIn / SignedIn / Loading / Error
- `UserPreferencesRepository` — DataStore에 email/displayName 영속화
- `SettingsScreen.kt` — `GoogleAccountSection` 컴포저블 존재 (현재 Gemini OAuth용)

---

## Standard Stack

### Core (이미 프로젝트에 추가됨)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| play-services-auth | 21.5.1 | AuthorizationClient / Identity API | Drive scope 요청에 필수 |
| credentials | 1.5.0 | Credential Manager API | Google Sign-In (이미 사용 중) |
| credentials-play-services | 1.5.0 | GMS 연동 | credentials 보조 |
| googleid | 1.1.1 | GoogleIdTokenCredential | Sign-In 응답 파싱 |

### 추가 불필요 — 사용하지 않는 라이브러리
| Library | 이유 |
|---------|------|
| google-api-client-android | 기존 OkHttp + 직접 REST로 대체 가능, 불필요한 의존성 |
| google-api-services-drive | Drive REST API는 Authorization header만으로 호출 가능 |

**DriveScopes 상수 출처:** `google-api-services-drive` 없이 직접 문자열 상수로 선언한다:
```kotlin
private const val SCOPE_DRIVE_FILE =
    "https://www.googleapis.com/auth/drive.file"
```

**Installation:** 신규 `build.gradle.kts` 변경 없음. 의존성 추가 불필요.

---

## Architecture Patterns

### 권장 구조 — GoogleAuthRepository 확장 방식

기존 `GoogleAuthRepository`에 Drive 전용 메서드를 추가한다.
별도 `GoogleDriveAuthRepository` 클래스를 만들지 않는다.

**근거:**
- Sign-In(Credential Manager)은 계정 단위 — Gemini/Drive 공유
- DataStore 영속화(email, displayName)는 이미 구현됨
- AuthState StateFlow는 재사용 가능
- Drive 전용 access token은 별도 필드로 메모리 캐시

```
data/auth/
├── AuthState.kt            ← 변경 없음
├── GoogleAuthRepository.kt ← Drive scope 메서드 추가
└── (신규) DriveAuthState.kt (선택) ← Drive 전용 토큰 상태
```

### Pattern 1: Drive 스코프 Authorization (AuthorizationClient)

**What:** 이미 Sign-In된 사용자에게 `drive.file` 스코프 권한을 추가로 요청한다.
**When to use:** 사용자가 Drive 연동 버튼을 탭한 시점.

```kotlin
// Source: https://developer.android.com/identity/authorization
private const val SCOPE_DRIVE_FILE =
    "https://www.googleapis.com/auth/drive.file"

suspend fun authorizeDrive(activity: Activity): DriveAuthResult {
    return try {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
            .build()

        val result = Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest)
            .await()

        if (result.hasResolution()) {
            // 사용자 동의 화면 필요 — PendingIntent 반환
            DriveAuthResult.NeedsResolution(result.pendingIntent!!)
        } else {
            // 이전에 이미 동의한 경우 — 즉시 token 반환
            cachedDriveAccessToken = result.accessToken
            DriveAuthResult.Success(result.accessToken ?: "")
        }
    } catch (e: Exception) {
        DriveAuthResult.Failure(e.message ?: "Unknown error")
    }
}
```

### Pattern 2: PendingIntent 처리 — Activity에서 결과 수신

**What:** `hasResolution() == true`일 때 Activity가 PendingIntent를 실행하고 결과를 받는다.
**When to use:** 사용자가 처음 Drive 스코프를 승인하는 시점 (최초 1회).

```kotlin
// Source: https://developer.android.com/identity/authorization
// Activity / Composable 진입점 (SettingsScreen에서 호출)
val driveAuthLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult()
) { activityResult ->
    scope.launch {
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
            viewModel.onDriveAuthorizationResult(authResult.accessToken ?: "")
        } catch (e: ApiException) {
            viewModel.onDriveAuthorizationFailed(e.message)
        }
    }
}

// ViewModel에서 NeedsResolution 상태 시 호출
fun launchDriveConsent(pendingIntent: PendingIntent) {
    driveAuthLauncher.launch(
        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    )
}
```

### Pattern 3: Access Token 갱신 — 서버 없는 클라이언트 전용 패턴

**What:** 클라이언트 전용 앱에서는 `AuthorizationClient.authorize()`를 재호출하면
GMS가 내부적으로 토큰을 갱신한다. Refresh token을 직접 관리할 필요 없다.

**핵심 원칙:**
- Android의 `AuthorizationClient`는 Google Play Services가 토큰 갱신을 투명하게 처리
- 이미 동의한 스코프는 `hasResolution() == false` 상태로 즉시 새 access token 반환
- 앱은 만료된 토큰을 감지하면 `authorize()`를 재호출하기만 하면 됨
- Refresh token을 클라이언트에 저장하지 않는다 (Google 보안 지침)

```kotlin
// Source: https://developer.android.com/identity/authorization
// access token 사용 시 401 응답 → 재갱신 패턴
suspend fun refreshDriveToken(activity: Activity): String? {
    val result = Identity.getAuthorizationClient(activity)
        .authorize(
            AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
                .build()
        ).await()

    return if (!result.hasResolution()) {
        cachedDriveAccessToken = result.accessToken
        result.accessToken
    } else {
        null // 재동의 필요 — UI에 알림
    }
}
```

### Pattern 4: DriveAuthState — 별도 상태 모델

**What:** Drive 전용 인증 상태를 표현하는 sealed interface.
기존 `AuthState`와 분리하여 Sign-In 상태와 Drive 권한 상태를 독립 관리한다.

```kotlin
// Drive 전용 상태 — AuthState와 독립
sealed interface DriveAuthState {
    data object NotAuthorized : DriveAuthState
    data class Authorized(val accessToken: String) : DriveAuthState
    data object Loading : DriveAuthState
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthState
    data class Error(val message: String) : DriveAuthState
}
```

**근거:** `AuthState`는 Sign-In(계정 존재 여부)을 표현하고,
`DriveAuthState`는 Drive 스코프 승인 여부를 표현한다.
두 상태는 독립적으로 변할 수 있다 (예: 로그인됨 + Drive 미승인).

### Anti-Patterns to Avoid

- **Drive scope를 Sign-In과 동시에 요청하지 않는다:** 최초 로그인 시 모든 스코프를 요청하면
  사용자 거부율이 높아진다. Drive 연동이 필요한 시점에 incremental authorization을 적용한다.
- **`google-api-client-android` + `GoogleAccountCredential` 패턴:** 2023년 이전 방식.
  Drive Android API 폐기 이후 REST 직접 호출이 권장됨. 이 라이브러리 추가 금지.
- **Refresh token을 DataStore에 저장하지 않는다:** Google Identity 클라이언트 앱에서
  refresh token은 Play Services가 관리한다. 직접 저장하면 보안 위반.
- **`GoogleSignInClient`를 새로 사용하지 않는다:** deprecated. `Identity.getAuthorizationClient`를 사용한다.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OAuth 동의 화면 | 커스텀 WebView OAuth | Identity.getAuthorizationClient | 시스템 UI 사용, 보안 보장 |
| Token 갱신 | 직접 refresh token → access token 교환 | authorize() 재호출 | GMS 내부 자동 처리 |
| Drive REST 호출 | 커스텀 HTTP 파싱 | OkHttp + Authorization header | 이미 프로젝트에 OkHttp 있음 |
| Account 선택 UI | 커스텀 계정 선택 다이얼로그 | Credential Manager | 시스템 계정 선택기 사용 |

**Key insight:** Android의 `AuthorizationClient`는 서버 없는 앱에서 OAuth 토큰 수명주기 전체를
대신 관리해준다. refresh token 파싱, 만료 감지, 갱신 요청을 직접 구현할 필요가 없다.

---

## OAuth 스코프 결정

### drive.file vs drive

| 스코프 URL | 분류 | 권한 범위 | Play Store 심사 |
|-----------|------|---------|----------------|
| `https://www.googleapis.com/auth/drive.file` | Non-sensitive | **앱이 생성하거나 사용자가 선택한 파일만** | 심사 불필요 |
| `https://www.googleapis.com/auth/drive` | Restricted | 모든 Drive 파일 읽기/쓰기 | Google 보안 심사 필수 |
| `https://www.googleapis.com/auth/drive.readonly` | Restricted | 모든 Drive 파일 읽기만 | Google 보안 심사 필수 |
| `https://www.googleapis.com/auth/drive.appdata` | Non-sensitive | 앱 전용 숨김 폴더 | 심사 불필요 |

**결정: `drive.file` 스코프 사용**

근거:
1. 회의록 파일을 Drive에 저장하는 용도 — 앱이 생성하는 파일만 접근하면 충분
2. Non-sensitive 스코프 — Play Store 추가 심사 불필요
3. 사용자가 공유할 파일을 직접 제어 가능 → 더 높은 신뢰도
4. 향후 Drive 파일 목록 조회가 필요하면 `drive.readonly` 추가 가능 (incremental)

---

## Common Pitfalls

### Pitfall 1: hasResolution() 미처리 — 사용자 동의 화면 스킵
**What goes wrong:** `authorize()` 결과에서 `hasResolution() == true`인 경우 무시하면
access token이 null이어서 Drive 호출이 모두 실패한다.
**Why it happens:** 첫 번째 Drive 스코프 요청 시 항상 `hasResolution() == true`
(사용자 동의 화면 필요). 두 번째부터는 `false`.
**How to avoid:** `hasResolution()` 분기를 반드시 처리하고, `NeedsConsent` 상태로
ViewModel에 전달 후 Activity에서 `startIntentSenderForResult`로 실행.
**Warning signs:** access token이 계속 null, Drive API 호출이 401 반환

### Pitfall 2: Activity Context 필수 — ViewModel에서 직접 호출 불가
**What goes wrong:** `Identity.getAuthorizationClient(context)` 호출 시
ApplicationContext를 전달하면 작동하지 않을 수 있다. `authorize()`는 Activity context 필요.
**Why it happens:** OAuth 동의 화면이 Activity 위에 표시되어야 하기 때문.
**How to avoid:** `signIn()`, `authorizeDrive()` 등 사용자 상호작용 메서드는
Activity를 파라미터로 받는 설계를 유지한다. (기존 `GoogleAuthRepository.signIn(activityContext)` 패턴 동일)
**Warning signs:** ApiException "10: Developer Error" 또는 UI 없이 authorize 실패

### Pitfall 3: DriveScopes 클래스 의존성 오해
**What goes wrong:** `DriveScopes.DRIVE_FILE` 상수를 사용하려고
`google-api-services-drive` 라이브러리를 추가한다.
**Why it happens:** 많은 예제 코드가 `DriveScopes.DRIVE_FILE`을 참조함.
**How to avoid:** 해당 상수는 단순히 `"https://www.googleapis.com/auth/drive.file"` 문자열이므로
직접 상수로 선언한다. 의존성 추가 불필요.

### Pitfall 4: Compose에서 ActivityResultLauncher 등록 타이밍
**What goes wrong:** `rememberLauncherForActivityResult`를 컴포저블 내부 조건문 안에서
등록하면 recomposition 시 등록이 해제된다.
**Why it happens:** Compose의 `remember` 계층 구조 위반.
**How to avoid:** 항상 컴포저블 최상위(unconditional call)에서 `rememberLauncherForActivityResult`를 등록.

### Pitfall 5: Drive 인증과 Sign-In 상태 혼동
**What goes wrong:** `AuthState.SignedIn` 상태를 Drive 사용 가능 상태로 간주하고
`DriveAuthState` 없이 직접 토큰 사용을 시도.
**Why it happens:** Sign-In(계정 확인)과 Authorization(Drive 스코프 승인)은 별개 단계.
**How to avoid:** Drive 기능 진입 전 `DriveAuthState.Authorized` 상태인지 별도 확인.

---

## Code Examples

### 기존 패턴 재사용 — authorize() Coroutine 래핑

```kotlin
// Source: https://developer.android.com/identity/authorization
// 기존 GoogleAuthRepository.requestAccessToken()와 동일한 패턴
// kotlinx-coroutines-play-services 의존성으로 .await() 사용
import kotlinx.coroutines.tasks.await

val result = Identity.getAuthorizationClient(activity)
    .authorize(authorizationRequest)
    .await()  // Task<AuthorizationResult> → suspend
```

### Drive API REST 직접 호출 (OkHttp, 추후 Phase에서 사용)

```kotlin
// Drive REST API — 추가 라이브러리 불필요
// Authorization: Bearer {accessToken} 헤더만 필요
val request = okhttp3.Request.Builder()
    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
    .addHeader("Authorization", "Bearer $accessToken")
    .post(requestBody)
    .build()
```

### DataStore — Drive 인증 상태 영속화

```kotlin
// UserPreferencesRepository에 추가할 키 (Phase 45 범위)
val DRIVE_AUTHORIZED_KEY = booleanPreferencesKey("drive_authorized")

// 토큰은 메모리 캐시만 사용 (민감 정보)
// 인증 여부(boolean)만 DataStore에 저장 → 앱 재시작 후 재인증 필요 여부 판단용
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| GoogleSignInClient + Drive scope | Identity.getAuthorizationClient | 2022-2023 | 기존 코드 변경 필요 없음, 신규 구현에 AuthorizationClient 사용 |
| Drive Android API (addApi(Drive.API)) | Drive REST API 직접 호출 | Dec 2018 deprecated, Feb 2023 shutdown | google-api-services-drive 사용 지양 |
| GoogleAccountCredential | AuthorizationResult.accessToken | 2023 | Drive 서비스 객체 불필요, 직접 토큰 사용 |
| KAPT | KSP | 2023 | 이미 프로젝트에서 KSP 사용 중 |

**Deprecated/outdated:**
- `play-services-drive`: 완전 폐기 (2023년 2월 이후 모든 호출 실패)
- `GoogleSignInClient` + `requestScopes()`: deprecated, `AuthorizationClient`로 교체
- `Drive.Builder` + `GoogleAccountCredential`: 기존 방식, 신규 구현에서 불필요

---

## Open Questions

1. **Drive access token을 언제 갱신해야 하는가?**
   - What we know: access token 유효기간 ~1시간. `authorize()` 재호출 시 GMS가 갱신.
   - What's unclear: Phase 45에서는 token 저장 범위가 메모리 캐시뿐 — 앱 재시작 시 매번 재인증 필요.
   - Recommendation: Phase 45 범위에서는 메모리 캐시 유지. 백그라운드 Drive 업로드가
     필요해지는 시점(후속 Drive 업로드 Phase)에서 WorkManager 내 token 갱신 전략 설계.

2. **Drive OAuth와 Gemini OAuth — 동일 계정 강제 여부**
   - What we know: 기술적으로 다른 계정 사용 가능.
   - What's unclear: UX 설계 — 동일 계정 사용 강제할지, 별도 계정 허용할지.
   - Recommendation: Phase 45에서는 동일 `GoogleAuthRepository`를 사용하므로
     Sign-In 단계(Credential Manager)에서 선택한 계정이 Drive 인증에도 사용된다.
     별도 계정 선택 UI는 이번 Phase 범위 외.

3. **Google Cloud Console 구성 — Web Client ID vs Android Client ID**
   - What we know: Drive 스코프는 OAuth consent screen에 등록 필요.
   - What's unclear: 기존 Web Client ID가 Drive 스코프를 허용하도록 구성되었는지 확인 불가 (런타임 외부).
   - Recommendation: 구현 완료 후 `authorize()` 호출 시 오류 발생 시 Cloud Console에서
     OAuth consent screen → Scopes 섹션에 `drive.file` 추가 필요. 개발자 가이드에 명시.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 45는 순수 코드/설정 변경으로 외부 서비스 의존성 없음.
Google Play Services는 이미 타겟 디바이스(Samsung Galaxy, Android 12+)에 내장됨.

---

## Sources

### Primary (HIGH confidence)
- https://developer.android.com/identity/authorization — AuthorizationClient 공식 가이드, Kotlin 예제
- https://developers.google.com/drive/api/guides/about-auth — Drive API OAuth 인증 개요
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth — Drive 스코프 분류 및 권장사항
- https://developers.google.com/drive/api/guides/android-api-deprecation — Drive Android API 폐기 공지

### Secondary (MEDIUM confidence)
- https://central.sonatype.com/artifact/com.google.apis/google-api-services-drive — 최신 버전 확인 (v3-rev20260322-2.0.0)
- https://medium.com/@python-javascript-php-html-css/implementing-a-non-deprecated-google-drive-authorization-api-in-android-88c410c550f8 — Non-deprecated Drive Auth 구현 패턴

### Tertiary (LOW confidence)
- WebSearch 결과들 — DriveScopes 의존성 문제, 커뮤니티 패턴 확인

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 기존 라이브러리(play-services-auth 21.5.1) + 공식 Android 문서 확인
- Architecture: HIGH — 기존 `GoogleAuthRepository` 코드 직접 분석, 공식 패턴 일치 확인
- Pitfalls: HIGH — `hasResolution()` 처리, Activity context 요구사항은 공식 문서에서 직접 확인
- Drive scope 결정: HIGH — 공식 스코프 분류 문서 확인 (Non-sensitive vs Restricted)
- Token 갱신: MEDIUM — 공식 문서에서 "authorize() 재호출"로 처리한다고 명시, 내부 동작은 불투명

**Research date:** 2026-04-03
**Valid until:** 2026-07-03 (90일 — play-services-auth API는 안정적)
