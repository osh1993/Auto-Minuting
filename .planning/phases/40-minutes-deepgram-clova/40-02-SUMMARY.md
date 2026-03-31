---
phase: 40-minutes-deepgram-clova
plan: 02
subsystem: minutes-engine
tags: [clova-summary, minutes-engine, engine-selector, naver-api]
dependency_graph:
  requires: [40-01]
  provides: [NaverClovaMinutesEngine, MinutesEngineType, 3종-엔진-선택-체계]
  affects: [MinutesEngineSelector, SecureApiKeyRepository, UserPreferencesRepository]
tech_stack:
  added: [Naver CLOVA Summary Legacy API]
  patterns: [2000자-분할-요약, MinutesEngineType-기반-엔진-선택]
key_files:
  created:
    - app/src/main/java/com/autominuting/data/minutes/NaverClovaMinutesEngine.kt
    - app/src/main/java/com/autominuting/domain/model/MinutesEngineType.kt
  modified:
    - app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt
decisions:
  - "MinutesEngineType enum을 SttEngineType과 동일한 패턴으로 domain/model에 배치"
  - "CLOVA Summary 2000자 제한에 대해 문장 경계 기준 분할 후 결과 합산 방식 채택"
  - "MinutesEngineSelector에서 GEMINI 타입일 때만 기존 authMode 서브셀렉션 유지"
metrics:
  completed: 2026-03-31
  tasks: 2/2
---

# Phase 40 Plan 02: NaverClovaMinutesEngine + MinutesEngineType + MinutesEngineSelector 확장 Summary

CLOVA Summary Legacy API 기반 NaverClovaMinutesEngine 구현 및 MinutesEngineType enum 도입으로 3종 회의록 엔진(Gemini, Deepgram, CLOVA) 선택 체계 완성

## Task 1: MinutesEngineType enum + SecureApiKeyRepository + UserPreferencesRepository

- `MinutesEngineType` enum 신설: GEMINI(기본값), DEEPGRAM, NAVER_CLOVA 3개 값
- `SecureApiKeyRepository`에 CLOVA Summary 전용 Client ID/Client Secret 키 6개 메서드(get/save/clear x 2) 추가
- `UserPreferencesRepository`에 `minutesEngineType` Flow, `setMinutesEngineType()`, `getMinutesEngineTypeOnce()` 추가
- 기존 SttEngineType 패턴과 동일한 DataStore 기반 저장/조회/관찰 구조

## Task 2: NaverClovaMinutesEngine 구현 + MinutesEngineSelector 확장

- `NaverClovaMinutesEngine` 구현 (196행):
  - CLOVA Summary Legacy API (naveropenapi.apigw.ntruss.com) POST 호출
  - 2000자 제한 대응: `splitIntoChunks()` 메서드로 문장 경계 기준 분할 후 청크별 요약 -> 결과 합산
  - HTTP 429 및 SocketTimeoutException 재시도 (최대 3회, 20초 대기)
  - X-NCP-APIGW-API-KEY-ID / X-NCP-APIGW-API-KEY 헤더 인증
- `MinutesEngineSelector` 확장:
  - 생성자에 `Provider<DeepgramMinutesEngine>`, `Provider<NaverClovaMinutesEngine>` 추가
  - `selectEngine()`을 MinutesEngineType 기반으로 변경 (GEMINI는 기존 authMode 서브셀렉션 유지)
  - `isAvailable()`에 Deepgram/CLOVA 엔진 추가

## Deviations from Plan

None - 플랜대로 정확히 실행되었다.

## Known Stubs

None - 모든 코드가 실제 API 호출 로직으로 구현되었다.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1-2 | a24c6ce | NaverClovaMinutesEngine + MinutesEngineType + MinutesEngineSelector 확장 |

## Verification

- `assembleDebug` 빌드 성공 (BUILD SUCCESSFUL)
- NaverClovaMinutesEngine이 MinutesEngine 인터페이스 3개 메서드(generate, engineName, isAvailable) 구현
- splitIntoChunks가 2000자 이하 텍스트는 분할하지 않고, 초과 텍스트는 문장 경계 기준 분할
- MinutesEngineSelector가 GEMINI 선택 시 기존 authMode 로직 유지, DEEPGRAM/NAVER_CLOVA 시 해당 엔진 직접 반환
- MinutesEngineType enum에 3개 값 존재
- SecureApiKeyRepository에 getClovaSummaryClientId/getClovaSummaryClientSecret 메서드 존재
- UserPreferencesRepository에 minutesEngineType Flow + setter + getter 존재
