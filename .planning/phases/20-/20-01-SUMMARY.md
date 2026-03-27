---
phase: 20-transcripts-action-menu
plan: 01
subsystem: ui
tags: [compose, dropdown-menu, action-menu, share-intent, workmanager]

# Dependency graph
requires:
  - phase: 19-manual-minutes
    provides: TranscriptsViewModel.generateMinutes(), TranscriptsScreen 기본 구조
provides:
  - 전사 카드 MoreVert 드롭다운 액션 메뉴 (재전사/공유/삭제)
  - TranscriptsViewModel.retranscribe() - TranscriptionTriggerWorker enqueue
  - TranscriptsViewModel.shareTranscript() - ACTION_SEND Intent 공유
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - DropdownMenu + IconButton 패턴으로 카드별 액션 메뉴 제공
    - ViewModel에서 Activity Context를 받아 startActivity 호출하는 공유 패턴

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt

key-decisions:
  - "long-press 삭제를 DropdownMenu 삭제 항목으로 교체 (combinedClickable -> clickable)"
  - "재전사 시 기존 전사 파일 삭제 후 TranscriptionTriggerWorker 재enqueue"

patterns-established:
  - "카드 액션 메뉴: Box { IconButton + DropdownMenu } 패턴"

requirements-completed: [FILE-03]

# Metrics
duration: 2min
completed: 2026-03-27
---

# Phase 20 Plan 01: 전사 목록 액션 메뉴 Summary

**전사 카드에 MoreVert 드롭다운 메뉴(재전사/공유/삭제) 추가 및 ViewModel 재전사/공유 함수 구현**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-27T09:15:18Z
- **Completed:** 2026-03-27T09:17:28Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- ViewModel에 retranscribe() 함수 추가: 기존 전사 삭제 후 TranscriptionTriggerWorker enqueue
- ViewModel에 shareTranscript() 함수 추가: 전사 텍스트 파일 읽어 ACTION_SEND Intent로 공유
- 전사 카드에 MoreVert 아이콘 + DropdownMenu 액션 메뉴 (재전사/공유/삭제)
- 재전사는 오디오 파일 존재 + 적절한 상태일 때만, 공유는 전사 경로 있을 때만 조건부 표시
- 기존 long-press 삭제를 메뉴 삭제 항목으로 이동

## Task Commits

Each task was committed atomically:

1. **Task 1: ViewModel에 재전사/공유 함수 추가** - `04a5b88` (feat)
2. **Task 2: 전사 카드에 DropdownMenu 액션 메뉴 추가** - `af71aaf` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt` - retranscribe(), shareTranscript() 함수 추가
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` - MoreVert + DropdownMenu 액션 메뉴 UI

## Decisions Made
- long-press 삭제를 DropdownMenu 삭제 항목으로 교체 (combinedClickable에서 clickable로 변경)
- 재전사 시 기존 전사 파일을 먼저 삭제한 후 Worker를 enqueue하여 깨끗한 재전사 보장
- shareTranscript()에서 File 읽기 실패 시 try-catch로 안전 처리

## Deviations from Plan

None - 플랜대로 정확히 실행됨.

## Issues Encountered
None

## User Setup Required
None - 외부 서비스 설정 불필요.

## Next Phase Readiness
- 전사 목록 액션 메뉴 완성, Phase 21로 진행 가능

---
*Phase: 20-transcripts-action-menu*
*Completed: 2026-03-27*
