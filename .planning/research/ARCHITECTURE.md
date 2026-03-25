# Architecture Patterns: v2.0 통합 아키텍처

**Domain:** v2.0 신규 기능의 기존 Clean Architecture 통합
**Researched:** 2026-03-25
**Focus:** 삼성 녹음기 연동, NotebookLM 통합, Gemini OAuth, 파일 관리

---

## 현재 아키텍처 요약

v1.0은 잘 구조화된 3-layer Clean Architecture를 따른다:

```
presentation/          ViewModel + StateFlow + Compose
  dashboard/           DashboardScreen, DashboardViewModel
  minutes/             MinutesScreen, MinutesDetailScreen, MarkdownText
  transcripts/         TranscriptsScreen, TranscriptEditScreen
  settings/            SettingsScreen, SettingsViewModel
  navigation/          AppNavigation, Screen (4탭 BottomNav)
  theme/               Material 3 테마

domain/
  model/               Meeting, PipelineStatus, MinutesFormat, AutomationMode
  repository/          AudioRepository, TranscriptionRepository, MinutesRepository, MeetingRepository

data/
  repository/          4개 Impl (Audio, Transcription, Minutes, Meeting)
  audio/               PlaudSdkManager, PlaudCloudApiService, AudioFileManager
  stt/                 WhisperEngine, MlKitEngine, SttEngine, AudioConverter
  minutes/             GeminiEngine
  local/               AppDatabase, MeetingDao, MeetingEntity, Converters
  preferences/         UserPreferencesRepository (DataStore)

di/                    AppModule, AudioModule, DatabaseModule, DataStoreModule, RepositoryModule, SttModule, WorkerModule
service/               AudioCollectionService (Foreground), PipelineNotificationHelper
worker/                TranscriptionTriggerWorker, MinutesGenerationWorker
receiver/              PipelineActionReceiver
```

**핵심 데이터 흐름:**
```
AudioCollectionService (BLE) → AudioRepository.startAudioCollection()
  → MeetingEntity 생성 (AUDIO_RECEIVED)
  → TranscriptionTriggerWorker (Whisper/ML Kit)
  → MeetingEntity 업데이트 (TRANSCRIBED)
  → MinutesGenerationWorker (Gemini API)
  → MeetingEntity 업데이트 (COMPLETED)
```

---

## v2.0 신규 기능별 통합 분석

### Feature 1: 삼성 녹음기 전사 텍스트 자동 감지

**목표:** 삼성 음성 녹음 앱의 온보드 AI 전사 완료를 감지하여 전사 텍스트를 자동 수집

**기술적 접근 (우선순위순):**

#### 접근법 A: ContentObserver (권장 1차)
삼성 녹음 앱이 전사 결과를 MediaStore 또는 내부 ContentProvider에 저장할 경우, ContentObserver로 변경을 감시할 수 있다.

**새 컴포넌트:**
```
data/samsung/
  SamsungRecorderObserver.kt    -- ContentObserver 구현체
  SamsungRecorderFileParser.kt  -- 전사 파일 포맷 파싱 (STT 텍스트 추출)
```

**기존 코드 영향:**
- `AudioCollectionService`에 삼성 녹음기 감시 모드 추가, 또는 별도 `SamsungRecorderService` 생성
- `PipelineStatus`에 `TRANSCRIPT_IMPORTED` 상태 추가 (외부 전사 텍스트 수입 시 STT 단계 스킵)
- `Meeting` 모델에 `source: MeetingSource` 필드 추가 (PLAUD_BLE / SAMSUNG_RECORDER / SHARE_INTENT)

**실현 가능성:** LOW confidence. 삼성 녹음 앱이 ContentProvider를 공개하는지 확인 필요. Android의 Scoped Storage (API 29+)로 인해 다른 앱의 내부 저장소에 직접 접근 불가. MediaStore에 등록하는 경우에만 ContentObserver가 동작한다.

#### 접근법 B: Accessibility Service (폴백)
녹음 앱 UI의 "전사 완료" 이벤트를 감시하는 방식.

**장점:** 앱 내부 저장소 접근 제한 우회 가능
**단점:** Google Play 정책 위반 가능, 유지보수 부담 극히 높음, 삼성 UI 업데이트에 취약
**판정:** 개인 사용이라면 기술적으로 가능하나, 유지보수 비용 대비 가치가 낮다.

