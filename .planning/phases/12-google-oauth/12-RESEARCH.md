# Phase 12: Google OAuth 인증 - Research

**Researched:** 2026-03-26
**Domain:** Android Google OAuth + Gemini API 인증
**Confidence:** MEDIUM

## Summary

Phase 12는 사용자가 Google 계정으로 로그인하여 API 키 없이 Gemini API를 사용할 수 있게 하는 것이 목표다. 핵심 기술적 과제는 두 가지: (1) Android에서 Google Sign-In 구현 (Credential Manager API), (2) 획득한 OAuth 토큰으로 Gemini API 호출.

**중요한 발견:** 현재 사용 중인 `com.google.ai.client.generativeai` SDK(v0.9.0)는 2025년 12월에 deprecated 되었다. Google은 Firebase AI Logic SDK로 마이그레이션을 권장한다. 그러나 이 SDK는 API key 기반이며 OAuth 토큰을 직접 받지 않는다. **OAuth로 Gemini API를 호출하려면 REST API 직접 호출이 필요하다** -- `generativelanguage.googleapis.com` 엔드포인트에 Bearer 토큰을 사용한다. 이 접근법은 Gemini CLI가 검증한 방식이며, 기존 OkHttp/Retrofit 스택으로 구현 가능하다.

**Primary recommendation:** Credential Manager로 Google Sign-In을 구현하고, AuthorizationClient로 `cloud-platform` 스코프의 access token을 획득한 뒤, OkHttp + Retrofit으로 Gemini REST API를 직접 호출하는 `GeminiOAuthEngine`을 구현한다. 기존 `GeminiEngine`(API 키)과 공통 interface `MinutesEngine`으로 추상화한다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-02 | 사용자가 Google OAuth로 로그인하여 API 키 없이 Gemini를 사용할 수 있다 (기술 검증 포함) | Credential Manager + AuthorizationClient로 토큰 획득, REST API로 Gemini 호출. 기술 검증 = 실제 generateContent 호출 성공 확인 |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.credentials:credentials | 1.5.0 (stable) | Credential Manager API | Google Sign-In 공식 권장 방식. 레거시 play-services-auth 대체 |
| androidx.credentials:credentials-play-services-auth | 1.5.0 | Credential Manager 백엔드 | Play Services 기반 구현 제공 |
| com.google.android.libraries.identity.googleid:googleid | 1.1.1 | Google ID Token 처리 | Credential Manager에서 Google Sign-In 옵션 생성 |
| com.google.android.gms:play-services-auth | 21.5.1 | AuthorizationClient | OAuth access token 획득 (스코프 기반 권한 부여) |
| OkHttp + Retrofit | 4.12.0 / 2.11.0 | Gemini REST API 호출 | 이미 프로젝트에 포함. Bearer 토큰 인터셉터 추가만 필요 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| DataStore | 1.2.1 (기존) | OAuth 상태 저장 | 로그인 상태, 선택된 인증 모드 persist |
| Security Crypto | 1.0.0 (기존) | 토큰 암호화 저장 | refresh token 암호화 저장 시 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Credential Manager | Legacy play-services-auth GoogleSignIn | 2025년 deprecated, 곧 제거 예정 |
| REST API 직접 호출 | Firebase AI Logic SDK | Firebase SDK는 OAuth 토큰 주입 미지원, API key만 사용 |
| AuthorizationClient | 서버 사이드 토큰 교환 | 앱 단독 동작 요구사항에 부합하지 않음 |

