---
phase: 39-stt-groq-deepgram-naver-clova
verified: 2026-03-31T00:00:00Z
status: passed
score: 5/5 must-haves verified
re_verification: false
---

# Phase 39: STT 엔진 확장 (Groq / Deepgram / Naver CLOVA) 검증 보고서

**Phase Goal:** 사용자가 Groq Whisper, Deepgram Nova-3, Naver CLOVA Speech 중 하나를 STT 엔진으로 선택하여 전사할 수 있다
**Verified:** 2026-03-31T00:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | SttEngineType enum에 GROQ, DEEPGRAM, NAVER_CLOVA 항목이 존재한다 | VERIFIED | `SttEngineType.kt` 라인 13-17: GROQ, DEEPGRAM, NAVER_CLOVA 3개 항목 선언 확인 |
| 2 | GroqSttEngine, DeepgramSttEngine, NaverClovaSttEngine 클래스가 SttEngine 인터페이스를 구현한다 | VERIFIED | 3개 파일 모두 `: SttEngine` 선언 및 `transcribe()`, `isAvailable()`, `engineName()` 구현 확인 |
| 3 | TranscriptionRepositoryImpl when 분기에 5개 엔진 타입이 모두 처리된다 | VERIFIED | `TranscriptionRepositoryImpl.kt` 라인 70-76: GEMINI, WHISPER, GROQ, DEEPGRAM, NAVER_CLOVA 5개 분기 모두 존재 |
| 4 | SecureApiKeyRepository에 Groq/Deepgram/Naver CLOVA API 키 저장 메서드가 존재한다 | VERIFIED | `saveGroqApiKey()` 라인 83, `saveDeepgramApiKey()` 라인 98, `saveClovaInvokeUrl()` 라인 113, `saveClovaSecretKey()` 라인 128 확인 |
| 5 | UserPreferencesRepository sttEngineType Flow에 IllegalArgumentException 폴백이 존재한다 | VERIFIED | `sttEngineType` Flow 라인 67-74 및 `getSttEngineTypeOnce()` 라인 161-168에 `catch (e: IllegalArgumentException) { SttEngineType.GEMINI }` 패턴 확인 |

**Score:** 5/5 truths verified

---

### Required Artifacts

| Artifact | 기대 내용 | Status | 세부 사항 |
|----------|-----------|--------|-----------|
| `app/src/main/java/com/autominuting/domain/model/SttEngineType.kt` | GROQ, DEEPGRAM, NAVER_CLOVA enum 항목 | VERIFIED | 5개 항목 (GEMINI, WHISPER, GROQ, DEEPGRAM, NAVER_CLOVA) 모두 존재 |
| `app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt` | SttEngine 구현, multipart/form-data REST | VERIFIED | 176라인, 실제 OkHttp 요청, 재시도 로직, API 키 검증 포함 |
| `app/src/main/java/com/autominuting/data/stt/DeepgramSttEngine.kt` | SttEngine 구현, binary body REST | VERIFIED | 166라인, binary body 방식, Token 인증, 응답 JSON 파싱 포함 |
| `app/src/main/java/com/autominuting/data/stt/NaverClovaSttEngine.kt` | SttEngine 구현, multipart + JSON params | VERIFIED | 203라인, CLOVA invoke URL 처리, multipart 요청, result 필드 검증 포함 |
| `app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt` | when 분기 5개 엔진 처리 | VERIFIED | 라인 70-76: exhaustive when 분기 확인 |
| `app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt` | Groq/Deepgram/Naver 키 저장/조회 메서드 | VERIFIED | 키 상수 정의 + get/save/clear 메서드 각각 존재 |
| `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt` | sttEngineType Flow에 IllegalArgumentException 폴백 | VERIFIED | Flow와 suspend 함수 양쪽에 폴백 처리 확인 |

---

### Key Link Verification

| From | To | Via | Status | 세부 사항 |
|------|----|----|--------|-----------|
| `TranscriptionRepositoryImpl` | `GroqSttEngine` | Hilt 주입 + when 분기 | WIRED | 생성자 주입(라인 35), when 분기(라인 73) 확인 |
| `TranscriptionRepositoryImpl` | `DeepgramSttEngine` | Hilt 주입 + when 분기 | WIRED | 생성자 주입(라인 36), when 분기(라인 74) 확인 |
| `TranscriptionRepositoryImpl` | `NaverClovaSttEngine` | Hilt 주입 + when 분기 | WIRED | 생성자 주입(라인 37), when 분기(라인 75) 확인 |
| `GroqSttEngine` | `SecureApiKeyRepository` | Hilt 주입 + `getGroqApiKey()` | WIRED | 생성자 주입, `getGroqApiKey()` 호출(라인 61) 확인 |
| `DeepgramSttEngine` | `SecureApiKeyRepository` | Hilt 주입 + `getDeepgramApiKey()` | WIRED | 생성자 주입, `getDeepgramApiKey()` 호출(라인 59) 확인 |
| `NaverClovaSttEngine` | `SecureApiKeyRepository` | Hilt 주입 + `getClovaInvokeUrl()` / `getClovaSecretKey()` | WIRED | 생성자 주입, 두 메서드 호출(라인 62, 72) 확인 |
| `UserPreferencesRepository` | `SttEngineType` | `getSttEngineTypeOnce()` | WIRED | `TranscriptionRepositoryImpl` 라인 66에서 호출 확인 |

