---
phase: 41-settings-ui-engine-keys
plan: 01
subsystem: presentation/settings
tags: [settings-ui, minutes-engine, api-keys, multi-engine]
dependency_graph:
  requires: [SecureApiKeyRepository, UserPreferencesRepository, MinutesEngineType, SttEngineType]
  provides: [회의록 엔진 선택 UI, 멀티 엔진 API 키 관리 UI]
  affects: [SettingsViewModel, SettingsScreen]
tech_stack:
  added: []
  patterns: [재사용 ApiKeyInputField composable, 조건부 UI 렌더링]
key_files:
  modified:
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
decisions:
  - API 키 입력 필드를 "전사 설정" 섹션에 통합 배치 (STT/회의록 양쪽 키를 한곳에 모아 UX 일관성 유지)
  - Groq/Deepgram/Naver API 키는 검증 없이 바로 저장 (Gemini와 달리 테스트 호출이 어려움)
  - 회의록 엔진 드롭다운은 "회의록 설정" 섹션 하단에 배치
metrics:
  completed: "2026-03-31"
  tasks: 2
  files: 2
---

# Phase 41 Plan 01: 설정 UI - 회의록 엔진 선택 + API 키 입력 Summary

SettingsViewModel/Screen에 회의록 엔진(Gemini/Deepgram/CLOVA) 드롭다운과 Groq/Deepgram/Naver CLOVA 엔진별 조건부 API 키 입력 UI를 추가

## Tasks Completed

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | SettingsViewModel에 회의록 엔진 + API 키 상태 추가 | 338df8b | SettingsViewModel.kt |
| 2 | SettingsScreen에 회의록 엔진 드롭다운 + API 키 입력 UI 추가 | 338df8b | SettingsScreen.kt |

## Changes Made

### SettingsViewModel.kt
- `minutesEngineType` StateFlow 추가 (sttEngineType과 동일 패턴)
- `setMinutesEngineType()` 함수 추가
- 6개 API 키 존재 여부 MutableStateFlow 추가: hasGroqApiKey, hasDeepgramApiKey, hasClovaInvokeUrl, hasClovaSecretKey, hasClovaSummaryClientId, hasClovaSummaryClientSecret
- init 블록에서 SecureApiKeyRepository로 초기 키 존재 여부 로드
- 12개 저장/삭제 함수 추가 (save/clear x 6개 키)

### SettingsScreen.kt
- "회의록 설정" 섹션에 회의록 엔진 드롭다운 추가 (Gemini/Deepgram/CLOVA Summary 3개 옵션)
- "전사 설정" 섹션에 조건부 API 키 입력 필드 추가:
  - STT 엔진 GROQ 선택 시: Groq API 키 입력
  - STT/회의록 엔진 DEEPGRAM 선택 시: Deepgram API 키 입력
  - STT 엔진 NAVER_CLOVA 선택 시: Invoke URL + Secret Key 입력
  - 회의록 엔진 NAVER_CLOVA 선택 시: Client ID + Client Secret 입력
- 각 엔진별 API 키 미입력 시 빨간색 경고 텍스트 표시
- 재사용 가능한 `ApiKeyInputField` private composable 추가 (저장/삭제 + 비밀번호 가시성 토글)

## Deviations from Plan

None - 플랜이 정확하게 실행됨.

## Known Stubs

None - 모든 API 키 저장/삭제 로직이 SecureApiKeyRepository에 완전히 연결됨.

## Verification

- assembleDebug 빌드 성공 확인
- SettingsViewModel에 minutesEngineType, 6개 API 키 상태, 저장/삭제 함수 존재 확인
- SettingsScreen에 회의록 엔진 드롭다운(3개 옵션) 존재 확인
- 조건부 API 키 입력 필드 및 경고 텍스트 로직 확인

## Self-Check: PASSED
