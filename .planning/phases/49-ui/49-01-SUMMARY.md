---
phase: 49-ui
plan: 01
subsystem: settings-ui
tags: [analysis, proposal, settings, ui-restructure]
dependency_graph:
  requires: []
  provides: [settings-restructure-proposal]
  affects: [settings-screen]
tech_stack:
  added: []
  patterns: [settings-section-analysis]
key_files:
  created:
    - .planning/phases/49-ui/49-SETTINGS-PROPOSAL.md
  modified: []
decisions:
  - "Option A (5개 섹션 적극적 재구성) 권장 — 사용자 멘탈 모델 기준 배치"
  - "ViewModel 변경 불필요 — UI 레이아웃 재배치만 수행"
  - "파일 분리는 SET7-01 스코프 외 — 향후 리팩토링 과제"
  - "LaunchedEffect/rememberLauncherForActivityResult는 이동 불가 (Compose lifecycle 제약)"
metrics:
  duration: "173초"
  completed: "2026-04-05"
  tasks_completed: 1
  tasks_total: 1
  files_created: 1
  files_modified: 0
---

# Phase 49 Plan 01: 설정 화면 구조 분석 및 수정안 작성 Summary

SettingsScreen.kt 1558줄 전체를 분석하여 4개 섹션/6개 Helper Composable의 구조를 문서화하고, 적극적 재구성안(5개 섹션)과 보수적 대안(2개 섹션)을 구체적 코드 이동 범위와 함께 49-SETTINGS-PROPOSAL.md로 작성

## Tasks Completed

| # | Task | Commit | Key Changes |
|---|------|--------|-------------|
| 1 | SettingsScreen.kt 현재 구조 분석 및 재구성 수정안 작성 | 7bfa132 | 49-SETTINGS-PROPOSAL.md 생성 (299줄) |

## What Was Done

### Task 1: SettingsScreen.kt 현재 구조 분석 및 재구성 수정안 작성

SettingsScreen.kt 전체 1558줄을 읽고 다음을 분석/문서화:

1. **현재 구조 분석**: 4개 SettingsSection (회의록 설정, 전사 설정, Gemini 인증, Google 계정 & Drive)의 줄 범위, 포함 항목, state 변수 매핑, 조건부 표시 로직, LaunchedEffect 이동 불가 이유
2. **Option A (적극적 재구성)**: 5개 섹션 (파이프라인, Google 계정, Google Drive, API 키, 모델 관리)으로 분리. 이동 블록 8개, 조건부 가드 변경 사항 (Drive 독립 시 SignedIn 가드, API 키 섹션 빈 여부 체크)
3. **Option B (보수적 변경)**: 2개 섹션 (엔진 설정, Google 계정 & 인증)으로 병합. 이동 블록 5개, 기존 가드 재활용
4. **비교표**: 변경 규모, 위험도, UX 개선도 비교. Option A 권장

## Decisions Made

1. **Option A 권장**: 사용자 멘탈 모델(파이프라인 → 계정 → Drive → API 키)에 일치하는 5개 섹션 구조가 UX 측면에서 우수
2. **ViewModel 변경 불필요 확인**: 모든 StateFlow/함수 시그니처 유지, UI 레이아웃만 변경
3. **파일 분리 스코프 외**: 1558줄 단일 파일은 유지보수 부담이나, SET7-01은 섹션 재배치만 요구
4. **LaunchedEffect 고정**: L132-165의 driveAuthLauncher + LaunchedEffect는 Compose lifecycle 제약으로 절대 이동 불가

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None - 이 Plan은 분석/제안 문서만 생성하며, 코드 변경은 포함하지 않는다.

## Self-Check: PASSED
