# Coding Conventions

**Analysis Date:** 2026-03-26

## Naming Patterns

**Files:**
- Screen composables: `[Feature]Screen.kt` (예: `DashboardScreen.kt`, `TranscriptsScreen.kt`)
- ViewModels: `[Feature]ViewModel.kt` (예: `DashboardViewModel.kt`, `SettingsViewModel.kt`)
- Repository 인터페이스: `[Domain]Repository.kt` in `domain/repository/`
- Repository 구현체: `[Domain]RepositoryImpl.kt` in `data/repository/`
- Room Entity: `[Domain]Entity.kt` in `data/local/entity/`
- DAO: `[Domain]Dao.kt` in `data/local/dao/`
- DI 모듈: `[Scope]Module.kt` (예: `DatabaseModule.kt`, `RepositoryModule.kt`)
- Worker: `[Action]Worker.kt` (예: `TranscriptionTriggerWorker.kt`, `MinutesGenerationWorker.kt`)
- 엔진 인터페이스: `[Domain]Engine.kt` (예: `SttEngine.kt`, `MinutesEngine.kt`)
- 엔진 구현체: `[Provider]Engine.kt` (예: `GeminiEngine.kt`, `WhisperEngine.kt`, `MlKitEngine.kt`)

**Classes:**
- ViewModel: `@HiltViewModel class [Feature]ViewModel` 패턴 엄수
- Repository 구현체: `@Singleton class [Name]Impl @Inject constructor(...)` 패턴
- Worker: `@HiltWorker class [Name]Worker @AssistedInject constructor(...)` 패턴
- sealed interface (UI 상태): `sealed interface [Name]State` (예: `ApiKeyValidationState`)
- 유틸리티 object: `object [Name]Helper` (예: `PipelineNotificationHelper`, `NotebookLmHelper`)

**Functions:**
- 상태 변경 함수: `set[Property](value)` (예: `setMinutesFormat(format)`, `setAutomationMode(mode)`)
- 조회 함수: `get[Property]Once()` — suspend 일회성 조회에 `Once` 접미어 사용
- 검증 함수: `validateAnd[Action]` (예: `validateAndSaveApiKey`)
- 초기화 함수: `clear[Property]()`, `reset[Property]()`

**Variables/Properties:**
- 내부 MutableStateFlow: `_[camelCase]` (예: `_testStatus`, `_isTestingGemini`)
- 공개 StateFlow: `_[camelCase].asStateFlow()` 또는 `.stateIn(...)` 으로 노출
- 상수: `SCREAMING_SNAKE_CASE` in `companion object`
- 로그 태그: `private const val TAG = "[ClassName]"` in `companion object`

**Types:**
- 도메인 모델: `data class` (Room 어노테이션 없음) in `domain/model/`
- DB Entity: `data class [Name]Entity` with `@Entity` in `data/local/entity/`
- sealed interface UI 상태: `sealed interface [Name]State` with `data object`/`data class` variants

## Code Style

**Formatting:**
- KSP를 annotation processor로 사용 (KAPT 없음)
- Kotlin 2.3.20 기준 최신 언어 기능 활용
- 들여쓰기: 4 space (표준 Kotlin)
- 단일 표현식 함수 사용 권장 (예: `override fun engineName(): String = "Gemini 2.5 Flash"`)
- `trimMargin()` 또는 `trimIndent()`로 멀티라인 문자열 포맷

**공식 포매터/린터 설정 파일 없음** — `.eslintrc`, `.prettierrc`, `detekt.yml` 등 미감지.

## Import Organization

**패턴 (실제 파일 기준):**
1. `android.*` — Android SDK
2. `androidx.*` — AndroidX / Jetpack
3. `com.autominuting.*` — 내부 패키지
4. `dagger.*` / `javax.inject.*` — DI
5. `kotlinx.*` — Kotlin 확장 라이브러리
6. `java.*` — Java 표준 라이브러리

**Path Aliases:** 사용하지 않음. 전체 패키지 경로 사용.

## Error Handling

**핵심 패턴: `Result<T>` 반환**
- 모든 비동기 연산(STT, Gemini API, 전사 등)은 `Result<T>`를 반환
- 성공: `Result.success(value)`
- 실패: `Result.failure(exception)`
- 호출자: `result.isSuccess`, `result.getOrThrow()`, `result.exceptionOrNull()` 로 처리

예시 (`app/src/main/java/com/autominuting/data/stt/SttEngine.kt`):
```kotlin
suspend fun transcribe(audioFilePath: String): Result<String>
```

**try-catch 패턴:**
- Worker 내부: 모든 예외를 catch하여 `PipelineStatus.FAILED`로 상태 업데이트 후 `Result.failure()` 반환
- ViewModel 내부: `try-catch(e: Exception)` → `_stateFlow.value = ErrorState(e.message)`
- 파일 삭제: `try { File(path).delete() } catch (_: Exception) { }` — 실패 무시, 주석으로 이유 설명

