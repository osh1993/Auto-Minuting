---
phase: 40-minutes-deepgram-clova
verified: 2026-03-31T00:00:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 40: 회의록 엔진 확장 (Deepgram Intelligence / Naver CLOVA Summary) Verification Report

**Phase Goal:** 사용자가 Deepgram Audio Intelligence 또는 Naver CLOVA Summary를 회의록 엔진으로 선택할 수 있다
**Verified:** 2026-03-31
**Status:** PASSED
**Re-verification:** No — 초기 검증

---

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                    | Status     | Evidence                                                                                                                                                  |
| --- | ---------------------------------------------------------------------------------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | MinutesEngineType enum에 GEMINI, DEEPGRAM, NAVER_CLOVA 항목이 존재한다                  | ✓ VERIFIED | `MinutesEngineType.kt` L7-14: `enum class MinutesEngineType { GEMINI, DEEPGRAM, NAVER_CLOVA }`                                                            |
| 2   | DeepgramMinutesEngine이 MinutesEngine 인터페이스를 구현한다 (engineName에 "영어만" 포함) | ✓ VERIFIED | `DeepgramMinutesEngine.kt` L27: `: MinutesEngine`, L37: `override fun engineName(): String = "Deepgram Intelligence (영어만)"`                            |
| 3   | NaverClovaMinutesEngine이 MinutesEngine 인터페이스를 구현한다 (2000자 분할 처리 포함)   | ✓ VERIFIED | `NaverClovaMinutesEngine.kt` L26: `: MinutesEngine`, L32: `const val MAX_CHUNK_SIZE = 2000`, L92-117: `splitIntoChunks()` 구현                           |
| 4   | MinutesEngineSelector가 MinutesEngineType 기반으로 3개 엔진을 선택한다                  | ✓ VERIFIED | `MinutesEngineSelector.kt` L61-80: `when (engineType) { GEMINI -> ..., DEEPGRAM -> deepgramEngineProvider.get(), NAVER_CLOVA -> clovaEngineProvider.get() }` |
| 5   | UserPreferencesRepository에 minutesEngineType Flow와 getter가 존재한다                  | ✓ VERIFIED | `UserPreferencesRepository.kt` L91-98: `val minutesEngineType: Flow<MinutesEngineType>`, L221-229: `suspend fun getMinutesEngineTypeOnce(): MinutesEngineType` |
| 6   | 기존 Gemini 엔진은 MinutesEngineSelector에서 여전히 동작한다                            | ✓ VERIFIED | `MinutesEngineSelector.kt` L62-77: GEMINI 타입 시 authMode 기반 GeminiEngine/GeminiOAuthEngine 서브셀렉션 로직 완전 유지                                   |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact                                                                                              | Expected                               | Status     | Details                                                   |
| ----------------------------------------------------------------------------------------------------- | -------------------------------------- | ---------- | --------------------------------------------------------- |
| `app/src/main/java/com/autominuting/domain/model/MinutesEngineType.kt`                               | GEMINI, DEEPGRAM, NAVER_CLOVA enum     | ✓ VERIFIED | 3개 항목 모두 존재, 문서 주석 포함                         |
| `app/src/main/java/com/autominuting/data/minutes/DeepgramMinutesEngine.kt`                           | MinutesEngine 구현, 영어 안내 포함      | ✓ VERIFIED | 142줄 완전 구현, engineName에 "(영어만)" 명시              |
| `app/src/main/java/com/autominuting/data/minutes/NaverClovaMinutesEngine.kt`                         | MinutesEngine 구현, 2000자 청크 분할    | ✓ VERIFIED | 206줄 완전 구현, splitIntoChunks() + summarizeChunk() 구현 |
| `app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt`                           | 3개 엔진 선택 로직                      | ✓ VERIFIED | 82줄, GEMINI/DEEPGRAM/NAVER_CLOVA when 분기 완전 구현     |
| `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt`                   | minutesEngineType Flow + getter        | ✓ VERIFIED | Flow L91, getter L221, setter L214, key L55 모두 존재      |
| `app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt`                         | Deepgram/CLOVA Summary 키 저장소        | ✓ VERIFIED | getDeepgramApiKey(), getClovaSummaryClientId/Secret() 모두 존재 |

---

### Key Link Verification

