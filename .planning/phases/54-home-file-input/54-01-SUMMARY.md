---
phase: 54-home-file-input
plan: 01
subsystem: presentation/dashboard
tags: [viewmodel, local-file, saf, pipeline, stateflow]
dependency_graph:
  requires: []
  provides: [DashboardViewModel.processLocalFile, DashboardViewModel.localFileState]
  affects: [DashboardScreen (Plan 02에서 UI 연결)]
tech_stack:
  added: []
  patterns: [ContentResolver.openInputStream + File.copyTo, OneTimeWorkRequestBuilder enqueue]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
decisions:
  - D-08: ViewModel 직접 구현 (ShareReceiverActivity 패턴 참조)
  - D-09: source="LOCAL_FILE" — 기존 SAMSUNG_SHARE, PLAUD_BLE, URL_DOWNLOAD와 동일 패턴
  - D-11: LocalFileState 별도 sealed interface 분리 (DownloadState와 혼동 방지)
  - D-12: ContentResolver.openInputStream + File.copyTo 방식 사용
metrics:
  duration: 약 7분
  completed: 2026-04-15T12:52:00Z
  tasks_completed: 1
  files_modified: 1
---

# Phase 54 Plan 01: DashboardViewModel processLocalFile() 구현 Summary

SAF URI를 앱 내부 저장소로 복사한 뒤 Meeting(source="LOCAL_FILE")을 insert하고 TranscriptionTriggerWorker를 enqueue하는 processLocalFile() 메서드를 DashboardViewModel에 추가함.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | DashboardViewModel.processLocalFile() 메서드 추가 | 9343541 | DashboardViewModel.kt |

## What Was Built

DashboardViewModel.kt에 다음 기능 추가:

1. **LocalFileState sealed interface** — Idle / Processing / Error 3상태
2. **localFileState StateFlow** — UI에서 파일 처리 상태 관찰 가능
3. **processLocalFile(uri: Uri, title: String)** — 핵심 진입점:
   - MIME 타입 기반 확장자 결정 (m4a/mp3/wav/ogg/flac)
   - `context.contentResolver.openInputStream(uri)` → `audio/local_${now}.ext` 복사
   - `Meeting(source="LOCAL_FILE", pipelineStatus=AUDIO_RECEIVED)` insert
   - `TranscriptionTriggerWorker` enqueue (automationMode 전달)
   - `observeTranscriptionProgress()` 호출로 기존 진행률 배너 연동
4. **clearLocalFileError()** — 에러 상태 초기화 헬퍼
5. **android.net.Uri import** 추가

## Verification

- `gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL (exit 0)
- grep 검증 7개 항목 모두 통과:
  - `fun processLocalFile(` ✓
  - `source = "LOCAL_FILE"` ✓
  - `OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>` ✓
  - `contentResolver.openInputStream` ✓
  - `LocalFileState` ✓
  - `val localFileState: StateFlow` ✓
  - `fun clearLocalFileError` ✓

## Deviations from Plan

없음 — 계획대로 정확히 구현됨.

## Self-Check: PASSED

- [x] DashboardViewModel.kt 수정 파일 존재: FOUND
- [x] 커밋 9343541 존재: FOUND
- [x] 컴파일 성공 확인: PASSED
