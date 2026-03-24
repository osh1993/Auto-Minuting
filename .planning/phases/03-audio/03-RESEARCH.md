# Phase 3: 오디오 수집 - Research

**Researched:** 2026-03-24
**Domain:** Android BLE 통신 (Plaud SDK), Foreground Service, WorkManager 파이프라인
**Confidence:** MEDIUM

## Summary

Phase 3은 Plaud 녹음기에서 BLE를 통해 오디오 파일을 다운로드하고, 앱 내부 저장소에 저장한 후, WorkManager로 전사 파이프라인을 트리거하는 단계이다. 핵심 기술은 Plaud SDK v0.2.8 (`NiceBuildSdk` API)을 이용한 BLE 연결/파일 다운로드이며, Foreground Service(`connectedDevice` 타입)로 백그라운드 동작을 보장한다.

Plaud SDK의 공식 문서(docs.plaud.ai)에서 확인한 결과, SDK 초기화에는 `appKey`와 `appSecret` 두 가지 자격 증명이 필요하며, 파일 다운로드 API는 `NiceBuildSdk.exportAudio()`(BLE)와 `NiceBuildSdk.exportAudioViaWiFi()`(Wi-Fi) 두 경로를 제공한다. MP3/WAV 포맷을 지원하며, `AudioExporter.ExportCallback`으로 진행률과 완료를 콜백 받는다. Guava 28.2 의존성 충돌이 예상되므로 Gradle `exclude` 또는 `force` 전략이 필요하다.

Android 14+(타깃 SDK 36)에서 Foreground Service에는 `foregroundServiceType="connectedDevice"` 선언이 필수이며, `FOREGROUND_SERVICE_CONNECTED_DEVICE` 퍼미션과 `BLUETOOTH_CONNECT` 런타임 퍼미션이 요구된다. 삼성 기기에서는 One UI 6.0 이후 Android 14 foreground service 정책을 준수하는 앱은 정상 동작이 보장된다고 공식 발표되었으나, 배터리 최적화 설정 안내 UI를 제공하는 것이 권장된다.

**Primary recommendation:** Plaud SDK `NiceBuildSdk.exportAudio()`를 Foreground Service 내에서 호출하고, 완료 콜백에서 MeetingEntity를 `AUDIO_RECEIVED` 상태로 저장 후 WorkManager로 전사 Worker를 enqueue하라.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Plaud SDK v0.2.8 (MIT 라이선스)을 1차 오디오 수집 경로로 채택 -- appKey 발급 필요
- **D-02:** Cloud API (비공식, JWT 인증)를 2차 폴백 경로로 유지
- **D-03:** FileObserver 경로는 Scoped Storage 제한으로 폐기 확정
- **D-04:** 온디바이스 처리 우선 원칙 유지
- **D-05:** `AudioRepository` 인터페이스가 domain 레이어에 정의됨 -- 이 인터페이스의 구현체를 data 레이어에 작성
- **D-06:** `MeetingEntity` + `MeetingDao` + Room DB가 존재 -- 수집된 오디오 메타데이터를 여기에 저장
- **D-07:** `PipelineStatus.AUDIO_RECEIVED` 상태가 이미 정의됨 -- 파일 저장 완료 시 이 상태로 전환
- **D-08:** Hilt DI 그래프 구성됨 -- 새 모듈은 기존 패턴(DatabaseModule, RepositoryModule) 따름
- **D-09:** WorkManager 초기화 완료 -- 파이프라인 트리거에 활용 가능

### Claude's Discretion
- Plaud SDK 연동: BLE 연결 흐름, 파일 다운로드 API 사용법, appKey 처리
- Foreground Service 설계: 알림 디자인, foregroundServiceType, 배터리 최적화 대응
- 파이프라인 트리거: 파일 저장 완료 시 WorkManager로 전사 단계 트리거하는 방식
- 오류 처리: BLE 연결 실패, 파일 전송 중단, 저장 공간 부족 등 예외 처리
- 삼성 기기 특화: Samsung 배터리 최적화 우회, Foreground Service 킬 방지

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUD-01 | Plaud 녹음기에서 전송되는 음성 파일을 감지하여 로컬 저장소에 저장 | Plaud SDK `NiceBuildSdk.exportAudio()` API로 BLE 파일 다운로드, `context.filesDir` 하위에 저장 |
| AUD-02 | 음성 파일 저장이 Foreground Service로 백그라운드에서 자동 처리 | `foregroundServiceType="connectedDevice"` Foreground Service + BLE 연결 유지 |
| AUD-03 | 새로운 오디오 파일 감지 시 파이프라인이 자동으로 시작 | `ExportCallback.onComplete()`에서 WorkManager `OneTimeWorkRequest` enqueue |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **응답 언어:** 한국어
- **코드 주석:** 한국어
- **커밋 메시지:** 한국어
- **문서화:** 한국어
- **변수명/함수명:** 영어

