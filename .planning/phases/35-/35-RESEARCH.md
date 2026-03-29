# Phase 35: 회의록 설정 구조 개편 - Research

**Researched:** 2026-03-29
**Domain:** Android Jetpack Compose UI / DataStore 설정 / 회의록 생성 파이프라인 리팩토링
**Confidence:** HIGH

## Summary

Phase 35는 설정 화면의 회의록 관련 설정을 세 가지 방향으로 개편한다: (1) 자동모드(automationMode) 설정을 "전사 설정" 섹션에서 "회의록 설정" 섹션으로 이동, (2) 설정 화면에서 "직접 입력" 커스텀 프롬프트를 기본 프롬프트로 설정할 수 있는 기능 추가, (3) minutesFormat 드롭다운(구조화/요약/액션아이템) 제거 및 관련 코드 정리.

현재 코드베이스를 분석한 결과, minutesFormat은 templateId와 customPrompt가 모두 없을 때만 사용되는 폴백 메커니즘이다. Phase 30에서 프롬프트 템플릿 시스템이 도입되면서 minutesFormat의 실질적 역할이 크게 줄었다. 기본 제공 템플릿 3종(구조화/요약/액션아이템)이 이미 MinutesFormat의 역할을 대체하고 있으므로, minutesFormat 제거는 안전하며 코드 단순화에 기여한다.

**Primary recommendation:** minutesFormat 관련 코드를 전면 제거하고, 자동모드를 회의록 섹션으로 이동하며, 설정에서 커스텀 프롬프트를 기본으로 지정할 수 있는 TextField를 추가한다.

## Project Constraints (from CLAUDE.md)

- 언어: 한국어 응답, 한국어 코드 주석, 한국어 커밋 메시지
- 변수명/함수명: 영어
- 플랫폼: Android 네이티브 (Kotlin)
- UI: Jetpack Compose + Material 3
- 설정: DataStore
- DI: Hilt
- 비동기: Coroutines + Flow

## Architecture Patterns

### 현재 설정 화면 구조

```
SettingsScreen
├── 회의록 설정 섹션
│   ├── 프롬프트 템플릿 관리 (버튼 → PromptTemplateScreen)
│   ├── 기본 프롬프트 템플릿 (드롭다운: 매번 선택 / 템플릿 목록)
│   └── 회의록 형식 (드롭다운: 구조화/요약/액션아이템) ← 제거 대상
├── 전사 설정 섹션
│   ├── 완전 자동 모드 (Switch) ← 이동 대상 (→ 회의록 설정)
│   ├── STT 엔진 (드롭다운: Gemini/Whisper)
│   └── Whisper 모델 관리 (다운로드/삭제)
└── 인증 섹션
    ├── 인증 모드 (RadioButton: API키/OAuth)
    └── API키 입력 / Google 로그인
```

### 변경 후 설정 화면 구조

```
SettingsScreen
├── 회의록 설정 섹션
│   ├── 완전 자동 모드 (Switch) ← 여기로 이동
│   ├── 프롬프트 템플릿 관리 (버튼 → PromptTemplateScreen)
│   ├── 기본 프롬프트 템플릿 (드롭다운: 매번 선택 / 직접 입력 / 템플릿 목록) ← 확장
│   └── [조건부] 직접 입력 프롬프트 (TextField) ← 신규
├── 전사 설정 섹션
│   ├── STT 엔진 (드롭다운: Gemini/Whisper)
│   └── Whisper 모델 관리 (다운로드/삭제)
└── 인증 섹션 (변경 없음)
```

### Pattern 1: minutesFormat 제거 영향 범위 (Impact Map)

**What:** MinutesFormat enum과 모든 참조를 코드베이스에서 제거
**Confidence:** HIGH (직접 코드 분석 완료)

minutesFormat이 사용되는 모든 위치와 제거 방법:

