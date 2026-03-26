---
phase: 16-file-delete
plan: 01
subsystem: presentation, data, domain
tags: [delete, multi-select, transcript, minutes, ui]
dependency_graph:
  requires: []
  provides: [deleteMinutesOnly, deleteTranscript, multi-select-ui]
  affects: [MinutesScreen, TranscriptsScreen, MeetingRepository]
tech_stack:
  added: []
  patterns: [combinedClickable, multi-select-mode, BackHandler]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt
    - app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
decisions:
  - 회의록 삭제 시 전사 파일 보존 (minutesPath만 null, pipelineStatus를 TRANSCRIBED로 되돌림)
  - 전사 삭제 시 연관 회의록도 함께 삭제 (transcriptPath+minutesPath null, AUDIO_SAVED로 되돌림)
metrics:
  duration: 3min
  completed: "2026-03-26T08:17:13Z"
  tasks_completed: 2
  files_modified: 7
---

# Phase 16 Plan 01: 파일 삭제 기능 Summary

회의록 다중 선택 삭제(전사 보존)와 전사 파일 별도 삭제 UI를 구현하여 사용자가 회의록과 전사를 독립적으로 관리할 수 있도록 개선

## What Was Done

### Task 1: 회의록 다중 선택 삭제 (전사 보존) — FILE-02
**Commit:** `eea2c40`

- MeetingDao에 `clearMinutesPath` 쿼리 추가 (minutesPath null, 상태 TRANSCRIBED)
- MeetingDao에 `clearTranscriptPath` 쿼리 추가 (transcriptPath+minutesPath null, 상태 AUDIO_SAVED)
- MeetingRepository 인터페이스에 `deleteMinutesOnly`, `deleteTranscript` 메서드 추가
- MeetingRepositoryImpl에 파일 삭제 + DB 업데이트 구현 (고아 파일 > 고아 레코드 원칙 준수)
- MinutesViewModel에서 `deleteMeeting` -> `deleteMinutesOnly` 호출로 변경, `deleteSelectedMinutes` 일괄 삭제 추가
- MinutesScreen에 long-press 다중 선택 모드 구현: TopAppBar (N개 선택됨 + 삭제/취소), Checkbox, BackHandler
- 삭제 확인 다이얼로그 메시지를 "전사 파일은 보존됩니다"로 변경

### Task 2: 전사 파일 별도 삭제 — FILE-03
**Commit:** `1c97069`

- TranscriptsViewModel에 `deleteTranscript(id)` 메서드 추가
- TranscriptsScreen에 `meetingToDelete` 상태 + 삭제 확인 다이얼로그 추가
- TranscriptMeetingCard를 `combinedClickable`로 변경 (onClick: 편집, onLongClick: 삭제)
- 다이얼로그에 "연관된 회의록도 함께 삭제됩니다" 안내 표시

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

1. **회의록 삭제 = 전사 보존**: 회의록 화면에서의 삭제는 minutesPath만 null로 설정하고 transcriptPath를 보존한다. 상태는 TRANSCRIBED로 되돌린다.
2. **전사 삭제 = 회의록 연쇄 삭제**: 전사를 삭제하면 연관 회의록도 무효하므로 함께 삭제한다. 상태는 AUDIO_SAVED로 되돌린다.

## Known Stubs

None - all functionality is fully wired.

## Self-Check: PASSED

- All 7 modified files exist on disk
- Commit eea2c40 (Task 1) verified in git log
- Commit 1c97069 (Task 2) verified in git log
