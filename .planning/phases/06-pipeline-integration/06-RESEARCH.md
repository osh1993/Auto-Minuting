# Phase 6: 파이프라인 통합 및 자동화 - Research

**Researched:** 2026-03-24
**Domain:** Android 파이프라인 자동화, 알림, Share Intent, DataStore 설정
**Confidence:** HIGH

## Summary

Phase 6은 기존에 구현된 개별 파이프라인 단계(오디오 수집 -> 전사 -> 회의록 생성)를 엔드-투-엔드 자동화로 완성하고, 사용자 설정(형식 선택, 자동화 모드)과 공유 기능을 추가하는 통합 단계이다. 핵심 파이프라인 체이닝(TranscriptionTriggerWorker -> MinutesGenerationWorker)은 이미 동작 중이므로, 이 Phase에서는 (1) GeminiEngine에 형식별 프롬프트 확장, (2) DataStore 기반 설정 저장/읽기, (3) Worker에서 단계별 알림 업데이트, (4) 하이브리드 모드 분기 로직, (5) Android Share Intent 공유를 구현한다.

모든 구현에 필요한 기술(WorkManager, NotificationCompat, DataStore, Share Intent)은 Android Platform API와 이미 프로젝트에 의존성으로 포함된 라이브러리이다. 새로운 외부 의존성 추가가 불필요하며, 기존 코드 패턴(HiltWorker, CoroutineWorker, StateFlow, NotificationChannel)을 확장하는 방식으로 구현할 수 있다.

**Primary recommendation:** 기존 Worker 체이닝 패턴 위에 DataStore 설정을 읽어 분기하는 로직을 추가하고, 각 Worker에서 NotificationManager로 진행 알림을 업데이트하는 방식으로 구현한다.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 3종 프리셋 형식 제공: 구조화된 회의록(현재 기본값), 요약, 액션 아이템 중심. 각 형식은 GeminiEngine에 별도 프롬프트로 구현
- **D-02:** 설정 화면에서 기본 형식을 선택하고 DataStore에 저장. 개별 회의록 생성 전에도 변경 가능
- **D-03:** 커스텀 프롬프트 편집은 v2로 미룸. v1은 프리셋만 지원
- **D-04:** 완전 자동 모드(기본값): 오디오 감지 -> 전사 -> 회의록 생성이 모두 자동으로 진행
- **D-05:** 하이브리드 모드: 전사 완료 후 1회 사용자 확인을 거쳐 회의록 생성 진행. 전사 결과를 먼저 확인하고 싶을 때 사용
- **D-06:** 모드 전환은 설정 화면에서 DataStore 기반 토글로 구현
- **D-07:** AudioCollectionService의 기존 NotificationChannel/NotificationCompat 패턴을 재사용. 파이프라인 전용 알림 채널 추가
- **D-08:** 각 Worker에서 단계 전환 시 알림 업데이트: "전사 중...", "회의록 생성 중...", "회의록 완료"
- **D-09:** 앱 내 진행 상태는 홈 화면 상단 배너(현재 진행 중인 파이프라인)와 기존 SuggestionChip 상태 표시 패턴 활용
- **D-10:** Android Share Intent(ACTION_SEND)로 text/plain 타입 Markdown 텍스트 공유. 파일 첨부 없이 텍스트만
- **D-11:** 공유 트리거 위치: MinutesDetailScreen 상단 AppBar에 공유 아이콘 + 회의록 생성 완료 알림에서 직접 공유 가능

### Claude's Discretion
- 알림 아이콘 디자인 및 색상
- 설정 화면 레이아웃 세부 구성
- 프리셋 프롬프트 문구 최적화 (기존 MINUTES_PROMPT를 기반으로 변형)
- 하이브리드 모드의 확인 UI (알림 액션 vs 앱 내 다이얼로그)

### Deferred Ideas (OUT OF SCOPE)
- 커스텀 프롬프트 편집 기능 (v2 ADV-03)
- 파이프라인 실행 이력/통계 (v2)
- 회의록 생성 취소/재생성 기능 (필요시 추후 Phase에서)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MIN-05 | 회의록 형식 선택 가능 (구조화된 회의록 / 요약 / 커스텀 템플릿) | GeminiEngine 프롬프트 확장 + DataStore 설정 + MinutesGenerationWorker inputData 전달 |
| MIN-06 | 생성된 회의록을 외부 앱으로 공유 (Android Share Intent) | Intent.ACTION_SEND + text/plain + createChooser 패턴 |
| UI-02 | 파이프라인 진행 상태 알림 (각 단계별 진행률) | NotificationCompat + NotificationChannel + Worker 내 알림 업데이트 |
| UI-04 | 자동화 수준 설정 -- 완전 자동 / 하이브리드 모드 선택 | DataStore 토글 + TranscriptionTriggerWorker 분기 로직 + 알림 액션 버튼 |
</phase_requirements>

