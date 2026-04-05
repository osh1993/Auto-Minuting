---
phase: 49-ui
plan: "03"
subsystem: presentation/settings
tags: [ui-restructure, settings, option-a]
dependency_graph:
  requires: [49-02]
  provides: [restructured-settings-screen]
  affects: [SettingsScreen.kt]
tech_stack:
  added: []
  patterns: [conditional-section-rendering, authState-guard]
key_files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
decisions:
  - Option A (적극적 재구성) 적용 — 5개 섹션으로 분리
  - Google Drive 섹션을 authState 조건부 외부 가드로 독립
  - API 키 섹션은 엔진 미선택 시 빈 섹션 방지 조건 추가
  - DriveFolderSection은 driveAuthState is Authorized 조건 추가로 이중 가드 구조
metrics:
  duration: ~8min
  completed: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 49 Plan 03: 설정 화면 Option A 재구성 Summary

설정 화면을 5개 논리적 섹션(파이프라인, Google 계정, Google Drive, API 키, 모델 관리)으로 재배치하여 사용자 멘탈 모델과 일치시킴

## Changes Made

### Task 1: 승인된 재구성안에 따라 SettingsScreen.kt 섹션 재배치

**Commit:** `4ba32cf`

기존 4개 섹션(회의록 설정, 전사 설정, Gemini 인증, Google 계정 & Drive)을 Option A 명세에 따라 5개 섹션으로 재구성:

| 섹션 | 타이틀 | 포함 항목 |
|------|--------|----------|
| 1 | 파이프라인 | 완전 자동 모드, STT 엔진, 회의록 엔진, 프롬프트 템플릿 관리/선택/직접 입력 |
| 2 | Google 계정 | 로그인/로그아웃, Gemini 인증 모드(API 키/OAuth), OAuth Client ID, Gemini API 키 |
| 3 | Google Drive | Drive 연결/해제, 자동 업로드 토글, 폴더 선택 (authState is SignedIn 외부 가드) |
| 4 | API 키 | Groq, Deepgram, CLOVA Speech, CLOVA Summary (엔진 선택 조건부, 빈 섹션 방지) |
| 5 | 모델 관리 | Whisper 모델 다운로드/삭제 |

**핵심 변경 사항:**
- Google Drive 섹션: `if (authState is AuthState.SignedIn)` 가드를 섹션 전체에 적용 (기존: 섹션 내부 중첩)
- DriveFolderSection: `if (driveAuthState is DriveAuthState.Authorized)` 추가 가드 (기존: 내부 early return에 의존)
- API 키 섹션: `showApiKeySection` 조건 변수로 빈 섹션 방지
- `rememberLauncherForActivityResult` (L132)와 `LaunchedEffect(driveAuthState)` (L158) 위치 유지
- ViewModel API 변경 없음 (git diff 확인 -- SettingsViewModel.kt 미수정)

### Task 2: 재구성된 설정 화면 시각 확인 (checkpoint:human-verify)

**Status:** 사용자 승인 완료

사용자가 기기(R3CY711JX2F)에서 다음을 확인하고 승인:
- 5개 섹션이 올바른 순서로 표시됨: 파이프라인 -> Google 계정 -> Google Drive -> API 키 -> 모델 관리
- Google Drive 섹션이 로그인 상태에서만 표시됨
- API 키 섹션이 엔진 선택에 따라 조건부 표시됨

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - 누락 기능] DriveFolderSection 이중 가드 추가**
- **Found during:** Task 1
- **Issue:** Google Drive 섹션 독립화로 DriveFolderSection이 AuthState.SignedIn 가드 안에는 있지만, DriveAuthState.Authorized 체크가 기존 DriveFolderSection 내부 early return에만 의존
- **Fix:** `if (driveAuthState is DriveAuthState.Authorized)` 외부 가드를 DriveFolderSection 호출 전에 추가하여 명시적 이중 가드 구조 적용
- **Files modified:** SettingsScreen.kt
- **Commit:** 4ba32cf

## Verification Results

- `./gradlew assembleDebug`: BUILD SUCCESSFUL
- SettingsSection 타이틀 5개 확인: 파이프라인, Google 계정, Google Drive, API 키, 모델 관리
- `rememberLauncherForActivityResult` 위치 변경 없음 (L132)
- `LaunchedEffect(driveAuthState)` 위치 변경 없음 (L158)
- `authState is AuthState.SignedIn` 가드 유지 (L530 OAuth 버튼, L553 Drive 섹션)
- SettingsViewModel.kt 변경 없음

## Known Stubs

없음 -- 기존 기능의 재배치만 수행, 새 기능/데이터 소스 추가 없음

## Self-Check: PASSED

- FOUND: SettingsScreen.kt
- FOUND: commit 4ba32cf