## Standard Stack

### Core (Phase 3 신규 추가)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Plaud SDK | v0.2.8 (AAR) | BLE 연결 + 오디오 파일 다운로드 | D-01 Locked. 공식 SDK, BLE 통신 추상화 |
| Guava | 28.2-android | Plaud SDK 필수 의존성 | SDK 내부 의존성, exclude 충돌 관리 필요 |

### Supporting (기존 스택 활용)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| WorkManager | 2.11.1 | 파이프라인 트리거 | 오디오 저장 완료 시 전사 Worker enqueue |
| Room | 2.8.4 | 메타데이터 저장 | MeetingEntity에 오디오 파일 경로/상태 기록 |
| Hilt | 2.56 | DI | 새 Repository/Service의 의존성 주입 |
| Coroutines | 1.10.1 | 비동기 처리 | Flow 기반 상태 관리 |
| Retrofit | 2.9.0+ | Cloud API 폴백 | JWT 인증 Cloud API 호출 (2차 경로) |
| OkHttp | 4.10.0+ | HTTP 클라이언트 | Cloud API 네트워크 통신 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Plaud SDK (BLE) | Cloud API (JWT) | 네트워크 의존, JWT 수동 추출 필요. 폴백으로 유지 |
| Plaud SDK (BLE) | BLE 역공학 (직접 GATT) | 유지보수 극대화, SDK 대비 이점 없음. 3차 보류 |
| Foreground Service | WorkManager Long-running | BLE 연결 유지에 Foreground Service가 더 적합 |

**Installation (build.gradle.kts 추가 사항):**
```kotlin
// Plaud SDK AAR
implementation(files("libs/plaud-sdk.aar"))

// Plaud SDK 필수 의존성
implementation("com.google.guava:guava:28.2-android")

// Retrofit (Cloud API 폴백용)
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.10.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
```

**Guava 충돌 해결:**
```kotlin
// libs.versions.toml에 guava 추가 불필요 -- SDK AAR이 28.2 요구
// 프로젝트에서 다른 Guava 버전 사용 시:
configurations.all {
    resolutionStrategy.force("com.google.guava:guava:28.2-android")
}
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/autominuting/
├── domain/
│   └── repository/
│       └── AudioRepository.kt          # 기존 인터페이스 (변경 없음)
├── data/
│   ├── repository/
│   │   └── AudioRepositoryImpl.kt      # 신규: SDK/Cloud API 연동
│   ├── audio/
│   │   ├── PlaudSdkManager.kt          # 신규: SDK 초기화/BLE 관리
│   │   ├── PlaudCloudApiService.kt     # 신규: Cloud API 폴백 (Retrofit)
│   │   └── AudioFileManager.kt         # 신규: 파일 저장/검증
│   └── local/
│       ├── entity/MeetingEntity.kt     # 기존 (변경 없음)
│       └── dao/MeetingDao.kt           # 기존 (변경 없음)
├── service/
│   └── AudioCollectionService.kt       # 신규: Foreground Service
├── worker/
│   └── TranscriptionTriggerWorker.kt   # 신규: 파이프라인 트리거 Worker
└── di/
    ├── AudioModule.kt                  # 신규: Plaud SDK DI 바인딩
    └── RepositoryModule.kt             # 기존: AudioRepository 바인딩 추가
```

### Pattern 1: Foreground Service + BLE 연결

