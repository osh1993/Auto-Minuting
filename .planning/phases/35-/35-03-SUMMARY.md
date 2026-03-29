---
phase: 35
plan: 3
subsystem: settings-ui, pipeline-workers
tags: [gap-closure, MinutesFormat-removal, refactor, build-fix]
dependency_graph:
  requires: [35-01, 35-02]
  provides: [MinutesFormat 참조 0건, assembleDebug 빌드 성공, 자동모드 Switch 위치 수정]
  affects: [SettingsScreen, TranscriptionTriggerWorker, PipelineNotificationHelper, TranscriptsViewModel, DashboardViewModel]
tech_stack:
  added: []
  patterns: [MinutesFormat enum 전면 제거 완료, 자동모드 Switch 섹션 재배치]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
    - app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
decisions:
  - "MinutesFormat enum 참조 전면 제거 — 4개 파일에서 11개 참조를 모두 제거하여 빌드 성공"
  - "자동모드 Switch를 전사 설정에서 회의록 설정 섹션 상단으로 이동 (의미적 일관성)"
  - "KEY_PROGRESS 상수 추가 — DashboardViewModel의 기존 참조 오류 해결 (Rule 3)"
metrics:
  duration: 5min
  completed: 2026-03-30
  tasks: 4
  files_modified: 4
requirements: [SET-01, SET-02]
---

# Phase 35 Plan 03: MinutesFormat 참조 제거 및 자동모드 Switch 이동 Summary

**One-liner:** MinutesFormat 잔존 참조 4개 파일 11건 전면 제거 및 자동모드 Switch를 회의록 설정 섹션으로 이동하여 assembleDebug 빌드 성공

---

## What Was Built

Phase 35의 Plans 01-02 실행 후 MinutesFormat.kt 파일은 삭제되었으나 4개 파일에 11개의 참조가 잔존하여 빌드가 실패하는 상태였다. 이 Plan에서 잔존 참조를 전면 제거하고 자동모드 Switch 위치를 올바른 섹션으로 이동하였다.

### 변경 내역

**TranscriptionTriggerWorker.kt:**
- `import com.autominuting.domain.model.MinutesFormat` 제거
- `val minutesFormat = inputData.getString(KEY_MINUTES_FORMAT) ?: MinutesFormat.STRUCTURED.name` 2라인 제거
- `workDataOf`에서 `MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat` 제거
- `notifyTranscriptionComplete()` 호출에서 `minutesFormat,` 인자 제거
- `const val KEY_MINUTES_FORMAT = "minutesFormat"` 상수 제거
- `const val KEY_PROGRESS = "progress"` 상수 추가 (DashboardViewModel 참조 오류 해결)

**PipelineNotificationHelper.kt:**
- `import com.autominuting.domain.model.MinutesFormat` 제거
- `notifyTranscriptionComplete()` 시그니처에서 `minutesFormat: String = MinutesFormat.STRUCTURED.name` 파라미터 제거
- Intent `putExtra("minutesFormat", minutesFormat)` 라인 제거
- `customPrompt` 파라미터 및 `putExtra("customPrompt", it)` 유지

**TranscriptsViewModel.kt:**
- `enqueueMinutesWorker()` 내 `val minutesFormat = userPreferencesRepository.getMinutesFormatOnce()` 제거
- `workDataOf`에서 `MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat.name` 제거

**SettingsScreen.kt:**
- `import com.autominuting.domain.model.MinutesFormat` 제거
- `val selectedFormat by viewModel.minutesFormat.collectAsStateWithLifecycle()` 제거
- "회의록 형식" Text 레이블 + `formatLabels mapOf(MinutesFormat...)` + `ExposedDropdownMenuBox` 블록 전체 삭제
- "완전 자동 모드" Switch Row + HYBRID 조건부 텍스트를 "전사 설정" 섹션에서 제거
- "완전 자동 모드" Switch Row + HYBRID 조건부 텍스트를 "회의록 설정" 섹션 상단에 추가

---

## Verification Results

| Check | Result |
|-------|--------|
| `grep -rn "MinutesFormat\|KEY_MINUTES_FORMAT\|getMinutesFormatOnce"` | 0건 |
| "완전 자동 모드" 텍스트가 "회의록 설정" 섹션 내부에 위치 | line 128, 섹션 시작 line 120 |
| "전사 설정" 섹션에 "완전 자동 모드" 없음 | 확인 |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL in 46s |
| `customPrompt` 파이프라인 코드 보존 | PipelineNotificationHelper customPrompt putExtra 유지 |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KEY_PROGRESS 상수 누락으로 인한 DashboardViewModel 컴파일 오류**
- **Found during:** Task 4 빌드 검증 (assembleDebug)
- **Issue:** `DashboardViewModel.kt:188`에서 `TranscriptionTriggerWorker.KEY_PROGRESS` 참조 — Worker에 해당 상수가 없어 컴파일 오류 발생. 이번 Plan에서 수정한 파일은 아니지만 빌드를 블로킹함
- **Fix:** `TranscriptionTriggerWorker` companion object에 `const val KEY_PROGRESS = "progress"` 상수 추가
- **Files modified:** `TranscriptionTriggerWorker.kt`
- **Commit:** 86b0eb5

---

## Known Stubs

없음 — 모든 변경 사항이 실제 코드 제거/이동이며, 새로운 스텁이 추가되지 않았다.

---

## Commits

| Hash | Description |
|------|-------------|
| 86b0eb5 | fix(35-03): MinutesFormat 참조 전면 제거 및 자동모드 Switch 이동 |

---

## Self-Check: PASSED

- FOUND: `.planning/phases/35-/35-03-SUMMARY.md`
- FOUND: commit 86b0eb5
- MinutesFormat 참조 건수: 0 (전체 코드베이스)
- assembleDebug BUILD SUCCESSFUL

---

## Phase 35 Completion Status

이 Plan 완료로 Phase 35의 전체 목표가 달성되었다:
- **SET-01:** MinutesFormat enum 전면 제거 — 완료 (파일 삭제 + 4개 파일 참조 제거)
- **SET-02:** 자동모드 Switch 회의록 설정 이동 — 완료
- **SET-03:** CUSTOM_PROMPT_MODE_ID 직접 입력 모드 — Plan 02에서 완료
