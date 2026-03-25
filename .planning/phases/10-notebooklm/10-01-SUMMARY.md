---
phase: 10-notebooklm
plan: 01
subsystem: ui
tags: [notebooklm, custom-tabs, intent, androidx-browser, compose]

# Dependency graph
requires:
  - phase: 09-samsung-share
    provides: 회의록 상세화면 및 설정화면 기반 UI
provides:
  - NotebookLmHelper 유틸리티 (앱 설치 확인, 직접 Intent 공유, Custom Tabs 폴백)
  - MinutesDetailScreen NotebookLM 전용 버튼
  - SettingsScreen NotebookLM 열기 링크
affects: []

# Tech tracking
tech-stack:
  added: [androidx.browser:browser:1.9.0]
  patterns: [Custom Tabs 폴백 패턴, 패키지 가시성 queries 선언]

key-files:
  created:
    - app/src/main/java/com/autominuting/util/NotebookLmHelper.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt

key-decisions:
  - "NotebookLM 앱 설치 시 직접 Intent, 미설치 시 Custom Tabs 폴백 2단계 전략"
  - "실제 패키지명 com.google.android.apps.labs.language.tailwind 사용 (CONTEXT.md의 잘못된 값 대신)"

patterns-established:
  - "Custom Tabs 폴백: 앱 직접 Intent -> ActivityNotFoundException catch -> Custom Tabs 웹"

requirements-completed: [NLMK-01, NLMK-02]

# Metrics
duration: 2min
completed: 2026-03-26
---

# Phase 10 Plan 01: NotebookLM 연동 Summary

**NotebookLmHelper 유틸리티로 NotebookLM 앱 직접 Intent 공유 + Custom Tabs 웹 폴백 구현, 회의록 상세/설정 화면에 전용 버튼 추가**

## Performance

- **Duration:** 2min
- **Started:** 2026-03-26T06:14:33Z
- **Completed:** 2026-03-26T06:16:45Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- NotebookLmHelper 유틸리티 생성 (앱 설치 확인, 직접 Intent 공유, Custom Tabs 폴백)
- MinutesDetailScreen TopAppBar에 NotebookLM 전용 버튼 추가 (기존 Share 버튼 유지)
- SettingsScreen에 NotebookLM 열기 섹션 추가 (Custom Tabs로 웹 열기)
- AndroidManifest queries 블록으로 Android 11+ 패키지 가시성 대응

## Task Commits

Each task was committed atomically:

1. **Task 1: androidx.browser 의존성 추가 + NotebookLmHelper 유틸리티 생성 + AndroidManifest queries 등록** - `cc99875` (feat)
2. **Task 2: MinutesDetailScreen NotebookLM 전용 버튼 + SettingsScreen NotebookLM 링크 추가** - `9705f45` (feat)

## Files Created/Modified
- `app/src/main/java/com/autominuting/util/NotebookLmHelper.kt` - NotebookLM 연동 유틸리티 (앱 설치 확인, 공유 Intent, Custom Tabs)
- `gradle/libs.versions.toml` - browser 1.9.0 버전 및 라이브러리 등록
- `app/build.gradle.kts` - implementation(libs.browser) 의존성 추가
- `app/src/main/AndroidManifest.xml` - NotebookLM 패키지 가시성 queries 블록 추가
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` - NotebookLM 전용 IconButton 추가
- `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` - NotebookLM 열기 섹션 추가

## Decisions Made
- NotebookLM 앱 설치 시 직접 Intent, 미설치 시 Custom Tabs 폴백 2단계 전략 채택
- 실제 패키지명 `com.google.android.apps.labs.language.tailwind` 사용 (CONTEXT.md D-03의 `com.google.android.apps.notebooklm`은 잘못된 값)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- NotebookLM 연동 기본 구현 완료
- 향후 NotebookLM API가 공개될 경우 자동 소스 등록 기능 확장 가능

---
*Phase: 10-notebooklm*
*Completed: 2026-03-26*
