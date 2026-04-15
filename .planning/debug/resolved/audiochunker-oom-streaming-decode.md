---
slug: audiochunker-oom-streaming-decode
status: resolved
phase: 55
created: 2026-04-15T22:30:00Z
updated: 2026-04-15T23:10:00Z
---

## Issue

65MB M4A 파일 전사 시 `AudioChunker.split()` → `AudioConverter.decodeAudioToPcm()` 에서 OOM 발생.

## Error

```
java.lang.OutOfMemoryError: Failed to allocate a 536870928 byte allocation
  with 50331648 free bytes and 222MB until OOM,
  target footprint 353672128, growth limit 536870912
  at AudioConverter.decodeAudioToPcm$app(AudioConverter.kt:161)
  at AudioChunker$split$2.invokeSuspend(AudioChunker.kt:38)
```

## Root Cause (Confirmed)

현재 `AudioChunker.split()` 구조:
1. `audioConverter.decodeAudioToPcm(inputPath)` → 파일 **전체** PCM을 `ByteArray`로 메모리에 로드
2. `splitPcmForTest(pcm, ...)` → 메모리상 ByteArray를 잘라 청크 생성

65MB M4A → 디코딩 후 PCM ≈ 537MB → 힙 한도(512MB) 초과 → OOM

## Fix Plan

`AudioChunker.split()`을 스트리밍 방식으로 재구현:
- `AudioConverter.decodeAudioToPcm()` 전체 로드 대신
- `MediaExtractor` + `MediaCodec`으로 600초 단위 직접 WAV 파일 쓰기
- `splitPcmForTest()` 유지 (단위 테스트용 순수 함수)
- `AudioConverter.decodeAudioToPcm()` internal 유지 (25MB 이하 단일 전사 경로 재사용)

## Resolution

root_cause: AudioChunker.split()이 AudioConverter.decodeAudioToPcm()로 파일 전체 PCM을 단일 ByteArray로 로드 → 65MB M4A → ~537MB ByteArray 할당 시도 → 힙 한도(512MB) 초과 OOM
fix: AudioChunker.split()을 MediaExtractor + MediaCodec 직접 사용 스트리밍 방식으로 재구현. 청크 단위(최대 19.2MB) ByteArrayOutputStream에 누적 후 WAV 플러시. AudioConverter.decodeAudioToPcm()은 25MB 이하 단일 전사 경로에서 계속 사용.
verification: compileDebugKotlin 성공, AudioChunkerTest 전체 통과, assembleDebug 성공
files_changed: [app/src/main/java/com/autominuting/util/AudioChunker.kt]

## Status

- [x] 증상 확인 (logcat OOM 스택트레이스)
- [x] 근본 원인 파악
- [x] 스트리밍 디코딩 재구현 완료
- [x] 컴파일 / 단위 테스트 / 빌드 검증 통과