| From                          | To                            | Via                                      | Status     | Details                                                                 |
| ----------------------------- | ----------------------------- | ---------------------------------------- | ---------- | ----------------------------------------------------------------------- |
| `MinutesEngineSelector`       | `DeepgramMinutesEngine`       | `Provider<DeepgramMinutesEngine>` 주입    | ✓ VERIFIED | L23: `private val deepgramEngineProvider`, L78: `deepgramEngineProvider.get()` |
| `MinutesEngineSelector`       | `NaverClovaMinutesEngine`     | `Provider<NaverClovaMinutesEngine>` 주입  | ✓ VERIFIED | L24: `private val clovaEngineProvider`, L79: `clovaEngineProvider.get()`     |
| `MinutesEngineSelector`       | `UserPreferencesRepository`   | `getMinutesEngineTypeOnce()` 호출         | ✓ VERIFIED | L59: `userPreferencesRepository.getMinutesEngineTypeOnce()` 호출        |
| `DeepgramMinutesEngine`       | `SecureApiKeyRepository`      | `getDeepgramApiKey()` 호출               | ✓ VERIFIED | L41, L58: `secureApiKeyRepository.getDeepgramApiKey()` 호출             |
| `NaverClovaMinutesEngine`     | `SecureApiKeyRepository`      | `getClovaSummaryClientId/Secret()` 호출  | ✓ VERIFIED | L42-43, L62-63: 두 키 모두 조회                                          |
| `RepositoryModule`            | `MinutesEngineSelector`       | `@Binds MinutesEngine`                   | ✓ VERIFIED | `RepositoryModule.kt` L70-73: `bindMinutesEngine(impl: MinutesEngineSelector)` |
| `MinutesEngineType`           | `UserPreferencesRepository`   | `MINUTES_ENGINE_KEY` DataStore 저장       | ✓ VERIFIED | L55: `MINUTES_ENGINE_KEY`, L91-98: Flow, L221-229: getter              |

---

### Data-Flow Trace (Level 4)

이 Phase의 아티팩트는 UI 컴포넌트가 아닌 엔진 구현체 및 설정 레이어이다.
데이터 흐름은 다음과 같이 확인됨:

| 아티팩트                     | 데이터 변수              | 소스                                     | 실제 데이터 생성 여부 | Status      |
| ---------------------------- | ------------------------ | ---------------------------------------- | --------------------- | ----------- |
| `DeepgramMinutesEngine`      | `summaryText`            | Deepgram `/v1/read` REST API 응답 파싱    | ✓ (JSON 파싱 후 반환) | ✓ FLOWING   |
| `NaverClovaMinutesEngine`    | `summary` (청크별 합산)  | CLOVA Summary API 응답 파싱               | ✓ (JSON 파싱 후 합산) | ✓ FLOWING   |
| `UserPreferencesRepository`  | `minutesEngineType`      | DataStore `MINUTES_ENGINE_KEY`           | ✓ (실제 DataStore 읽기) | ✓ FLOWING   |

---

### Behavioral Spot-Checks

이 Phase는 네트워크 API 연동이 필요한 엔진 구현이므로 서버 없이 독립 실행 가능한 체크만 수행함.

| Behavior                                      | Command                                                   | Result                                         | Status  |
| --------------------------------------------- | --------------------------------------------------------- | ---------------------------------------------- | ------- |
| MinutesEngineType enum 항목 수                 | 파일 직접 검사                                             | GEMINI, DEEPGRAM, NAVER_CLOVA 3개 확인          | ✓ PASS  |
| DeepgramMinutesEngine: MinutesEngine 구현 여부 | 파일 직접 검사                                             | `: MinutesEngine` 선언 + 3개 메서드 override 확인 | ✓ PASS  |
| NaverClovaMinutesEngine: 2000자 분할 상수      | 파일 직접 검사                                             | `MAX_CHUNK_SIZE = 2000` 확인                    | ✓ PASS  |
| MinutesEngineSelector: when 분기 완전성        | 파일 직접 검사                                             | GEMINI/DEEPGRAM/NAVER_CLOVA 모두 처리            | ✓ PASS  |
| Hilt 바인딩: MinutesEngine → Selector         | RepositoryModule 직접 검사                                 | `@Binds bindMinutesEngine` 확인                 | ✓ PASS  |
| API 키 연동: Deepgram/CLOVA                    | SecureApiKeyRepository 직접 검사                           | getDeepgramApiKey(), getClovaSummary* 확인      | ✓ PASS  |

---

### Requirements Coverage