## Standard Stack

이 Phase는 새로운 라이브러리가 불필요하다. 모든 구현이 이미 프로젝트에 포함된 의존성으로 가능하다.

### Core (이미 프로젝트에 포함)
| Library | Version | Purpose | 이 Phase에서의 역할 |
|---------|---------|---------|---------------------|
| WorkManager | 2.11.1 | 백그라운드 작업 | Worker 체이닝 분기(자동/하이브리드), 알림 업데이트 |
| DataStore Preferences | 1.2.1 | 설정 저장 | 회의록 형식, 자동화 모드 저장/읽기 |
| NotificationCompat | AndroidX Core | 알림 | 파이프라인 진행 알림 + 액션 버튼 |
| Jetpack Compose + Material 3 | BOM 2026.03.00 | UI | 설정 화면 확장, 공유 아이콘 |
| Hilt | 2.56+ | DI | 새 Repository/UseCase 주입 |

### 신규 의존성
없음. 모든 기능이 기존 의존성으로 구현 가능하다.

## Architecture Patterns

### 프로젝트 구조 (Phase 6 추가 파일)
```
app/src/main/java/com/autominuting/
├── data/
│   ├── minutes/
│   │   └── GeminiEngine.kt           # 수정: 형식별 프롬프트 추가
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt  # 신규: DataStore 래퍼
│   └── repository/
│       └── MinutesRepositoryImpl.kt   # 수정: 형식 파라미터 전달
├── domain/
│   ├── model/
│   │   ├── MinutesFormat.kt           # 신규: 형식 enum
│   │   ├── AutomationMode.kt         # 신규: 모드 enum
│   │   └── PipelineStatus.kt         # 기존 유지
│   └── repository/
│       └── MinutesRepository.kt       # 수정: 형식 파라미터 추가
├── presentation/
│   ├── minutes/
│   │   └── MinutesDetailScreen.kt     # 수정: Share 아이콘 추가
│   └── settings/
│       ├── SettingsScreen.kt          # 수정: 형식 선택 + 모드 토글
│       └── SettingsViewModel.kt       # 신규: 설정 상태 관리
├── service/
│   └── PipelineNotificationHelper.kt  # 신규: 알림 유틸리티
├── worker/
│   ├── TranscriptionTriggerWorker.kt  # 수정: 하이브리드 분기 + 알림
│   └── MinutesGenerationWorker.kt     # 수정: 형식 전달 + 알림
└── receiver/
    └── PipelineActionReceiver.kt      # 신규: 알림 액션 수신
```

### Pattern 1: DataStore Preferences를 활용한 설정 관리
**What:** 사용자 설정(회의록 형식, 자동화 모드)을 DataStore Preferences에 저장/읽기
**When to use:** 간단한 key-value 형태의 사용자 설정이 필요할 때
**Example:**
```kotlin
// Source: https://developer.android.com/topic/libraries/architecture/datastore
// data/preferences/UserPreferencesRepository.kt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val MINUTES_FORMAT_KEY = stringPreferencesKey("minutes_format")
        val AUTOMATION_MODE_KEY = stringPreferencesKey("automation_mode")
    }

    // enum name으로 저장/읽기 (MeetingEntity의 PipelineStatus 패턴과 동일)
    val minutesFormat: Flow<MinutesFormat> = dataStore.data.map { prefs ->
        val name = prefs[MINUTES_FORMAT_KEY] ?: MinutesFormat.STRUCTURED.name
        MinutesFormat.valueOf(name)
    }

    val automationMode: Flow<AutomationMode> = dataStore.data.map { prefs ->
        val name = prefs[AUTOMATION_MODE_KEY] ?: AutomationMode.FULL_AUTO.name
        AutomationMode.valueOf(name)
    }

    suspend fun setMinutesFormat(format: MinutesFormat) {
        dataStore.edit { prefs ->
            prefs[MINUTES_FORMAT_KEY] = format.name
        }
    }

    suspend fun setAutomationMode(mode: AutomationMode) {
        dataStore.edit { prefs ->
            prefs[AUTOMATION_MODE_KEY] = mode.name
        }
    }
}
```

