# Technology Stack: v2.0 신규 기능용 추가 스택

**프로젝트:** Auto Minuting v2.0
**조사일:** 2026-03-25
**범위:** 기존 스택(Kotlin 2.3.20, Compose BOM 2026.03, Hilt 2.59.2, Room 2.8.4, WorkManager 2.11.1, Google AI Client SDK 0.9.0, Retrofit/OkHttp, DataStore)은 검증 완료. 신규 기능에 필요한 추가 라이브러리만 조사.

---

## 신규 기능별 필요 기술 매핑

| 신규 기능 | 필요 기술 | 신규 라이브러리? |
|-----------|-----------|-----------------|
| 삼성 녹음기 전사 감지 (ContentObserver) | Platform API | 아니오 |
| 삼성 녹음앱 공유 수신 | Platform API (ACTION_SEND) | 아니오 |
| NotebookLM 연동 | Custom Tabs + 공유 Intent | 예 (browser) |
| 전사파일/회의록 삭제 | Room DAO + 파일 삭제 | 아니오 |
| Gemini API 키 설정 UI | EncryptedSharedPreferences | 예 (security-crypto) |
| Gemini OAuth 인증 | Credential Manager | 예 |

---

## 확정 추가 라이브러리

### 1. Credential Manager -- Gemini OAuth 인증

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| androidx.credentials:credentials | 1.5.0 | Credential Manager 코어 | Google 공식 권장 인증 API. 레거시 Google Sign-In 대체 | HIGH |
| androidx.credentials:credentials-play-services-auth | 1.5.0 | Play Services 백엔드 | API 33 이하 디바이스 호환 필수 | HIGH |
| com.google.android.libraries.identity.googleid:googleid | 1.1.1 | Google ID 토큰 요청 | Sign-in with Google 버튼 + ID 토큰 발급 | MEDIUM |

**왜 Credential Manager인가:**
- Google Sign-In for Android(레거시 `play-services-auth`)는 2024년부터 deprecated.
- Credential Manager가 유일한 공식 후속 API (Passkey + OAuth + Password 통합).
- Gemini API OAuth 호출에 Google 계정 인증이 전제 조건.

**Gemini OAuth 구현 경로:**
- Google AI Client SDK(`generativeai:0.9.0`)는 API 키 인증만 지원하므로, OAuth 토큰으로 Gemini를 호출하려면 **기존 Retrofit/OkHttp로 REST API 직접 호출**해야 함.
- 엔드포인트: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
- Authorization 헤더에 `Bearer {access_token}` 전달.
- OAuth 스코프: `https://www.googleapis.com/auth/generative-language` (LOW confidence -- 공식 문서 접근 실패, training data 기반).

**권장 전략:** API 키를 기본으로 유지하고, OAuth는 "내 Google 계정 할당량 사용" 옵션으로 추가 제공. GeminiEngine에 인증 방식 분기 로직 추가.

### 2. Security Crypto -- API 키 안전 저장

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| androidx.security:security-crypto | 1.1.0 | API 키 암호화 저장 | 사용자 입력 API 키를 Android Keystore 기반 AES로 암호화 | HIGH |

**왜 Security Crypto인가:**
- 현재: Gemini API 키가 `local.properties` -> `BuildConfig` 하드코딩. 사용자 변경 불가.
- v2.0: 사용자가 Settings UI에서 API 키를 입력/변경해야 함.
- 일반 DataStore는 평문 저장 (루팅 기기 노출 위험).
- `EncryptedSharedPreferences`는 Android Keystore 기반 자동 암호화. 별도 키 관리 불필요.

**참고:** v1.1.0에서 EncryptedSharedPreferences API가 deprecated 표시되었지만 기능은 정상. 대안(직접 Keystore + Tink)은 복잡도가 높아 실용적 선택은 EncryptedSharedPreferences.

### 3. Browser (Custom Tabs) -- NotebookLM 웹 연동

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| androidx.browser:browser | 1.8.0 | Chrome Custom Tabs | NotebookLM 웹을 앱 내 브라우저로 열기 | HIGH |

**왜 Custom Tabs인가:**
- NotebookLM은 공식 REST API가 존재하지 않음 (2026-03 기준).
- MCP 서버는 데스크톱 Claude 환경용이며 Android 앱에서 직접 호출하는 아키텍처가 아님.
- Custom Tabs는 Chrome과 세션/쿠키를 공유하므로 사용자가 이미 Google 로그인 상태라면 NotebookLM에 바로 접근 가능.
- WebView보다 보안적으로 안전하고 성능이 좋음.

---

## 플랫폼 API 활용 (추가 라이브러리 불필요)

### 삼성 녹음기 전사 감지: ContentObserver

| Technology | Purpose | Confidence |
|------------|---------|------------|
| ContentObserver (android.database) | MediaStore URI 변경 감지 | HIGH (API 자체) |
| MediaStore.Files | 전사 파일 쿼리/필터링 | MEDIUM (삼성 녹음앱 동작 불확실) |

**구현 방식:**
```kotlin
// MediaStore 외부 파일 URI를 관찰
val uri = MediaStore.Files.getContentUri("external")
contentResolver.registerContentObserver(uri, true, observer)
```

**핵심 리스크:** 삼성 녹음 앱이 전사 파일을 MediaStore에 등록하는지 확인 필요. 앱 전용 저장소(`/data/data/com.sec.android.app.voicenote/`)에만 저장하면 ContentObserver로 감지 불가. **반드시 실기기 검증 필요.**

