---
phase: 44-groq-stt-fix
plan: 44-01
subsystem: stt
tags: [bugfix, okhttp, groq, stt, connection-pool]
dependency_graph:
  requires: []
  provides: [groq-stt-working]
  affects: [TranscriptionRepositoryImpl, SttEngineFactory]
tech_stack:
  added: []
  patterns: [response.use{}, OkHttpClient-singleton]
key_files:
  modified:
    - app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt
decisions:
  - response.use{}로 429 continue 경로 포함 모든 응답 경로에서 커넥션 자동 반환
  - callResult null 반환 패턴으로 use{} 블록 내 continue 우회
metrics:
  duration: 5m
  completed: 2026-04-03
  tasks_completed: 3
  files_modified: 1
---

# Phase 44 Plan 01: GroqSttEngine OkHttp Request 재사용 버그 수정 Summary

## One-liner

OkHttpClient 멤버 변수화 + requestBody/request 루프 내부 재생성 + response.use{} 커넥션 자동 반환으로 Groq STT 4건 버그 수정

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1-a | OkHttpClient 클래스 멤버 변수로 이동 | a38d4c2 | GroqSttEngine.kt |
| 1-b | requestBody/request 재시도 루프 내부로 이동 | a38d4c2 | GroqSttEngine.kt |
| 1-c | response.use { resp -> } 패턴 적용 | a38d4c2 | GroqSttEngine.kt |
| 2 | isNullOrBlank() → isBlank() 교체 | a38d4c2 | GroqSttEngine.kt |
| 3 | assembleDebug 빌드 검증 | a38d4c2 | - |

## Bugs Fixed

**버그 1 & 2 (치명)**: `requestBody`와 `request`가 재시도 루프 외부에서 한 번만 생성됨
- 파일 스트림(`MultipartBody`)은 첫 `execute()` 후 소진되어 재사용 불가
- 동일 `Request` 재사용 시 두 번째 호출부터 `IllegalStateException` 또는 빈 body 전송
- 수정: 루프 내부로 이동하여 매 시도마다 새로 생성

**버그 3 (중대)**: 429 재시도 경로에서 `response.close()` 미호출
- `response.body?.string()` 읽은 뒤 `continue`로 넘어가면 커넥션 미반환
- 커넥션 풀 누수 → MAX_RETRIES 재시도 후 고갈 → 타임아웃
- 수정: `response.use { resp -> }` 패턴으로 모든 경로에서 자동 반환
- 429 케이스에서 `use{}` 블록 내 `continue` 불가 문제는 `null` 반환 후 블록 외부에서 `delay` + 재시도 처리

**버그 4 (경미)**: `text.isNullOrBlank()` — `String`은 non-null
- `optString()`은 항상 `String`을 반환하므로 null 체크 불필요
- 수정: `text.isBlank()`로 교체

**추가 개선**: `OkHttpClient` 매 호출마다 재생성 → 클래스 멤버 변수로 이동
- 커넥션 풀 + 스레드 풀 포함 객체를 `@Singleton` 클래스에서 단일 인스턴스로 재사용

## Deviations from Plan

### 구현 조정

**[Rule 1 - 로직] 429 처리 패턴 재설계**
- 찾은 시점: Task 1-c 구현 중
- 문제: 원래 플랜의 `return@use null` + `continue` 패턴에서 `continue`가 `use{}` 블록 외부 루프를 직접 참조할 수 없음
- 수정: `callResult`를 `null`로 받아 블록 외부에서 `delay()` 후 다음 반복으로 진행하는 패턴 채택
- 기존 로직(429 재시도, 타임아웃 재시도, MAX_RETRIES 초과 시 break) 완전 보존
- 파일: `GroqSttEngine.kt`
- 커밋: a38d4c2

## Build Result

```
BUILD SUCCESSFUL in 33s
44 actionable tasks: 10 executed, 34 up-to-date
```

## Known Stubs

없음 — 버그 수정 플랜이므로 스텁 해당 없음

## Self-Check: PASSED

- GroqSttEngine.kt 수정 완료 확인
- 커밋 a38d4c2 존재 확인
- BUILD SUCCESSFUL 확인
