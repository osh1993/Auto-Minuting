---
phase: 51
name: Gemini 다중 API 키 설정 UI
created: 2026-04-15
requirements: GEMINI-01, GEMINI-04
---

# Phase 51 Context

## Domain

설정 화면의 Gemini API 섹션을 **단일 키 입력 → 다중 키 목록 관리**로 확장한다.
Phase 52(라운드로빈 + 오류 자동 전환)를 위한 저장 구조가 이 Phase에서 확정된다.

## Canonical Refs

- `app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt` — API 키 저장소 (확장 대상)
- `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` — ApiKeySection (교체 대상)
- `app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt` — hasApiKey, validateAndSaveApiKey (확장 대상)
- `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt` — DataStore (라운드로빈 인덱스 추가 위치)
- `.planning/REQUIREMENTS.md` — GEMINI-01, GEMINI-04 요구사항

## Decisions

### 1. 키 목록 UI 패턴

- **LazyColumn 리스트** 방식 채택
- 각 키가 한 행: `[별명]  [AIza****...XYZ]  🗑(삭제 아이콘)`
- 키 개수 **무제한** (상한 없음)
- 마스킹: 앞 4자 + `****` + 뒤 4자 표시 (`AIza****WXYZ`)
- 목록 하단에 "새 키 추가" 버튼 배치

### 2. 키 식별 방법

- 사용자가 각 키에 **별명(label)** 을 직접 입력
- 추가 시 별명 + 키 값 두 필드 제공
- 별명은 목록에서 주요 식별자로 표시
- Phase 52 오류 알림에서 "'{별명}' 키 오류 — 다음 키로 전환" 형태로 활용

### 3. 검증 정책

- 새 키 추가 시 **검증 후 저장** (현재 `validateAndSaveApiKey()` 방식 유지)
- `generateContent` 호출로 실제 API 응답 확인 후 목록에 추가
- 검증 실패 시 목록에 추가되지 않고 오류 메시지 표시
- 버튼 레이블: "검증 후 추가" (기존 "검증 후 저장"에서 변경)

### 4. 저장 구조

**EncryptedSharedPreferences — 인덱스별 별도 키 방식:**
```
gemini_api_key_count        → "N" (등록된 키 수)
gemini_api_key_0_label      → "회사용"
gemini_api_key_0_value      → "AIza..."
gemini_api_key_1_label      → "개인용"
gemini_api_key_1_value      → "AIza..."
```
- 기존 `KEY_GEMINI_API = "gemini_api_key"` 단일 키는 마이그레이션 처리
  - 앱 시작 시 기존 단일 키가 존재하면 index 0으로 이동 후 삭제
- 삭제 시 인덱스 재정렬 (삭제된 항목 이후 인덱스를 앞으로 당김)

**DataStore — 라운드로빈 인덱스 (Phase 52용, 지금 구조만 결정):**
- `UserPreferencesRepository`에 `gemini_roundrobin_index: Int` 추가
- Phase 52에서 실제 구현 — Phase 51에서는 DataStore 키 상수만 선언해도 무방

## Implementation Notes

- `SecureApiKeyRepository`에 다중 키 CRUD 메서드 추가:
  - `getGeminiApiKeys(): List<GeminiApiKeyEntry>` (label + maskedKey)
  - `addGeminiApiKey(label: String, key: String)`
  - `removeGeminiApiKey(index: Int)`
  - `getAllGeminiApiKeyValues(): List<String>` (Phase 52용 — 복호화된 전체 키)
- `SettingsViewModel`의 `_hasApiKey: StateFlow<Boolean>` →
  `_geminiApiKeys: StateFlow<List<GeminiApiKeyEntry>>`로 교체
- 기존 `clearApiKey()` 메서드는 제거하고 인덱스 기반 `removeGeminiApiKey(index)` 대체
- `GeminiApiKeyEntry` data class: `label: String`, `maskedKey: String`, `index: Int`

## Out of Scope (Phase 51)

- 라운드로빈 순환 로직 → Phase 52
- 오류 발생 시 자동 키 전환 → Phase 52
- 키별 사용량/오류 횟수 통계 → Future Requirements
- DataStore 라운드로빈 인덱스 실제 사용 → Phase 52

## Deferred Ideas

없음
