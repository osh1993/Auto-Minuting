---
phase: "35"
plan: 2
subsystem: settings
tags: [feature, settings, custom-prompt, pipeline]
dependency_graph:
  requires: [MinutesFormat-제거-완료, 자동모드-회의록설정-이동]
  provides: [직접입력-프롬프트-기본설정, CUSTOM_PROMPT_MODE_ID]
  affects: [UserPreferencesRepository, SettingsViewModel, SettingsScreen, DashboardViewModel, TranscriptsViewModel, ShareReceiverActivity, PipelineActionReceiver, PipelineNotificationHelper, TranscriptionTriggerWorker]
tech_stack:
  added: []
  patterns: [CUSTOM_PROMPT_MODE_ID-특수-templateId-분기, DataStore-커스텀프롬프트-저장]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt
    - app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt
    - app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
decisions:
  - CUSTOM_PROMPT_MODE_ID(-1L)를 특수 templateId로 사용하여 직접 입력 모드 표현
  - TranscriptionTriggerWorker에서 inputData에 templateId가 없으면 UserPreferences에서 기본값 조회
  - PipelineActionReceiver에서 MinutesFormat import 제거, customPrompt 전달로 대체
metrics:
  duration: "~5min"
  completed: "2026-03-29"
---

# Phase 35 Plan 02: 직접 입력 프롬프트 기본 설정 Summary

설정 화면 기본 프롬프트 드롭다운에 "직접 입력" 옵션을 추가하고, 커스텀 프롬프트를 DataStore에 저장하여 4개 enqueue 지점(DashboardVM, TranscriptsVM, ShareReceiverActivity, PipelineActionReceiver) 전체에서 직접 입력 프롬프트로 회의록 생성이 가능하도록 연동

## Tasks Completed

### Task 1: 직접 입력 프롬프트 DataStore + ViewModel + 전체 파이프라인 연동
**Commit:** 5f58182

UserPreferencesRepository에 CUSTOM_PROMPT_MODE_ID(-1L) 상수와 DEFAULT_CUSTOM_PROMPT_KEY, defaultCustomPrompt Flow, setDefaultCustomPrompt(), getDefaultCustomPromptOnce()를 추가했다. SettingsViewModel에 defaultCustomPrompt StateFlow와 setDefaultCustomPrompt() 메서드를 추가했다.

4개 enqueue 지점 모두에 CUSTOM_PROMPT_MODE_ID 분기를 추가:
- DashboardViewModel: generateMinutesForPipeline()에서 CUSTOM_PROMPT_MODE_ID일 때 커스텀 프롬프트로 Worker enqueue
- TranscriptsViewModel: generateMinutes()에서 동일 분기 추가
- ShareReceiverActivity: processSharedText()에서 templateId 기반 workData 분기 (CUSTOM_PROMPT_MODE_ID/templateId>0/기본)
- PipelineActionReceiver: intent에서 customPrompt 읽어 KEY_CUSTOM_PROMPT로 Worker에 전달

PipelineNotificationHelper에 customPrompt 파라미터 추가. TranscriptionTriggerWorker에서 기본 templateId를 UserPreferences에서 조회하고, CUSTOM_PROMPT_MODE_ID일 때 커스텀 프롬프트를 MinutesGenerationWorker와 PipelineNotificationHelper에 전달하도록 수정.

### Task 2: 설정 화면 직접 입력 UI + 빌드 검증
**Commit:** fab48fb

SettingsScreen.kt에서 기본 프롬프트 드롭다운의 selectedTemplateName을 when 분기로 변경하여 CUSTOM_PROMPT_MODE_ID일 때 "직접 입력"을 표시한다. 드롭다운 메뉴에 "직접 입력" DropdownMenuItem을 추가했다.

직접 입력 모드 선택 시 조건부로 OutlinedTextField(minLines=3, maxLines=8)를 표시하며, 프롬프트가 비어있으면 "기본 구조화된 회의록 형식이 사용됩니다" 안내 텍스트를 표시한다.

compileDebugKotlin 빌드 성공 확인.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] TranscriptionTriggerWorker에 UserPreferencesRepository 의존성 추가**
- **Found during:** Task 1
- **Issue:** TranscriptionTriggerWorker가 FULL_AUTO/HYBRID 분기에서 customPrompt를 전달해야 하지만, UserPreferencesRepository 의존성이 없어 기본 templateId 조회와 커스텀 프롬프트 읽기가 불가능했다
- **Fix:** constructor에 UserPreferencesRepository 주입 추가, templateId 0일 때 UserPreferences에서 기본값 조회, CUSTOM_PROMPT_MODE_ID 분기 추가
- **Files modified:** TranscriptionTriggerWorker.kt
- **Commit:** 5f58182

**2. [Rule 1 - Bug] PipelineActionReceiver에서 MinutesFormat import 제거**
- **Found during:** Task 1
- **Issue:** PipelineActionReceiver가 더 이상 minutesFormat을 사용하지 않으므로 MinutesFormat import가 불필요 (빌드 경고 방지)
- **Fix:** import 삭제, minutesFormat 읽기 코드를 customPrompt 읽기로 교체
- **Files modified:** PipelineActionReceiver.kt
- **Commit:** 5f58182

## Verification

- UserPreferencesRepository.kt: CUSTOM_PROMPT_MODE_ID, DEFAULT_CUSTOM_PROMPT_KEY, defaultCustomPrompt Flow, setDefaultCustomPrompt(), getDefaultCustomPromptOnce() 존재 확인
- SettingsViewModel.kt: defaultCustomPrompt StateFlow, setDefaultCustomPrompt() 존재 확인
- DashboardViewModel.kt: CUSTOM_PROMPT_MODE_ID 분기 존재 확인
- TranscriptsViewModel.kt: CUSTOM_PROMPT_MODE_ID 분기 존재 확인
- ShareReceiverActivity.kt: CUSTOM_PROMPT_MODE_ID 분기 + getDefaultCustomPromptOnce 호출 존재 확인
- PipelineActionReceiver.kt: KEY_CUSTOM_PROMPT 참조 존재, MinutesFormat import 없음 확인
- PipelineNotificationHelper.kt: notifyTranscriptionComplete에 customPrompt 파라미터 존재 확인
- SettingsScreen.kt: "직접 입력" 드롭다운 옵션, CUSTOM_PROMPT_MODE_ID 참조, defaultCustomPrompt 수집, OutlinedTextField minLines=3 존재 확인
- compileDebugKotlin BUILD SUCCESSFUL

## Known Stubs

None.
