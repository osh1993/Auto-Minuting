---
phase: 38-independent-ui
plan: 01
subsystem: data-viewmodel
tags: [room-dao, join-query, viewmodel, stateflow]
dependency_graph:
  requires: []
  provides: [minutes-with-meeting-title-query, minutes-count-per-meeting-query, minutes-ui-model, minutes-count-map]
  affects: [38-02]
tech_stack:
  added: []
  patterns: [room-left-join-pojo, group-by-count-query, stateflow-mapping]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/local/dao/MinutesDao.kt
    - app/src/main/java/com/autominuting/domain/repository/MinutesDataRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesDataRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
decisions:
  - DAO JOIN 접근법 채택 (ViewModel combine 대비 효율적)
  - 하위 호환용 minutes 프로퍼티 유지하여 기존 MinutesScreen 컴파일 보장
metrics:
  duration: 4m 41s
  completed: 2026-03-31
---

# Phase 38 Plan 01: 데이터/ViewModel 레이어 Summary

MinutesDao에 LEFT JOIN/GROUP BY 쿼리를 추가하고, ViewModel에서 출처 전사명과 meetingId별 회의록 수를 StateFlow로 제공

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | MinutesDao JOIN/count 쿼리 + Repository 인터페이스/구현 추가 | 8dc5005 | MinutesDao.kt, MinutesDataRepository.kt, MinutesDataRepositoryImpl.kt |
| 2 | MinutesViewModel + TranscriptsViewModel StateFlow 변경 | 115cb81 | MinutesViewModel.kt, TranscriptsViewModel.kt |

## What Was Built

1. **MinutesDao JOIN 쿼리**: `getAllMinutesWithMeetingTitle()` - minutes 테이블과 meetings 테이블을 LEFT JOIN하여 출처 Meeting 제목을 함께 반환
2. **MinutesDao count 쿼리**: `getMinutesCountPerMeeting()` - meetingId별 회의록 수를 GROUP BY로 일괄 조회
3. **MinutesWithMeetingTitle POJO**: Room 쿼리 결과를 매핑하는 데이터 클래스
4. **MinutesCountPerMeeting POJO**: GROUP BY count 결과를 매핑하는 데이터 클래스
5. **MinutesDataRepository 인터페이스 확장**: 도메인 레이어에 `Pair<Minutes, String?>` / `Map<Long, Int>` 반환 메서드 추가
6. **MinutesUiModel**: Minutes + meetingTitle을 포함하는 UI 모델 클래스
7. **MinutesViewModel.minutesUiModels**: 검색 + 출처 전사명 포함 StateFlow
8. **TranscriptsViewModel.minutesCountMap**: meetingId별 회의록 수 Map StateFlow

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] MinutesScreen 컴파일 호환성 유지**
- **발견 시점:** Task 2
- **문제:** `val minutes` -> `val minutesUiModels` 이름 변경 시 MinutesScreen.kt에서 `viewModel.minutes` 참조가 깨짐
- **수정:** 하위 호환용 `val minutes` 프로퍼티를 추가하여 minutesUiModels에서 Minutes만 추출. Plan 02에서 MinutesScreen 전환 후 제거 예정
- **수정 파일:** MinutesViewModel.kt
- **커밋:** 115cb81

## Verification Results

- `./gradlew :app:kspDebugKotlin` - BUILD SUCCESSFUL (Room/Hilt 코드 생성 포함)
- `./gradlew :app:compileDebugKotlin` - BUILD SUCCESSFUL (경고만, 에러 없음)
- MinutesDao에 LEFT JOIN + GROUP BY 쿼리 존재 확인
- MinutesDataRepository 인터페이스에 새 메서드 2개 존재 확인
- MinutesViewModel.minutesUiModels가 MinutesUiModel 리스트 반환 확인
- TranscriptsViewModel.minutesCountMap이 Map<Long, Int> 반환 확인

## Known Stubs

None - 모든 데이터 파이프라인이 실제 Room 쿼리에 연결되어 있음.

## Self-Check: PASSED

- All 5 modified files exist on disk
- Commit 8dc5005 (Task 1) found in git log
- Commit 115cb81 (Task 2) found in git log
