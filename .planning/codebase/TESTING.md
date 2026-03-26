# Testing Patterns

**Analysis Date:** 2026-03-26

## Test Framework

**현재 상태: 자동화 테스트 없음**

`app/src/test/` 및 `app/src/androidTest/` 디렉토리가 존재하지 않는다. `app/build.gradle.kts`에 테스트 의존성(JUnit, MockK, Turbine 등)이 선언되지 않았다.

**Runner:** 선언 없음

**Assertion Library:** 선언 없음

**Run Commands:**
```bash
# 테스트 인프라 미구축 — 실행 불가
./gradlew test          # 단위 테스트 없음
./gradlew connectedCheck # 인스트루먼트 테스트 없음
```

## Test File Organization

테스트 파일 없음. 표준 Android 프로젝트 구조에 따라 추가 시:

```
app/src/
├── test/java/com/autominuting/     # JVM 단위 테스트
└── androidTest/java/com/autominuting/  # 인스트루먼트 테스트
```

## 수동 테스트 인프라 (현재 존재)

자동화 테스트 대신 앱 내에 수동 검증 도구가 구현되어 있다.

**DashboardViewModel의 테스트 헬퍼:**
- `insertTestData()` — 더미 회의록 3건을 DB에 직접 삽입
- `testGeminiApi()` — 샘플 전사 텍스트로 Gemini API 실제 호출 테스트
- 위치: `app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt`

**TestWorker:**
- WorkManager 초기화 검증용 1초 대기 Worker
- 위치: `app/src/main/java/com/autominuting/worker/TestWorker.kt`

## 권장 테스트 구조 (미래 작성 시)

아래는 프로젝트 CLAUDE.md 권장 스택 및 현재 코드 패턴 기반 작성 가이드다.

### 권장 테스트 의존성 (`app/build.gradle.kts`에 추가)

```kotlin
// 단위 테스트
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
testImplementation("io.mockk:mockk:1.13+")
testImplementation("app.cash.turbine:turbine:1.2+")

// 인스트루먼트 테스트
androidTestImplementation("androidx.test.ext:junit:1.2+")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.room:room-testing:2.8.4")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")
```

### ViewModel 테스트 패턴

현재 ViewModel 구조 기반 권장 패턴:

```kotlin
// DashboardViewModelTest.kt
@ExtendWith(MockKExtension::class)
class DashboardViewModelTest {

    @MockK
    lateinit var meetingRepository: MeetingRepository

    @MockK
    lateinit var minutesRepository: MinutesRepository

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        // Dispatchers.Main을 TestCoroutineDispatcher로 교체
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = DashboardViewModel(meetingRepository, minutesRepository, mockk())
    }

    @Test
    fun `activePipeline - AUDIO_RECEIVED 상태 회의 반환`() = runTest {
        // given
        val meeting = Meeting(
            id = 1L,
            title = "테스트 회의",
            recordedAt = Instant.now(),
            audioFilePath = "/test/audio.wav",
            pipelineStatus = PipelineStatus.AUDIO_RECEIVED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        every { meetingRepository.getMeetings() } returns flowOf(listOf(meeting))

        // when / then
        viewModel.activePipeline.test {
            assertThat(awaitItem()).isEqualTo(meeting)
        }
    }
}
```

### Repository 테스트 패턴

`Result<T>` 반환 패턴 기반:

```kotlin
// TranscriptionRepositoryImplTest.kt
class TranscriptionRepositoryImplTest {

    @MockK
    lateinit var whisperEngine: WhisperEngine

    @MockK
    lateinit var mlKitEngine: MlKitEngine

    private lateinit var repository: TranscriptionRepositoryImpl

    @Test
    fun `transcribe - Whisper 성공 시 ML Kit 미호출`() = runTest {
        // given
        coEvery { whisperEngine.transcribe(any()) } returns Result.success("전사 결과")

        // when
        val result = repository.transcribe("/test/audio.wav")

        // then
        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 0) { mlKitEngine.transcribe(any()) }
    }

    @Test
    fun `transcribe - Whisper 실패 시 ML Kit 폴백`() = runTest {
        // given
        coEvery { whisperEngine.transcribe(any()) } returns Result.failure(Exception("Whisper 실패"))
        coEvery { mlKitEngine.transcribe(any()) } returns Result.success("ML Kit 결과")

        // when
        val result = repository.transcribe("/test/audio.wav")

        // then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo("ML Kit 결과")
    }
}
```

### Room DAO 테스트 패턴

```kotlin
// MeetingDaoTest.kt (androidTest)
@RunWith(AndroidJUnit4::class)
class MeetingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var meetingDao: MeetingDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        meetingDao = database.meetingDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetMeeting() = runTest {
        val entity = MeetingEntity(
            title = "테스트 회의",
            recordedAt = System.currentTimeMillis(),
            audioFilePath = "/test/audio.wav",
            pipelineStatus = "AUDIO_RECEIVED",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = meetingDao.insert(entity)
        assertThat(id).isGreaterThan(0)
    }
}
```

### Compose UI 테스트 패턴

```kotlin
// DashboardScreenTest.kt (androidTest)
@HiltAndroidTest
class DashboardScreenTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboardScreen_showsTitle() {
        composeTestRule.setContent {
            AutoMinutingTheme {
                DashboardScreen()
            }
        }
        composeTestRule.onNodeWithText("Auto Minuting").assertIsDisplayed()
    }
}
```

## Mocking 가이드

**MockK 사용 (권장):**

```kotlin
// Repository 목킹
@MockK
lateinit var meetingRepository: MeetingRepository

// Flow 반환 목킹
every { meetingRepository.getMeetings() } returns flowOf(emptyList())

// suspend 함수 목킹
coEvery { meetingRepository.insertMeeting(any()) } returns 1L
coVerify { meetingRepository.updatePipelineStatus(1L, PipelineStatus.FAILED, any()) }
```

**목킹 대상:**
- Repository 인터페이스 (`MeetingRepository`, `MinutesRepository` 등)
- STT/Minutes 엔진 인터페이스 (`SttEngine`, `MinutesEngine`)
- `SecureApiKeyRepository` (암호화 저장소)

**목킹 제외 (실제 구현 사용):**
- Room 인메모리 DB (`Room.inMemoryDatabaseBuilder`)
- `DataStore` (테스트용 `DataStore<Preferences>` 제공)
- 도메인 모델 (`Meeting`, `PipelineStatus` 등 순수 data class)

## Coverage

**요구사항:** 미설정 (테스트 인프라 없음)

**추가 시 권장 목표:**
- Repository 레이어: 80%+ (폴백 경로 포함)
- ViewModel: 70%+ (StateFlow 상태 전환 포함)
- DAO: 90%+ (Room 인메모리 DB로 실제 SQL 검증)

## 테스트 우선순위 (신규 테스트 작성 시)

1. **`TranscriptionRepositoryImpl`** — Whisper/ML Kit 이중 폴백 로직 (`app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt`)
2. **`MeetingRepositoryImpl`** — 파일 삭제 + DB 삭제 순서 (`app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt`)
3. **`TranscriptionTriggerWorker`** — Worker Result + 상태 전환 (`app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt`)
4. **`MeetingDao`** — Room DAO CRUD + 검색 쿼리 (`app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt`)
5. **`GeminiEngine`** — API 키 미설정 실패 경로 (`app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt`)

---

*Testing analysis: 2026-03-26*
