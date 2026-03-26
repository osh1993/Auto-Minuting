# Technology Stack

**Analysis Date:** 2026-03-26

## Languages

**Primary:**
- Kotlin 2.3.20 - 전체 Android 앱 소스 코드

**Secondary:**
- Python - POC 스크립트 (`poc/minutes-test/gemini-test.py`, `poc/plaud-analysis/cloud-api-test.py`)

## Runtime

**Environment:**
- Android API 31–36 (Android 12–16)
- Min SDK: API 31 (Android 12) — `SpeechRecognizer.createOnDeviceSpeechRecognizer` API 31+ 요구
- Target SDK: API 36
- Compile SDK: API 36

**Package Manager:**
- Gradle 9.x (Android Gradle Plugin 9.1.0)
- Lockfile: `gradle/libs.versions.toml` (Version Catalog 방식)

## Frameworks

**Core:**
- Android Jetpack (플랫폼 내장) — Lifecycle, Activity, ContentResolver
- Jetpack Compose BOM 2026.03.00 (`gradle/libs.versions.toml`) — 전체 UI 레이어
- Material 3 (BOM 링크) — Design system, Samsung One UI Material You 연동

**DI:**
- Hilt 2.59.2 — 앱 전역 의존성 주입 (`app/src/main/java/com/autominuting/di/`)
- KSP 2.3.6 — Room/Hilt 어노테이션 프로세싱 (kapt 대체)

**Navigation:**
- Compose Navigation 2.9.0 — 화면 간 네비게이션 (`presentation/navigation/AppNavigation.kt`)

**Background:**
- WorkManager 2.11.1 — 전사·회의록 생성 Worker 스케줄링 (`worker/`)
- HiltWorkerFactory — `AutoMinutingApplication.kt`에서 WorkManager와 Hilt 통합

**Testing:**
- 현재 테스트 소스 파일 없음 (`app/src/test/` 디렉토리 없음)

**Build/Dev:**
- Android Gradle Plugin 9.1.0
- Kotlin Compose Plugin 2.3.20
- JDK 17 (compile/target: `JavaVersion.VERSION_17`)

## Key Dependencies

**Critical:**
- `com.google.ai.client.generativeai:generativeai:0.9.0` — Gemini 2.5 Flash API 키 모드 SDK (`data/minutes/GeminiEngine.kt`)
- `com.squareup.retrofit2:retrofit:2.11.0` — Gemini OAuth REST API 호출 (`data/minutes/GeminiRestApiService.kt`)
- `com.squareup.okhttp3:okhttp:4.12.0` — HTTP 클라이언트, Bearer 토큰 인터셉터 (`data/minutes/BearerTokenInterceptor.kt`)
- `androidx.room:room-runtime:2.8.4` — 회의 메타데이터 로컬 DB (`data/local/AppDatabase.kt`)
- `androidx.work:work-runtime-ktx:2.11.1` — 전사·회의록 생성 백그라운드 파이프라인 (`worker/`)

**Infrastructure:**
- `androidx.datastore:datastore-preferences:1.2.1` — 사용자 설정 영속화 (`data/preferences/UserPreferencesRepository.kt`)
- `androidx.security:security-crypto:1.0.0` — API 키 암호화 저장 (`data/security/SecureApiKeyRepository.kt`)
- `androidx.browser:browser:1.9.0` — Custom Tabs로 NotebookLM 웹 열기 (`util/NotebookLmHelper.kt`)
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1` — 전체 비동기 처리
- `com.google.guava:guava:33.4.0-android` — Plaud SDK 의존성 (SDK AAR 자체는 미배치)

**Auth:**
- `androidx.credentials:credentials:1.5.0` — Credential Manager API Google Sign-In (`data/auth/GoogleAuthRepository.kt`)
- `androidx.credentials:credentials-play-services-auth:1.5.0` — Play Services 연동
- `com.google.android.libraries.identity.googleid:googleid:1.1.1` — Google ID 토큰 파싱
- `com.google.android.gms:play-services-auth:21.5.1` — AuthorizationClient로 OAuth access token 획득

**UI:**
- `androidx.compose.material:material-icons-extended` — 확장 아이콘 세트 (BOM 링크)
- `com.google.android.material:material:1.12.0` — XML 테마용 Material Design
- `androidx.hilt:hilt-navigation-compose:1.2.0` — Compose ViewModel Hilt 주입
- `androidx.lifecycle:lifecycle-runtime-compose:2.9.0` — Compose Lifecycle 통합

**Native (JNI):**
- `libwhisper.so` (선언만, 아직 미탑재) — whisper.cpp JNI 네이티브 라이브러리 (`data/stt/WhisperEngine.kt`)
- 모델 파일: `ggml-small.bin` — 앱 내부 저장소 `filesDir/models/` (런타임 배치 필요)

## Configuration

**Environment:**
- `local.properties` — `GEMINI_API_KEY`, `GOOGLE_OAUTH_WEB_CLIENT_ID` 빌드 시 읽어 `BuildConfig` 필드로 주입
- 런타임 API 키는 `EncryptedSharedPreferences` (`data/security/SecureApiKeyRepository.kt`)에 저장
- 사용자 설정은 DataStore (`data/preferences/UserPreferencesRepository.kt`)에 저장

**Build:**
- `app/build.gradle.kts` — 앱 모듈 빌드 설정
- `gradle/libs.versions.toml` — 전체 의존성 버전 중앙 관리 (Version Catalog)
- `settings.gradle.kts` — 모듈 포함 및 리포지토리 설정
- `build.gradle.kts` — 루트 플러그인 선언

**Room Schema:**
- `app/schemas/` — Room DB 스키마 JSON 자동 생성 (`ksp.arg("room.schemaLocation", ...)`)
- 현재 버전: `AppDatabase` version 3

## Platform Requirements

**Development:**
- JDK 17
- Android Studio (Gradle 9.x 지원)
- Galaxy 실기기 권장 (Galaxy AI/Samsung 녹음앱 통합 테스트)

**Production:**
- Android 12 이상 (API 31+)
- Samsung Galaxy 기기 (Galaxy AI STT, 삼성 녹음앱 공유 연동)
- Plaud 녹음기 (선택적 — BLE 연동은 현재 제거됨, 삼성 녹음앱 공유로 대체)
- Google Play Services (Credential Manager, Play Services Auth 의존)

---

*Stack analysis: 2026-03-26*
