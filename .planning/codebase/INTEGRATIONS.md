# External Integrations

**Analysis Date:** 2026-03-26

## APIs & External Services

**AI 회의록 생성:**
- **Google Gemini 2.5 Flash** — 전사 텍스트에서 구조화된 회의록 생성
  - SDK/Client (API 키 모드): `com.google.ai.client.generativeai:generativeai:0.9.0`
  - SDK/Client (OAuth 모드): Retrofit `GeminiRestApiService` → `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
  - Auth: `GEMINI_API_KEY` (BuildConfig 또는 EncryptedSharedPreferences)
  - 구현 파일: `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` (API 키), `app/src/main/java/com/autominuting/data/minutes/GeminiOAuthEngine.kt` (OAuth)
  - 엔진 선택: `app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt`

**음성 전사 (STT):**
- **whisper.cpp (JNI)** — 온디바이스 한국어 음성 전사 (1차 경로)
  - SDK/Client: `libwhisper.so` 네이티브 라이브러리 (현재 미탑재, 스텁 모드)
  - 모델: `ggml-small.bin` (앱 내부 `filesDir/models/`에 런타임 배치 필요)
  - 구현 파일: `app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt`
  - 변환 유틸: `app/src/main/java/com/autominuting/data/stt/AudioConverter.kt` (MediaCodec으로 16kHz mono WAV 변환)

- **Android SpeechRecognizer** — 온디바이스 STT 2차 폴백 경로
  - SDK/Client: 플랫폼 API `android.speech.SpeechRecognizer`
  - `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (API 31+) 우선, 실패 시 기본 인식기로 폴백
  - 로케일: `ko-KR`
  - 구현 파일: `app/src/main/java/com/autominuting/data/stt/MlKitEngine.kt` (클래스명 MlKitEngine이지만 실제로는 Android SpeechRecognizer 사용)

## Data Storage

**Databases:**
- **Room (SQLite)** — 회의 메타데이터 및 파이프라인 상태 로컬 저장
  - Connection: 내부 저장소 (Room 기본 경로)
  - Client: `androidx.room:room-runtime:2.8.4`
  - 구현 파일: `app/src/main/java/com/autominuting/data/local/AppDatabase.kt`
  - 테이블: `meetings` (MeetingEntity), `prompt_templates` (PromptTemplateEntity)
  - 현재 버전: 3 (MIGRATION_1_2, MIGRATION_2_3 정의됨)
  - DAO: `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt`, `PromptTemplateDao.kt`

**File Storage:**
- 앱 내부 저장소 (`filesDir`)
  - 오디오 파일: `filesDir/audio/` — 공유 수신 음성 파일 저장
  - 전사 텍스트: `filesDir/transcripts/` — 전사 결과 TXT 파일
  - STT 모델: `filesDir/models/ggml-small.bin` — Whisper 모델 파일
  - 변환 캐시: `cacheDir/whisper_tmp/` — Whisper 입력용 임시 WAV 파일
  - 관리 클래스: `app/src/main/java/com/autominuting/data/audio/AudioFileManager.kt`

**Settings/Preferences:**
- **DataStore (Preferences)** — 사용자 설정 영속화
  - 저장 항목: 회의록 형식(`MinutesFormat`), 자동화 모드(`AutomationMode`), 인증 모드(`AuthMode`), Google 계정 이메일/이름
  - 구현 파일: `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt`

- **EncryptedSharedPreferences** — API 키 암호화 저장
  - 저장 항목: `GEMINI_API_KEY`, `GOOGLE_OAUTH_WEB_CLIENT_ID`
  - 암호화: AES256-GCM (값), AES256-SIV (키)
  - 구현 파일: `app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt`

**Caching:**
- 파일 시스템 캐시 (`cacheDir/whisper_tmp/`) — Whisper 변환 임시 파일만

## Authentication & Identity