**What:** `connectedDevice` 타입 Foreground Service 안에서 Plaud SDK BLE 스캔/연결/다운로드를 수행
**When to use:** 사용자가 오디오 수집을 시작할 때
**Example:**
```kotlin
// Source: https://developer.android.com/develop/background-work/services/fgs/service-types
// + https://docs.plaud.ai/api_guide/sdks/android

class AudioCollectionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground Service 시작 (connectedDevice 타입)
        val notification = createNotification("오디오 수집 대기 중...")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        // Plaud SDK BLE 스캔 시작
        val bleAgent = TntAgent.getInstant().bleAgent
        bleAgent.scanBle(true) { errorCode ->
            handleScanError(errorCode)
        }

        return START_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "audio_collection_channel"
    }
}
```

### Pattern 2: SDK 콜백 -> Room + WorkManager 파이프라인

**What:** 파일 다운로드 완료 콜백에서 DB 저장 + 전사 Worker enqueue
**When to use:** `ExportCallback.onComplete()` 시점
**Example:**
```kotlin
// Source: https://docs.plaud.ai/api_guide/sdks/android

NiceBuildSdk.exportAudio(
    sessionId = sessionId,
    outputDir = audioDir,
    format = AudioExportFormat.WAV,
    channels = 1,
    callback = object : AudioExporter.ExportCallback {
        override fun onProgress(progress: Float) {
            updateNotification("다운로드 중... ${(progress * 100).toInt()}%")
        }

        override fun onComplete(filePath: String) {
            scope.launch {
                // 1. MeetingEntity 생성 + DB 저장
                val meetingId = meetingDao.insert(
                    MeetingEntity(
                        title = "회의 ${formatDate(now)}",
                        recordedAt = now.toEpochMilli(),
                        audioFilePath = filePath,
                        pipelineStatus = PipelineStatus.AUDIO_RECEIVED.name,
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli()
                    )
                )

                // 2. WorkManager로 전사 Worker enqueue
                val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
                    .setInputData(workDataOf("meetingId" to meetingId))
                    .build()
                workManager.enqueue(workRequest)
            }
        }

        override fun onError(error: String) {
            handleExportError(error)
        }
    }
)
```

### Pattern 3: AudioRepositoryImpl - SDK + Cloud API 이중 경로

**What:** AudioRepository 구현체가 SDK(1차)와 Cloud API(2차)를 모두 지원
**When to use:** SDK 연결 실패 시 자동 폴백
**Example:**
```kotlin
class AudioRepositoryImpl @Inject constructor(
    private val plaudSdkManager: PlaudSdkManager,
    private val cloudApiService: PlaudCloudApiService,
    private val audioFileManager: AudioFileManager,
    private val meetingDao: MeetingDao,
    private val workManager: WorkManager
) : AudioRepository {

    private val _isCollecting = MutableStateFlow(false)

    override suspend fun startAudioCollection(): Flow<String> = channelFlow {
        _isCollecting.value = true
        try {
            // 1차: SDK BLE 경로
            plaudSdkManager.connectAndDownload { filePath ->
                send(filePath)
            }
        } catch (e: PlaudSdkException) {
            // 2차: Cloud API 폴백
            cloudApiService.downloadLatestRecordings().collect { filePath ->
                send(filePath)
            }
        }
    }

    override suspend fun stopAudioCollection() {
        _isCollecting.value = false
        plaudSdkManager.disconnect()
    }

    override fun isCollecting(): Flow<Boolean> = _isCollecting.asStateFlow()
}
```

### Anti-Patterns to Avoid
- **FileObserver 사용 금지:** Scoped Storage(API 30+)로 타 앱 파일 감시 불가. D-03에서 폐기 확정.
- **Service 내 직접 DB 접근:** Repository 패턴을 우회하지 말 것. Clean Architecture 계층 준수.
- **BLE 연결을 UI 스레드에서 수행:** 반드시 코루틴 또는 별도 스레드에서 수행.
- **하드코딩된 appKey:** BuildConfig 또는 local.properties에서 읽되, 절대 소스코드에 직접 기입 금지.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BLE 통신 프로토콜 | GATT 직접 구현 | Plaud SDK `NiceBuildSdk` | SDK가 GATT 통신, 파일 전송, 체크섬을 추상화 |
| Foreground Service 알림 | 커스텀 알림 시스템 | `NotificationCompat.Builder` | AndroidX 호환성 라이브러리가 API 레벨별 차이 처리 |
| 백그라운드 작업 스케줄링 | AlarmManager + BroadcastReceiver | WorkManager | 배터리 최적화, 재시도, 제약조건 관리 내장 |
| HTTP 클라이언트 | HttpURLConnection | Retrofit + OkHttp | 타입 안전 API, 인터셉터, 로깅 지원 |
| BLE 퍼미션 관리 | 수동 권한 체크 | ActivityResultContracts | 콜백 지옥 방지, Compose 통합 |

