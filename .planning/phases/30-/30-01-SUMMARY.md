---
phase: 30-prompt-template-selection
plan: 01
subsystem: ui, data
tags: [datastore, compose, workmanager, prompt-template]

# Dependency graph
requires:
  - phase: 29-transcript-card-ux
    provides: 전사 카드 UI 및 MoreVert 메뉴 구조
provides:
  - 기본 프롬프트 템플릿 ID DataStore 저장/조회
  - MinutesGenerationWorker 템플릿 ID 기반 프롬프트 해결
  - TranscriptionTriggerWorker FULL_AUTO 체이닝 시 templateId 전달
  - TranscriptsScreen/DashboardScreen 템플릿 선택 다이얼로그 연동
  - 설정 화면 기본 프롬프트 템플릿 드롭다운
affects: [pipeline, settings, dashboard, transcripts]

# Tech tracking
tech-stack:
  added: []
  patterns: [기본 템플릿 ID 0L=미설정 패턴, Worker inputData 템플릿/커스텀프롬프트 우선순위]

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt

key-decisions:
  - "프롬프트 해결 우선순위: customPrompt > templateId > minutesFormat 폴백"
  - "기본 템플릿 ID 0L을 미설정 상태로 사용하여 매번 선택 모드 구현"

patterns-established:
  - "enqueueMinutesWorker 공통 헬퍼 패턴: ViewModel에서 Worker enqueue 로직 중복 제거"

requirements-completed: [PIPE-03, PIPE-04]

# Metrics
duration: 7min
completed: 2026-03-29
---

# Phase 30 Plan 01: 프롬프트 템플릿 선택 Summary

**기본 템플릿 DataStore 설정 + ManualMinutesDialog 연동으로 회의록 생성 시 템플릿 선택/자동 적용 구현**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-29T09:05:50Z
- **Completed:** 2026-03-29T09:13:03Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- UserPreferencesRepository에 기본 프롬프트 템플릿 ID DataStore 저장/조회 추가
- MinutesGenerationWorker가 templateId/customPrompt 기반으로 프롬프트를 해결하여 회의록 생성
- TranscriptsScreen/DashboardScreen에서 기본 템플릿 미설정 시 ManualMinutesDialog 표시
- 설정 화면에 기본 프롬프트 템플릿 드롭다운 추가 (매번 선택 + 템플릿 목록)
- FULL_AUTO 체이닝에서 templateId가 Worker에 전달되어 자동 모드에서도 적용

## Task Commits

Each task was committed atomically:

1. **Task 1: 데이터 레이어 -- 기본 템플릿 ID DataStore + Worker 템플릿 적용** - `7972711` (feat)
2. **Task 2: UI 레이어 -- 템플릿 선택 다이얼로그 + 설정 기본 템플릿 + Dashboard 연동** - `29720e8` (feat)

## Files Created/Modified
- `UserPreferencesRepository.kt` - DEFAULT_TEMPLATE_ID_KEY, defaultTemplateId Flow, setter/getter 추가
- `MinutesGenerationWorker.kt` - KEY_TEMPLATE_ID/KEY_CUSTOM_PROMPT 상수, PromptTemplateRepository 주입, 프롬프트 해결 로직
- `TranscriptionTriggerWorker.kt` - KEY_TEMPLATE_ID 상수, FULL_AUTO 체이닝 시 templateId 전달
- `TranscriptsViewModel.kt` - templates/defaultTemplateId StateFlow, generateMinutesWithTemplate, enqueueMinutesWorker 헬퍼
- `TranscriptsScreen.kt` - meetingForTemplateSelect 상태, ManualMinutesDialog 표시 로직
- `DashboardViewModel.kt` - templates/defaultTemplateId StateFlow, generateMinutesForPipelineWithTemplate, enqueueMinutesWorker 헬퍼
- `DashboardScreen.kt` - showPipelineTemplateDialog, ManualMinutesDialog 연동
- `SettingsViewModel.kt` - templates/defaultTemplateId StateFlow, setDefaultTemplateId
- `SettingsScreen.kt` - 기본 프롬프트 템플릿 드롭다운 (매번 선택 + 템플릿 목록)

## Decisions Made
- 프롬프트 해결 우선순위: customPrompt > templateId > minutesFormat 폴백 -- 유연한 확장성
- 기본 템플릿 ID 0L = 미설정 상태 -- 별도 null 처리 없이 Long 타입으로 DataStore 저장
- 기존 MinutesFormat 드롭다운은 폴백용으로 유지 -- 하위 호환성 보장

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all data flows are wired to real sources.

## Next Phase Readiness
- 프롬프트 템플릿 선택 기능 완성, 다음 Phase로 진행 가능
- 기본 3종 프리셋(구조화/요약/액션아이템)은 ensureDefaultTemplates()로 이미 DB에 존재

## Self-Check: PASSED

---
*Phase: 30-prompt-template-selection*
*Completed: 2026-03-29*
