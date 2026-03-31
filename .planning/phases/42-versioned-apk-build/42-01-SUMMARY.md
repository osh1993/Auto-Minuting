---
phase: 42-versioned-apk-build
plan: 01
subsystem: build
tags: [gradle, apk, versioning]
dependency_graph:
  requires: [41-01]
  provides: [versioned-apk-output]
  affects: [app/build.gradle.kts]
tech_stack:
  added: []
  patterns: [base.archivesName for APK naming]
key_files:
  created: []
  modified: [app/build.gradle.kts]
decisions:
  - base.archivesName.set() 방식 채택 (applicationVariants internal API 대비 안정적)
metrics:
  duration: ~3min
  completed: 2026-03-31
---

# Phase 42 Plan 01: APK 파일명 버전 번호 포함 Summary

Release APK 파일명에 versionName을 자동 포함하도록 build.gradle.kts 수정 (base.archivesName 방식)

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | build.gradle.kts에 versionName 6.0 + APK 출력 파일명 설정 | c730af8 | app/build.gradle.kts |

## Changes Made

### Task 1: build.gradle.kts 수정

- `versionCode`를 1에서 6으로, `versionName`을 "1.0"에서 "6.0"으로 변경
- `android {}` 블록 직후에 `base.archivesName.set("AutoMinuting-v${android.defaultConfig.versionName}")` 추가
- `assembleRelease` 빌드 성공, `AutoMinuting-v6.0-release.apk` 출력 확인

## Decisions Made

1. **base.archivesName.set() 방식 채택**: 플랜에서 제시한 두 가지 접근법(applicationVariants vs base.archivesName) 중 `base.archivesName.set()`을 선택. AGP internal API(`BaseVariantOutputImpl`)에 의존하지 않아 AGP 버전 업그레이드 시 안정적.

## Deviations from Plan

None - 플랜대로 정확히 실행됨.

## Verification Results

- assembleRelease: BUILD SUCCESSFUL
- 출력 파일: `app/build/outputs/apk/release/AutoMinuting-v6.0-release.apk` (62.8MB)
- 파일명에 버전 번호 "v6.0" 포함 확인

## Known Stubs

None.
