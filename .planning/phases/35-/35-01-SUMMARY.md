---
phase: "35"
plan: 1
subsystem: settings
tags: [refactor, settings, dead-code-removal]
dependency_graph:
  requires: []
  provides: [MinutesFormat-제거-완료, 자동모드-회의록설정-이동]
  affects: [MinutesEngine, GeminiEngine, GeminiOAuthEngine, MinutesRepository, UserPreferencesRepository, SettingsScreen, Workers, ViewModels]
tech_stack:
  added: []
  patterns: [customPrompt-null-시-STRUCTURED-기본-폴백]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/GeminiOAuthEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt
    - app/src/main/java/com/autominuting/domain/repository/MinutesRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt
    - app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt
    - app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
  deleted:
    - app/src/main/java/com/autominuting/domain/model/MinutesFormat.kt
decisions:
  - MinutesFormat enum 전면 제거, PromptTemplate 시스템이 완전 대체
  - customPrompt null 시 STRUCTURED_PROMPT 기본 폴백 (GeminiEngine/GeminiOAuthEngine)
  - 자동모드 Switch를 회의록 설정 섹션으로 이동 (의미적 일관성)
metrics:
  duration: "~10min"
  completed: "2026-03-29"
---

# Phase 35 Plan 01: MinutesFormat 제거 및 설정 구조 개편 Summary

MinutesFormat enum과 16개 파일의 관련 참조를 전면 제거하고, 자동모드 Switch를 회의록 설정 섹션으로 이동하여 설정 화면의 논리적 구조를 개선

## Tasks Completed

### Task 1: MinutesFormat enum 및 데이터 레이어 전면 제거
**Commit:** ff1bf89

MinutesFormat.kt 파일을 삭제하고, MinutesEngine/GeminiEngine/GeminiOAuthEngine/MinutesRepository 인터페이스 및 구현체에서 format 파라미터를 제거했다. UserPreferencesRepository에서 minutesFormat Flow/setter/getter를 삭제하고, Worker/ViewModel/Activity/Receiver/Service 총 15개 파일에서 minutesFormat 관련 코드를 일괄 제거했다.

- GeminiEngine: SUMMARY_PROMPT, ACTION_ITEMS_PROMPT 삭제, STRUCTURED_PROMPT만 기본 폴백으로 유지
- GeminiOAuthEngine: MinutesPrompts.STRUCTURED를 기본 폴백으로 사용
- MinutesGenerationWorker: 프롬프트 해결 우선순위를 customPrompt > templateId > STRUCTURED 기본으로 단순화

### Task 2: 자동모드 Switch를 회의록 설정 섹션으로 이동 + minutesFormat UI 제거
**Commit:** e64e694

SettingsScreen.kt에서 "회의록 형식" 드롭다운(ExposedDropdownMenuBox + formatLabels)을 완전히 삭제하고, "전사 설정" 섹션에 있던 자동모드(완전 자동/하이브리드) Switch를 "회의록 설정" 섹션 최상단으로 이동했다. MinutesFormat import와 selectedFormat 상태 수집도 제거했다.

## Deviations from Plan

None - 플랜대로 정확히 실행됨.

## Verification

- `grep -r "MinutesFormat" app/src/main/java/com/autominuting/` 결과: 0건
- `grep -r "KEY_MINUTES_FORMAT" app/src/main/java/com/autominuting/` 결과: 0건
- `grep -r "getMinutesFormatOnce" app/src/main/java/com/autominuting/` 결과: 0건
- `grep -r "MINUTES_FORMAT_KEY" app/src/main/java/com/autominuting/` 결과: 0건
- `grep "SUMMARY_PROMPT\|ACTION_ITEMS_PROMPT" GeminiEngine.kt` 결과: 0건
- GeminiEngine.kt에 STRUCTURED_PROMPT 존재 확인
- MinutesEngine.kt generate() 시그니처 확인: `suspend fun generate(transcriptText: String, customPrompt: String? = null): Result<String>`
- "완전 자동 모드"가 "회의록 설정" 섹션 내에 위치 확인
- "전사 설정" 섹션에는 STT 엔진만 포함 확인
- `./gradlew compileDebugKotlin` 빌드 성공 (CMake/NDK는 worktree submodule 미초기화로 별도)

## Known Stubs

None.
