---
phase: 43-ux
plan: 01
subsystem: presentation
tags: [ux, ui, navigation, overflow-menu]
dependency_graph:
  requires: []
  provides: [card-tap-navigation, overflow-rename-menu]
  affects: [TranscriptsScreen, MinutesScreen]
tech_stack:
  added: []
  patterns: [overflow-menu-action-migration]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt
decisions:
  - 이름 변경 메뉴를 삭제 항목 바로 위에 배치하여 위험 액션과 분리
  - 회의록 카드에서는 공유와 삭제 사이에 배치
metrics:
  duration: 3.4min
  completed: "2026-04-03T04:32:13Z"
---

# Phase 43 Plan 01: 카드 터치 열기 + 이름 변경 메뉴 이동 Summary

전사/회의록 카드 제목 텍스트의 clickable 이름변경을 제거하고 overflow 메뉴로 이동하여 카드 탭 네비게이션 UX 일관성 확보

## What Was Done

### Task 1: 전사목록 카드 — 제목 클릭 이름변경 제거 + overflow 메뉴 추가
- `TranscriptsScreen.kt`의 제목 Text에서 `.clickable { onRenameRequest(meeting) }` 제거
- DropdownMenu에 "이름 변경" 항목 추가 (Edit 아이콘, 삭제 바로 위 배치)
- `Icons.Default.Edit` import 추가
- **Commit:** `8cd3c44`

### Task 2: 회의록 카드 — 제목 클릭 이름변경 제거 + overflow 메뉴 추가
- `MinutesScreen.kt`의 제목 Text에서 `.clickable { onRenameRequest(minutes) }` 및 불필요한 modifier 파라미터 제거
- DropdownMenu에 "이름 변경" 항목 추가 (Edit 아이콘, 공유와 삭제 사이 배치)
- `Icons.Default.Edit` import 추가
- **Commit:** `39c05ec`

## Verification Results

1. `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
2. TranscriptsScreen.kt에서 제목 Text의 `.clickable { onRenameRequest` 패턴 없음 확인
3. MinutesScreen.kt에서 제목 Text의 `.clickable { onRenameRequest` 패턴 없음 확인
4. TranscriptsScreen.kt DropdownMenu에 "이름 변경" 텍스트 존재 확인 (3회)
5. MinutesScreen.kt DropdownMenu에 "이름 변경" 텍스트 존재 확인 (3회)

## Decisions Made

- 이름 변경 메뉴 항목을 삭제 항목 바로 위에 배치 (전사목록), 공유-삭제 사이 배치 (회의록) — 위험 액션 분리 및 기존 메뉴 흐름 유지

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Self-Check: PASSED