| 파일 | 사용 방식 | 제거 방법 |
|------|-----------|-----------|
| `domain/model/MinutesFormat.kt` | enum 정의 | 파일 전체 삭제 |
| `data/preferences/UserPreferencesRepository.kt` | MINUTES_FORMAT_KEY, minutesFormat Flow, setMinutesFormat(), getMinutesFormatOnce() | 해당 필드/메서드 전체 삭제 |
| `presentation/settings/SettingsScreen.kt` | 회의록 형식 드롭다운 UI | 해당 UI 블록 삭제 |
| `presentation/settings/SettingsViewModel.kt` | minutesFormat StateFlow, setMinutesFormat() | 해당 필드/메서드 삭제 |
| `data/minutes/GeminiEngine.kt` | format 파라미터로 프롬프트 분기 | customPrompt null일 때 기본 프롬프트(STRUCTURED) 사용으로 변경 |
| `data/minutes/GeminiOAuthEngine.kt` | format 파라미터 | 동일하게 기본 프롬프트로 변경 |
| `data/minutes/MinutesEngine.kt` | generate() interface의 format 파라미터 | format 파라미터 제거 |
| `data/minutes/MinutesEngineSelector.kt` | generate() delegate의 format 파라미터 | format 파라미터 제거 |
| `domain/repository/MinutesRepository.kt` | generateMinutes() interface의 format 파라미터 | format 파라미터 제거 |
| `data/repository/MinutesRepositoryImpl.kt` | generateMinutes()의 format 파라미터 | format 파라미터 제거 |
| `worker/MinutesGenerationWorker.kt` | KEY_MINUTES_FORMAT inputData에서 읽기, minutesFormat 변수 | format 관련 코드 제거, customPrompt null이면 기본 STRUCTURED 프롬프트 직접 사용 |
| `worker/TranscriptionTriggerWorker.kt` | KEY_MINUTES_FORMAT inputData 전달 | 해당 키 전달 코드 삭제 |
| `presentation/dashboard/DashboardViewModel.kt` | getMinutesFormatOnce() → Worker에 전달 | 해당 코드 삭제 |
| `presentation/transcripts/TranscriptsViewModel.kt` | getMinutesFormatOnce() → Worker에 전달 | 해당 코드 삭제 |
| `presentation/share/ShareReceiverActivity.kt` | getMinutesFormatOnce() → Worker에 전달 | 해당 코드 삭제 |
| `receiver/PipelineActionReceiver.kt` | intent에서 minutesFormat 읽어 Worker에 전달 | 해당 코드 삭제 |
| `service/PipelineNotificationHelper.kt` | minutesFormat을 intent extra로 전달 | 해당 코드 삭제 |

### Pattern 2: 프롬프트 해결 우선순위 (변경 전/후)

**변경 전 (Phase 30 결정):**
```
customPrompt > templateId > minutesFormat 폴백
```

**변경 후:**
```
customPrompt > templateId > 기본 STRUCTURED 프롬프트 (하드코드 폴백)
```

핵심 변경: minutesFormat enum을 통한 동적 폴백이 사라지고, customPrompt도 templateId도 없는 경우 항상 GeminiEngine의 STRUCTURED_PROMPT가 기본으로 사용된다. 기존 3종 프리셋(구조화/요약/액션아이템)은 이미 기본 제공 PromptTemplate(isBuiltIn=true)로 존재하므로 기능 손실이 없다.

### Pattern 3: 직접 입력 프롬프트의 DataStore 저장

**What:** 설정에서 "직접 입력"을 선택하면 커스텀 프롬프트 텍스트를 DataStore에 저장
**When to use:** 기본 프롬프트 드롭다운에서 "직접 입력" 옵션을 선택한 경우

새로운 DataStore 키 필요:
```kotlin
// UserPreferencesRepository에 추가
val DEFAULT_CUSTOM_PROMPT_KEY = stringPreferencesKey("default_custom_prompt")
```

기본 프롬프트 모드 표현 방식 (두 가지 옵션):

**옵션 A: defaultTemplateId 확장 (추천)**
- `0L` = 매번 선택 (기존)
- `> 0` = 특정 템플릿 ID (기존)
- `-1L` = 직접 입력 모드 (신규)
- 직접 입력 모드일 때 `DEFAULT_CUSTOM_PROMPT_KEY`에서 프롬프트 텍스트 읽기

**옵션 B: 별도 enum**
- 새로운 enum `DefaultPromptMode { EVERY_TIME, TEMPLATE, CUSTOM }` 도입
- 복잡도 증가 — 기존 defaultTemplateId Flow를 구독하는 모든 곳 수정 필요

옵션 A가 기존 코드와의 호환성이 높고 변경 범위가 작다. 매직넘버 -1L을 상수로 정의하면 가독성 문제도 해결된다.

### Pattern 4: 자동모드 이동 (UI only)