#### 접근법 C: 파일시스템 감시 (현실적 대안)
삼성 녹음 앱의 저장 경로가 외부 저장소 (예: `/sdcard/Recordings/`)인 경우 FileObserver로 감시.

**새 컴포넌트:**
```
data/samsung/
  RecordingFileObserver.kt      -- FileObserver 구현, 전사 파일 생성 감시
```

**기존 코드 영향:**
- `AndroidManifest.xml`에 `READ_EXTERNAL_STORAGE` 또는 `READ_MEDIA_AUDIO` (API 33+) 퍼미션 추가
- Foreground Service에 `dataSync` 타입 추가

**실현 가능성:** MEDIUM confidence. 삼성 녹음 앱은 기본적으로 `/sdcard/Recordings/` 또는 `/sdcard/Samsung/Voice Recorder/`에 녹음 파일을 저장한다. 전사 텍스트(`.txt`, `.srt`)도 같은 위치에 저장되는지 기기에서 확인 필요.

#### 권장 전략
접근법 C(FileObserver)를 1차로 시도하고, 실패 시 Feature 2(공유 방식)로 폴백. ContentObserver는 파일 경로 확인 후 2차로 시도. Accessibility Service는 사용하지 않는다.

---

### Feature 2: 삼성 녹음앱에서 공유로 전사 텍스트 수신

**목표:** 사용자가 삼성 녹음 앱에서 "공유" 버튼을 눌러 전사 텍스트를 Auto Minuting으로 전달

**기술적 접근: Share Intent Receiver**

이 기능은 Android의 표준 Share Intent 메커니즘을 활용하며, 구현 확실성이 높다.

**새 컴포넌트:**
```
presentation/
  share/
    ShareReceiverActivity.kt    -- Intent 수신 전용 Activity
    ShareReceiverViewModel.kt   -- 수신된 데이터 처리 로직
```

**기존 코드 변경:**

1. **AndroidManifest.xml** -- ShareReceiverActivity 등록:
```xml
<activity
    android:name=".presentation.share.ShareReceiverActivity"
    android:exported="true"
    android:theme="@style/Theme.AutoMinuting.Transparent">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/*" />
    </intent-filter>
</activity>
```

2. **`PipelineStatus`** -- `TRANSCRIPT_IMPORTED` 추가:
```kotlin
enum class PipelineStatus {
    AUDIO_RECEIVED,
    TRANSCRIBING,
    TRANSCRIBED,
    TRANSCRIPT_IMPORTED,  // 외부에서 전사 텍스트를 직접 수입한 경우
    GENERATING_MINUTES,
    COMPLETED,
    FAILED
}
```

3. **`Meeting` 도메인 모델** -- source 추가:
```kotlin
data class Meeting(
    // ... 기존 필드 ...
    val source: MeetingSource = MeetingSource.PLAUD_BLE,
)

enum class MeetingSource {
    PLAUD_BLE,           // Plaud 녹음기 BLE 수신
    SAMSUNG_RECORDER,    // 삼성 녹음기 자동 감지
    SHARE_INTENT,        // 외부 앱 공유로 수신
}
```

4. **`MeetingEntity`** -- source 컬럼 추가 (Room migration 필요)

5. **`MeetingDao`** -- source별 필터 쿼리 추가 가능 (선택적)

**데이터 흐름:**
```
삼성 녹음 앱 → 공유 → ShareReceiverActivity
  → 텍스트 추출 (Intent.EXTRA_TEXT)
  → MeetingEntity 생성 (TRANSCRIPT_IMPORTED, source=SHARE_INTENT)
  → 전사 파일 저장 (/files/transcripts/{id}.txt)
  → FULL_AUTO면 즉시 MinutesGenerationWorker enqueue
  → HYBRID면 확인 알림 표시
```

**실현 가능성:** HIGH confidence. Android 표준 API, 삼성 녹음 앱은 텍스트 공유를 지원한다.

---

### Feature 3: NotebookLM 연동

**목표:** 생성된 회의록 또는 전사 텍스트를 NotebookLM에 전달하여 심층 분석

**기술적 접근 (우선순위순):**

#### 접근법 A: MCP 서버 직접 호출 (권장)
프로젝트에 이미 `notebooklm-mcp` 서버가 설정되어 있다. 안드로이드 앱에서 MCP 서버의 HTTP 엔드포인트를 직접 호출한다.

