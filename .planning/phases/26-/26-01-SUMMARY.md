---
phase: 26-minutes-title-actions
plan: 01
subsystem: database
tags: [room, migration, gemini, meeting-title, worker]

requires:
  - phase: 08-foundation-hardening
    provides: Room DB v2 마이그레이션 패턴
provides:
  - minutesTitle 필드가 추가된 DB 스키마 (v4)
  - MinutesGenerationWorker에서 Gemini 응답 첫 줄 자동 제목 추출
  - MeetingRepository.updateMinutesTitle API
affects: [26-02, minutes-ui, meeting-card]

tech-stack:
  added: []
  patterns: [Room ALTER TABLE 마이그레이션, Gemini 응답 파싱 패턴]

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/local/AppDatabase.kt
    - app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt
    - app/src/main/java/com/autominuting/domain/model/Meeting.kt
    - app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt
    - app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt
    - app/src/main/java/com/autominuting/di/DatabaseModule.kt
    - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt

key-decisions:
  - "Gemini 응답 첫 줄을 제목으로 사용, # 마크다운 헤더 제거 후 100자 제한"
  - "clearMinutesPath에 minutesTitle = NULL 포함하여 회의록 삭제 시 제목도 초기화"

patterns-established:
  - "lineSequence + removePrefix 패턴: Gemini Markdown 응답에서 메타데이터 추출"

requirements-completed: [NAME-03]

duration: 2min
completed: 2026-03-28
---

# Phase 26 Plan 01: minutesTitle 데이터 레이어 및 자동 제목 추출 Summary

**Room DB v3->v4 마이그레이션으로 minutesTitle 컬럼 추가, MinutesGenerationWorker에서 Gemini 응답 첫 줄을 자동 제목으로 추출/저장**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-28T12:39:08Z
- **Completed:** 2026-03-28T12:41:20Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- MeetingEntity/Meeting 도메인 모델에 minutesTitle 필드 추가
- MIGRATION_3_4로 기존 DB 데이터 유실 없이 스키마 업그레이드
- Gemini 응답 첫 줄에서 마크다운 헤더(#) 제거 후 자동 제목 추출

## Task Commits

Each task was committed atomically:

1. **Task 1: minutesTitle 컬럼 추가 (DB 마이그레이션 v3->v4 + 모델 업데이트)** - `1c66c36` (feat)
2. **Task 2: MinutesGenerationWorker에서 Gemini 응답 첫 줄 제목 추출** - `e8c89ea` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/data/local/AppDatabase.kt` - MIGRATION_3_4 추가, version=4
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` - minutesTitle 필드 + toDomain/fromDomain 매핑
- `app/src/main/java/com/autominuting/domain/model/Meeting.kt` - minutesTitle 프로퍼티
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` - updateMinutes에 minutesTitle 추가, updateMinutesTitle/clearMinutesPath 수정
- `app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt` - updateMinutesTitle 인터페이스
- `app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt` - updateMinutesTitle 구현
- `app/src/main/java/com/autominuting/di/DatabaseModule.kt` - MIGRATION_3_4 등록
- `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` - Gemini 응답 첫 줄 제목 추출 및 DB 저장

## Decisions Made
- Gemini 응답의 첫 번째 비어있지 않은 줄을 제목으로 사용 (# 마크다운 헤더 제거, 100자 제한)
- clearMinutesPath에 minutesTitle = NULL 포함하여 회의록 재생성 시 이전 제목 잔존 방지

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- minutesTitle 데이터가 DB에 저장되므로 UI에서 회의록 카드 제목 표시 가능
- Plan 02에서 UI 표시 및 제목 편집 기능 구현 준비 완료

---
*Phase: 26-minutes-title-actions*
*Completed: 2026-03-28*