**Key insight:** Plaud SDK가 BLE 통신의 복잡성(GATT 서비스 디스커버리, 파일 청크 전송, 인증)을 완전히 추상화하므로, BLE 저수준 코드를 작성할 필요가 전혀 없다.

## Common Pitfalls

### Pitfall 1: Guava 버전 충돌
**What goes wrong:** Plaud SDK가 Guava 28.2에 의존하나, 프로젝트 다른 의존성이 최신 Guava(33.x)를 transitively 포함할 수 있음
**Why it happens:** AAR의 transitive dependency와 프로젝트 의존성 간 버전 불일치
**How to avoid:** `resolutionStrategy.force()` 또는 `exclude group: "com.google.guava"`로 버전 통일
**Warning signs:** `DuplicateClassException`, `NoSuchMethodError` 런타임 크래시

### Pitfall 2: Android 14+ Foreground Service 타입 누락
**What goes wrong:** `foregroundServiceType` 미선언 시 `MissingForegroundServiceTypeException` 발생
**Why it happens:** Android 14부터 모든 Foreground Service에 타입 명시 필수
**How to avoid:** AndroidManifest.xml에 `android:foregroundServiceType="connectedDevice"` 선언 + `FOREGROUND_SERVICE_CONNECTED_DEVICE` 퍼미션 추가
**Warning signs:** API 34+ 기기에서 서비스 시작 시 즉시 크래시

### Pitfall 3: BLE 퍼미션 미요청
**What goes wrong:** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` 런타임 퍼미션 없이 BLE 사용 시 `SecurityException`
**Why it happens:** Android 12(API 31)부터 BLE 퍼미션이 런타임 요청 필수로 변경
**How to avoid:** Activity에서 `ActivityResultContracts.RequestMultiplePermissions()`로 사전 요청
**Warning signs:** `SecurityException: Need BLUETOOTH_CONNECT permission`

### Pitfall 4: Plaud SDK appKey/appSecret 미설정
**What goes wrong:** SDK 초기화 실패, BLE 스캔 불가
**Why it happens:** appKey 발급이 사전 조건이나 발급 지연/거절 가능
**How to avoid:** appKey를 `local.properties`에서 읽는 BuildConfig 필드로 설정. 미설정 시 Cloud API 폴백 자동 전환
**Warning signs:** SDK 초기화 콜백에서 에러 반환

### Pitfall 5: 삼성 기기 Foreground Service 킬
**What goes wrong:** Samsung One UI의 배터리 최적화가 Foreground Service를 중단
**Why it happens:** 삼성 커스텀 배터리 관리 정책
**How to avoid:** Samsung은 One UI 6.0부터 Android 14 FGS 정책 준수 앱의 foreground service를 보장한다고 발표. 타깃 SDK 36이므로 정상 동작 예상. 추가로 PowerManager `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Intent 안내 UI 제공
**Warning signs:** 장시간 BLE 연결 후 서비스 예기치 않게 종료

### Pitfall 6: 대용량 오디오 파일 저장 공간 부족
**What goes wrong:** WAV 파일(1시간 = ~600MB)이 내부 저장소를 가득 채움
**Why it happens:** 내부 저장소(`filesDir`) 용량 제한
**How to avoid:** 저장 전 `StatFs`로 가용 공간 확인. 부족 시 사용자 알림 + 다운로드 중단. MP3 포맷 우선 권장(1시간 = ~60MB)
**Warning signs:** `IOException: No space left on device`

## Code Examples

