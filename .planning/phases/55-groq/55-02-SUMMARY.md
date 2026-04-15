---
phase: 55-groq
plan: 02
subsystem: stt
tags: [groq, whisper, chunked-transcription, audio-chunker, kotlin, hilt]

# Dependency graph
requires:
  - phase: 55-01
    provides: AudioChunker.split() — 25MB 초과 오디오를 16kHz mono WAV 청크로 분할
provides:
  - GroqSttEngine.transcribeChunked() — 25MB 초과 파일 자동 분할 후 순차 전사 + 이어붙이기
  - GroqSttEngine.transcribeSingle() — 기존 단일 요청 로직 재사용 가능 private 함수로 추출
  - 파일 크기 분기 (fileSize <= MAX_FILE_SIZE) — 25MB 기준 자동 경로 선택
affects: [55-groq, groq-stt, pipeline, stt-engine]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "크기 기반 분기 패턴: transcribe() → transcribeSingle() / transcribeChunked() 위임"
    - "finally 블록 임시 디렉토리 정리 — 성공/실패 모두 보장"
    - "청크 완료 기반 이산 진행률 콜백 ((i+1)/total)"
    - "CHUNK_DELAY_MS 상수로 RPM 안전 마진 분리"

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt

key-decisions:
  - "AudioConverter를 GroqSttEngine 생성자에 추가 — Hilt @Singleton + @Inject constructor()로 자동 바인딩, SttModule.kt 수정 불필요"
  - "청크 임시 디렉토리를 cacheDir 대신 회의 폴더 하위에 생성 — WorkManager 재시도 시 cacheDir 비워짐 방어"
  - "단순 concat stitching (joinToString(newline)) — GROQ-03 요구사항은 이어붙이기만 명시, LCS 미적용"
  - "CHUNK_DELAY_MS=500L — Free tier RPM 20 안전 마진 하한, RESEARCH.md 권고 500~1000ms"

patterns-established:
  - "단일/청크 분기 패턴: 공통 entrypoint(transcribe)에서 크기 기반으로 specialized 함수 위임"
  - "transcribeSingle 재사용: 청크 내부 전사도 동일 함수 사용 → DRY + 재시도 로직 공유"

requirements-completed: [GROQ-01, GROQ-02, GROQ-03]

# Metrics
duration: 6min
completed: 2026-04-15
---

# Phase 55 Plan 02: Groq 청크 분할 전사 Summary

**GroqSttEngine.transcribe()의 25MB 실패 경로를 AudioChunker 기반 자동 분할 → 순차 전사 → joinToString 이어붙이기 경로로 교체 (GROQ-01/02/03 완성)**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-15T12:49:00Z
- **Completed:** 2026-04-15T12:55:29Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- GroqSttEngine 생성자에 AudioConverter 추가 (Hilt 자동 바인딩)
- transcribe() 크기 분기 구현: 25MB 이하 → transcribeSingle(), 초과 → transcribeChunked()
- transcribeSingle() private 함수로 기존 단일 요청 로직 추출 — 단일 파일 및 각 청크 모두 재사용
- transcribeChunked(): AudioChunker.split() 호출 → forEachIndexed 순차 전사 → joinToString("\n") 이어붙이기
- finally 블록에서 groq_chunks_{timestamp}/ 임시 디렉토리 삭제 (성공/실패 모두)
- CHUNK_DELAY_MS=500L 추가로 Free tier RPM 20 안전 마진 확보
- 기존 25MB 실패 에러 메시지 완전 제거

## Task Commits

1. **Task 1: transcribe() 크기 분기 + transcribeSingle/transcribeChunked 분리** - `36a9d57` (feat)

**Plan metadata:** (docs commit 예정)

## Files Created/Modified

- `app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt` - AudioConverter 의존성 추가, transcribeSingle/transcribeChunked 신설, 크기 분기 로직 구현, 구 25MB 실패 경로 제거

## Decisions Made

- AudioConverter를 GroqSttEngine 생성자에 추가 — Hilt @Singleton + @Inject constructor()로 자동 바인딩, SttModule.kt 수정 불필요
- 청크 임시 디렉토리를 cacheDir 대신 회의 폴더 하위에 생성 — WorkManager 재시도 시 cacheDir 비워짐 방어
- 단순 concat stitching (joinToString("\n")) — GROQ-03 요구사항은 이어붙이기만 명시, LCS 미적용
- CHUNK_DELAY_MS=500L — Free tier RPM 20 안전 마진 하한, 55-RESEARCH.md 권고 500~1000ms

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- GROQ-01/02/03 요구사항 모두 완성 — 25MB 초과 파일도 사용자 개입 없이 전사 파이프라인 통과
- Phase 55 전체 완료 (55-01: AudioChunker 신설, 55-02: GroqSttEngine 통합)
- 실기기 UAT에서 25MB 초과 M4A 파일 전사 검증 필요

## Self-Check: PASSED

- GroqSttEngine.kt: FOUND
- 55-02-SUMMARY.md: FOUND
- Commit 36a9d57: FOUND

---
*Phase: 55-groq*
*Completed: 2026-04-15*
