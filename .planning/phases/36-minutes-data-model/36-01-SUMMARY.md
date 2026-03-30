---
phase: 36-minutes-data-model
plan: 01
subsystem: database
tags: [room, migration, entity, dao, foreign-key, kotlin]

requires:
  - phase: 35
    provides: PromptTemplate 시스템 (templateId 참조 기반)
provides:
  - MinutesEntity Room Entity (FK meetingId -> meetings, SET_NULL)
  - Minutes 도메인 모델
  - MinutesDao CRUD DAO (Flow 기반 조회)
  - Room DB v4->v5 마이그레이션 (데이터 이관 + 컬럼 제거)
  - MeetingEntity/Meeting에서 minutesPath/minutesTitle 제거
  - PipelineStatus에서 MINUTES_ONLY 제거
affects: [36-02, 36-03, 37, 38]

tech-stack:
  added: []
  patterns: [Room 테이블 재생성 마이그레이션 패턴, ForeignKey SET_NULL 고아 보존 패턴]

key-files:
  created:
    - app/src/main/java/com/autominuting/data/local/entity/MinutesEntity.kt
    - app/src/main/java/com/autominuting/domain/model/Minutes.kt
    - app/src/main/java/com/autominuting/data/local/dao/MinutesDao.kt
  modified:
    - app/src/main/java/com/autominuting/data/local/AppDatabase.kt
    - app/src/main/java/com/autominuting/di/DatabaseModule.kt
    - app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt
    - app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt
    - app/src/main/java/com/autominuting/domain/model/Meeting.kt
    - app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt

key-decisions:
  - "ForeignKey onDelete=SET_NULL로 Meeting 삭제 시 Minutes 보존"
  - "SQLite 테이블 재생성 패턴으로 minutesPath/minutesTitle 컬럼 제거 (DROP COLUMN 미지원)"
  - "MINUTES_ONLY -> COMPLETED 상태 변환을 마이그레이션 SQL에서 처리"

patterns-established:
  - "Room 테이블 재생성 패턴: CREATE new -> INSERT INTO new SELECT -> DROP old -> RENAME"
  - "FK SET_NULL 패턴: 부모 삭제 시 자식 보존 (meetingId=NULL)"

requirements-completed: [DATA-01]

duration: 4min
completed: 2026-03-30
---

# Phase 36 Plan 01: Minutes 데이터 모델 기반 구축 Summary

**MinutesEntity/MinutesDao/Minutes 도메인 모델 신설 + Room DB v4->v5 마이그레이션으로 기존 minutesPath/minutesTitle 데이터 이관 및 컬럼 제거**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-30T23:22:07Z
- **Completed:** 2026-03-30T23:26:43Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- MinutesEntity, Minutes 도메인 모델, MinutesDao 3개 파일 신설 (ForeignKey SET_NULL + Index)
- Room DB v4->v5 마이그레이션: 5단계 SQL (minutes 테이블 생성, 데이터 이관, meetings 테이블 재생성, MINUTES_ONLY->COMPLETED 변환, 교체)
- MeetingEntity/Meeting/MeetingDao/PipelineStatus에서 minutes 관련 필드/메서드 완전 제거

## Task Commits

각 Task를 원자적으로 커밋:

1. **Task 1: MinutesEntity + Minutes 도메인 모델 + MinutesDao 신설** - `52c3a89` (feat)
2. **Task 2: Room DB v5 마이그레이션 + MeetingEntity/MeetingDao/PipelineStatus 정리** - `0ae97e2` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/data/local/entity/MinutesEntity.kt` - Minutes Room Entity (FK, Index, toDomain/fromDomain)
- `app/src/main/java/com/autominuting/domain/model/Minutes.kt` - Minutes 순수 도메인 모델
- `app/src/main/java/com/autominuting/data/local/dao/MinutesDao.kt` - Minutes CRUD DAO (11개 메서드)
- `app/src/main/java/com/autominuting/data/local/AppDatabase.kt` - version=5, MinutesEntity 등록, minutesDao(), MIGRATION_4_5
- `app/src/main/java/com/autominuting/di/DatabaseModule.kt` - MIGRATION_4_5 등록, provideMinutesDao 추가
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` - minutesPath/minutesTitle 필드 제거
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` - updateMinutes/updateMinutesTitle/clearMinutesPath/markMinutesOnly 제거
- `app/src/main/java/com/autominuting/domain/model/Meeting.kt` - minutesPath/minutesTitle 필드 제거
- `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` - MINUTES_ONLY 값 제거

## Decisions Made
- ForeignKey onDelete=SET_NULL 선택: Meeting 삭제 시 Minutes Row가 보존되어 고아 회의록으로 남음 (Phase 37 요구사항 충족)
- SQLite 테이블 재생성 패턴 사용: minSdk 31의 SQLite 3.32.2가 DROP COLUMN 미지원
- MINUTES_ONLY -> COMPLETED 변환을 마이그레이션 SQL CASE WHEN으로 처리: enum 제거 후 파싱 오류 방지

## Deviations from Plan

None - 플랜을 정확히 따라 실행함.

## Known Compilation Errors

이 Plan은 의도적으로 데이터 레이어만 변경하므로, 다음 파일에서 컴파일 에러가 발생한다:
- `MeetingRepositoryImpl` - minutesPath/minutesTitle/MINUTES_ONLY 참조
- `MinutesGenerationWorker` - meetingDao.updateMinutes() 호출
- `MinutesViewModel/MinutesDetailViewModel` - Meeting.minutesPath 참조
- `MinutesScreen/MinutesDetailScreen` - Meeting.minutesPath/minutesTitle 참조
- `TranscriptsViewModel` - MINUTES_ONLY 참조, regenerateMinutes() 워크어라운드

이 에러들은 Plan 02 (Repository/Worker 계층)와 Plan 03 (Presentation 계층)에서 순차적으로 해결한다.

## Issues Encountered
None

## User Setup Required
None - 외부 서비스 설정 불필요.

## Next Phase Readiness
- MinutesEntity/MinutesDao가 준비되어 Plan 02에서 MinutesRepository + Worker 변경 가능
- MIGRATION_4_5가 등록되어 기존 사용자 데이터 이관 보장
- Plan 02에서 MeetingRepositoryImpl, MinutesGenerationWorker의 컴파일 에러 해결 예정

## Self-Check: PASSED

- All 3 created files exist (MinutesEntity.kt, Minutes.kt, MinutesDao.kt)
- SUMMARY.md exists
- Commit 52c3a89 found
- Commit 0ae97e2 found

---
*Phase: 36-minutes-data-model*
*Completed: 2026-03-30*
