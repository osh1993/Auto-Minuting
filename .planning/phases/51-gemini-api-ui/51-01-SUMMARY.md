---
phase: 51-gemini-api-ui
plan: 01
subsystem: settings/gemini-api-keys
tags: [gemini, api-key, multi-key, encrypted-storage, settings-ui, migration]
dependency_graph:
  requires: []
  provides:
    - GeminiApiKeyEntry data class (label, maskedKey, index)
    - SecureApiKeyRepository 다중 키 CRUD API
    - SettingsViewModel.geminiApiKeys StateFlow
    - GeminiApiKeySection composable
  affects:
    - Phase 52 (라운드로빈 로직이 getAllGeminiApiKeyValues() 사용)
tech_stack:
  added: []
  patterns:
    - EncryptedSharedPreferences 다중 항목 저장 (count + label_{n} + value_{n} 패턴)
    - 레거시 단일 키 → 다중 키 마이그레이션 (init 블록 호출)
    - Column + forEach (verticalScroll 중첩 방지)
key_files:
  created:
    - app/src/main/java/com/autominuting/data/security/GeminiApiKeyEntry.kt
  modified:
    - app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt
decisions:
  - "ApiKeyValidationState.Success를 data object에서 data class(addedLabel)로 변경 — 성공 메시지에 별명 표시 가능"
  - "Column + forEach 패턴으로 키 목록 렌더링 — SettingsScreen이 verticalScroll Column이어서 LazyColumn 중첩 금지"
  - "마이그레이션은 SettingsViewModel.init에서 호출 — Repository는 순수 로직, VM이 앱 시작 시점 보장"
metrics:
  duration: ~20min
  completed_date: "2026-04-15"
  tasks_completed: 3
  tasks_total: 4
  files_created: 1
  files_modified: 4
---

# Phase 51 Plan 01: Gemini 다중 API 키 설정 UI Summary

**One-liner:** EncryptedSharedPreferences에 다중 Gemini API 키를 CRUD 저장하고, 설정 화면에서 키 목록 관리(추가/삭제/마이그레이션) UI를 제공한다.

## Tasks Completed

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | GeminiApiKeyEntry 타입 + SecureApiKeyRepository 다중 키 CRUD | 3c2864e | Done |
| 2 | SettingsViewModel 교체 (_hasApiKey → _geminiApiKeys) | 553a043 | Done |
| 3 | SettingsScreen GeminiApiKeySection 교체 | abc76a9 | Done |
| 4 | 수동 기능 확인 체크포인트 | — | Checkpoint (awaiting) |

## What Was Built

**GeminiApiKeyEntry.kt (신규)**
- `data class GeminiApiKeyEntry(label, maskedKey, index)` — UI 표시용 (실제 키 값 미포함)

**SecureApiKeyRepository.kt (확장)**
- `migrateGeminiApiKeyIfNeeded()`: 레거시 단일 `gemini_api_key` → 다중 키 구조 index 0으로 자동 이전
- `getGeminiApiKeys()`: 등록된 키 목록 반환 (마스킹 처리: `AIza****WXYZ`)
- `addGeminiApiKey(label, key)`: 중복 키 검사 후 추가
- `removeGeminiApiKey(index)`: 삭제 후 인덱스 재정렬
- `getAllGeminiApiKeyValues()`: Phase 52 라운드로빈용 복호화 키 목록

**UserPreferencesRepository.kt (확장)**
- `GEMINI_ROUNDROBIN_INDEX_KEY`: Phase 52 라운드로빈 현재 인덱스 상수 (사전 선언)

**SettingsViewModel.kt (교체)**
- `_hasApiKey` → `_geminiApiKeys: MutableStateFlow<List<GeminiApiKeyEntry>>`
- `validateAndSaveApiKey` → `validateAndAddApiKey(label, apiKey)` (중복 키/네트워크 오류 처리 분리)
- `clearApiKey()` 제거, `removeGeminiApiKey(index)` 추가
- `ApiKeyValidationState.Success`: `data object` → `data class(addedLabel: String = "")` 변경
- `init`: `migrateGeminiApiKeyIfNeeded()` + `_geminiApiKeys` 초기 로드

**SettingsScreen.kt (교체)**
- `ApiKeySection` 제거, `GeminiApiKeySection` 신규 구현
- 빈 상태 컴포넌트, OutlinedCard 키 목록 (FontFamily.Monospace 마스킹 표시)
- 인라인 추가 폼 (별명 + API 키 필드, 검증 중 CircularProgressIndicator)
- AlertDialog 삭제 확인 (삭제 버튼 `tint = MaterialTheme.colorScheme.error`)
- `Column + forEach` 패턴 (verticalScroll 중첩 방지)

## Deviations from Plan

**1. [Rule 3 - Blocking] Task 2/3 순서 변경: SettingsScreen 컴파일 오류로 Task 3를 Task 2와 함께 처리**
- **Found during:** Task 2 컴파일 검증
- **Issue:** SettingsScreen.kt의 기존 ApiKeySection이 `hasApiKey`, `validateAndSaveApiKey`, `clearApiKey`를 참조하고 있어 Task 2 적용 후 컴파일 실패
- **Fix:** Task 3 (SettingsScreen 교체)를 즉시 실행하여 컴파일 오류 해소 후 각각 커밋
- **Files modified:** SettingsScreen.kt
- **Commit:** abc76a9

## Known Stubs

없음 — GeminiApiKeySection은 `geminiApiKeys StateFlow`를 직접 수집하여 실제 EncryptedSharedPreferences 데이터를 표시한다.

## Self-Check: PASSED

- FOUND: GeminiApiKeyEntry.kt
- FOUND: SecureApiKeyRepository.kt (migrateGeminiApiKeyIfNeeded, getGeminiApiKeys, addGeminiApiKey, removeGeminiApiKey, getAllGeminiApiKeyValues, maskApiKey)
- FOUND: SettingsViewModel.geminiApiKeys StateFlow, validateAndAddApiKey, removeGeminiApiKey
- FOUND: SettingsScreen.GeminiApiKeySection
- FOUND: commit 3c2864e (Task 1)
- FOUND: commit 553a043 (Task 2)
- FOUND: commit abc76a9 (Task 3)
- BUILD SUCCESSFUL (assembleDebug)