---

### Data-Flow Trace (Level 4)

엔진 클래스는 렌더링 컴포넌트가 아닌 서비스 레이어이므로 Level 4 데이터 플로우 추적은 API 키 흐름에 대해서만 확인.

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GroqSttEngine.transcribe()` | `apiKey` | `SecureApiKeyRepository.getGroqApiKey()` | EncryptedSharedPreferences 조회 (실제 저장소) | FLOWING |
| `DeepgramSttEngine.transcribe()` | `apiKey` | `SecureApiKeyRepository.getDeepgramApiKey()` | EncryptedSharedPreferences 조회 (실제 저장소) | FLOWING |
| `NaverClovaSttEngine.transcribe()` | `invokeUrl`, `secretKey` | `getClovaInvokeUrl()`, `getClovaSecretKey()` | EncryptedSharedPreferences 조회 (실제 저장소) | FLOWING |
| `TranscriptionRepositoryImpl.transcribe()` | `selectedEngine` | `userPreferencesRepository.getSttEngineTypeOnce()` | DataStore Flow `.first()` 호출 | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — 엔진 클래스는 네트워크 I/O가 필요한 서비스 레이어로, 실제 API 호출 없이는 동작 검증 불가. 설정 UI 연동(Phase 41)은 별도 검증 필요.

---

### Requirements Coverage

| Requirement | 설명 | Status | Evidence |
|-------------|------|--------|---------|
| STT6-01 | Groq Whisper API STT 엔진 선택 및 API 키 입력 | PARTIAL | 백엔드 구현 완료. 설정 UI는 Phase 41에서 구현 예정 |
| STT6-02 | Deepgram Nova-3 STT 엔진 선택 및 API 키 입력 | PARTIAL | 백엔드 구현 완료. 설정 UI는 Phase 41에서 구현 예정 |
| STT6-03 | Naver CLOVA Speech STT 엔진 선택 및 API 키 입력 | PARTIAL | 백엔드 구현 완료. 설정 UI는 Phase 41에서 구현 예정 |
| STT6-04 | 각 STT 엔진이 실제로 한국어 오디오를 전사하여 텍스트 반환 | PARTIAL | 각 엔진 클래스의 전사 로직 구현 완료. 실기기 E2E 검증은 인간 검증 필요 |

**Note:** STT6-01~STT6-04는 Phase 39, 41에 걸쳐 구현된다. Phase 39는 데이터/엔진 레이어 구현을 담당하며, 설정 UI는 Phase 41에서 완성된다. Phase 39 범위 내 구현 목표는 모두 달성.

---

### Anti-Patterns Found

스캔 대상 파일: SttEngineType.kt, GroqSttEngine.kt, DeepgramSttEngine.kt, NaverClovaSttEngine.kt, TranscriptionRepositoryImpl.kt, SecureApiKeyRepository.kt, UserPreferencesRepository.kt

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GroqSttEngine.kt` | 144 | `text.isNullOrBlank()` — String에 isNullOrBlank() 사용 (Kotlin Non-null String에 불필요한 null 검사) | Info | 기능 무관, 컴파일 경고 가능성 |

블로커 또는 기능 영향 안티패턴 없음.

---

### Human Verification Required

#### 1. 실제 API 전사 동작 검증

**Test:** 각 엔진(Groq, Deepgram, Naver CLOVA)에 유효한 API 키를 설정하고 한국어 오디오 파일로 전사를 실행한다
**Expected:** 각 엔진이 한국어 텍스트를 올바르게 반환한다
**Why human:** 실제 네트워크 호출 및 외부 API 응답이 필요하여 정적 분석으로 검증 불가

#### 2. 설정 UI에서 엔진 선택 가능 여부 (Phase 41 완성 후)

**Test:** 설정 화면에서 Groq / Deepgram / Naver CLOVA 엔진을 선택하고 API 키를 입력한다
**Expected:** 엔진이 변경되어 다음 전사 시 해당 엔진이 사용된다
**Why human:** 설정 UI는 Phase 41에서 구현 예정으로, 현재 UI 검증 불가

---

### Gaps Summary

Phase 39 범위 내 모든 성공 기준이 충족되었다. 5개 검증 항목 전부 통과.

- `SttEngineType` enum에 3개 신규 항목(GROQ, DEEPGRAM, NAVER_CLOVA) 추가 완료
- 3개 신규 엔진 클래스가 `SttEngine` 인터페이스를 완전히 구현하고 실제 REST API 호출 로직 포함
- `TranscriptionRepositoryImpl`의 when 분기가 5개 엔진 타입을 모두 처리하며 exhausitve 분기 보장
- `SecureApiKeyRepository`에 Groq, Deepgram, Naver CLOVA 각각의 API 키 저장/조회/삭제 메서드 완비
- `UserPreferencesRepository`의 `sttEngineType` Flow와 `getSttEngineTypeOnce()`에 `IllegalArgumentException` 폴백 적용으로 미래 enum 변경 시 앱 크래시 방지

설정 UI(Phase 41)와 실제 전사 E2E 검증은 Phase 39 범위 밖이므로 해당 Phase에서 별도 검증 필요.

---

_Verified: 2026-03-31T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
