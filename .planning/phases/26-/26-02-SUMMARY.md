---
phase: 26-minutes-title-actions
plan: 02
subsystem: ui
tags: [compose, dropdown-menu, minutes-title, share-intent, meeting-card]

requires:
  - phase: 26-minutes-title-actions
    provides: minutesTitle 필드가 추가된 DB 스키마 (v4), MeetingRepository.updateMinutesTitle API
provides:
  - MinutesScreen MoreVert 액션 메뉴 (공유/삭제)
  - 회의록 제목(minutesTitle) 우선 표시 UI
  - 회의록 이름 편집 다이얼로그
  - MinutesViewModel.shareMinutes 함수
affects: [minutes-detail, meeting-card]

tech-stack:
  added: []
  patterns: [MoreVert + DropdownMenu 회의록 카드 패턴, minutesTitle 우선 표시 패턴]

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt

key-decisions:
  - "카드 제목 클릭으로 이름 편집 다이얼로그 진입 (Phase 25 TranscriptsScreen 패턴 재사용)"
  - "공유 시 EXTRA_SUBJECT에 minutesTitle 우선, 없으면 title 사용"

patterns-established:
  - "MinutesScreen MoreVert 패턴: TranscriptsScreen과 동일한 DropdownMenu 구조 적용"

requirements-completed: [NAME-04, UX-01]

duration: 2min
completed: 2026-03-28
---

# Phase 26 Plan 02: 회의록 카드 액션 메뉴 및 이름 편집 UI Summary

**MinutesScreen에 MoreVert 드롭다운(공유/삭제), minutesTitle 우선 표시, 이름 편집 다이얼로그 추가**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-28T12:42:52Z
- **Completed:** 2026-03-28T12:45:20Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- MinutesViewModel에 updateMinutesTitle, shareMinutes 함수 추가
- 회의록 카드 제목을 minutesTitle 우선 표시로 변경
- Delete IconButton을 MoreVert + DropdownMenu(공유/삭제)로 교체
- Phase 25 패턴 재사용한 이름 편집 다이얼로그 추가

## Task Commits

Each task was committed atomically:

1. **Task 1: MinutesViewModel에 updateMinutesTitle + shareMinutes 함수 추가** - `52fa836` (feat)
2. **Task 2: MinutesScreen 카드에 MoreVert 메뉴 + minutesTitle 표시 + 이름 편집** - `dc16289` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt` - updateMinutesTitle, shareMinutes 함수 추가
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` - MoreVert 메뉴, minutesTitle 표시, 이름 편집 다이얼로그

## Decisions Made
- 카드 제목 클릭으로 이름 편집 다이얼로그 진입 (Phase 25 TranscriptsScreen 패턴 재사용)
- 공유 시 EXTRA_SUBJECT에 minutesTitle 우선, 없으면 title 사용

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 26 (회의록 제목 및 액션) 전체 완료
- minutesTitle 데이터 레이어 + UI 표시/편집/공유 파이프라인 완성

---
*Phase: 26-minutes-title-actions*
*Completed: 2026-03-28*
