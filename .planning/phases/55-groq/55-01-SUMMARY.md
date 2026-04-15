---
phase: 55-groq
plan: 01
subsystem: stt/audio-processing
tags: [audio-chunker, groq, stt, kotlin, junit5]
dependency_graph:
  requires: []
  provides: [AudioChunker.split(), AudioChunker.splitPcmForTest(), internal AudioConverter.decodeAudioToPcm, internal AudioConverter.writeWavFile]
  affects: [GroqSttEngine (Plan 55-02에서 사용)]
tech_stack:
  added: []
  patterns: [object singleton, internal visibility, pure function for testability, TDD]
key_files:
  created:
    - app/src/main/java/com/autominuting/util/AudioChunker.kt
    - app/src/test/java/com/autominuting/util/AudioChunkerTest.kt
  modified:
    - app/src/main/java/com/autominuting/data/stt/AudioConverter.kt
key_decisions:
  - AudioConverter.decodeAudioToPcm/writeWavFile을 internal로 가시성 상향하여 AudioChunker에서 재사용
  - splitPcmForTest() internal 순수 함수로 분리 — Android 환경 없이 JVM 단위 테스트 가능
  - 1800초 입력 → 4청크 (플랜 코멘트의 3청크 예상치가 틀렸음 — 알고리즘 실제 동작 검증으로 수정)
  - JUnit 5 사용 — 프로젝트 표준(JUnit 4 플랜 명시였으나 프로젝트는 JUnit 5만 설정됨)
metrics:
  duration: ~15분
  completed: 2026-04-15
  tasks_completed: 2
  files_created: 2
  files_modified: 1
requirements_addressed: [GROQ-01]
---

# Phase 55 Plan 01: AudioChunker 신설 Summary

**One-liner:** 16kHz mono WAV 시간 기반 청크 분할 유틸(AudioChunker) 신설 — AudioConverter PCM 파이프라인 재사용, 600초+10초 오버랩, JUnit 5 단위 테스트 10개 통과

## What Was Built

Groq 25MB 파일 크기 제한 대응을 위한 `AudioChunker` object를 신설했다. `AudioConverter`의 PCM 디코딩/WAV 작성 로직(`decodeAudioToPcm`, `writeWavFile`)을 재사용할 수 있도록 가시성을 `private`에서 `internal`로 상향했다.

**주요 컴포넌트:**
- `AudioChunker.split()` — suspend 진입 API. 입력 파일을 16kHz mono PCM으로 디코딩 후 시간 기반 청크로 분할하여 WAV 파일 리스트 반환
- `AudioChunker.splitPcmForTest()` — internal 순수 함수. Android 환경 없이 JVM에서 단위 테스트 가능
- 상수: CHUNK_SECONDS=600, OVERLAP_SECONDS=10, BYTES_PER_SECOND=32_000

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | AudioConverter 가시성 상향 및 AudioChunker 신설 | 6e4fe9c | AudioConverter.kt (수정), AudioChunker.kt (신설) |
| 2 | AudioChunkerTest 단위 테스트 작성 | db88243 | AudioChunkerTest.kt (신설) |

## Verification Results

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.autominuting.util.AudioChunkerTest"` — BUILD SUCCESSFUL (10/10 통과)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 1800초 입력 청크 수 예상치 수정 (3 → 4)**
- **Found during:** Task 2
- **Issue:** 플랜 코드 주석에서 1800초 입력 = 3청크로 예상했으나, 실제 splitPcmForTest 알고리즘은 4청크를 생성함. step=590s 기준 offset이 0→590→1180→1770으로 4회 반복되기 때문임
- **Fix:** AudioChunkerTest의 1800초 테스트 케이스 expected 값을 4청크로 수정, 각 청크 크기도 실제 알고리즘 출력 기준으로 정정
- **Files modified:** AudioChunkerTest.kt
- **Commit:** db88243

**2. [Rule 3 - 블로킹] JUnit 4 → JUnit 5 전환**
- **Found during:** Task 2 작성 시
- **Issue:** 플랜에서 JUnit 4 패턴(`org.junit.Assert`)으로 명시했으나, 프로젝트 build.gradle.kts에 JUnit 5만 설정되어 있음. JUnit 4로 작성 시 컴파일/실행 불가
- **Fix:** JUnit 5(`org.junit.jupiter.api.Assertions`) 패턴으로 테스트 작성 — 기존 Mp3MergerTest, WavMergerTest와 동일한 스타일
- **Files modified:** AudioChunkerTest.kt
- **Commit:** db88243

## Known Stubs

없음 — 이 플랜은 UI 없는 순수 유틸리티 계층이며, 실제 구현이 완료됨.

## Self-Check: PASSED