**Installation (libs.versions.toml 추가분):**
```toml
[versions]
credentialManager = "1.5.0"
googleid = "1.1.1"
playServicesAuth = "21.5.1"

[libraries]
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentialManager" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentialManager" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/autominuting/
├── data/
│   ├── auth/
│   │   ├── GoogleAuthRepository.kt       # Google Sign-In + 토큰 관리
│   │   └── AuthState.kt                  # 인증 상태 sealed class
│   ├── minutes/
│   │   ├── MinutesEngine.kt              # 인터페이스 (신규)
│   │   ├── GeminiEngine.kt               # API 키 기반 (기존, interface 구현)
│   │   ├── GeminiOAuthEngine.kt          # OAuth 기반 (신규)
│   │   └── GeminiRestApiService.kt       # Retrofit 인터페이스 (신규)
│   └── security/
│       └── SecureApiKeyRepository.kt     # 기존 유지
├── di/
│   └── AuthModule.kt                     # 인증 관련 Hilt Module
└── presentation/
    └── settings/
        ├── SettingsViewModel.kt          # OAuth 로그인/로그아웃 액션 추가
        └── SettingsScreen.kt             # Google 로그인 버튼 추가
```

### Pattern 1: MinutesEngine Interface 추상화
**What:** API 키와 OAuth 두 인증 방식을 공통 인터페이스로 추상화
**When to use:** Phase 8 D-09에서 예고한 "Phase 12에서 OAuth 추가 시 interface 도입"
**Example:**
```kotlin
// 회의록 생성 엔진 인터페이스
interface MinutesEngine {
    suspend fun generate(
        transcriptText: String,
        format: MinutesFormat = MinutesFormat.STRUCTURED
    ): Result<String>

    fun engineName(): String
    fun isAvailable(): Boolean
}

// 기존 GeminiEngine이 interface 구현
@Singleton
class GeminiEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : MinutesEngine {
    override fun isAvailable(): Boolean {
        val apiKey = secureApiKeyRepository.getGeminiApiKey()
            ?: BuildConfig.GEMINI_API_KEY
        return apiKey.isNotBlank()
    }
    // generate(), engineName() 기존 로직 유지
}
```

### Pattern 2: Gemini REST API 직접 호출 (OAuth)
**What:** OkHttp Interceptor로 Bearer 토큰을 주입하여 REST API 호출
**When to use:** OAuth 모드에서 Gemini API 호출 시
**Example:**
```kotlin
// Retrofit 인터페이스
interface GeminiRestApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// OkHttp Bearer 토큰 인터셉터
class BearerTokenInterceptor(
    private val tokenProvider: suspend () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() }
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

// Retrofit 인스턴스 (Hilt Module에서 제공)
// baseUrl: https://generativelanguage.googleapis.com/
```

### Pattern 3: Credential Manager Google Sign-In
**What:** Credential Manager API로 Google 계정 선택 + ID Token 획득
**When to use:** 설정 화면에서 "Google 로그인" 버튼 클릭 시
**Example:**
```kotlin
// Google Sign-In 요청
val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(WEB_CLIENT_ID)  // Google Cloud Console에서 생성
    .build()

val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

val result = credentialManager.getCredential(
    request = request,
    context = activityContext
)

// 응답 처리
val credential = result.credential
if (credential is CustomCredential &&
    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
    // googleIdToken.idToken -> 서버 검증용
    // googleIdToken.displayName -> UI 표시용
}
```

### Pattern 4: AuthorizationClient로 Access Token 획득
**What:** Google Sign-In 후 API 접근 권한(스코프) 요청하여 access token 획득
**When to use:** Gemini API 호출에 필요한 OAuth access token이 필요할 때
**Example:**
```kotlin
// Generative Language API 스코프로 권한 요청
val requestedScopes = listOf(
    Scope("https://www.googleapis.com/auth/cloud-platform")
)

val authorizationRequest = AuthorizationRequest.builder()
    .setRequestedScopes(requestedScopes)
    .build()

Identity.getAuthorizationClient(activity)
    .authorize(authorizationRequest)
    .addOnSuccessListener { authorizationResult ->
        if (authorizationResult.hasResolution()) {
            // 사용자 동의 UI 표시 필요
            pendingIntentLauncher.launch(
                IntentSenderRequest.Builder(
                    authorizationResult.pendingIntent!!.intentSender
                ).build()
            )
        } else {
            // 이미 권한 부여됨 - access token 사용 가능
            val accessToken = authorizationResult.accessToken
        }
    }
```

