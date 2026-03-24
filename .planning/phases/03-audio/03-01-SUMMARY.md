---
phase: 03-audio
plan: 01
subsystem: audio
tags: [plaud-sdk, ble, retrofit, okhttp, guava, room, hilt]

# Dependency graph
requires:
  - phase: 02-skeleton
    provides: "Room DB (MeetingEntity/MeetingDao), Hilt DI, AudioRepository 인터페이스"
provides:
  - "AudioRepositoryImpl -- SDK 1차/Cloud API 2차 이중 경로 지원"
  - "PlaudSdkManager -- Plaud SDK BLE 연결/스캔/다운로드 래퍼"
  - "AudioFileManager -- 오디오 파일 저장/검증/공간 확인"
  - "PlaudCloudApiService -- Cloud API 폴백 Retrofit 인터페이스"
  - "AudioModule -- Plaud Cloud API DI 바인딩"
  - "Retrofit/OkHttp Gradle 의존성"
affects: [03-audio, 04-stt, 05-minutes]

# Tech tracking
tech-stack:
  added: [retrofit-2.11.0, okhttp-4.12.0, guava-28.2-android, plaud-sdk-aar]
  patterns: [sdk-with-cloud-fallback, suspendCancellableCoroutine-callback-wrapping, channelFlow-dual-path]

key-files:
  created:
    - app/src/main/java/com/autominuting/data/audio/PlaudSdkManager.kt
    - app/src/main/java/com/autominuting/data/audio/AudioFileManager.kt
    - app/src/main/java/com/autominuting/data/audio/PlaudCloudApiService.kt
    - app/src/main/java/com/autominuting/data/repository/AudioRepositoryImpl.kt
    - app/src/main/java/com/autominuting/di/AudioModule.kt
    - app/libs/README.txt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/com/autominuting/di/RepositoryModule.kt

key-decisions:
  - "NiceBuildSdkWrapper 스텁 패턴: AAR 미배치 시에도 컴파일 가능하도록 래퍼 객체 도입"
  - "SDK 스텁은 항상 실패 -> Cloud API 폴백 자동 전환되도록 설계"
  - "PlaudCloudApiService.downloadLatestRecordings를 Flow<String>으로 반환하여 channelFlow 내에서 collect 가능"

patterns-established:
  - "SDK 콜백 래핑: suspendCancellableCoroutine으로 SDK 콜백 API를 코루틴화"
  - "이중 경로 폴백: try SDK -> catch PlaudSdkException -> Cloud API 패턴"
  - "channelFlow 사용: 여러 소스에서 오디오 파일 경로를 emit하는 패턴"

requirements-completed: [AUD-01]

# Metrics
duration: 3min
completed: 2026-03-24
---

# Phase 3 Plan 1: 오디오 수집 데이터 레이어 Summary

**Plaud SDK BLE + Cloud API 이중 경로 AudioRepository 구현체 및 Retrofit/OkHttp/Guava 의존성 구성**

## Performance

- **Duration:** 3min
- **Started:** 2026-03-24T10:45:55Z
- **Completed:** 2026-03-24T10:49:42Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Retrofit 2.11.0, OkHttp 4.12.0, Guava 28.2-android Gradle 의존성 추가 및 BuildConfig에 PLAUD_APP_KEY/SECRET 설정
- AudioRepositoryImpl 완성: SDK 1차(BLE) -> Cloud API 2차(JWT) 이중 경로, 파일 저장 시 MeetingEntity AUDIO_RECEIVED 상태로 DB 기록
- PlaudSdkManager, AudioFileManager, PlaudCloudApiService, AudioModule, RepositoryModule 바인딩 모두 완성

## Task Commits

Each task was committed atomically:

1. **Task 1: Gradle 의존성 추가 및 BuildConfig 설정** - `77c28c9` (feat)
2. **Task 2: PlaudSdkManager + AudioFileManager + PlaudCloudApiService + AudioRepositoryImpl + DI 바인딩** - `40320d1` (feat)

## Files Created/Modified
- `gradle/libs.versions.toml` - Retrofit, OkHttp, Guava 버전 및 라이브러리 선언 추가
- `app/build.gradle.kts` - buildConfig=true, PLAUD_APP_KEY/SECRET BuildConfig 필드, SDK/Retrofit 의존성, Guava resolutionStrategy
- `app/libs/README.txt` - plaud-sdk.aar 배치 안내
- `app/src/main/java/com/autominuting/data/audio/PlaudSdkManager.kt` - Plaud SDK BLE 연결/스캔/다운로드 래퍼 (NiceBuildSdkWrapper 스텁 포함)
- `app/src/main/java/com/autominuting/data/audio/AudioFileManager.kt` - 오디오 파일 저장/검증/공간 확인 (StatFs)
- `app/src/main/java/com/autominuting/data/audio/PlaudCloudApiService.kt` - Cloud API Retrofit 인터페이스 + 스트리밍 다운로드 서비스
- `app/src/main/java/com/autominuting/data/repository/AudioRepositoryImpl.kt` - AudioRepository 구현체 (SDK 1차 + Cloud API 2차 폴백)
- `app/src/main/java/com/autominuting/di/AudioModule.kt` - PlaudCloudApi Retrofit DI 제공
- `app/src/main/java/com/autominuting/di/RepositoryModule.kt` - bindAudioRepository 바인딩 추가

## Decisions Made
- **NiceBuildSdkWrapper 스텁 도입:** plaud-sdk.aar 미배치 상태에서도 컴파일이 통과하도록 래퍼 객체를 만들어 SDK 호출을 중앙화. AAR 배치 후 래퍼 내부만 실제 SDK 호출로 교체하면 됨
- **SDK 스텁 기본 실패 설계:** 스텁은 항상 에러를 반환하여 AudioRepositoryImpl의 Cloud API 폴백이 자동 작동
- **JWT 토큰 빈 값:** Cloud API 폴백의 JWT 토큰은 사용자 설정 연동 후 교체 필요 (현재 빈 문자열)

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

| File | Line | Stub | Reason | Resolution Plan |
|------|------|------|--------|-----------------|
| PlaudSdkManager.kt | NiceBuildSdkWrapper | SDK API 호출이 스텁으로 구현 | plaud-sdk.aar 미배치 | AAR 배치 후 실제 SDK 호출로 교체 |
| AudioRepositoryImpl.kt | jwtToken 변수 | JWT 토큰 빈 문자열 | 사용자 설정 UI 미구현 | 설정 화면 구현 시 DataStore에서 읽도록 교체 |

이 스텁들은 플랜 목표(AudioRepository 구현체 + 이중 경로 구조)를 달성하는 데 영향을 주지 않는 의도적 스텁이다. SDK AAR 배치 및 사용자 설정 연동은 별도 작업으로 처리된다.

## Issues Encountered
None

## User Setup Required
None - 외부 서비스 설정 불필요. 단, plaud-sdk.aar 파일은 app/libs/에 수동 배치 필요 (README.txt 참조).

## Next Phase Readiness
- AudioRepository 구현체가 완성되어 DI로 주입 가능
- Plan 02 (Foreground Service + WorkManager 파이프라인 트리거)에서 이 구현체를 Service에서 사용할 준비 완료
- Phase 4(STT)에서 MeetingEntity의 AUDIO_RECEIVED 상태를 기반으로 전사 파이프라인 시작 가능

## Self-Check: PASSED

- All 8 files verified present on disk
- Commit 77c28c9 (Task 1) verified in git log
- Commit 40320d1 (Task 2) verified in git log

---
*Phase: 03-audio*
*Completed: 2026-03-24*
