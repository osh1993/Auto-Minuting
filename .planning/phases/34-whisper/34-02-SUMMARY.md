---
phase: 34-whisper
plan: 02
subsystem: stt-ui
tags: [whisper, progress-ui, notification, dashboard, transcripts]
dependency_graph:
  requires: [stt-progress-callback]
  provides: [stt-progress-ui]
  affects: [dashboard, transcripts, notifications]
tech_stack:
  added: []
  patterns: [workmanager-progress-observation, determinate-indeterminate-progress]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
decisions:
  - Worker setProgress + 알림 업데이트를 onProgress 콜백 내에서 동시 수행
  - TranscriptsScreen은 WorkInfo 관찰 대신 indeterminate 스피너로 단순화
metrics:
  duration: 3min
  completed: "2026-03-29T14:58:21Z"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 5
---

# Phase 34 Plan 02: Whisper 전사 진행률 UI 연결 Summary

Worker onProgress 콜백으로 시스템 알림에 determinate 프로그레스바를 표시하고, DashboardScreen 배너에 퍼센트 텍스트 + LinearProgressIndicator, TranscriptsScreen 카드에 CircularProgressIndicator 스피너를 추가하여 Whisper 전사 진행 상황을 3곳에서 확인 가능

## What Was Done

### Task 1: Worker 진행률 콜백 + 알림 업데이트
- **Commit:** df56c06
- PipelineNotificationHelper.updateProgress에 `progress: Int = -1` 파라미터 추가
- progress >= 0이면 `setProgress(100, progress, false)` determinate, < 0이면 indeterminate
- TranscriptionTriggerWorker에서 transcribe 호출 시 onProgress 트레일링 람다 전달
- 콜백 내에서 `setProgress(workDataOf(KEY_PROGRESS to percent))` + 알림 업데이트 동시 수행
- companion object에 `KEY_PROGRESS = "progress"` 상수 추가

### Task 2: DashboardScreen 배너 + TranscriptsScreen 카드 진행률 UI
- **Commit:** f4c7b8b
- DashboardViewModel에 `transcriptionProgress: StateFlow<Float>` + `observeTranscriptionProgress(UUID)` 추가
- WorkManager `getWorkInfoByIdFlow`로 Worker 진행률 관찰, 완료 시 0f 초기화
- URL 다운로드 파이프라인 enqueue 후 `observeTranscriptionProgress(workRequest.id)` 호출
- DashboardScreen에서 TRANSCRIBING 배너에 진행률 > 0이면 "전사 중 N%" + determinate LinearProgressIndicator
- TranscriptsScreen TranscriptionStatusBadge를 SuggestionChip에서 CircularProgressIndicator + "전사 중" 텍스트로 변경

## Data Flow

```
whisper.cpp progress_callback
  -> JNI -> WhisperEngine.onNativeProgress
    -> TranscriptionRepositoryImpl.onProgress
      -> TranscriptionTriggerWorker onProgress 람다
        -> setProgress(workDataOf) -- WorkInfo로 관찰 가능
        -> PipelineNotificationHelper.updateProgress(progress=N) -- 시스템 알림
      -> DashboardViewModel.observeTranscriptionProgress
        -> getWorkInfoByIdFlow -> transcriptionProgress StateFlow
          -> DashboardScreen LinearProgressIndicator
```

## Deviations from Plan

None - 플랜 그대로 실행됨.

## Known Stubs

None - 모든 UI에서 실제 진행률 데이터 표시.

## Decisions Made

1. **Worker setProgress + 알림 동시 업데이트**: onProgress 콜백 한 곳에서 WorkManager setProgress와 PipelineNotificationHelper.updateProgress를 모두 호출하여 코드 중복 방지.
2. **TranscriptsScreen indeterminate 스피너**: TranscriptsScreen에서는 WorkInfo를 직접 관찰하지 않고, TRANSCRIBING 상태에 CircularProgressIndicator를 표시하는 단순한 방식 채택. 정확한 퍼센트는 시스템 알림과 대시보드 배너에서 확인.

## Self-Check: PASSED
