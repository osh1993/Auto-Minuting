---
phase: 50-audio-merge
plan: 01
subsystem: share-receiver, audio-util
tags: [wav-merge, share-intent, send-multiple, tdd]
dependency_graph:
  requires: []
  provides: [WavMerger, multi-audio-share]
  affects: [ShareReceiverActivity]
tech_stack:
  added: [JUnit 5.11.4]
  patterns: [WAV PCM concat, ByteBuffer LITTLE_ENDIAN, SEND_MULTIPLE audio detection]
key_files:
  created:
    - app/src/main/java/com/autominuting/util/WavMerger.kt
    - app/src/test/java/com/autominuting/util/WavMergerTest.kt
  modified:
    - app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt
    - app/build.gradle.kts
    - gradle/libs.versions.toml
decisions:
  - JUnit 5 + JUnit Platform Launcher 추가 (프로젝트 첫 단위 테스트 인프라)
  - WavMerger를 별도 object로 분리 — ShareReceiverActivity와 독립적 테스트 가능
  - handleMultipleAudioShare에서 processSharedAudio를 호출하지 않고 직접 구현 — Content URI 수명 보장 + 이미 로컬 파일이므로 URI 변환 불필요
metrics:
  duration: ~15min
  completed: 2026-04-05
  tasks: 2/2
  files_created: 2
  files_modified: 3
---

# Phase 50 Plan 01: WavMerger + ShareReceiverActivity 다중 오디오 합치기 Summary

WAV PCM 바이트 이어붙이기 유틸리티(WavMerger)와 SEND_MULTIPLE audio 감지 분기를 구현하여, 여러 오디오 파일 공유 시 자동으로 합쳐 기존 STT/회의록 파이프라인에 진입시킨다.

## What Was Built

### Task 1: WavMerger 유틸리티 클래스 (TDD)

- **WavHeader** data class: audioFormat, numChannels, sampleRate, byteRate, blockAlign, bitsPerSample, dataSize, dataOffset
- **parseWavHeader()**: RIFF/WAVE 매직 넘버 검증, fmt 청크 파싱, data 청크 순차 검색 (비표준 청크 대응)
- **WavMerger.merge()**: 동일 포맷 검증, PCM 데이터 스트리밍 concat (8KB 버퍼), 헤더 ChunkSize/SubChunk2Size 재계산
- **5개 단위 테스트**: 동일 포맷 합치기, 헤더 ChunkSize 검증, fmt 불일치 예외, 비WAV 예외, 단일 파일 복사

### Task 2: ShareReceiverActivity 다중 오디오 분기

- **SEND_MULTIPLE + audio 감지**: `isAudioShare` 블록 직전에 `ACTION_SEND_MULTIPLE` + 전체 URI audio MIME 검사 분기 추가
- **handleMultipleAudioShare()**: lifecycleScope 내에서 WavMerger.merge() 호출 후 Meeting 생성 + TranscriptionTriggerWorker enqueue
- **첫 번째 파일명 사용** (MERGE-02): getDisplayName(uris.first())로 Meeting 제목 설정
- **에러 처리**: 비WAV "WAV 파일만 합치기를 지원합니다", fmt 불일치 "오디오 파일 형식이 일치하지 않아 합칠 수 없습니다"
- **단일 파일 SEND_MULTIPLE** (Pitfall 5): size == 1이면 기존 processSharedAudio()로 위임
- **RECORD_AUDIO 권한**: pendingAudioUris 필드 추가, 기존 권한 콜백에서 다중/단일 분기

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 (RED) | 2499fc4 | WavMerger 실패 테스트 5개 추가 + JUnit 5 의존성 |
| 1 (GREEN) | bf96305 | WavMerger 구현 — 5개 테스트 통과 |
| 2 | 67c5c7f | ShareReceiverActivity 다중 오디오 합치기 분기 추가 |

## Verification Results

1. `./gradlew testDebugUnitTest --tests "com.autominuting.util.WavMergerTest"` -- 5개 테스트 모두 통과
2. `./gradlew assembleDebug` -- 빌드 성공
3. 기존 단일 파일 `isAudioShare` 경로 및 `processSharedAudio()` 메서드 변경 없음 확인

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JUnit Platform Launcher 누락**
- **Found during:** Task 1 (GREEN)
- **Issue:** JUnit 5 테스트 실행 시 `Could not start Gradle Test Executor` 오류 — JUnit Platform Launcher 의존성 누락
- **Fix:** `junit-platform-launcher:1.11.4` testRuntimeOnly 의존성 추가
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts
- **Commit:** bf96305

## Known Stubs

없음 — 모든 데이터 소스가 실제 코드에 연결됨.

## Self-Check: PASSED

- [x] WavMerger.kt 존재
- [x] WavMergerTest.kt 존재
- [x] SUMMARY.md 존재
- [x] Commit 2499fc4 존재
- [x] Commit bf96305 존재
- [x] Commit 67c5c7f 존재
