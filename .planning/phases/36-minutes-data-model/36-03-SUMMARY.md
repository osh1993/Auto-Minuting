---
phase: 36-minutes-data-model
plan: 03
subsystem: presentation, navigation
tags: [viewmodel, compose-screen, navigation, minutes-table, kotlin]

requires:
  - phase: 36
    plan: 02
    provides: MinutesDataRepository, MeetingRepository minutes 메서드 제거
provides:
  - MinutesViewModel Minutes 테이블 기반 CRUD
  - MinutesDetailViewModel minutesId 기반 상세 조회
  - MinutesScreen/MinutesDetailScreen Minutes 모델 기반
  - Navigation 라우팅 minutesId 기반
affects: [37, 38]

tech-stack:
  added: []
  patterns: [MinutesDataRepository 주입 패턴, minutesId 기반 Navigation 라우팅]

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/navigation/Screen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt

key-decisions:
  - "MinutesScreen에서 PipelineStatus 배지 완전 제거 (Minutes 테이블에 pipelineStatus 없음, Phase 38에서 새 디자인)"
  - "MinutesDetailScreen에서 Meeting 정보 영역 제거, Minutes 자체 정보(제목/생성시각)만 표시"
  - "TranscriptsScreen MinutesStatusBadge를 meeting.pipelineStatus == COMPLETED 조건으로 변경"

requirements-completed: [DATA-01, DATA-02]

duration: 7min
completed: 2026-03-30
---

# Phase 36 Plan 03: Presentation 레이어 Minutes 테이블 기반 교체 Summary

**MinutesViewModel/MinutesDetailViewModel을 MinutesDataRepository 기반으로 전환, Screen/Navigation을 minutesId 기반으로 교체, assembleDebug BUILD SUCCESSFUL**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-30T23:38:14Z
- **Completed:** 2026-03-30T23:45:21Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- MinutesViewModel: MeetingRepository -> MinutesDataRepository 주입, getAllMinutes() 기반 목록 조회
- MinutesViewModel: deleteMinutes/updateMinutesTitle/shareMinutes를 Minutes ID 기반으로 변경
- MinutesDetailViewModel: meetingId -> minutesId 기반 라우팅, getMinutesById() 사용
- MinutesScreen: Meeting 모델 참조 완전 제거, Minutes 모델 기반 카드 표시
- MinutesScreen: PipelineStatus 배지 제거 (Minutes 테이블에 pipelineStatus 컬럼 없음)
- MinutesDetailScreen: Meeting 정보 영역 -> Minutes 자체 정보(제목/생성시각) 표시
- Screen.kt: MinutesDetail route를 minutes/{minutesId}로 변경
- AppNavigation.kt: navArgument를 minutesId 기반으로 변경
- TranscriptsScreen: MinutesStatusBadge를 pipelineStatus == COMPLETED 조건으로 변경

## Task Commits

1. **Task 1: MinutesViewModel + MinutesDetailViewModel을 Minutes 테이블 기반으로 교체** - `e4c5606` (feat)
2. **Task 2: Screen + Navigation 라우팅을 minutesId 기반으로 교체 + 빌드 검증** - `7cc6425` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt` - MinutesDataRepository 주입, Minutes 기반 CRUD
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailViewModel.kt` - minutesId 기반 상세 조회
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` - Minutes 모델 기반 카드, PipelineStatus 배지 제거
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` - Minutes 모델 기반 상세 화면
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` - MinutesStatusBadge pipelineStatus 기반으로 변경
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt` - 누락된 Instant import 추가
- `app/src/main/java/com/autominuting/presentation/navigation/Screen.kt` - MinutesDetail route minutesId 기반
- `app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt` - navArgument minutesId 기반

## Decisions Made
- MinutesScreen에서 PipelineStatus 배지 완전 제거: Minutes 테이블에 pipelineStatus가 없으므로, Phase 38에서 새 디자인으로 추가
- MinutesDetailScreen에서 Meeting 정보(recordedAt, pipelineStatus) 제거: Minutes 자체 정보(제목, 생성시각)만 표시
- TranscriptsScreen MinutesStatusBadge: meeting.minutesPath != null -> meeting.pipelineStatus == COMPLETED으로 변경

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] TranscriptsViewModel.kt 누락된 Instant import 추가**
- **Found during:** Task 2 빌드 검증
- **Issue:** TranscriptsViewModel.updateTitle()에서 Instant.now() 사용하나 java.time.Instant import 누락
- **Fix:** import java.time.Instant 추가
- **Files modified:** TranscriptsViewModel.kt
- **Commit:** 7cc6425

## Known Stubs

None - 모든 화면이 실제 Minutes 데이터 소스에 연결됨.

## Issues Encountered
None

## User Setup Required
None

## Next Phase Readiness
- Phase 36 전체 완료: Entity/Dao/Migration(01) + Repository/Worker(02) + Presentation(03)
- Phase 37 진행 가능: 전사-회의록 독립 삭제 로직 구현
- Phase 38 진행 가능: 독립 아키텍처 UI 반영 (카드 디자인, 회의록 수 badge)

## Self-Check: PASSED

- All 8 modified files exist
- SUMMARY.md exists
- Commit e4c5606 found (Task 1)
- Commit 7cc6425 found (Task 2)
- assembleDebug BUILD SUCCESSFUL

---
*Phase: 36-minutes-data-model*
*Completed: 2026-03-30*