**Accessibility Service는 사용하지 않음:** Google Play 정책상 장애인 보조 외 용도로 Accessibility Service 사용 시 앱 리뷰 거절 리스크가 높음.

### 삼성 녹음앱 공유 수신: Share Intent

| Technology | Purpose | Confidence |
|------------|---------|------------|
| Intent.ACTION_SEND | 텍스트 공유 수신 | HIGH |

**구현:** AndroidManifest에 intent-filter 추가.
```xml
<activity android:name=".ShareReceiverActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

삼성 녹음앱에서 "공유" 버튼 탭 시 Auto Minuting이 대상 앱으로 표시됨. `intent.getStringExtra(Intent.EXTRA_TEXT)`로 전사 텍스트 수신.

### 전사파일/회의록 삭제

기존 Room DAO에 `@Delete` 또는 `@Query("DELETE FROM meetings WHERE id = :id")` 추가. 로컬 오디오/텍스트 파일은 `File.delete()`. 추가 라이브러리 불필요.

---

## 전체 의존성 추가 사항

### libs.versions.toml 추가

```toml
[versions]
credentials = "1.5.0"
securityCrypto = "1.1.0"
browser = "1.8.0"
googleid = "1.1.1"

[libraries]
# Credential Manager (Gemini OAuth)
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }

# API 키 보안 저장
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }

# Chrome Custom Tabs (NotebookLM 웹)
browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
```

### build.gradle.kts 추가

```kotlin
// Gemini OAuth 인증
implementation(libs.credentials)
implementation(libs.credentials.play.services)
implementation(libs.googleid)

// API 키 보안 저장
implementation(libs.security.crypto)

// NotebookLM Custom Tabs
implementation(libs.browser)
```

### AndroidManifest 추가 사항

```xml
<!-- 삼성 녹음앱 공유 수신용 Activity -->
<activity android:name=".ShareReceiverActivity"
    android:exported="true"
    android:theme="@style/Theme.AutoMinuting">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

---

## 추가하지 않는 것 (Anti-Additions)

| 기술 | 왜 추가하지 않는가 |
|------|-------------------|
| play-services-auth (레거시 Google Sign-In) | Deprecated. Credential Manager가 후속 |
| Accessibility Service | Play Store 정책 위반 리스크. 장애인 보조 외 용도 거절됨 |
| NotebookLM REST 클라이언트 | 공식 REST API 미존재 |
| Firebase Auth | 불필요한 복잡도. Credential Manager로 충분 |
| Vertex AI SDK | 서버사이드용. 모바일에서는 REST API + OkHttp가 적절 |
| WebView (NotebookLM용) | Custom Tabs가 보안/성능/UX 모두 우수 |
| Room FTS (전문 검색) | v2.0 데이터 규모에 LIKE 검색으로 충분 (v1.0 결정 유지) |
| DocumentsProvider / SAF | 삭제 기능에 불필요. 내부 파일은 직접 삭제 가능 |

---

## 기존 스택 v2.0 추가 활용

| 기존 기술 | v2.0에서의 추가 역할 |
|-----------|---------------------|
| DataStore | OAuth 인증 상태 플래그, 사용자 설정 확장 |
| Retrofit/OkHttp | Gemini OAuth REST API 호출 (SDK 미지원 경로) |
| Room | Meeting 엔티티 삭제 쿼리, source 필드 추가 (Plaud vs Samsung) |
| WorkManager | ContentObserver 감지 -> 파이프라인 트리거 연계 |
| Hilt | 신규 컴포넌트(SecureKeyStore, CredentialHelper 등) DI |
| Compose Navigation | ShareReceiverActivity -> 메인 네비게이션 연계 |

---

## Confidence 요약

| 영역 | Confidence | 근거 |
|------|-----------|------|
| Credential Manager 버전/API | HIGH | Android 공식 문서에서 v1.5.0 stable 확인 |
| Security Crypto 버전 | HIGH | Android 공식 릴리스 페이지에서 v1.1.0 확인 |
| Custom Tabs | HIGH | 표준 라이브러리, 버전 안정적 |
| Gemini OAuth 스코프/엔드포인트 | LOW | 공식 문서 접근 실패. Training data 기반. 실기기 검증 필수 |
| 삼성 녹음앱 ContentObserver 가능 여부 | LOW | 삼성 녹음앱의 파일 저장 위치 미확인. 실기기 검증 필수 |
| NotebookLM API 부재 | MEDIUM | MCP 서버 존재하나 REST API는 미확인 |
| googleid 라이브러리 버전 | MEDIUM | Training data 기반. Maven Central에서 최신 버전 확인 권장 |

---

## 소스

- Android Credential Manager 릴리스: https://developer.android.com/jetpack/androidx/releases/credentials - HIGH (v1.5.0 stable 확인)
- Android Security Crypto 릴리스: https://developer.android.com/jetpack/androidx/releases/security - HIGH (v1.1.0 확인)
- Android ContentObserver: https://developer.android.com/reference/android/database/ContentObserver - HIGH
- Android Share Intent 수신: https://developer.android.com/training/sharing/receive - HIGH
- Chrome Custom Tabs: https://developer.android.com/develop/ui/views/layout/webapps/custom-tabs - HIGH
- Gemini OAuth: https://ai.google.dev/gemini-api/docs/oauth - 접근 실패 (LOW, training data 기반)
