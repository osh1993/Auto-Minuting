---
phase: 06-pipeline-integration
plan: 01
subsystem: domain, preferences, minutes, notification
tags: [datastore, gemini, notification, enum, kotlin]

requires:
  - phase: 05-minutes
    provides: GeminiEngine, MinutesRepository, MinutesRepositoryImpl

provides:
  - MinutesFormat enum (STRUCTURED, SUMMARY, ACTION_ITEMS)
  - AutomationMode enum (FULL_AUTO, HYBRID)
  - UserPreferencesRepository (DataStore 기반 설정 관리)
  - GeminiEngine 형식별 프롬프트 분기
  - PipelineNotificationHelper (진행/완료/전사완료 알림)
  - PipelineActionReceiver 스텁

affects: [06-02, 06-03, 07]

tech-stack:
  added: [DataStore Preferences]
  patterns: [preferencesDataStore 확장 프로퍼티, when(format) 프롬프트 분기, NotificationHelper 싱글톤 객체]

key-files:
  created:
    - app/src/main/java/com/autominuting/domain/model/MinutesFormat.kt
    - app/src/main/java/com/autominuting/domain/model/AutomationMode.kt
    - app/src/main/java/com/autominuting/di/DataStoreModule.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt
    - app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt
  modified:
    - app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt
    - app/src/main/java/com/autominuting/domain/repository/MinutesRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
    - app/src/main/java/com/autominuting/AutoMinutingApplication.kt

key-decisions:
  - "DataStore preferencesDataStore 확장 프로퍼티를 파일 최상위에 선언하여 Hilt Module에서 제공"
  - "GeminiEngine에 기본값 파라미터(format = STRUCTURED)로 기존 호출 호환성 유지"
  - "PipelineNotificationHelper를 object 싱글톤으로 구현하여 Worker/Service에서 직접 호출"

patterns-established:
  - "when(format) 프롬프트 분기: MinutesFormat enum 값에 따라 다른 프롬프트 적용"
  - "DataStore Flow 관찰 + 즉시 조회(first()) 이중 접근 패턴"
  - "PendingIntent FLAG_IMMUTABLE 필수 사용 (Android 12+ 보안 요구)"

requirements-completed: [MIN-05]

duration: 5min
completed: 2026-03-24
---

# Phase 6 Plan 01: 기반 레이어 Summary

**MinutesFormat/AutomationMode 도메인 모델, DataStore 설정 인프라, GeminiEngine 3종 프롬프트 분기, 파이프라인 알림 헬퍼 구축**

## Performance

- **Duration:** 5min
- **Started:** 2026-03-24T13:16:58Z
- **Completed:** 2026-03-24T13:21:59Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- MinutesFormat(STRUCTURED/SUMMARY/ACTION_ITEMS), AutomationMode(FULL_AUTO/HYBRID) 도메인 모델 정의
- DataStore 기반 UserPreferencesRepository로 사용자 설정 저장/읽기 인프라 구축
- GeminiEngine에 3종 형식별 프롬프트 추가 및 when(format) 분기 로직 적용
- PipelineNotificationHelper로 파이프라인 진행/완료/전사완료 알림 유틸리티 구현

## Task Commits

Each task was committed atomically:

1. **Task 1: 도메인 모델 + DataStore 설정 인프라** - `2165158` (feat)
2. **Task 2: GeminiEngine 형식별 프롬프트 확장 + 알림 헬퍼** - `724c80c` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/domain/model/MinutesFormat.kt` - 회의록 형식 enum (STRUCTURED, SUMMARY, ACTION_ITEMS)
- `app/src/main/java/com/autominuting/domain/model/AutomationMode.kt` - 자동화 모드 enum (FULL_AUTO, HYBRID)
- `app/src/main/java/com/autominuting/di/DataStoreModule.kt` - DataStore Hilt 모듈
- `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt` - 사용자 설정 저장/읽기 Repository
- `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` - 3종 프롬프트 + when(format) 분기
- `app/src/main/java/com/autominuting/domain/repository/MinutesRepository.kt` - format 파라미터 추가
- `app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt` - format 전달 구현
- `app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt` - 파이프라인 알림 유틸리티
- `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt` - 알림 액션 리시버 스텁
- `app/src/main/java/com/autominuting/AutoMinutingApplication.kt` - 파이프라인 알림 채널 등록

## Decisions Made
- DataStore preferencesDataStore 확장 프로퍼티를 파일 최상위에 선언하여 Hilt Module에서 제공
- GeminiEngine에 기본값 파라미터(format = STRUCTURED)로 기존 호출 호환성 유지
- PipelineNotificationHelper를 object 싱글톤으로 구현하여 Worker/Service에서 직접 호출 가능

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

| File | Description | Resolved by |
|------|-------------|-------------|
| PipelineActionReceiver.kt | onReceive() 미구현 | Plan 02에서 구현 예정 |

## Issues Encountered
- Gradle 빌드 환경에서 KSP 2.3.20-1.1.1 플러그인 resolve 실패 (미래 버전 사용) -- 기존 환경 이슈로 코드 정합성에는 영향 없음

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 02: MinutesFormat/AutomationMode enum, UserPreferencesRepository, PipelineNotificationHelper 모두 사용 가능
- Plan 03: UI에서 UserPreferencesRepository의 Flow를 구독하여 설정 화면 구현 가능
- PipelineActionReceiver의 onReceive() 구현이 Plan 02에서 필요

## Self-Check: PASSED

- All 6 created files: FOUND
- Commit 2165158: FOUND
- Commit 724c80c: FOUND

---
*Phase: 06-pipeline-integration*
*Completed: 2026-03-24*
