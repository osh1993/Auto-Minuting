# Architecture

**Analysis Date:** 2026-03-26

## Pattern Overview

**Overall:** Clean Architecture (3-tier: Presentation → Domain → Data)

**Key Characteristics:**
- Domain 레이어는 Android 의존성 없는 순수 Kotlin 인터페이스와 모델로 구성
- Data 레이어가 Domain 인터페이스를 구현하며, Hilt `@Binds`로 DI 역전(IoC)
- Presentation 레이어는 ViewModel + StateFlow + Jetpack Compose 로 구성
- 파이프라인 처리(전사, 회의록 생성)는 WorkManager CoroutineWorker로 백그라운드에서 수행
- 외부 공유 진입점은 별도 Activity(`ShareReceiverActivity`)로 분리되어 UI 없이 투명 동작

## Layers

**Presentation Layer:**
- Purpose: UI 렌더링 및 사용자 이벤트 처리
- Location: `app/src/main/java/com/autominuting/presentation/`
- Contains: Composable 화면, ViewModel, Navigation
- Depends on: Domain 레이어의 Repository 인터페이스, 도메인 모델
- Used by: 사용자, Android 시스템

**Domain Layer:**
- Purpose: 비즈니스 로직 및 계약 정의 (Android 독립적)
- Location: `app/src/main/java/com/autominuting/domain/`
- Contains: Repository 인터페이스, 도메인 모델(data class, enum)
- Depends on: 순수 Kotlin stdlib, kotlinx.coroutines (Flow)
- Used by: Presentation Layer, Worker Layer

**Data Layer:**
- Purpose: 영속성, 외부 API, 파일 I/O 등 실제 구현
- Location: `app/src/main/java/com/autominuting/data/`
- Contains: RepositoryImpl, Room Entity/DAO, SttEngine 구현체, MinutesEngine 구현체, DataStore, 보안 저장소
- Depends on: Android SDK, Room, Retrofit, DataStore, EncryptedSharedPreferences
- Used by: Domain 인터페이스를 통해 Presentation/Worker에서 사용

**Worker Layer:**
- Purpose: 백그라운드 파이프라인 실행 (비동기 체이닝)
- Location: `app/src/main/java/com/autominuting/worker/`
- Contains: `TranscriptionTriggerWorker`, `MinutesGenerationWorker`, `TestWorker`
- Depends on: Domain Repository 인터페이스, Data Layer DAO (직접 접근)
- Used by: `ShareReceiverActivity`, `PipelineActionReceiver`

**DI Layer:**
- Purpose: Hilt 모듈로 의존성 그래프 구성
- Location: `app/src/main/java/com/autominuting/di/`
- Contains: `AppModule`, `AuthModule`, `DatabaseModule`, `DataStoreModule`, `RepositoryModule`, `SttModule`, `WorkerModule`
- Depends on: Data Layer 구현체
- Used by: 전체 앱 빌드타임

## Data Flow

**파이프라인 흐름 1 — 음성 파일 공유 수신:**