**새 컴포넌트:**
```
data/notebooklm/
  NotebookLmMcpClient.kt       -- MCP 서버 HTTP 클라이언트
  NotebookLmRepository.kt      -- Repository 인터페이스 (domain/)
  NotebookLmRepositoryImpl.kt  -- 구현체 (data/)
  NotebookLmConfig.kt          -- 서버 URL, 인증 정보
```

**Domain layer 추가:**
```kotlin
// domain/repository/NotebookLmRepository.kt
interface NotebookLmRepository {
    /** 전사 텍스트를 NotebookLM 소스로 추가 */
    suspend fun addSource(notebookId: String, text: String): Result<String>

    /** 노트북에 회의록 노트 생성 */
    suspend fun createNote(notebookId: String, title: String, content: String): Result<String>

    /** 노트북 목록 조회 */
    suspend fun listNotebooks(): Result<List<NotebookInfo>>

    /** 연결 상태 확인 */
    suspend fun isConnected(): Boolean
}
```

**기존 코드 변경:**
- `UserPreferencesRepository`에 NotebookLM 설정 키 추가 (서버 URL, 기본 노트북 ID)
- `SettingsScreen`에 NotebookLM 연결 설정 섹션 추가
- `MinutesGenerationWorker`에 회의록 생성 완료 후 NotebookLM 업로드 단계 선택적 추가
- `PipelineStatus`에 `UPLOADING_TO_NOTEBOOK` 추가 (선택적)

**제약사항:**
- MCP 서버가 로컬 PC에서 실행되어야 함 (같은 네트워크)
- 외출 시 사용 불가 → Gemini API 직접 호출이 기본, NotebookLM은 선택적 부가 기능

#### 접근법 B: 안드로이드 공유 Intent로 NotebookLM 앱에 전달
NotebookLM Android 앱이 있다면 Share Intent로 텍스트를 전달하는 방식. 그러나 NotebookLM은 웹 전용이므로 Chrome Custom Tab 또는 WebView로 URL 기반 접근만 가능하다.

**판정:** 접근법 A(MCP)를 기본으로 하되, NotebookLM 없이도 앱이 완전히 동작하도록 설계. NotebookLM 연동은 "보너스 기능"으로 위치시킨다.

**실현 가능성:** MEDIUM confidence. MCP 서버 자체는 동작하나, 안드로이드에서의 HTTP 호출과 인증 흐름을 검증해야 한다.

---

### Feature 4: 전사파일/회의록 삭제 기능

**목표:** 사용자가 전사 텍스트와 회의록을 개별적으로 또는 회의 단위로 삭제

**기술적 접근:**

이 기능은 기존 아키텍처 내에서 완전히 해결 가능하다. 새 컴포넌트가 필요 없다.

**기존 코드 변경:**

1. **`MeetingRepository`** -- 이미 `deleteMeeting(id)` 존재. 관련 파일 삭제 로직 추가 필요.

2. **`MeetingRepositoryImpl`** -- 삭제 시 연관 파일도 정리:
```kotlin
override suspend fun deleteMeeting(id: Long) {
    // 1. DB에서 회의 정보 조회 (파일 경로 확보)
    // 2. 오디오 파일, 전사 파일, 회의록 파일 삭제
    // 3. DB 레코드 삭제
    meetingDao.delete(id)
}
```

3. **`MeetingDao`** -- `getMeetingByIdOnce()` suspend 함수 추가 (Flow가 아닌 즉시 반환):
```kotlin
@Query("SELECT * FROM meetings WHERE id = :id")
suspend fun getMeetingByIdOnce(id: Long): MeetingEntity?
```

4. **`TranscriptsViewModel` / `MinutesViewModel`** -- 삭제 UI 액션 추가:
```kotlin
fun deleteMeeting(meetingId: Long) {
    viewModelScope.launch {
        meetingRepository.deleteMeeting(meetingId)
    }
}
```

5. **UI** -- 리스트 항목에 스와이프 삭제 또는 롱프레스 메뉴 추가

**데이터 흐름:**
```
사용자 삭제 요청 → ViewModel.deleteMeeting(id)
  → MeetingRepositoryImpl: DB에서 경로 조회
  → AudioFileManager: 오디오 파일 삭제
  → File: 전사 파일 삭제 (/files/transcripts/{id}.txt)
  → File: 회의록 파일 삭제 (/files/minutes/{id}.md)
  → MeetingDao: DB 레코드 삭제
  → UI: Flow 자동 갱신 (Room의 reactive query)
```