자동모드(automationMode) 이동은 순수 UI 변경이다. SettingsScreen.kt에서 Switch 컴포넌트의 위치만 "전사 설정" 섹션에서 "회의록 설정" 섹션으로 옮기면 된다.

automationMode는 "전사 후 자동으로 회의록을 생성할지" 결정하는 설정이므로, "회의록 설정" 섹션에 위치하는 것이 의미적으로 더 정확하다.

ViewModel, DataStore, Worker 등 데이터 레이어는 변경 불필요.

### Anti-Patterns to Avoid

- **DataStore 마이그레이션 불필요:** minutesFormat 키를 DataStore에서 제거해도 기존 사용자 데이터에 영향 없음. DataStore는 존재하지 않는 키를 조회하면 기본값을 반환하므로, 이전 사용자의 stored minutesFormat 값은 무시된다.
- **MinutesEngine interface 깨뜨리지 않기:** format 파라미터 제거 시 interface, 모든 구현체, 호출부를 동시에 수정해야 한다. 컴파일 타임에 잡히므로 안전하지만, 누락 없이 일괄 수정 필요.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 설정 값 마이그레이션 | DataStore 마이그레이션 코드 | 불필요 — 그냥 키 삭제 | DataStore는 없는 키에 대해 기본값 반환 |
| 커스텀 프롬프트 저장 | Room entity 추가 | DataStore stringPreferencesKey | 단일 문자열 값, Room 불필요 |

## Common Pitfalls

### Pitfall 1: MinutesEngine format 파라미터 제거 시 컴파일 오류
**What goes wrong:** format 파라미터를 interface에서 제거하면 모든 구현체와 호출부에서 컴파일 오류 발생
**Why it happens:** MinutesEngine → GeminiEngine, GeminiOAuthEngine, MinutesEngineSelector 3개 구현체 + MinutesRepository → MinutesRepositoryImpl 1개 구현체
**How to avoid:** interface 수정 → 모든 구현체 수정 → 모든 호출부 수정 순서로 진행. Worker의 inputData에서 format 관련 키 전달도 함께 제거
**Warning signs:** 빌드 실패

### Pitfall 2: GeminiEngine 기본 프롬프트 손실
**What goes wrong:** minutesFormat 제거 시 customPrompt가 null이면 프롬프트 없이 API 호출
**Why it happens:** 현재 GeminiEngine.generate()에서 customPrompt == null && format == X일 때 STRUCTURED/SUMMARY/ACTION_ITEMS 중 선택
**How to avoid:** customPrompt == null일 때 STRUCTURED_PROMPT를 기본값으로 사용하도록 변경. STRUCTURED_PROMPT는 삭제하지 않고 기본 프롬프트로 유지
**Warning signs:** 회의록 생성 시 Gemini가 전사 텍스트만 받고 프롬프트 없이 응답

### Pitfall 3: 직접 입력 프롬프트가 비어있을 때
**What goes wrong:** 사용자가 "직접 입력" 모드를 선택했지만 프롬프트 텍스트를 입력하지 않음
**Why it happens:** 드롭다운에서 "직접 입력"만 선택하고 TextField를 비워둠
**How to avoid:** 직접 입력 프롬프트가 비어있으면 기본 STRUCTURED 프롬프트로 폴백. 또는 빈 상태에서는 저장 불가하도록 UI에서 validation

### Pitfall 4: TranscriptionTriggerWorker의 minutesFormat 전달 제거
**What goes wrong:** Worker enqueue 시 minutesFormat을 전달하는 코드가 여러 곳에 분산되어 있어 일부만 제거됨
**Why it happens:** DashboardViewModel, TranscriptsViewModel, ShareReceiverActivity, PipelineActionReceiver 4곳에서 각각 Worker를 enqueue
**How to avoid:** 모든 enqueue 지점을 리서치에서 식별 완료 — 이 4곳 + PipelineNotificationHelper의 Intent extra까지 총 5곳을 확인

## Code Examples

### minutesFormat 제거 후 GeminiEngine.generate() 패턴
```kotlin
// GeminiEngine.kt — format 파라미터 제거 후
override suspend fun generate(
    transcriptText: String,
    customPrompt: String?
): Result<String> {
    // ...
    val prompt = if (customPrompt != null) {
        customPrompt + "\n\n---\n\n## 회의 전사 텍스트\n\n" + transcriptText
    } else {
        // 기본 프롬프트 (기존 STRUCTURED_PROMPT)
        STRUCTURED_PROMPT + transcriptText
    }
    // ...
}
```

