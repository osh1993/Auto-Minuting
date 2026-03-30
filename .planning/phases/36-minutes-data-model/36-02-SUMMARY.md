---
phase: 36-minutes-data-model
plan: 02
subsystem: repository, worker, viewmodel
tags: [repository, hilt, worker, viewmodel, minutes-crud, kotlin]

requires:
  - phase: 36
    plan: 01
    provides: MinutesEntity, MinutesDao, Minutes 도메인 모델
provides:
  - MinutesDataRepository 인터페이스 (회의록 CRUD)
  - MinutesDataRepositoryImpl 구현체
  - MeetingRepository에서 minutes 관련 메서드 제거
  - MinutesGenerationWorker Minutes 테이블 INSERT 기반
  - TranscriptsViewModel 워크어라운드 제거
affects: [36-03, 37, 38]

tech-stack:
  added: []
  patterns: [MinutesDataRepository CRUD 패턴, Worker에서 별도 테이블 INSERT 패턴]

key-files:
  created:
    - app/src/main/java/com/autominuting/domain/repository/MinutesDataRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesDataRepositoryImpl.kt
  modified:
    - app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt
    - app/src/main/java/com/autominuting/di/RepositoryModule.kt
    - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt

key-decisions:
  - "MinutesDataRepository로 명명하여 기존 MinutesRepository(Gemini API 호출)와 이름 충돌 방지"
  - "Meeting 삭제 시 Minutes 파일 보존 (FK SET_NULL 정책 일관성)"
  - "regenerateMinutes()에서 새 Meeting Row 생성 대신 기존 meetingId 재사용"

requirements-completed: [DATA-02]

duration: 3min
completed: 2026-03-30
---

# Phase 36 Plan 02: Repository/Worker/ViewModel Minutes 테이블 기반 교체 Summary

**MinutesDataRepository CRUD 신설, MeetingRepository에서 minutes 메서드 4개 제거, Worker의 Minutes 테이블 INSERT 전환, regenerateMinutes() 워크어라운드 제거**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-30T23:30:22Z
- **Completed:** 2026-03-30T23:33:46Z
- **Tasks:** 1
- **Files modified:** 7

## Accomplishments
- MinutesDataRepository 인터페이스 + MinutesDataRepositoryImpl 구현체 신설 (9개 CRUD 메서드)
- MeetingRepository에서 deleteMinutesOnly/deleteTranscript/archiveAsMinutesOnly/updateMinutesTitle 4개 메서드 제거
- MeetingRepositoryImpl.deleteMeeting()에서 minutesPath 파일 삭제 로직 제거 (오디오/전사 파일만 삭제)
- MinutesGenerationWorker: meetingDao.updateMinutes() -> minutesDao.insert() + meetingDao.updatePipelineStatus()
- TranscriptsViewModel.regenerateMinutes(): copy(id=0) + insertMeeting 워크어라운드 제거, 기존 meetingId로 enqueue
- TranscriptsViewModel.deleteTranscript(): deleteTranscript() -> deleteMeeting() (FK SET_NULL로 Minutes 보존)
- RepositoryModule에 bindMinutesDataRepository 바인딩 추가

## Task Commits

1. **Task 1: MinutesDataRepository 신설 + MeetingRepository 정리 + Worker/ViewModel 로직 교체** - `abaf3a2` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/domain/repository/MinutesDataRepository.kt` - 회의록 CRUD 인터페이스 (9개 메서드)
- `app/src/main/java/com/autominuting/data/repository/MinutesDataRepositoryImpl.kt` - MinutesDao 기반 CRUD 구현체 (파일 삭제 포함)
- `app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt` - minutes 관련 4개 메서드 제거
- `app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt` - minutes 메서드 구현 제거, deleteMeeting에서 minutes 파일 삭제 로직 제거
- `app/src/main/java/com/autominuting/di/RepositoryModule.kt` - bindMinutesDataRepository 바인딩 추가
- `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` - minutesDao 주입, Minutes 테이블 INSERT + PipelineStatus 업데이트
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt` - regenerateMinutes 워크어라운드 제거, deleteTranscript -> deleteMeeting

## Decisions Made
- MinutesDataRepository로 명명: 기존 MinutesRepository(Gemini API 호출 전용)와 역할 분리
- Meeting 삭제 시 Minutes 파일 보존: FK SET_NULL 정책과 일관성 유지, 고아 회의록이 파일도 보존
- regenerateMinutes()에서 기존 meetingId 재사용: Worker가 새 Minutes Row를 INSERT하므로 새 Meeting Row 불필요

## Deviations from Plan

None - 플랜을 정확히 따라 실행함.

## Known Compilation Errors

이 Plan은 데이터/Worker/TranscriptsViewModel 레이어만 변경하므로, Presentation 레이어에서 컴파일 에러가 남아있을 수 있다:
- `MinutesViewModel` - Meeting.minutesPath 참조 (Plan 03에서 해결)
- `MinutesDetailViewModel` - Meeting.minutesPath 참조 (Plan 03에서 해결)
- `MinutesScreen/MinutesDetailScreen` - Meeting.minutesPath/minutesTitle 참조 (Plan 03에서 해결)

## Known Stubs

None - 모든 메서드가 실제 로직으로 구현됨.

## Issues Encountered
None

## User Setup Required
None

## Next Phase Readiness
- MinutesDataRepository가 준비되어 Plan 03에서 Presentation 레이어 마이그레이션 가능
- MinutesGenerationWorker가 Minutes 테이블에 INSERT하므로 회의록 생성 파이프라인 동작
- TranscriptsViewModel.regenerateMinutes()가 정리되어 중복 Meeting Row 문제 해결

## Self-Check: PASSED

- All 2 created files exist (MinutesDataRepository.kt, MinutesDataRepositoryImpl.kt)
- SUMMARY.md exists
- Commit abaf3a2 found

---
*Phase: 36-minutes-data-model*
*Completed: 2026-03-30*