### AndroidManifest.xml 추가 선언
```xml
<!-- Source: https://developer.android.com/develop/background-work/services/fgs/service-types -->

<!-- BLE 퍼미션 (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />

<!-- Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- 배터리 최적화 무시 요청 (선택) -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Plaud SDK 네트워크 (Cloud API 폴백 + SDK 내부) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Foreground Service 선언 -->
<service
    android:name=".service.AudioCollectionService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

### Plaud SDK 초기화 (Application에서)
```kotlin
// Source: https://docs.plaud.ai/api_guide/sdks/android

class AutoMinutingApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Plaud SDK 초기화 (appKey가 없으면 건너뜀 -- Cloud API 폴백)
        val appKey = BuildConfig.PLAUD_APP_KEY
        val appSecret = BuildConfig.PLAUD_APP_SECRET
        if (appKey.isNotBlank() && appSecret.isNotBlank()) {
            NiceBuildSdk.initSdk(
                context = this,
                appKey = appKey,
                appSecret = appSecret,
                bleAgentListener = PlaudBleListener(),
                hostName = "AutoMinuting"
            )
        }
    }
}
```

### BLE 퍼미션 요청 (Compose)
```kotlin
// Source: Android developer docs - runtime permissions

@Composable
fun BlePermissionRequest(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) onGranted()
        else onDenied()
    }

    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }
}
```

### WorkManager 전사 트리거
```kotlin
// Source: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work