**Auth Provider:**
- Google OAuth 2.0 (Credential Manager API)
  - 구현: `app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt`
  - Google Sign-In: `GetGoogleIdOption` + `CredentialManager.getCredential()`
  - Access Token 획득: `Identity.getAuthorizationClient()` + `AuthorizationRequest`
  - OAuth Scope (우선): `https://www.googleapis.com/auth/generative-language.retriever`
  - OAuth Scope (폴백): `https://www.googleapis.com/auth/cloud-platform`
  - Client ID 설정: `GOOGLE_OAUTH_WEB_CLIENT_ID` (BuildConfig 또는 EncryptedSharedPreferences)
  - 상태 관리: `StateFlow<AuthState>` + DataStore 로그인 정보 persist

**인증 모드 (이중 경로):**
- `API_KEY` 모드: `GeminiEngine` — Gemini SDK + 직접 API 키
- `OAUTH` 모드: `GeminiOAuthEngine` — Retrofit REST + Bearer 토큰 (`BearerTokenInterceptor.kt`)
- 선택 로직: `app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt`

## Monitoring & Observability

**Error Tracking:**
- 없음 (Firebase Crashlytics 미적용)

**Logs:**
- `android.util.Log` 직접 사용 — 전 파일에서 TAG 상수 정의 후 `Log.d/e/w` 사용
- 파이프라인 진행 상황: `app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt` — 시스템 알림(Notification)으로 사용자에게 표시

## CI/CD & Deployment

**Hosting:**
- 로컬 개발 전용 (Play Store 미게시, 별도 CI 없음)

**CI Pipeline:**
- 없음

## Webhooks & Callbacks

**Incoming:**
- **Android Share Intent** — 외부 앱에서 `ACTION_SEND` / `ACTION_SEND_MULTIPLE`로 공유 수신
  - 수신 Activity: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
  - 지원 MIME: `text/plain`, `text/*`, `*/*`, 오디오 파일
  - 소스 식별: 삼성 녹음앱 공유 → `source = "SAMSUNG_SHARE"`
  - 텍스트 공유: 전사 텍스트로 간주 → `MinutesGenerationWorker` 즉시 enqueue
  - 음성 공유: 오디오 파일 저장 → `TranscriptionTriggerWorker` enqueue
  - 인코딩 자동 감지: UTF-16 LE/BE BOM 감지 (삼성 녹음앱이 UTF-16 LE로 TXT 저장)

- **WorkManager Worker 체인:**
  - `app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt` → STT 전사 실행
  - `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` → 회의록 생성 실행
  - `app/src/main/java/com/autominuting/worker/TestWorker.kt` — 파이프라인 테스트용

- **BroadcastReceiver:**
  - `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt` — 알림 액션 처리 (회의록 공유 등)

**Outgoing:**
- **NotebookLM 연동** — 회의록을 NotebookLM으로 전달
  - 앱 설치 시: `ACTION_SEND` Intent → `com.google.android.apps.labs.language.tailwind`
  - 앱 미설치 시: Custom Tabs → `https://notebooklm.google.com`
  - 구현 파일: `app/src/main/java/com/autominuting/util/NotebookLmHelper.kt`

## Environment Configuration

**Required env vars (local.properties):**
- `GEMINI_API_KEY` — Gemini API 키 (빌드 시 BuildConfig.GEMINI_API_KEY에 주입)
- `GOOGLE_OAUTH_WEB_CLIENT_ID` — Google OAuth Web Client ID (빌드 시 BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID에 주입)

**Secrets location:**
- 빌드 타임: `local.properties` (gitignore 대상)
- 런타임: `EncryptedSharedPreferences` (`data/security/SecureApiKeyRepository.kt`) — AES256-GCM 암호화

## Plaud SDK (현재 미탑재)

- **Plaud SDK AAR** — Plaud 녹음기 BLE 연동 (현재 BLE 기능 삭제됨)
  - AAR 배치 경로: `app/libs/plaud-sdk.aar` (현재 미배치)
  - Guava `33.4.0-android` 의존성만 libs.versions.toml에 남아있음
  - 참고: `app/libs/README.txt`

---

*Integration audit: 2026-03-26*