### Anti-Patterns to Avoid
- **Deprecated SDK 계속 사용:** `com.google.ai.client.generativeai`는 archived. OAuth 모드에서는 REST API 직접 호출 필수
- **ID Token을 API 호출에 사용:** ID Token은 인증 확인용, API 호출에는 Access Token 필요
- **토큰 만료 무시:** Access Token은 1시간 만료. 자동 갱신 로직 필수
- **Activity Context 누수:** Credential Manager는 Activity Context 필요하지만 ViewModel에서 직접 참조 금지

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Google Sign-In UI | 커스텀 로그인 화면 | Credential Manager 바텀시트 | OS 레벨 UI, 보안 보장, 일관된 UX |
| OAuth 토큰 관리 | 수동 토큰 갱신/저장 | AuthorizationClient + play-services-auth | Google이 토큰 라이프사이클 관리 |
| Gemini REST API 모델 | 수동 JSON 파싱 | Retrofit + data class | 타입 안전성, 에러 처리 |
| 암호화 토큰 저장 | 커스텀 암호화 | EncryptedSharedPreferences (기존) | 이미 프로젝트에 검증됨 |

**Key insight:** Google Sign-In은 Credential Manager가 모든 복잡성(기기 계정 관리, 동의 화면, 토큰 갱신)을 처리한다. 직접 구현하면 보안 취약점과 UX 불일치가 발생한다.

## Common Pitfalls

### Pitfall 1: deprecated generative-ai-android SDK에 OAuth 주입 시도
**What goes wrong:** `GenerativeModel(apiKey=...)` 생성자에는 API 키만 받는다. OAuth 토큰을 넣으면 인증 실패
**Why it happens:** SDK가 API key 전용으로 설계됨 (x-goog-api-key 헤더 사용)
**How to avoid:** OAuth 모드에서는 반드시 REST API 직접 호출 (`Authorization: Bearer` 헤더)
**Warning signs:** "Invalid API key" 에러

### Pitfall 2: ID Token과 Access Token 혼동
**What goes wrong:** Credential Manager의 Google Sign-In은 ID Token만 반환. 이를 API 호출에 사용하면 403
**Why it happens:** Sign-In(인증)과 Authorization(권한 부여)는 별개 API
**How to avoid:** Sign-In 성공 후 반드시 AuthorizationClient.authorize()로 Access Token 별도 요청
**Warning signs:** "Insufficient permissions" 에러

### Pitfall 3: OAuth Consent Screen 미설정
**What goes wrong:** Google Cloud Console에서 OAuth 동의 화면 미설정 시 로그인 자체 불가
**Why it happens:** OAuth 앱은 반드시 동의 화면 구성 필요 (테스트 모드에서도)
**How to avoid:** Google Cloud 프로젝트에서 (1) Generative Language API 활성화, (2) OAuth 동의 화면 구성, (3) 테스트 사용자 등록, (4) Android 클라이언트 ID + Web 클라이언트 ID 생성
**Warning signs:** "Error: access_denied" 또는 빈 크레덴셜 응답

### Pitfall 4: cloud-platform 스코프의 과도한 권한
**What goes wrong:** `cloud-platform` 스코프는 모든 GCP 리소스 접근 권한이라 사용자가 동의를 꺼릴 수 있음
**Why it happens:** Gemini Generative Language API에 더 좁은 스코프가 있을 수 있으나, 공식 문서가 `cloud-platform`만 명시
**How to avoid:** 먼저 `generative-language.retriever` 스코프 단독으로 시도, 실패 시 `cloud-platform` 사용. 기술 검증 단계에서 확인
**Warning signs:** 넓은 권한 동의 화면에 대한 사용자 불만

### Pitfall 5: Access Token 만료 처리 누락
**What goes wrong:** OAuth access token은 기본 1시간 만료. 만료 후 API 호출 실패
**Why it happens:** API 키는 만료 없지만 OAuth 토큰은 시간 제한
**How to avoid:** OkHttp Authenticator 인터페이스로 401 응답 시 자동 토큰 갱신. AuthorizationClient.authorize()를 재호출하면 캐시된/갱신된 토큰 반환
**Warning signs:** 일정 시간 후 갑자기 API 호출 실패

