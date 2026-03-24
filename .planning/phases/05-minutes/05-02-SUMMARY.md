---
phase: 05-minutes
plan: 02
subsystem: ui
tags: [jetpack-compose, viewmodel, navigation, markdown, stateflow]

# Dependency graph
requires:
  - phase: 05-01
    provides: "MinutesGenerationWorker가 생성한 회의록 파일 (minutesPath)"
  - phase: 04-stt
    provides: "TranscriptsScreen/TranscriptEditScreen UI 패턴"
provides:
  - "MinutesViewModel: 회의록 목록 상태 관리"
  - "MinutesScreen: 회의록 목록 화면 (LazyColumn + PipelineStatus 칩)"
  - "MinutesDetailViewModel: 회의록 파일 내용 로드"
  - "MinutesDetailScreen: 회의록 Markdown 플레인텍스트 읽기 화면"
  - "Screen.MinutesDetail Navigation 경로"
affects: [07-ui-polish]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "flatMapLatest로 파일 내용 반응형 로드 (meeting -> minutesPath -> readText)"
    - "SelectionContainer로 읽기 전용 텍스트 선택 지원"

key-files:
  created:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt
  modified:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/Screen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt

key-decisions:
  - "Markdown을 플레인텍스트로 표시 (Phase 7에서 렌더러 업그레이드 예정)"
  - "회의록 있는 회의를 목록 상단에 정렬하여 접근성 향상"

patterns-established:
  - "DetailViewModel 파일 읽기: flatMapLatest + flowOn(Dispatchers.IO) 패턴"
  - "읽기 전용 상세 화면: Scaffold + TopAppBar + verticalScroll + SelectionContainer"

requirements-completed: [MIN-01, MIN-04]

# Metrics
duration: 3min
completed: 2026-03-24
---

# Phase 5 Plan 02: 회의록 목록/상세 UI Summary

**MinutesScreen(목록)과 MinutesDetailScreen(상세 읽기)으로 회의록 앱 내 확인 기능 구현, TranscriptsScreen 패턴 일관 적용**

## Performance

- **Duration:** 3min
- **Started:** 2026-03-24T12:48:24Z
- **Completed:** 2026-03-24T12:51:33Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- MinutesViewModel이 MeetingRepository에서 회의 목록을 수집하여 회의록 있는 항목을 상단에 정렬
- MinutesScreen이 LazyColumn으로 회의 목록을 표시하고 PipelineStatus별 SuggestionChip 상태 칩 제공
- MinutesDetailViewModel이 flatMapLatest로 회의록 파일 내용을 반응형으로 로드
- MinutesDetailScreen이 Markdown 내용을 플레인텍스트로 표시 (SelectionContainer로 텍스트 선택 가능)
- Screen.MinutesDetail 경로 추가 및 AppNavigation에 완전 연동

## Task Commits

Each task was committed atomically:

1. **Task 1: MinutesViewModel + MinutesScreen 목록 화면** - `ce5124d` (feat)
2. **Task 2: MinutesDetailScreen + Navigation 경로 연동** - `de17bd9` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt` - 회의록 목록 상태 관리 ViewModel
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` - 회의록 목록 화면 (LazyColumn + 상태 칩)
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailViewModel.kt` - 회의록 파일 내용 로드 ViewModel
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` - 회의록 상세 읽기 화면
- `app/src/main/java/com/autominuting/presentation/navigation/Screen.kt` - MinutesDetail 경로 추가
- `app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt` - MinutesDetail composable 등록 및 콜백 연결

## Decisions Made
- Markdown을 플레인텍스트로 표시: Phase 7에서 Markdown 렌더러로 업그레이드 예정이므로 현재는 Text 컴포넌트로 표시
- 회의록 있는 회의를 목록 상단에 정렬: sortedByDescending으로 minutesPath != null인 항목 우선 표시

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- JAVA_HOME 미설정으로 gradlew assembleDebug 빌드 검증 불가. grep 기반 acceptance criteria로 대체 검증 완료.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 회의록 목록/상세 UI 완성, Phase 7(UI 폴리시)에서 Markdown 렌더러 및 디자인 개선 가능
- Navigation 경로 완전 연동되어 목록 -> 상세 -> 뒤로가기 흐름 구현 완료

## Self-Check: PASSED

All 6 files verified present. Both commit hashes (ce5124d, de17bd9) confirmed in git log.

---
*Phase: 05-minutes*
*Completed: 2026-03-24*
