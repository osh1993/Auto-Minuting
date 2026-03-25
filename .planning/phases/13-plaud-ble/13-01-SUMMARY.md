---
phase: 13-plaud-ble
plan: 01
subsystem: ble
tags: [plaud-sdk, ble, nicebuild-sdk, stateflow, coroutines]

# Dependency graph
requires:
  - phase: 03-audio
    provides: PlaudSdkManager 스텁 구현 및 AudioRepository 인터페이스
provides:
  - PlaudBleAgentListener BLE 이벤트 콜백 구현체
  - PlaudSdkManager 실제 SDK 호출 (NiceBuildSdk, TntAgent)
  - BleConnectionState sealed interface
  - getFileList() 세션 ID 목록 조회 API
affects: [13-plaud-ble, audio-collection, ble-debug]

# Tech tracking
tech-stack:
  added: [NiceBuildSdk, TntAgent, BleCore, AudioExportFormat]
  patterns: [CompletableDeferred 비동기 대기, StateFlow BLE 상태 관리, 3단계 BLE 연결 흐름]

key-files:
  created:
    - app/src/main/java/com/autominuting/data/audio/PlaudBleAgentListener.kt
  modified:
    - app/src/main/java/com/autominuting/data/audio/PlaudSdkManager.kt

key-decisions:
  - "CompletableDeferred로 BLE 콜백 비동기 래핑 (suspendCancellableCoroutine 대신)"
  - "WAV를 exportAudio 기본 포맷으로 변경 (SDK 실제 지원 포맷 반영)"
  - "NiceBuildSdkWrapper 스텁 완전 제거 (부분 잔존 방지)"

patterns-established:
  - "BLE 콜백 -> CompletableDeferred -> suspend fun 패턴"
  - "BleConnectionState sealed interface로 상태 머신 표현"
  - "scanBle -> awaitDeviceFound -> connectionBLE -> awaitConnected 3단계 흐름"

requirements-completed: [PLUD-01]

# Metrics
duration: 5min
completed: 2026-03-26
---

# Phase 13 Plan 01: Plaud SDK 스텁 교체 Summary

**NiceBuildSdkWrapper 스텁을 실제 NiceBuildSdk/TntAgent API 호출로 교체하고, BleAgentListener 콜백을 StateFlow 기반으로 구현**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-25T21:53:55Z
- **Completed:** 2026-03-25T21:58:57Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- PlaudBleAgentListener 신규 구현: BLE 스캔/연결/에러 이벤트를 StateFlow로 전달
- PlaudSdkManager 전면 교체: 스텁 삭제, 실제 SDK API 직접 호출
- 3단계 BLE 연결 흐름 구현 (scanBle -> connectionBLE -> awaitConnected)
- exportAudio 기본 포맷을 WAV로 변경, getFileList() 신규 메서드 추가

## Task Commits

Each task was committed atomically:

1. **Task 1: PlaudBleAgentListener 구현** - `231828f` (feat)
2. **Task 2: PlaudSdkManager 스텁을 실제 SDK 호출로 교체** - `2c9eb28` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/data/audio/PlaudBleAgentListener.kt` - BLE 이벤트 콜백 구현체, BleConnectionState sealed interface, CompletableDeferred 비동기 대기
- `app/src/main/java/com/autominuting/data/audio/PlaudSdkManager.kt` - 실제 NiceBuildSdk/TntAgent/BleCore API 호출, 3단계 BLE 연결 흐름, WAV 기본 포맷

## Decisions Made
- CompletableDeferred를 사용하여 BLE 콜백을 suspend fun으로 래핑 (suspendCancellableCoroutine 중첩 대신 더 깔끔한 코드)
- exportAudio 기본 포맷을 MP3에서 WAV로 변경 (SDK 실제 지원 포맷: WAV, PCM)
- NiceBuildSdkWrapper internal object를 완전 삭제하여 스텁 잔존 혼란 방지
- exportAudio 콜백은 SDK의 ExportCallback 인터페이스 패턴을 따라 suspendCancellableCoroutine 유지

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required. (plaud-sdk.aar 배치는 별도 Phase 작업)

## Next Phase Readiness
- PlaudSdkManager가 실제 SDK API를 호출하도록 교체 완료
- plaud-sdk.aar 배치 후 컴파일 검증 필요 (AAR 미배치 시 컴파일 에러 발생)
- 실기기 BLE 테스트로 스캔/연결/파일전송 동작 확인 필요
- appKey/appSecret 발급 상태 확인 필요

---
*Phase: 13-plaud-ble*
*Completed: 2026-03-26*