| Requirement | Source Plan  | Description                                                                    | Status      | Evidence                                                        |
| ----------- | ------------ | ------------------------------------------------------------------------------ | ----------- | --------------------------------------------------------------- |
| MIN6-01     | 40-01, 40-02 | 사용자가 설정에서 Deepgram Audio Intelligence를 회의록 엔진으로 선택할 수 있다  | ✓ SATISFIED | MinutesEngineType.DEEPGRAM + DeepgramMinutesEngine + UserPrefs  |
| MIN6-02     | 40-02        | 사용자가 설정에서 Naver CLOVA Summary를 회의록 엔진으로 선택할 수 있다          | ✓ SATISFIED | MinutesEngineType.NAVER_CLOVA + NaverClovaMinutesEngine         |
| MIN6-03     | 40-01, 40-02 | 각 회의록 엔진이 전사 텍스트를 받아 요약 결과를 회의록으로 저장한다             | ✓ SATISFIED | generate() 메서드 구현 완전, MinutesEngineSelector 통해 위임     |

**비고:** SET6-01/SET6-02(설정 UI에서 엔진 선택/API 키 입력)는 Phase 41 범위이며 Phase 40 검증 대상 아님.
설정 UI 연결 없이 데이터/로직 레이어만 구현된 상태는 Phase 40 계획 범위와 일치함.

---

### Anti-Patterns Found

| File                          | Line | Pattern           | Severity | Impact              |
| ----------------------------- | ---- | ----------------- | -------- | ------------------- |
| 발견된 안티패턴 없음           | —    | —                 | —        | —                   |

검사 결과:
- `DeepgramMinutesEngine.kt`: TODO/FIXME 없음, 빈 구현 없음, hardcoded empty 없음
- `NaverClovaMinutesEngine.kt`: TODO/FIXME 없음, splitIntoChunks() 완전 구현, 빈 반환 없음
- `MinutesEngineSelector.kt`: TODO/FIXME 없음, when 분기 완전, exhaustive 처리
- `UserPreferencesRepository.kt`: MINUTES_ENGINE_KEY, Flow, getter, setter 모두 완전 구현

---

### Human Verification Required

#### 1. Deepgram Intelligence API 실동작 확인

**Test:** 유효한 Deepgram API 키를 설정하고 영어 전사 텍스트로 회의록 생성 실행
**Expected:** `/v1/read?summarize=true&language=en` 엔드포인트에서 요약 결과 수신 및 회의록 파일 저장
**Why human:** 실제 Deepgram API 키 없이 네트워크 호출 검증 불가

#### 2. Naver CLOVA Summary API 실동작 확인

**Test:** 유효한 CLOVA Summary Client ID + Client Secret을 설정하고 한국어 전사 텍스트(2000자 이상 포함)로 회의록 생성
**Expected:** 2000자 초과 텍스트가 청크로 분할되어 각각 API 호출 후 합산 결과 반환
**Why human:** 실제 CLOVA API 키 없이 청크 분할 API 호출 검증 불가

#### 3. 설정 UI에서 엔진 선택 가능 여부 (Phase 41 선행 조건 확인)

**Test:** 현재 설정 화면에서 회의록 엔진을 Deepgram/CLOVA로 변경 가능한지 확인
**Expected:** Phase 41 미완료로 인해 설정 UI에서 엔진 선택 불가 (예상됨)
**Why human:** Phase 41(설정 UI 확장)이 Pending 상태이므로 시각적 확인 필요

---

### Gaps Summary

없음. 모든 6개 검증 항목이 VERIFIED 상태.

Phase 40의 구현 범위(데이터/로직 레이어)는 완전히 충족됨:
- MinutesEngineType enum 신설 완료
- DeepgramMinutesEngine 구현 완료 (영어 안내 포함, 재시도 로직 포함)
- NaverClovaMinutesEngine 구현 완료 (2000자 청크 분할 포함, 재시도 로직 포함)
- MinutesEngineSelector 확장 완료 (기존 Gemini authMode 로직 유지)
- UserPreferencesRepository minutesEngineType 저장/조회 완료
- Hilt 바인딩 완료 (RepositoryModule을 통해 MinutesEngineSelector가 MinutesEngine으로 바인딩)
- SecureApiKeyRepository에 Deepgram/CLOVA Summary 키 저장소 완료

설정 UI 연결(Phase 41)은 별도 Phase 범위이며, Phase 40 목표 달성에 영향 없음.

---

_Verified: 2026-03-31_
_Verifier: Claude (gsd-verifier)_