**실현 가능성:** HIGH confidence. 순수 로컬 작업, 기존 인프라로 충분.

---

### Feature 5: Gemini API 키 설정 UI

**목표:** 현재 `BuildConfig.GEMINI_API_KEY` 하드코딩을 사용자가 직접 입력하는 방식으로 전환

**기술적 접근:**

**기존 코드 변경:**

1. **`UserPreferencesRepository`** -- API 키 저장:
```kotlin
companion object {
    // ... 기존 키 ...
    val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
}

val geminiApiKey: Flow<String> = dataStore.data.map { prefs ->
    prefs[GEMINI_API_KEY] ?: ""
}

suspend fun setGeminiApiKey(key: String) { ... }
suspend fun getGeminiApiKeyOnce(): String { ... }
```

2. **`GeminiEngine`** -- BuildConfig 대신 동적 API 키:
```kotlin
// 기존: val apiKey = BuildConfig.GEMINI_API_KEY
// 변경: 생성자로 API 키 제공자 주입
@Singleton
class GeminiEngine @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun generate(...): Result<String> {
        val apiKey = preferencesRepository.getGeminiApiKeyOnce()
        if (apiKey.isBlank()) return Result.failure(...)
        // ...
    }
}
```

3. **`SettingsScreen`** -- API 키 입력 필드 추가 (SecureTextField, 마스킹 처리)

4. **`SettingsViewModel`** -- API 키 상태 관리:
```kotlin
val geminiApiKey: StateFlow<String>   // 마스킹된 표시용
fun setGeminiApiKey(key: String)
fun validateApiKey(key: String)       // Gemini API 테스트 호출로 유효성 검증
```

**보안 고려사항:**
- DataStore는 앱 내부 저장소에 파일로 저장되므로 루팅 기기에서 접근 가능
- EncryptedDataStore(Tink 기반) 사용을 권장하나, 개인 사용 앱이므로 DataStore 평문도 수용 가능
- UI에서는 비밀번호 필드처럼 마스킹 표시

**실현 가능성:** HIGH confidence. DataStore + UI 변경만 필요.

---

### Feature 6: Gemini OAuth 인증

**목표:** API 키 대신 Google OAuth로 Gemini API 인증. 사용자의 Google 계정을 활용.

**기술적 접근:**

#### Google AI Client SDK의 인증 방식
현재 사용 중인 `com.google.ai.client.generativeai` SDK는 **API 키 전용**이다. OAuth를 사용하려면:

1. **Firebase AI Logic SDK로 전환** -- Firebase 프로젝트 설정 + google-services.json
2. **Vertex AI 엔드포인트 직접 호출** -- OAuth 토큰 + Retrofit

**권장: Credential Manager + Google Sign-In → API 키 유지 전략**

Gemini API의 무료 티어는 API 키로만 동작하고, OAuth 기반 접근은 Google Cloud Vertex AI(유료)를 거쳐야 한다. 따라서:

- **1차:** API 키 방식 유지 (현재 동작 중, 무료)
- **2차:** Google Sign-In은 NotebookLM 연동 등 다른 Google 서비스 인증에 활용
- **3차:** 유료 전환 시 Vertex AI + OAuth로 마이그레이션

**새 컴포넌트 (Google Sign-In용):**
```
data/auth/
  GoogleAuthManager.kt          -- Credential Manager 래퍼
  AuthState.kt                  -- 인증 상태 모델

domain/model/
  AuthState.kt                  -- 도메인 인증 상태
```

**기존 코드 변경:**
- `build.gradle`에 `androidx.credentials:credentials:1.5+`, `com.google.android.libraries.identity.googleid` 추가
- `UserPreferencesRepository`에 인증 토큰 저장 (EncryptedDataStore 권장)
- `SettingsScreen`에 Google 계정 로그인 버튼 추가
- `SettingsViewModel`에 로그인/로그아웃 로직 추가

**데이터 흐름:**
```
설정 화면 → "Google 계정 연결" 버튼
  → Credential Manager → Google Sign-In 바텀시트
  → ID Token 수신 → AuthState 업데이트
  → NotebookLM/기타 Google 서비스에서 활용
```

**실현 가능성:** MEDIUM confidence. Google Sign-In 자체는 표준적이나, Gemini API와의 OAuth 통합은 Vertex AI(유료) 경유가 필요하여 무료 티어에서는 API 키가 실질적 선택지.

---

