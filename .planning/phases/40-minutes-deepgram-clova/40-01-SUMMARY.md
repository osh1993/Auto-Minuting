---
phase: 40-minutes-deepgram-clova
plan: 01
subsystem: minutes-engine
tags: [deepgram, text-intelligence, minutes, summary]
dependency_graph:
  requires: [SecureApiKeyRepository.getDeepgramApiKey]
  provides: [DeepgramMinutesEngine]
  affects: [minutes-engine-selection]
tech_stack:
  added: [deepgram-text-intelligence-api]
  patterns: [okhttp-token-auth, retry-with-backoff]
key_files:
  created:
    - app/src/main/java/com/autominuting/data/minutes/DeepgramMinutesEngine.kt
  modified: []
decisions:
  - "Token 인증 방식 사용 (Bearer 아님) — Deepgram 고유 방식, DeepgramSttEngine과 동일"
  - "customPrompt 무시 — Deepgram API는 자체 요약 로직 사용, 커스텀 프롬프트 적용 불가"
  - "summary.short -> summary.text 폴백 — API 응답 필드 우선순위"
metrics:
  duration: "3분"
  completed: "2026-03-31"
  tasks_completed: 1
  tasks_total: 1
  files_created: 1
  files_modified: 0
requirements: [MIN6-01, MIN6-03]
---

# Phase 40 Plan 01: DeepgramMinutesEngine 구현 Summary

Deepgram Text Intelligence API (/v1/read)를 사용하여 영어 전사 텍스트를 요약하는 MinutesEngine 구현체 완성

## Task Results

### Task 1: DeepgramMinutesEngine 구현

- **Commit:** `f49bbeb`
- **파일:** `app/src/main/java/com/autominuting/data/minutes/DeepgramMinutesEngine.kt` (142줄)
- **결과:** MinutesEngine 인터페이스의 3개 메서드 모두 구현 완료
  - `generate()`: Deepgram /v1/read API에 POST 요청, JSON 응답에서 summary 추출
  - `engineName()`: "Deepgram Intelligence (영어만)" 반환
  - `isAvailable()`: SecureApiKeyRepository에서 API 키 존재 여부 확인
- **빌드:** assembleDebug BUILD SUCCESSFUL

## 핵심 구현 사항

| 항목 | 구현 |
|------|------|
| 인증 | `Authorization: Token $apiKey` (Deepgram 고유 방식) |
| 엔드포인트 | `https://api.deepgram.com/v1/read?summarize=true&language=en` |
| 요청 body | `{"text": "..."}` JSON POST |
| 응답 파싱 | `results.summary.short` -> `results.summary.text` 폴백 |
| 재시도 | 최대 3회, 429/SocketTimeout 시 20초 대기 |
| customPrompt | 무시 (로그 경고 출력) |
| 타임아웃 | connect 30s, read 60s, write 30s |

## Deviations from Plan

None - 플랜이 정확하게 실행되었다.

## Known Stubs

None - 모든 로직이 완전히 구현되었다.

## Self-Check: PASSED
