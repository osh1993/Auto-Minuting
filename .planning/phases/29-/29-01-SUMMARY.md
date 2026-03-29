---
phase: 29-전사-카드-ux-개선
plan: 01
subsystem: ui
tags: [compose, dashboard, hybrid-mode, dropdown-menu, pipeline]

requires:
  - phase: 19-수동-회의록-생성
    provides: MinutesGenerationWorker, 수동 회의록 생성 플로우
provides:
  - 하이브리드 모드 전사 완료 시 대시보드 확인 배너 (회의록 생성/무시)
  - 전사 카드 회의록 버튼의 MoreVert 드롭다운 메뉴 이동
affects: [dashboard, transcripts]

tech-stack:
  added: []
  patterns: [combine-flow-dismissed-filter, hybrid-mode-conditional-banner]

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt

key-decisions:
  - "dismissed 파이프라인 ID를 MutableStateFlow<Set<Long>>로 관리하여 배너 필터링"
  - "하이브리드 모드 전사 완료 시 CircularProgressIndicator 대신 CheckCircle 아이콘 사용"

patterns-established:
  - "combine flow 패턴: 데이터 스트림 + 로컬 상태 결합으로 UI 필터링"

requirements-completed: [PIPE-01, PIPE-02]

duration: 8min
completed: 2026-03-29
---

# Phase 29 Plan 01: 전사 카드 UX 개선 Summary

**하이브리드 모드 전사 완료 시 대시보드 확인/무시 배너 추가 + 전사 카드 회의록 버튼을 MoreVert 메뉴로 이동하여 카드 높이 절약**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-29T08:51:18Z
- **Completed:** 2026-03-29T08:59:44Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- DashboardViewModel에 automationMode 관찰, generateMinutesForPipeline(), dismissPipeline() 추가
- DashboardScreen 배너에서 TRANSCRIBED + HYBRID 모드일 때 "회의록 생성"/"무시" 버튼 렌더링
- TranscriptsScreen 카드 하단의 FilledTonalButton/OutlinedButton을 MoreVert DropdownMenu로 이동하여 카드 면적 ~40dp 절약

## Task Commits

Each task was committed atomically:

1. **Task 1: 대시보드 하이브리드 모드 확인 플로우** - `124a2ca` (feat)
2. **Task 2: 전사 카드 회의록 버튼 MoreVert 메뉴 이동** - `c16aea4` (feat)

**Plan metadata:** (pending)

## Files Created/Modified
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt` - automationMode StateFlow, generateMinutesForPipeline(), dismissPipeline(), dismissed 필터링
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt` - HYBRID 모드 확인/무시 버튼 배너, CheckCircle 아이콘
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` - 회의록 작성/재생성을 DropdownMenuItem으로 이동, 카드 하단 버튼 제거

## Decisions Made
- dismissed 파이프라인 ID를 MutableStateFlow<Set<Long>>로 관리하여 앱 재시작 시 초기화 (영구 저장 불필요)
- 하이브리드 모드 전사 완료 배너에서 CircularProgressIndicator 대신 CheckCircle 아이콘으로 시각 구분

## Deviations from Plan

None - 플랜 그대로 실행됨.

## Issues Encountered
None

## User Setup Required
None - 외부 서비스 설정 불필요.

## Next Phase Readiness
- 하이브리드 모드 확인 플로우 완료, 다음 UI 개선 작업 진행 가능
- 전사 카드 높이 축소 완료로 목록 가독성 개선

---
*Phase: 29-전사-카드-ux-개선*
*Completed: 2026-03-29*