### Feature 7: Plaud SDK BLE 연결 디버깅

**목표:** 기존 BLE 연결 코드의 실기기 디버깅 및 수정

**기존 코드 영향:**
- `PlaudSdkManager` -- SDK 초기화, 스캔, 연결, 파일 전송 로직 수정
- `AudioCollectionService` -- 서비스 생명주기 개선
- `AudioRepositoryImpl` -- 에러 핸들링 강화

새 컴포넌트 불필요. 기존 코드의 디버깅 및 수정 작업.

**실현 가능성:** MEDIUM confidence. Plaud SDK 자체의 문서와 실기기 테스트에 의존.

---

## 통합 아키텍처 설계

### 신규 컴포넌트 맵

```
[신규] data/samsung/
  SamsungRecorderObserver.kt         -- FileObserver/ContentObserver
  SamsungRecorderFileParser.kt       -- 전사 파일 파싱

[신규] data/notebooklm/
  NotebookLmMcpClient.kt             -- MCP HTTP 클라이언트
  NotebookLmRepositoryImpl.kt        -- Repository 구현체

[신규] data/auth/
  GoogleAuthManager.kt               -- Credential Manager 래퍼

[신규] domain/repository/
  NotebookLmRepository.kt            -- NotebookLM 인터페이스

[신규] domain/model/
  MeetingSource.kt                   -- 회의 소스 enum
  AuthState.kt                       -- 인증 상태

[신규] presentation/share/
  ShareReceiverActivity.kt           -- 공유 Intent 수신
  ShareReceiverViewModel.kt          -- 공유 데이터 처리

[신규] di/
  NotebookLmModule.kt                -- NotebookLM DI
  AuthModule.kt                      -- 인증 DI
```

### 수정 대상 컴포넌트

| 파일 | 변경 내용 | 영향도 |
|------|-----------|--------|
| `PipelineStatus.kt` | TRANSCRIPT_IMPORTED 추가 | 낮음 (enum 추가) |
| `Meeting.kt` | source 필드 추가 | 중간 (모델 변경) |
| `MeetingEntity.kt` | source 컬럼 + migration | 중간 (DB 스키마) |
| `MeetingDao.kt` | getMeetingByIdOnce() 추가 | 낮음 |
| `MeetingRepositoryImpl.kt` | 삭제 시 파일 정리 로직 | 낮음 |
| `GeminiEngine.kt` | 동적 API 키 주입 | 중간 (생성자 변경) |
| `UserPreferencesRepository.kt` | API 키, NotebookLM 설정, 인증 토큰 | 중간 |
| `SettingsScreen.kt` | API 키 입력, Google 로그인, NotebookLM 설정 | 높음 (UI 대폭 확장) |
| `SettingsViewModel.kt` | 신규 설정 상태 관리 | 높음 |
| `MinutesGenerationWorker.kt` | NotebookLM 업로드 단계 추가 (선택적) | 중간 |
| `AudioCollectionService.kt` | 삼성 녹음기 감시 모드 (선택적) | 중간 |
| `AndroidManifest.xml` | ShareReceiverActivity, 퍼미션 추가 | 낮음 |
| `AppNavigation.kt` | 변경 없음 (ShareReceiver는 별도 Activity) | 없음 |
| `RepositoryModule.kt` | NotebookLmRepository 바인딩 추가 | 낮음 |

### Room DB Migration 전략

v1.0 → v2.0에서 `MeetingEntity`에 `source` 컬럼이 추가된다.

```kotlin
// AppDatabase.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE meetings ADD COLUMN source TEXT NOT NULL DEFAULT 'PLAUD_BLE'"
        )
    }
}
```

기존 데이터는 모두 `PLAUD_BLE`로 기본값 설정. 비파괴적 마이그레이션.

---

## 전체 데이터 흐름 (v2.0)