### Pitfall 6: Credential Manager는 Activity Context 필요
**What goes wrong:** ViewModel에서 직접 Credential Manager 호출 불가
**Why it happens:** Credential Manager.getCredential()은 Activity context를 인자로 받음
**How to avoid:** ViewModel은 이벤트만 발행하고, Activity/Composable에서 실제 호출 후 결과를 ViewModel에 전달하는 패턴 사용
**Warning signs:** "Context is not an Activity" 런타임 에러

## Code Examples

### Gemini REST API Request/Response 모델
```kotlin
// Gemini REST API 요청 모델
data class GenerateContentRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>,
    val role: String = "user"
)

data class Part(
    val text: String
)

// Gemini REST API 응답 모델
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

// Retrofit 서비스
interface GeminiRestApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-2.5-flash",
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}
```

### GeminiOAuthEngine 구현 골격
```kotlin
@Singleton
class GeminiOAuthEngine @Inject constructor(
    private val geminiRestApiService: GeminiRestApiService,
    private val googleAuthRepository: GoogleAuthRepository
) : MinutesEngine {

    override suspend fun generate(
        transcriptText: String,
        format: MinutesFormat
    ): Result<String> {
        if (!isAvailable()) {
            return Result.failure(IllegalStateException("Google 로그인이 필요합니다"))
        }

        val prompt = getPromptForFormat(format) + transcriptText
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            )
        )

        return try {
            val response = geminiRestApiService.generateContent(
                model = "gemini-2.5-flash",
                request = request
            )
            val text = response.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("빈 응답"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean =
        googleAuthRepository.isSignedIn()

    override fun engineName(): String = "Gemini 2.5 Flash (OAuth)"
}
```

