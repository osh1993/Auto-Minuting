---
phase: quick
plan: 260330-bee
subsystem: stt/whisper
tags: [performance, ndk, whisper, arm-optimization, quantization]
dependency_graph:
  requires: []
  provides: [whisper-singleton-cache, arm-neon-optimization, quantized-model]
  affects: [whisper-transcription-speed, model-download-size]
tech_stack:
  added: []
  patterns: [singleton-caching, mutex-thread-safety, arm-dotprod-fp16]
key_files:
  created: []
  modified:
    - app/src/main/cpp/whisper_jni.cpp
    - app/src/main/cpp/CMakeLists.txt
    - app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
    - app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt
decisions:
  - "싱글톤 캐싱으로 모델 재로드 제거 — 앱 종료 시까지 g_ctx 유지"
  - "n_threads=4 복원 — ARM big.LITTLE 빅코어 활용"
  - "q5_1 양자화 모델로 교체 — 500MB→200MB, 속도/메모리 개선"
metrics:
  duration: 2min
  completed: 2026-03-30
  tasks: 2
  files: 4
---

# Quick Task 260330-bee: Whisper P0~P3 성능 최적화 Summary

whisper_context 싱글톤 캐싱(P0) + n_threads=4 복원(P1) + ARM dotprod/fp16 최적화(P2) + q5_1 양자화 모델 교체(P3)로 전사 성능 4가지 개선 일괄 적용

## 완료된 작업

### Task 1: P0+P1 — whisper_context 싱글톤 캐싱 및 n_threads 복원
- **커밋:** `3ab5a68`
- **변경:** `whisper_jni.cpp` — 전역 `g_ctx` 싱글톤 + `std::mutex` 보호 + 전사 후 `whisper_free` 제거 + `n_threads=4`
- **효과:** 동일 모델 재로드 제거 (매 전사마다 ~2초 절약), 빅코어 4개 활용으로 전사 속도 향상

### Task 2: P2+P3 — ARM 최적화 플래그 + 양자화 모델 교체
- **커밋:** `73c1504`
- **변경:**
  - `CMakeLists.txt` — `-march=armv8-a+dotprod+fp16` 컴파일 플래그 추가
  - `WhisperEngine.kt` — MODEL_FILE을 `ggml-small-q5_1.bin`으로 변경
  - `WhisperModelManager.kt` — MODEL_FILE + MODEL_URL + KDoc 양자화 모델로 변경 (500MB→200MB)
- **효과:** ARM NEON dotprod/fp16 명령어 활용 + 모델 용량 60% 감소

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] WhisperEngine.kt KDoc 누락 수정**
- **Found during:** Task 2
- **Issue:** WhisperEngine.kt의 KDoc 주석에 `ggml-small.bin` 참조가 남아 있었음
- **Fix:** `ggml-small-q5_1.bin`으로 업데이트
- **Files modified:** WhisperEngine.kt
- **Commit:** `73c1504`

## Known Stubs

None.

## Self-Check: PASSED