```
입력 경로 3가지:

[경로 1: Plaud BLE] (기존)
  AudioCollectionService → PlaudSdkManager → AudioRepositoryImpl
    → MeetingEntity(AUDIO_RECEIVED, PLAUD_BLE)
    → TranscriptionTriggerWorker → WhisperEngine
    → MeetingEntity(TRANSCRIBED)
    → MinutesGenerationWorker → GeminiEngine
    → MeetingEntity(COMPLETED)

[경로 2: 삼성 녹음기 자동 감지] (신규)
  SamsungRecorderObserver (FileObserver/ContentObserver)
    → 전사 텍스트 파일 발견
    → SamsungRecorderFileParser → 텍스트 추출
    → MeetingEntity(TRANSCRIPT_IMPORTED, SAMSUNG_RECORDER)
    → MinutesGenerationWorker → GeminiEngine  (STT 단계 스킵)
    → MeetingEntity(COMPLETED)

[경로 3: 공유 Intent] (신규)
  삼성 녹음앱 → 공유 → ShareReceiverActivity
    → Intent.EXTRA_TEXT 추출
    → MeetingEntity(TRANSCRIPT_IMPORTED, SHARE_INTENT)
    → MinutesGenerationWorker → GeminiEngine  (STT 단계 스킵)
    → MeetingEntity(COMPLETED)

후속 처리 (선택적):
  COMPLETED → NotebookLmRepository.addSource()  (사용자 설정에 따라)
```

**핵심 설계 원칙:** 경로 2, 3은 이미 전사된 텍스트를 받으므로 STT 단계를 완전히 스킵한다. `TRANSCRIPT_IMPORTED` 상태에서 바로 `MinutesGenerationWorker`로 진입한다.

---

## Patterns to Follow

### Pattern 1: 다중 입력 경로 통합 (MeetingSource 패턴)
**What:** 모든 입력 경로(BLE, 파일감시, 공유)가 동일한 `MeetingEntity` 생성 → 동일한 파이프라인 후반부 실행
**When:** 새로운 입력 소스가 추가될 때마다
**Why:** 파이프라인 후반부(회의록 생성, 알림, UI 표시)를 중복 구현하지 않는다

```kotlin
// 모든 입력 경로의 공통 진입점
suspend fun createMeetingFromTranscript(
    transcriptText: String,
    source: MeetingSource,
    title: String = generateTitle()
): Long {
    val meetingId = meetingRepository.insertMeeting(
        Meeting(
            title = title,
            source = source,
            pipelineStatus = PipelineStatus.TRANSCRIPT_IMPORTED,
            // ...
        )
    )
    saveTranscriptFile(meetingId, transcriptText)
    enqueueMinutesGeneration(meetingId)
    return meetingId
}
```

### Pattern 2: 선택적 후처리 체이닝
**What:** 파이프라인 완료 후 NotebookLM 업로드 같은 부가 작업을 사용자 설정에 따라 선택적 실행
**When:** 외부 서비스 연동이 필수가 아닌 부가 기능일 때
**Why:** 외부 서비스 장애가 핵심 파이프라인을 중단시키지 않는다

```kotlin
// MinutesGenerationWorker 완료 후
if (userPreferences.notebookLmEnabled && notebookLmRepository.isConnected()) {
    try {
        notebookLmRepository.addSource(notebookId, minutesText)
    } catch (e: Exception) {
        // 실패해도 파이프라인은 COMPLETED 유지
        Log.w(TAG, "NotebookLM 업로드 실패 (무시): ${e.message}")
    }
}
```

### Pattern 3: API 키 동적 주입
**What:** BuildConfig 하드코딩 대신 DataStore에서 런타임에 읽기
**When:** 사용자가 직접 API 키를 관리해야 할 때
**Why:** 앱 재빌드 없이 API 키 변경 가능, 여러 키 전환 가능

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: 삼성 녹음기 연동에 과도한 투자
**What:** ContentObserver, Accessibility 등 여러 감지 방식을 모두 구현하여 "반드시" 자동 감지를 실현하려 함
**Why bad:** 삼성 앱 업데이트 시 모두 깨질 수 있으며, 유지보수 비용이 핵심 가치 대비 과도
**Instead:** 공유 Intent(Feature 2)를 안정적 기본 경로로 확보. 자동 감지는 "있으면 좋은 것"으로 범위를 제한.

### Anti-Pattern 2: NotebookLM을 필수 경로로 설정
**What:** NotebookLM 연결 실패 시 회의록 생성 파이프라인이 중단
**Why bad:** MCP 서버 가용성에 전체 앱이 종속
**Instead:** NotebookLM은 완전 선택적 후처리. Gemini API 직접 호출이 항상 기본.

### Anti-Pattern 3: Meeting 모델의 과도한 확장
**What:** source, notebookId, googleAuthToken 등 모든 신규 필드를 Meeting에 추가
**Why bad:** Meeting 모델이 비대해지고 단일 책임 원칙 위반
**Instead:** source만 Meeting에 추가. NotebookLM 관련은 별도 테이블/모델로 분리. 인증은 UserPreferences에서 관리.

