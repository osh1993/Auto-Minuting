---
phase: 38-independent-ui
plan: 02
subsystem: ui
tags: [jetpack-compose, material3, navigation, suggestion-chip, badge]
dependency_graph:
  requires:
    - phase: 38-01
      provides: MinutesUiModel StateFlow, minutesCountMap StateFlow
  provides:
    - MinutesScreen 출처 전사명 표시 + 탭 네비게이션
    - TranscriptsScreen 회의록 수 MinutesCountBadge
    - AppNavigation onSourceTranscriptClick 콜백 연결
  affects: []
tech_stack:
  added: []
  patterns: [ui-model-collectAsState, count-badge-conditional-render, cross-screen-navigation-callback]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt
key_decisions:
  - MinutesStatusBadge를 MinutesCountBadge로 완전 교체 (PipelineStatus 기반 -> 실제 count 기반)
  - meetingId NULL 시 onSurfaceVariant 색상 + 클릭 비활성화로 삭제된 전사 표현
patterns-established:
  - "UiModel collectAsState: ViewModel의 UiModel StateFlow를 Screen에서 직접 수집하여 도메인 모델 + UI 부가 정보를 함께 전달"
  - "조건부 Badge 렌더링: count == 0이면 컴포저블 자체를 렌더링하지 않는 패턴"
requirements-completed: [UI5-01, UI5-02]
duration: 3m 38s
completed: 2026-03-31
---

# Phase 38 Plan 02: UI 레이어 Summary

**회의록 카드에 출처 전사명 표시 + 전사 카드에 실제 회의록 수 badge + 출처 전사 탭 네비게이션 연결**

## Performance

- **Duration:** 3m 38s
- **Started:** 2026-03-31T00:31:44Z
- **Completed:** 2026-03-31T00:35:22Z
- **Tasks:** 2 (1 code task + 1 build verification)
- **Files modified:** 3

## Accomplishments

- MinutesScreen에서 minutesUiModels StateFlow를 수집하여 출처 전사명을 카드에 표시 (meetingId NULL 시 "삭제된 전사")
- TranscriptsScreen에서 PipelineStatus 기반 MinutesStatusBadge를 실제 count 기반 MinutesCountBadge로 교체 (0개면 미표시)
- AppNavigation에서 onSourceTranscriptClick 콜백을 연결하여 출처 전사 탭 시 TranscriptEdit 화면으로 이동

## Task Commits

Each task was committed atomically:

1. **Task 1: MinutesScreen 출처 전사 표시 + TranscriptsScreen badge + AppNavigation 연결** - `cafd6a1` (feat)
2. **Task 2: assembleDebug 빌드 검증** - 코드 변경 없음 (BUILD SUCCESSFUL 확인)

## Files Created/Modified

- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` - minutesUiModels 수집, 출처 전사명 Row 추가, onSourceClick 콜백 연결
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` - minutesCountMap 수집, MinutesCountBadge 추가, MinutesStatusBadge 삭제
- `app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt` - onSourceTranscriptClick 콜백으로 TranscriptEdit 네비게이션 연결

## Decisions Made

- MinutesStatusBadge를 완전히 제거하고 MinutesCountBadge로 교체. PipelineStatus 기반의 "회의록 완료/미작성" 표시 대신 실제 Minutes 테이블 count를 사용하여 정확한 정보를 제공
- meetingId가 null인 경우 클릭 비활성화 + onSurfaceVariant 색상, null이 아닌 경우 primary 색상으로 링크 느낌을 주어 클릭 가능함을 시각적으로 표현
- GENERATING_MINUTES 상태일 때는 기존 "회의록 생성 중" 칩을 유지하여 진행 중 상태를 사용자에게 전달

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None - 모든 UI가 실제 ViewModel StateFlow에 연결되어 있음.

## Issues Encountered

None

## User Setup Required

None - UI 코드 변경만 포함.

## Next Phase Readiness

- Phase 38 완료. 전사-회의록 1:N 독립 아키텍처가 데이터/ViewModel/UI 전 레이어에 반영됨
- MinutesViewModel의 하위 호환용 `minutes` 프로퍼티는 더 이상 사용되지 않으므로 향후 정리 가능

---
*Phase: 38-independent-ui*
*Completed: 2026-03-31*