fun triggerTranscriptionPipeline(
    workManager: WorkManager,
    meetingId: Long
) {
    val inputData = workDataOf("meetingId" to meetingId)

    val transcriptionWork = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    workManager.enqueueUniqueWork(
        "transcription_$meetingId",
        ExistingWorkPolicy.KEEP,
        transcriptionWork
    )
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| FileObserver 파일 감시 | Plaud SDK BLE 연결 | Phase 1 PoC (2026-03) | Scoped Storage로 FileObserver 불가, SDK가 유일한 공식 경로 |
| kapt 어노테이션 프로세싱 | KSP | Kotlin 2.x | 빌드 속도 2배 향상, Hilt/Room 모두 KSP 지원 |
| FGS 타입 미지정 | connectedDevice 타입 필수 | Android 14 (2023) | 타입 없으면 MissingForegroundServiceTypeException |
| Samsung 비표준 배터리 정책 | Android 14 FGS 정책 준수 보장 | One UI 6.0 (2024-07) | Samsung 공식 발표로 FGS 킬 이슈 감소 |

**Deprecated/outdated:**
- `FileObserver` (타 앱 감시용): Scoped Storage로 사실상 사용 불가
- `Sdk.initSdk(context, appKey, listener, ...)`: 구버전 API. 현재는 `NiceBuildSdk.initSdk()`에 `appSecret` 파라미터 추가됨

## Open Questions

1. **Plaud SDK appKey/appSecret 발급 여부**
   - What we know: support@plaud.ai에 신청 필요, 개인 개발자 대상 발급 여부 미확인
   - What's unclear: 발급 소요 시간, 거절 가능성
   - Recommendation: 발급 대기 중 Cloud API 폴백 경로 병행 구현. appKey 미설정 시 자동으로 Cloud API 모드 전환

2. **Plaud SDK `appSecret` 존재 확인**
   - What we know: docs.plaud.ai에서 `appKey` + `appSecret` 두 파라미터 확인. PoC 문서에는 `appKey`만 언급
   - What's unclear: appSecret 발급 프로세스가 appKey와 동일한지
   - Recommendation: appKey 신청 시 appSecret도 함께 요청

3. **오디오 파일 실제 크기 및 포맷**
   - What we know: MP3/WAV 지원, 비트레이트/샘플레이트 미확인
   - What's unclear: 1시간 회의 기준 파일 크기, Plaud 기본 출력 포맷
   - Recommendation: MP3 포맷 기본 설정 (용량 효율), WAV는 옵션으로 제공

4. **BLE 연결 안정성**
   - What we know: SDK v0.2.8 초기 버전, 커뮤니티 피드백 부족
   - What's unclear: 장시간 연결 시 안정성, 재연결 자동화 여부
   - Recommendation: 연결 끊김 시 자동 재연결 로직 + 3회 실패 시 Cloud API 폴백

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Plaud SDK AAR | BLE 오디오 다운로드 | 확인 필요 (다운로드) | v0.2.8 | Cloud API |
| appKey/appSecret | SDK 초기화 | 미발급 (신청 필요) | -- | Cloud API 모드 |
| Android BLE API | SDK 내부 사용 | O (Platform API) | API 31+ | -- |
| Plaud 녹음기 기기 | 실제 테스트 | 확인 필요 | -- | Cloud API 모드로 개발 가능 |

**Missing dependencies with no fallback:**
- 없음 -- Cloud API가 모든 SDK 실패 케이스의 폴백 역할

**Missing dependencies with fallback:**
- Plaud SDK appKey: 미발급 시 Cloud API(JWT) 폴백으로 전환
- Plaud 녹음기 기기: 미보유 시 Cloud API 모드로 개발/테스트 가능

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockK + Turbine |
| Config file | 없음 -- Wave 0에서 설정 필요 |
| Quick run command | `./gradlew test --tests "com.autominuting.data.audio.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUD-01 | SDK에서 파일 다운로드 후 로컬 저장 | unit | `./gradlew test --tests "*.AudioRepositoryImplTest"` | Wave 0 |
| AUD-01 | 파일 저장 시 MeetingEntity 생성 | unit | `./gradlew test --tests "*.AudioFileManagerTest"` | Wave 0 |
| AUD-02 | Foreground Service 시작/중지 | unit | `./gradlew test --tests "*.AudioCollectionServiceTest"` | Wave 0 |
| AUD-03 | 파일 완료 시 WorkManager enqueue | unit | `./gradlew test --tests "*.TranscriptionTriggerTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.autominuting.data.audio.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/autominuting/data/audio/AudioRepositoryImplTest.kt` -- AUD-01
- [ ] `app/src/test/java/com/autominuting/data/audio/AudioFileManagerTest.kt` -- AUD-01
- [ ] `app/src/test/java/com/autominuting/data/audio/PlaudSdkManagerTest.kt` -- AUD-01, AUD-02
- [ ] `app/src/test/java/com/autominuting/worker/TranscriptionTriggerWorkerTest.kt` -- AUD-03
- [ ] 테스트 의존성 추가: JUnit 5, MockK, Turbine, kotlinx-coroutines-test
- [ ] `app/src/test/` 디렉토리 생성 (현재 존재하지 않음)

## Sources

### Primary (HIGH confidence)
- [Plaud SDK 공식 문서](https://docs.plaud.ai/api_guide/sdks/android) - SDK API, 초기화, BLE 연결, 파일 다운로드
- [Plaud SDK GitHub](https://github.com/Plaud-AI/plaud-sdk) - 소스 코드, 의존성, 라이선스
- [Android Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types) - connectedDevice 타입 요구사항
- [Android 14 FGS 변경사항](https://developer.android.com/about/versions/14/changes/fgs-types-required) - 필수 타입 선언
- [WorkManager Chaining](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work) - Worker 체이닝 패턴

### Secondary (MEDIUM confidence)
- [Samsung Don't Kill My App](https://dontkillmyapp.com/samsung) - Samsung 배터리 최적화 정책, One UI 6.0 변경사항
- [Android BLE Background](https://developer.android.com/develop/connectivity/bluetooth/ble/background) - BLE 백그라운드 동작 가이드
- [Plaud SDK Integration Guide](https://github.com/Plaud-AI/plaud-sdk/blob/main/docs/sdk-integration-guide.md) - 통합 가이드

### Tertiary (LOW confidence)
- Plaud SDK `appSecret` 파라미터: docs.plaud.ai에서 확인했으나, PoC 문서(poc/plaud-analysis/sdk-evaluation.md)의 API와 차이 있음. 실제 SDK 버전에 따라 다를 수 있음

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - Plaud SDK AAR 직접 테스트 불가 (appKey 미발급), 공식 문서 기반
- Architecture: HIGH - Clean Architecture 패턴 + Android 공식 가이드 기반
- Pitfalls: HIGH - Android 공식 문서 + 커뮤니티 검증된 이슈
- Plaud SDK API: MEDIUM - docs.plaud.ai 최신 문서 확인했으나 PoC 문서와 일부 API 차이 존재

**Research date:** 2026-03-24
**Valid until:** 2026-04-07 (Plaud SDK는 초기 버전으로 API 변경 가능성 있어 7일)
