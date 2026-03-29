---
phase: 31-gemini-quota
plan: 01
subsystem: data, ui
tags: [datastore, quota, gemini-api, compose, dashboard]

# Dependency graph
requires:
  - phase: 23-stt-engine
    provides: GeminiSttEngine 클라우드 STT 엔진
provides:
  - GeminiQuotaTracker — DataStore 기반 일일 API 호출 추적
  - 대시보드 쿼터 사용량 카드 (프로그레스 바 + STT/Minutes 구분)
  - 90% 초과 경고 배너
affects: [dashboard, stt, minutes-generation]

# Tech tracking
tech-stack:
  added: []
  patterns: [DataStore 키 기반 일일 카운터 + 날짜 자동 리셋]

key-files:
  created:
    - app/src/main/java/com/autominuting/data/quota/GeminiQuotaTracker.kt
    - app/src/main/java/com/autominuting/di/QuotaModule.kt
  modified:
    - app/src/main/java/com/autominuting/data/stt/GeminiSttEngine.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt

key-decisions:
  - "DataStore 공유: 기존 user_preferences DataStore를 GeminiQuotaTracker에서도 사용 (별도 DataStore 불필요)"
  - "성공 호출만 카운트: 429 재시도나 실패는 쿼터 카운트에 포함하지 않음"

patterns-established:
  - "날짜 자동 리셋: LocalDate.now().toString() 비교로 DataStore 카운터 일일 리셋"

requirements-completed: [QUOTA-01, QUOTA-02]

# Metrics
duration: 3min
completed: 2026-03-29
---

# Phase 31 Plan 01: Gemini 쿼터 관리 Summary

**DataStore 기반 Gemini Free RPD 1500 일일 쿼터 추적 + 대시보드 사용량 카드 + 90% 초과 경고 배너**

## Performance

- **Duration:** 3min
- **Started:** 2026-03-29T09:19:53Z
- **Completed:** 2026-03-29T09:23:22Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- GeminiQuotaTracker: DataStore에 날짜별 STT/Minutes API 호출 카운트 저장, 날짜 변경 시 자동 리셋
- GeminiSttEngine/MinutesRepositoryImpl: API 호출 성공 시 자동으로 쿼터 사용량 기록
- 대시보드에 프로그레스 바 + STT/Minutes 구분 숫자 + 잔여량 표시 카드 추가
- 사용량 90% 초과 시 errorContainer 색상 경고 배너 자동 표시

## Task Commits

Each task was committed atomically:

1. **Task 1: GeminiQuotaTracker 데이터 레이어 + API 호출 지점 연동** - `660ae53` (feat)
2. **Task 2: 대시보드 쿼터 위젯 + 90% 경고 배너** - `c6efdaa` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/data/quota/GeminiQuotaTracker.kt` - 쿼터 추적 (QuotaCategory, QuotaUsage, recordUsage, usage Flow)
- `app/src/main/java/com/autominuting/di/QuotaModule.kt` - 향후 확장용 빈 Hilt 모듈
- `app/src/main/java/com/autominuting/data/stt/GeminiSttEngine.kt` - transcribe 성공 시 STT 쿼터 기록
- `app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt` - generateMinutes 성공 시 MINUTES 쿼터 기록
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt` - quotaUsage StateFlow 노출
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt` - 쿼터 카드 + 90% 경고 배너 UI

## Decisions Made
- DataStore 공유: 기존 user_preferences DataStore를 GeminiQuotaTracker에서도 사용 (별도 DataStore 파일 생성 불필요, 키가 겹치지 않음)
- 성공 호출만 카운트: 429 재시도나 예외 발생 시에는 쿼터 카운트를 증가시키지 않음
- 프로그레스 바 색상 3단계: primary(기본) → tertiary(70% 이상) → error(90% 이상)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - 모든 데이터가 실제 DataStore에서 실시간으로 제공됨.

## Next Phase Readiness
- 쿼터 추적 인프라 완성, 향후 쿼터 초과 시 API 호출 차단 로직 추가 가능
- 대시보드에 쿼터 카드가 표시되어 사용자가 일일 사용량을 모니터링할 수 있음

## Self-Check: PASSED

- All 6 files FOUND
- Commits 660ae53 and c6efdaa verified in git log

---
*Phase: 31-gemini-quota*
*Completed: 2026-03-29*