### defaultTemplateId 확장으로 직접 입력 모드 표현
```kotlin
// UserPreferencesRepository.kt 추가
companion object {
    /** 직접 입력 모드를 나타내는 특수 템플릿 ID */
    const val CUSTOM_PROMPT_MODE_ID = -1L

    /** 직접 입력 기본 프롬프트 키 */
    val DEFAULT_CUSTOM_PROMPT_KEY = stringPreferencesKey("default_custom_prompt")
}

/** 기본 커스텀 프롬프트 텍스트를 관찰한다. */
val defaultCustomPrompt: Flow<String> = dataStore.data.map { prefs ->
    prefs[DEFAULT_CUSTOM_PROMPT_KEY] ?: ""
}

suspend fun setDefaultCustomPrompt(prompt: String) {
    dataStore.edit { prefs ->
        prefs[DEFAULT_CUSTOM_PROMPT_KEY] = prompt
    }
}
```

### 자동모드 UI 이동 (SettingsScreen.kt 구조)
```kotlin
// 회의록 설정 섹션에 자동모드 추가
SettingsSection(title = "회의록 설정") {
    // 완전 자동 모드 Switch (기존 전사 설정 섹션에서 이동)
    Row(verticalAlignment = Alignment.CenterVertically, ...) {
        Column(modifier = Modifier.weight(1f)) {
            Text("완전 자동 모드", ...)
            Text("오디오 감지부터 회의록 생성까지 자동 진행", ...)
        }
        Switch(
            checked = automationMode == AutomationMode.FULL_AUTO,
            onCheckedChange = { ... }
        )
    }
    // ... 나머지 회의록 설정
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| MinutesFormat enum (3종 프리셋) | PromptTemplate + customPrompt | Phase 30 (v4.0) | MinutesFormat은 폴백으로만 사용 |
| 설정에서 형식 선택 | 생성 시 템플릿 선택 다이얼로그 | Phase 30 | 런타임 선택이 설정 선택보다 유연 |

**Deprecated/outdated:**
- MinutesFormat enum: Phase 30에서 PromptTemplate 시스템 도입으로 실질적 역할 소멸. 이번 phase에서 공식 제거.

## Open Questions

1. **SUMMARY_PROMPT, ACTION_ITEMS_PROMPT 코드 보존 여부**
   - What we know: 기본 제공 PromptTemplate 3종이 이미 이 역할을 대체
   - What's unclear: GeminiEngine에 하드코딩된 3개 프롬프트를 삭제할지, STRUCTURED만 남기고 나머지 제거할지
   - Recommendation: STRUCTURED_PROMPT만 기본 폴백으로 유지, SUMMARY_PROMPT/ACTION_ITEMS_PROMPT는 삭제. 기본 제공 템플릿이 동일 프롬프트를 포함하고 있으므로 중복.

## Sources

### Primary (HIGH confidence)
- 직접 코드 분석: SettingsScreen.kt, SettingsViewModel.kt, UserPreferencesRepository.kt, MinutesFormat.kt, AutomationMode.kt, GeminiEngine.kt, MinutesEngine.kt, MinutesEngineSelector.kt, GeminiOAuthEngine.kt, MinutesRepository.kt, MinutesRepositoryImpl.kt, MinutesGenerationWorker.kt, TranscriptionTriggerWorker.kt, DashboardViewModel.kt, DashboardScreen.kt, TranscriptsViewModel.kt, ShareReceiverActivity.kt, PipelineActionReceiver.kt, PipelineNotificationHelper.kt, ManualMinutesDialog.kt, PromptTemplate.kt, PromptTemplateRepository.kt

### Secondary (MEDIUM confidence)
- Phase 30 결정: 프롬프트 해결 우선순위 (customPrompt > templateId > minutesFormat)
- Phase 28 결정: 설정 섹션 3분류 (회의록 설정/전사 설정/인증)

## Metadata

**Confidence breakdown:**
- 영향 범위 분석: HIGH - 모든 참조를 직접 코드에서 확인
- 제거 안전성: HIGH - 컴파일 타임 검증 가능, 런타임 side effect 없음
- 직접 입력 설계: MEDIUM - 옵션 A(defaultTemplateId=-1L) 패턴이 가장 단순하나, planner가 최종 결정

**Research date:** 2026-03-29
**Valid until:** 2026-04-30 (코드베이스 내부 분석이므로 안정적)