1. 삼성 녹음앱이 `Intent.ACTION_SEND`(audio/*)를 통해 `ShareReceiverActivity`에 전달
2. `ShareReceiverActivity.processSharedAudio()` 가 ContentResolver로 내부 저장소(`filesDir/audio/`)에 파일 복사
3. `MeetingRepository.insertMeeting()` 호출 → Room DB에 `AUDIO_RECEIVED` 상태로 삽입
4. `WorkManager`가 `TranscriptionTriggerWorker` enqueue
5. Worker가 `TranscriptionRepository.transcribe()` 호출 → Whisper(1차) → ML Kit(2차) 폴백
6. 전사 성공 시 텍스트를 `filesDir/transcripts/{id}.txt`에 저장, DB 상태를 `TRANSCRIBED`로 갱신
7. `AutomationMode.FULL_AUTO`인 경우 `MinutesGenerationWorker` 자동 체이닝
8. `MinutesGenerationWorker`가 `MinutesRepository.generateMinutes()` 호출 → Gemini API(API 키 또는 OAuth)
9. 회의록을 `filesDir/minutes/{id}.md`에 저장, DB 상태를 `COMPLETED`로 갱신
10. `PipelineNotificationHelper.notifyComplete()` 로 완료 알림 표시

**파이프라인 흐름 2 — 텍스트(전사 결과) 직접 공유:**

1. 삼성 녹음앱이 전사 txt 파일(UTF-16 LE)을 `ACTION_SEND`로 공유
2. `ShareReceiverActivity.processSharedText()` 가 BOM 감지 인코딩 변환 후 텍스트 추출
3. 텍스트를 `filesDir/transcripts/share_{ts}.txt`에 저장
4. `Meeting` 을 `TRANSCRIBED` 상태로 DB에 삽입 (STT 단계 건너뜀)
5. `MinutesGenerationWorker`만 직접 enqueue

**하이브리드 모드 분기:**

- `AutomationMode.HYBRID` 일 때 TranscriptionWorker 완료 후 WorkManager를 체이닝하지 않고 `PipelineNotificationHelper.notifyTranscriptionComplete()` 로 알림 표시
- 사용자가 알림의 "회의록 생성 시작" 버튼을 누르면 `PipelineActionReceiver`가 `MinutesGenerationWorker`를 enqueue

**State Management:**
- ViewModel은 `StateFlow`를 사용하고 `SharingStarted.WhileSubscribed(5_000)` 로 수명주기에 맞게 구독 관리
- Compose UI는 `collectAsStateWithLifecycle()` 로 StateFlow 구독
- `MutableStateFlow` 는 ViewModel 내부에서만 직접 쓰기, 외부에는 `asStateFlow()` 로 노출

## Key Abstractions

**Repository 인터페이스:**
- Purpose: Domain-Data 경계 계약
- Examples: `app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt`, `TranscriptionRepository.kt`, `MinutesRepository.kt`, `AudioRepository.kt`, `PromptTemplateRepository.kt`
- Pattern: `interface` + `@Binds` Hilt 모듈로 구현체 교체 가능

**SttEngine 인터페이스:**
- Purpose: STT 엔진 구현체 추상화 (Whisper, ML Kit 등)
- Examples: `app/src/main/java/com/autominuting/data/stt/SttEngine.kt`
- Pattern: `suspend fun transcribe()`, `suspend fun isAvailable()`, `fun engineName()` 계약

**MinutesEngine 인터페이스:**
- Purpose: 회의록 생성 엔진 추상화 (API 키 모드, OAuth 모드)
- Examples: `app/src/main/java/com/autominuting/data/minutes/MinutesEngine.kt`
- Pattern: `MinutesEngineSelector`가 DataStore authMode를 보고 `GeminiEngine` 또는 `GeminiOAuthEngine` 동적 선택

**Entity ↔ Domain 매핑:**
- Purpose: Room Entity와 도메인 모델 분리
- Examples: `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt`
- Pattern: Entity 내부에 `toDomain()` / companion `fromDomain()` 함수

**PipelineStatus enum:**
- Purpose: 파이프라인 전 단계 상태 추적
- Location: `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt`
- Values: `AUDIO_RECEIVED` → `TRANSCRIBING` → `TRANSCRIBED` → `GENERATING_MINUTES` → `COMPLETED` / `FAILED`

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/autominuting/MainActivity.kt`
- Triggers: 앱 런처 아이콘, 일반 앱 시작
- Responsibilities: `AutoMinutingTheme` + `AppNavigation()` 설정, Hilt 주입

**ShareReceiverActivity:**
- Location: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
- Triggers: 외부 앱(삼성 녹음앱 등)의 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` 인텐트
- Responsibilities: 공유 콘텐츠 수신(음성 파일 또는 텍스트), DB 삽입, WorkManager enqueue, UI 없이 투명 동작

**AutoMinutingApplication:**
- Location: `app/src/main/java/com/autominuting/AutoMinutingApplication.kt`
- Triggers: 앱 프로세스 시작
- Responsibilities: Hilt 그래프 초기화(`@HiltAndroidApp`), `HiltWorkerFactory` 설정, 알림 채널 등록

**PipelineActionReceiver:**
- Location: `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt`
- Triggers: 알림 액션 버튼 탭 (`GENERATE_MINUTES`, `SHARE_MINUTES` BroadcastIntent)
- Responsibilities: 하이브리드 모드에서 사용자 확인 후 `MinutesGenerationWorker` enqueue, 완료 알림에서 공유 Intent 실행

## Error Handling

**Strategy:** Kotlin `Result<T>` 반환 패턴 + Worker `Result.failure()` 체계

**Patterns:**
- Repository 및 Engine 계층은 `Result<T>` 반환: `Result.success(value)` / `Result.failure(exception)`
- Worker는 성공 시 `Result.success()`, 실패 시 DB 상태를 `FAILED`로 업데이트 후 `Result.failure()` 반환
- 파일 삭제 등 부가적 작업은 `try { } catch (_: Exception) { }` 로 조용히 무시 ("고아 파일 > 고아 레코드" 원칙)
- EncryptedSharedPreferences 초기화 실패는 `null` 반환으로 방어 처리 (OEM 기기 호환)

## Cross-Cutting Concerns

**Logging:** `android.util.Log` 직접 사용. 클래스별 `TAG` companion object 상수 정의. Worker/Repository는 D(debug), W(warn), E(error) 레벨 구분.

**Validation:** 오디오 파일 유효성은 `AudioFileManager.validateAudioFile()` 에서 수행. 공유 텍스트 길이는 `ShareReceiverActivity`에서 인라인 검증 (`< 10자` 거부).

**Authentication:** `UserPreferencesRepository.authMode` Flow → `MinutesEngineSelector`가 런타임에 GeminiEngine(API 키) 또는 GeminiOAuthEngine(OAuth) 선택. API 키는 `SecureApiKeyRepository`(EncryptedSharedPreferences AES256-GCM)에 저장.

**File Storage:** 앱 내부 저장소만 사용. 오디오: `filesDir/audio/`, 전사: `filesDir/transcripts/{id}.txt`, 회의록: `filesDir/minutes/{id}.md`

---

*Architecture analysis: 2026-03-26*