### Pattern 2: Worker 내 알림 업데이트
**What:** CoroutineWorker에서 NotificationManager를 직접 호출하여 진행 알림 업데이트
**When to use:** 백그라운드 Worker에서 사용자에게 진행 상태를 알릴 때
**Example:**
```kotlin
// Worker 내에서 알림 업데이트 패턴
// AudioCollectionService.updateNotification() 패턴을 Worker에서 재활용

private fun updatePipelineNotification(context: Context, text: String) {
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Auto Minuting")
        .setContentText(text)
        .setOngoing(true)     // 진행 중에는 스와이프 제거 불가
        .setSilent(true)       // 업데이트 시 소리/진동 없음
        .build()

    notificationManager.notify(PIPELINE_NOTIFICATION_ID, notification)
}
```

### Pattern 3: 하이브리드 모드 분기 (알림 액션 버튼)
**What:** 전사 완료 시 알림에 "회의록 생성 시작" 액션 버튼을 추가하여 사용자 확인 후 진행
**When to use:** D-05 하이브리드 모드에서 사용자 확인이 필요할 때
**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/build-notification
// 알림 액션 버튼으로 하이브리드 모드 구현

// 1. 알림 액션용 PendingIntent 생성
val generateIntent = Intent(context, PipelineActionReceiver::class.java).apply {
    action = "com.autominuting.action.GENERATE_MINUTES"
    putExtra("meetingId", meetingId)
    putExtra("transcriptPath", transcriptPath)
}
val pendingIntent = PendingIntent.getBroadcast(
    context,
    meetingId.toInt(),
    generateIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

// 2. 알림에 액션 추가
val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
    .setContentTitle("전사 완료")
    .setContentText("전사 결과를 확인하고 회의록 생성을 시작하세요")
    .addAction(
        R.drawable.ic_launcher_foreground,
        "회의록 생성 시작",
        pendingIntent
    )
    .setAutoCancel(true)
    .build()

// 3. BroadcastReceiver에서 WorkManager로 MinutesGenerationWorker enqueue
```

### Pattern 4: Android Share Intent
**What:** ACTION_SEND로 text/plain 텍스트를 외부 앱에 공유
**When to use:** D-10 회의록 Markdown 텍스트 공유
**Example:**
```kotlin
// Source: https://developer.android.com/training/sharing/send

fun shareMinutes(context: Context, title: String, minutesText: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, title)  // 이메일 등에서 제목으로 사용
        putExtra(Intent.EXTRA_TEXT, minutesText)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
```

### Pattern 5: GeminiEngine 형식별 프롬프트 확장
**What:** MinutesFormat enum 값에 따라 다른 프롬프트를 선택
**When to use:** D-01 3종 프리셋 형식 지원
**Example:**
```kotlin
// GeminiEngine 확장 패턴

enum class MinutesFormat {
    STRUCTURED,    // 구조화된 회의록 (기존 MINUTES_PROMPT)
    SUMMARY,       // 요약 (3~5줄)
    ACTION_ITEMS   // 액션 아이템 중심
}

// generate() 메서드에 format 파라미터 추가
suspend fun generate(transcriptText: String, format: MinutesFormat = MinutesFormat.STRUCTURED): Result<String> {
    val prompt = when (format) {
        MinutesFormat.STRUCTURED -> MINUTES_PROMPT
        MinutesFormat.SUMMARY -> SUMMARY_PROMPT
        MinutesFormat.ACTION_ITEMS -> ACTION_ITEMS_PROMPT
    }
    // ... 기존 Gemini API 호출 로직
}
```

### Anti-Patterns to Avoid
- **Worker 내에서 DataStore 직접 접근하지 말 것:** Worker에서는 inputData로 설정값을 전달받아야 한다. DataStore는 Flow 기반이므로 Worker에서 직접 collect하면 복잡해진다. enqueue 시점에 설정값을 읽어 inputData에 포함시킨다.
- **알림 채널 미생성:** Android 8.0+ 에서 채널 없는 알림은 무시된다. 앱 시작 시 또는 Worker 실행 전에 반드시 채널 생성.
- **PendingIntent FLAG 누락:** Android 12(API 31)+ 에서 PendingIntent에 FLAG_IMMUTABLE 또는 FLAG_MUTABLE을 반드시 지정해야 한다. 누락 시 크래시.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 설정 저장소 | SharedPreferences 래퍼 | DataStore Preferences | Flow 지원, 타입 안전성, 코루틴 네이티브 |
| 알림 관리 | 커스텀 알림 시스템 | NotificationCompat + NotificationManager | 플랫폼 표준, 모든 API 레벨 호환 |
| 텍스트 공유 | 커스텀 공유 UI | Intent.ACTION_SEND + createChooser | 시스템 UI, 모든 앱 호환 |
| 백그라운드 체이닝 | 커스텀 스케줄러 | WorkManager 체이닝 | 배터리 최적화, 재시도, 제약 조건 지원 |

**Key insight:** 이 Phase의 모든 기능은 Android Platform API가 제공하는 표준 메커니즘으로 구현 가능하다. 커스텀 솔루션을 만들 이유가 없다.

## Common Pitfalls

### Pitfall 1: PendingIntent mutability 플래그 누락
**What goes wrong:** Android 12(API 31)+ 에서 FLAG_IMMUTABLE/FLAG_MUTABLE 없이 PendingIntent 생성 시 IllegalArgumentException 크래시
**Why it happens:** 이전 API 레벨에서는 선택사항이었으나 API 31부터 필수가 됨
**How to avoid:** 모든 PendingIntent에 FLAG_IMMUTABLE 추가 (입력이 필요한 경우만 FLAG_MUTABLE)
**Warning signs:** minSdk 31이므로 반드시 플래그 지정 필요

### Pitfall 2: Worker에서 DataStore collect 블로킹
**What goes wrong:** Worker.doWork()에서 DataStore Flow를 collect하면 첫 값을 기다리며 지연 발생
**Why it happens:** DataStore는 비동기 Flow 기반이므로 동기적 접근이 어려움
**How to avoid:** enqueue 시점에서 설정값을 읽어 inputData로 Worker에 전달. Worker는 inputData에서 설정값 추출
**Warning signs:** Worker 실행 시간이 비정상적으로 길어지거나, 첫 실행에서만 지연

### Pitfall 3: 알림 채널 중복 생성
**What goes wrong:** createNotificationChannel을 매번 호출하면 기존 채널 설정(사용자가 변경한 중요도 등)이 덮어씌워질 수 있음
**Why it happens:** Android는 같은 ID로 채널을 다시 생성하면 대부분 무시하지만, 일부 설정이 리셋될 수 있음
**How to avoid:** Application.onCreate() 또는 전용 초기화 함수에서 한 번만 생성. AudioCollectionService의 기존 패턴 참조
**Warning signs:** 사용자가 설정한 알림 소리/진동이 초기화됨

### Pitfall 4: 하이브리드 모드에서 Worker 자동 체이닝
**What goes wrong:** TranscriptionTriggerWorker가 항상 MinutesGenerationWorker를 enqueue하므로 하이브리드 모드에서도 자동으로 회의록 생성 시작
**Why it happens:** 현재 코드(Line 107-117)에서 무조건 체이닝하고 있음
**How to avoid:** inputData에서 automationMode를 읽어 FULL_AUTO인 경우만 자동 enqueue, HYBRID인 경우 알림만 표시
**Warning signs:** 하이브리드 모드인데 확인 없이 회의록이 생성됨

### Pitfall 5: Compose에서 Intent 실행 시 Context
**What goes wrong:** Composable 함수 내에서 context.startActivity() 호출 시 Activity context가 아니면 FLAG_ACTIVITY_NEW_TASK 필요
**Why it happens:** ApplicationContext에서는 Activity 스택 없이 Activity를 시작할 수 없음
**How to avoid:** Composable에서는 LocalContext.current를 사용 (Activity context 보장)
**Warning signs:** "Calling startActivity() from outside of an Activity context" 예외

## Code Examples

### DataStore 인스턴스 Hilt 모듈 제공
```kotlin
// Source: https://developer.android.com/topic/libraries/architecture/datastore
// di/DataStoreModule.kt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// 최상위 레벨에 DataStore 선언 (싱글턴 보장)
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
```

### 파이프라인 알림 채널 등록
```kotlin
// service/PipelineNotificationHelper.kt
// AudioCollectionService.onCreate() 알림 채널 패턴을 재활용

object PipelineNotificationHelper {
    const val PIPELINE_CHANNEL_ID = "pipeline_progress_channel"
    const val PIPELINE_NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            PIPELINE_CHANNEL_ID,
            "파이프라인 진행",
            NotificationManager.IMPORTANCE_LOW  // 소리 없이 표시만
        ).apply {
            description = "회의록 생성 파이프라인 진행 상태 알림"
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun updateProgress(context: Context, text: String, ongoing: Boolean = true) {
        val builder = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Minuting")
            .setContentText(text)
            .setOngoing(ongoing)
            .setSilent(true)

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(PIPELINE_NOTIFICATION_ID, builder.build())
    }

    // 완료 알림 (공유 액션 포함)
    fun notifyComplete(context: Context, meetingId: Long, minutesPath: String) {
        // 공유 PendingIntent
        val shareIntent = Intent(context, PipelineActionReceiver::class.java).apply {
            action = "com.autominuting.action.SHARE_MINUTES"
            putExtra("meetingId", meetingId)
            putExtra("minutesPath", minutesPath)
        }
        val sharePendingIntent = PendingIntent.getBroadcast(
            context, meetingId.toInt(), shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("회의록 완료")
            .setContentText("회의록이 생성되었습니다")
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "공유", sharePendingIntent)

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(PIPELINE_NOTIFICATION_ID, builder.build())
    }
}
```

### 설정 화면 Compose UI (형식 선택 + 모드 토글)
```kotlin
// SettingsScreen 확장 패턴
// Material 3 DropdownMenu + Switch 조합

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val selectedFormat by viewModel.minutesFormat.collectAsStateWithLifecycle()
    val automationMode by viewModel.automationMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 회의록 형식 선택
        Text("회의록 형식", style = MaterialTheme.typography.titleMedium)
        // ExposedDropdownMenuBox로 3종 프리셋 선택 UI
        // ...

        // 자동화 모드 토글
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("완전 자동 모드", modifier = Modifier.weight(1f))
            Switch(
                checked = automationMode == AutomationMode.FULL_AUTO,
                onCheckedChange = { isAuto ->
                    viewModel.setAutomationMode(
                        if (isAuto) AutomationMode.FULL_AUTO else AutomationMode.HYBRID
                    )
                }
            )
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences | DataStore Preferences | 2021 (stable) | Flow 기반 비동기, 타입 안전 |
| setForegroundAsync (WorkManager) | ForegroundInfo | WorkManager 2.7+ | Worker에서 Foreground 알림 표시 |
| PendingIntent (no flag) | FLAG_IMMUTABLE 필수 | API 31 (Android 12) | 미지정 시 크래시 |
| NotificationManager.notify | NotificationCompat 권장 | 지속적 | API 레벨 호환성 보장 |

## Open Questions

1. **하이브리드 모드 확인 UI: 알림 액션 vs 앱 내 다이얼로그**
   - What we know: D-05에서 전사 완료 후 1회 확인이 필요. CONTEXT.md에서 Claude 재량으로 지정
   - What's unclear: 알림 액션 버튼이 더 편리하지만, 전사 결과를 미리보려면 앱 내 이동이 필요
   - Recommendation: **알림 액션 버튼**을 권장. 알림 탭 -> 앱 내 전사 확인 화면으로 이동 + 별도 "회의록 생성 시작" 액션 버튼 제공. 앱에 들어가지 않아도 바로 생성 시작 가능

2. **알림에서 공유 실행 시 Activity Context**
   - What we know: BroadcastReceiver에서 startActivity 호출 시 FLAG_ACTIVITY_NEW_TASK 필요
   - What's unclear: 공유 Intent가 Chooser를 열어야 하므로 Activity context가 이상적
   - Recommendation: PipelineActionReceiver에서 FLAG_ACTIVITY_NEW_TASK를 추가하여 실행

## Project Constraints (from CLAUDE.md)

- **응답 언어:** 한국어
- **코드 주석:** 한국어
- **커밋 메시지:** 한국어
- **변수명/함수명:** 영어
- **플랫폼:** Android 네이티브 (Kotlin)
- **아키텍처:** Clean Architecture (domain/data/presentation 레이어 분리)
- **DI:** Hilt
- **비동기:** Coroutines/Flow
- **UI:** Jetpack Compose + Material 3

## Sources

### Primary (HIGH confidence)
- Android Share Intent: https://developer.android.com/training/sharing/send
- Android Notifications: https://developer.android.com/develop/ui/views/notifications/build-notification
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- PendingIntent: https://developer.android.com/reference/android/app/PendingIntent

### Secondary (MEDIUM confidence)
- 프로젝트 기존 코드 패턴: AudioCollectionService, TranscriptionTriggerWorker, MinutesGenerationWorker, GeminiEngine
- DataStore enum 저장: https://medium.com/androiddevelopers/all-about-preferences-datastore-cc7995679334

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 모든 라이브러리가 이미 프로젝트에 포함되어 있고, 버전이 확인됨
- Architecture: HIGH - 기존 코드 패턴(Worker, Notification, Hilt)을 직접 확인하고 확장 방향이 명확
- Pitfalls: HIGH - Android 공식 문서에서 검증된 알려진 이슈들

**Research date:** 2026-03-24
**Valid until:** 2026-04-24 (안정적인 Android Platform API 기반, 변동 가능성 낮음)
