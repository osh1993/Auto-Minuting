---
phase: quick
plan: 260325-b8p
subsystem: presentation/dashboard
tags: [ui, ble, service-control, dashboard]
dependency_graph:
  requires: [AudioCollectionService]
  provides: [dashboard-ble-toggle]
  affects: [DashboardViewModel, DashboardScreen]
tech_stack:
  added: []
  patterns: [StateFlow toggle, Foreground Service intent control]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt
decisions:
  - 서비스 실행 상태를 로컬 Boolean StateFlow로 관리 (시스템 강제 종료 시 동기화는 향후 개선)
metrics:
  duration: 4min
  completed: 2026-03-25
---

# Quick Task 260325-b8p: 대시보드 녹음기 연결 토글 버튼 Summary

대시보드에 AudioCollectionService 시작/중지 토글 카드 추가, 수집 상태에 따른 색상/버튼 스타일 전환

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | ViewModel에 서비스 토글 로직 추가 | 0a7a348 | DashboardViewModel.kt |
| 2 | 대시보드 UI에 녹음기 연결 토글 버튼 추가 | cecbbd8 | DashboardScreen.kt |

## What Was Built

### Task 1: ViewModel 서비스 토글 로직
- `isCollecting: StateFlow<Boolean>` 추가하여 오디오 수집 상태 추적
- `toggleCollection()` 함수로 AudioCollectionService에 START/STOP intent 전달
- `@ApplicationContext` 주입으로 서비스 시작/중지 컨텍스트 확보

### Task 2: 대시보드 녹음기 연결 카드 UI
- 파이프라인 배너 아래에 녹음기 연결 카드 배치
- 수집 상태별 카드 색상 전환: primaryContainer (수집 중) / secondaryContainer (미수집)
- 수집 중: OutlinedButton + CircularProgressIndicator(16dp)로 활성 상태 시각화
- 미수집: filled Button으로 "녹음기 연결 시작" 표시

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 워크트리 Gradle 빌드 불가로 메인 레포에서 컴파일 검증**
- **Found during:** Task 1, Task 2
- **Issue:** 워크트리에서 KSP 플러그인 2.3.20-1.1.1 해상도 실패 (네트워크/캐시 문제)
- **Fix:** 변경된 파일을 메인 레포에 복사하여 compileDebugKotlin 검증 후 원복
- **Files modified:** 없음 (검증 방식 변경)

**2. [Rule 2 - Adjustment] 계획의 UI 배치 위치 조정**
- **Found during:** Task 2
- **Issue:** 계획에서 언급한 "녹음에서 회의록까지, 자동으로." 텍스트가 현재 코드에 존재하지 않음
- **Fix:** 파이프라인 배너 아래, 기본 콘텐츠 위에 카드 배치 (자연스러운 위치)
- **Files modified:** DashboardScreen.kt

## Verification

- compileDebugKotlin: BUILD SUCCESSFUL
- DashboardViewModel에 isCollecting, toggleCollection() 존재 확인
- DashboardScreen에 녹음기 연결 카드와 토글 버튼 존재 확인

## Known Stubs

없음 - 모든 기능이 실제 서비스 연동으로 구현됨

## Decisions Made

1. **로컬 Boolean StateFlow 상태 관리**: 서비스 실행 상태를 ViewModel 내 MutableStateFlow로 관리. 시스템에 의한 서비스 강제 종료 시 상태 불일치 가능성이 있으나, 이는 향후 ServiceConnection 바인딩으로 개선 예정.

## Self-Check: PASSED