### 인증 모드 선택 로직
```kotlin
// Hilt Module에서 활성 엔진 제공
@Module
@InstallIn(SingletonComponent::class)
object MinutesEngineModule {

    @Provides
    @Singleton
    fun provideActiveMinutesEngine(
        geminiEngine: GeminiEngine,           // API 키 모드
        geminiOAuthEngine: GeminiOAuthEngine,  // OAuth 모드
        userPreferencesRepository: UserPreferencesRepository
    ): MinutesEngine {
        // 사용자 설정에 따라 활성 엔진 결정
        // 동적 전환은 Provider<> 패턴 사용
        return geminiEngine // 기본값, 런타임에 전환
    }
}

// 또는 더 유연하게: 둘 다 주입하고 호출 시점에 선택
enum class AuthMode { API_KEY, OAUTH }
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| play-services-auth GoogleSignIn | Credential Manager API | 2024년 4월 deprecated 발표 | 새 프로젝트는 반드시 Credential Manager 사용 |
| generative-ai-android SDK | Firebase AI Logic SDK 또는 REST API | 2025년 12월 archived | OAuth 사용 시 REST API 직접 호출 필요 |
| API key only | API key + OAuth 병행 | 2025년 Gemini CLI 출시 | OAuth로 Gemini 무료 티어 접근 가능 확인 |

**Deprecated/outdated:**
- `com.google.android.gms:play-services-auth` GoogleSignInClient: Credential Manager로 대체
- `com.google.ai.client.generativeai`: Firebase SDK로 대체, 더 이상 업데이트 없음

## Open Questions

1. **Gemini API OAuth에 필요한 최소 스코프**
   - What we know: 공식 문서는 `cloud-platform`과 `generative-language.retriever` 제시
   - What's unclear: generateContent 호출에 `cloud-platform` 없이 `generative-language.retriever`만으로 충분한지
   - Recommendation: 기술 검증 태스크에서 좁은 스코프부터 시도하여 확인. 실패 시 `cloud-platform` 사용

2. **Google Cloud 프로젝트 설정 주체**
   - What we know: OAuth 동의 화면 + API 활성화 + 클라이언트 ID 필요
   - What's unclear: 개인 프로젝트인데 "테스트" 모드로 충분한지, 게시(publish) 필요한지
   - Recommendation: 테스트 모드로 시작. 테스트 사용자에 본인 구글 계정 등록

3. **Access Token 갱신 메커니즘**
   - What we know: AuthorizationClient.authorize() 재호출 시 캐시된/갱신된 토큰 반환 가능
   - What's unclear: 백그라운드에서 (WorkManager) 자동 갱신이 가능한지
   - Recommendation: OkHttp Authenticator에서 401 시 AuthorizationClient 재호출. 포그라운드 필요 시 사용자에게 재로그인 요청

4. **기존 generativeai SDK 마이그레이션 시점**
   - What we know: deprecated이지만 당장 동작 중단은 아님
   - What's unclear: Google이 서버 사이드에서 지원 중단할 시점
   - Recommendation: Phase 12에서는 API 키 모드의 기존 SDK 유지, OAuth 모드만 REST API. 향후 통합 마이그레이션 별도 계획

## Google Cloud Console 설정 가이드

Phase 12 실행 전 반드시 완료해야 하는 사전 작업:

1. **Google Cloud 프로젝트 생성/선택**
2. **Generative Language API 활성화**: APIs & Services > Library > "Generative Language API" 검색 후 활성화
3. **OAuth 동의 화면 구성**: APIs & Services > OAuth consent screen > External > 앱 이름, 이메일 입력
4. **테스트 사용자 등록**: Audience > Test users > 본인 Google 계정 추가
5. **OAuth 클라이언트 ID 생성 (Android)**: Credentials > Create Credentials > OAuth 2.0 Client ID > Android > 패키지명(`com.autominuting`) + SHA-1 인증서 지문
6. **OAuth 클라이언트 ID 생성 (Web)**: Credentials > Create Credentials > OAuth 2.0 Client ID > Web application (Credential Manager의 serverClientId로 사용)

**주의:** Android 클라이언트 ID와 Web 클라이언트 ID가 모두 필요하다. Credential Manager의 `setServerClientId()`에는 Web 클라이언트 ID를 사용한다.

## Sources

### Primary (HIGH confidence)
- [Android Credential Manager Sign-In 구현](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation) - Credential Manager API 코드 패턴
- [Android AuthorizationClient](https://developer.android.com/identity/authorization) - Access Token 획득 방법
- [Gemini API OAuth 공식 문서](https://ai.google.dev/gemini-api/docs/oauth) - OAuth 스코프, REST API 호출 방법
- [generative-ai-android deprecated](https://github.com/google-gemini/generative-ai-android) - SDK deprecated 확인

### Secondary (MEDIUM confidence)
- [Gemini OAuth Cookbook](https://github.com/google-gemini/cookbook/blob/main/quickstarts/Authentication_with_OAuth.ipynb) - Python 예제지만 REST API 패턴 동일
- [Credential Manager Release Notes](https://developer.android.com/jetpack/androidx/releases/credentials) - 버전 정보
- [googleid Maven](https://mvnrepository.com/artifact/com.google.android.libraries.identity.googleid) - 최신 버전 1.1.1

### Tertiary (LOW confidence)
- [Gemini CLI DeepWiki](https://deepwiki.com/google-gemini/gemini-cli/2.2-authentication) - CLI OAuth 흐름 분석 (Android 직접 적용 불가하나 참고)
- [Firebase AI Logic](https://firebase.google.com/docs/ai-logic) - Firebase SDK 방향성 (OAuth 직접 미지원)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Credential Manager, AuthorizationClient는 Google 공식 문서 확인 완료
- Architecture: MEDIUM - MinutesEngine interface 패턴은 Phase 8 D-09에서 계획됨. REST API 호출 패턴은 검증 필요
- Pitfalls: MEDIUM - OAuth 스코프 관련 불확실성 존재 (실기기 검증 필수)

**Research date:** 2026-03-26
**Valid until:** 2026-04-26 (OAuth API는 안정적이나 Gemini API 스코프 변경 가능성)