---

## Component Boundaries (v2.0)

| Component | Responsibility | Communicates With | 신규/수정 |
|-----------|---------------|-------------------|-----------|
| **ShareReceiverActivity** | 외부 앱에서 공유된 텍스트 수신 | MeetingRepository, WorkManager | 신규 |
| **SamsungRecorderObserver** | 삼성 녹음기 전사 파일 감시 | FileSystem, MeetingRepository | 신규 |
| **NotebookLmMcpClient** | MCP 서버 HTTP 통신 | Retrofit/OkHttp | 신규 |
| **NotebookLmRepositoryImpl** | NotebookLM 소스 관리/노트 생성 | NotebookLmMcpClient | 신규 |
| **GoogleAuthManager** | Google Sign-In 관리 | Credential Manager | 신규 |
| **GeminiEngine** | 회의록 생성 (동적 API 키) | UserPreferencesRepository | 수정 |
| **MeetingRepositoryImpl** | 삭제 시 파일 정리 포함 | AudioFileManager, MeetingDao | 수정 |
| **SettingsScreen/VM** | 확장된 설정 UI | 다수의 Repository | 수정 |
| **AudioCollectionService** | 기존 BLE + 잠재적 파일 감시 | AudioRepository, SamsungRecorderObserver | 수정 |

---

## 권장 빌드 순서

의존성과 확실성 수준에 따라 정렬. 확실한 것부터 구현하여 조기 가치를 확보.

```
Phase 1: 파일 관리 + API 키 설정
  ├── 전사파일/회의록 삭제 (HIGH confidence, 기존 인프라)
  ├── Gemini API 키 설정 UI (HIGH confidence, DataStore + UI)
  └── Meeting 모델 확장 (source 필드, DB migration)

Phase 2: 공유 Intent 수신
  ├── ShareReceiverActivity (HIGH confidence, 표준 Android)
  ├── TRANSCRIPT_IMPORTED 파이프라인 경로 (기존 Worker 재활용)
  └── 삼성 녹음앱에서 공유 → 회의록 생성 E2E 동작

Phase 3: NotebookLM 연동
  ├── NotebookLmMcpClient + Repository (MEDIUM confidence)
  ├── 설정 UI (서버 URL, 노트북 선택)
  └── 파이프라인 후처리 체이닝 (선택적)

Phase 4: 삼성 녹음기 자동 감지 (리서치 선행)
  ├── 삼성 녹음 앱 저장 경로 조사
  ├── FileObserver/ContentObserver 구현 시도
  └── 불가 시 Phase 2 공유 방식이 기본 경로로 확정

Phase 5: Google 인증 + Gemini OAuth
  ├── Google Sign-In (Credential Manager)
  ├── NotebookLM 인증 연계
  └── Vertex AI OAuth 마이그레이션 (유료 전환 시점에)

Phase 6: Plaud BLE 디버깅
  ├── 실기기 연결 테스트
  ├── PlaudSdkManager 수정
  └── E2E: 녹음기 → BLE → STT → 회의록
```

**순서 근거:**
- Phase 1-2는 확실성이 높고 즉각적 가치를 제공 (삭제, API 키, 공유 수신)
- Phase 3은 외부 서비스 연동이므로 핵심 기능 이후에
- Phase 4는 리서치가 필요하여 결과에 따라 범위 축소 가능
- Phase 5는 무료 티어에서는 실질적 필요가 낮으므로 후순위
- Phase 6은 프로젝트에서 "마지막 수행" 명시

---

## Sources

- 기존 코드베이스 분석 (58개 Kotlin 파일) - HIGH confidence
- v1.0 ARCHITECTURE.md 연구 자료 - HIGH confidence
- Android ContentObserver 문서: https://developer.android.com/reference/android/database/ContentObserver - HIGH confidence
- Android Share Intent 문서: https://developer.android.com/training/sharing/receive - HIGH confidence
- Android Credential Manager: https://developer.android.com/identity/sign-in/credential-manager - HIGH confidence
- Google AI Client SDK (API 키 전용): https://ai.google.dev/gemini-api/docs/quickstart - MEDIUM confidence
- NotebookLM MCP 서버: 프로젝트 내 설정 확인 - MEDIUM confidence
- 삼성 녹음 앱 파일 저장 경로: 기기별 확인 필요 - LOW confidence