**커스텀 예외:**
- `TranscriptionException(message, cause)` in `app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt`
- 도메인 특화 예외를 파일 하단에 같이 정의하는 패턴

**Fallback 패턴 (이중 경로):**
```kotlin
// 1차 엔진 시도 → 실패 시 2차 엔진으로 폴백
val primaryResult = primaryEngine.transcribe(path)
if (primaryResult.isSuccess) return primaryResult
val fallbackResult = fallbackEngine.transcribe(path)
```
`app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt` 참조.

## Logging

**Framework:** `android.util.Log`

**패턴:**
- 모든 클래스에 `companion object { private const val TAG = "ClassName" }` 정의
- Debug: `Log.d(TAG, "한국어 메시지: $variable")` — 정상 흐름 로깅
- Warning: `Log.w(TAG, "경고 메시지")` — 폴백 전환, 비치명적 실패
- Error: `Log.e(TAG, "오류 메시지", throwable)` — 치명적 실패, 예외 포함

예시 패턴:
```kotlin
Log.d(TAG, "전사 파이프라인 시작: meetingId=$meetingId, audioPath=$audioFilePath")
Log.w(TAG, "Whisper 전사 실패, ML Kit 폴백 전환: ${result.exceptionOrNull()?.message}")
Log.e(TAG, "전사 실패: $errorMessage", error)
```

## Comments

**KDoc 스타일:**
- 모든 `class`, `interface`, `object`에 KDoc 블록 주석 필수
- 모든 public/internal 함수에 KDoc 단문 주석 (`/** ... */`)
- `@property` 태그로 data class 프로퍼티 문서화 (예: `Meeting.kt`)
- `@param`, `@return` 태그 사용
- 인라인 주석: `// 한국어로 단계 설명` (절차적 로직에서 번호 매김)

**Design Decision 주석:**
- 아키텍처 결정사항은 주석으로 명시 (예: `// per D-08`, `// Phase 8 D-09 결정에 따라 도입`)
- 파이프라인 단계 설명: 번호 매기기 패턴 (`// 1. 조회`, `// 2. 파일 삭제`, `// 3. DB 업데이트`)

**주석 원칙:**
- "고아 파일 > 고아 레코드" 같은 설계 원칙을 인라인 주석으로 명시
- 실패를 무시하는 catch 블록에 반드시 이유 주석 추가

## Function Design

**Size:** 단일 책임. 각 함수는 파이프라인의 한 단계를 담당.

**Parameters:** `@Assisted` 주입 패턴으로 Worker 파라미터 전달. ViewModel 함수는 필요한 값만 파라미터로 받음.

**Return Values:**
- suspend 함수: `Result<T>` (비즈니스 로직) 또는 `Unit` (상태 업데이트)
- Flow 반환: 상태 관찰용 (DAO → Repository → ViewModel)
- 파일 저장 함수: 저장된 파일 경로(`String`) 반환

## Module Design

**Hilt DI 모듈 패턴:**
- 인터페이스-구현체 바인딩: `abstract class [Name]Module` + `@Binds`
- 외부 라이브러리/시스템 객체 제공: `object [Name]Module` + `@Provides`

예시:
- `app/src/main/java/com/autominuting/di/RepositoryModule.kt` — `@Binds` 패턴
- `app/src/main/java/com/autominuting/di/DatabaseModule.kt` — `@Provides` 패턴

**Barrel Files:** 사용하지 않음. 각 파일을 직접 임포트.

**Singleton 스코프:** `@Singleton` + `@InstallIn(SingletonComponent::class)` 조합 일관 사용.

## State Management (ViewModel)

**StateFlow 노출 패턴:**
```kotlin
// 쓰기: private MutableStateFlow
private val _hasApiKey = MutableStateFlow(false)
// 읽기: public StateFlow (asStateFlow 또는 stateIn)
val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

// Repository Flow → ViewModel StateFlow 변환
val meetings: StateFlow<List<Meeting>> = repository.getMeetings()
    .map { it.filter { ... } }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

**UI 이벤트 처리:** `viewModelScope.launch { ... }` 블록 내에서 수행.

## Entity-Domain Mapping

**패턴:** Entity에 `toDomain()` 인스턴스 함수 + `companion object { fun fromDomain() }` 정적 팩토리
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` 참조

**Enum 직렬화:** enum을 `String` (`.name`)으로 저장, 조회 시 `EnumClass.valueOf(str)` 복원.

**Timestamp 직렬화:** `Instant`를 `Long` (epoch millis)으로 저장, `Instant.ofEpochMilli()`로 복원.

---

*Convention analysis: 2026-03-26*
