---
phase: 34-whisper
plan: 01
subsystem: stt
tags: [whisper, jni, progress-callback, stt-engine]
dependency_graph:
  requires: []
  provides: [stt-progress-callback]
  affects: [transcription-pipeline]
tech_stack:
  added: []
  patterns: [jni-callback, progress-pipeline]
key_files:
  created: []
  modified:
    - app/src/main/cpp/whisper_jni.cpp
    - app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
    - app/src/main/java/com/autominuting/data/stt/SttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/GeminiSttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/MlKitEngine.kt
    - app/src/main/java/com/autominuting/domain/repository/TranscriptionRepository.kt
    - app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt
decisions:
  - whisper_progress_cb 정적 함수로 JNI 콜백 구현 (같은 스레드이므로 GlobalRef 불필요)
  - onProgress 기본값 = {} 로 기존 호출자 하위 호환성 보장
metrics:
  duration: 2min
  completed: "2026-03-29T14:52:41Z"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 7
---

# Phase 34 Plan 01: Whisper 전사 진행률 콜백 파이프라인 Summary

whisper.cpp progress_callback을 JNI -> Kotlin으로 연결하고, SttEngine/TranscriptionRepository 전체에 onProgress 콜백 체인을 구축하여 전사 진행률 데이터 흐름 완성

## What Was Done

### Task 1: JNI progress_callback + WhisperEngine onNativeProgress 연결
- **Commit:** fb37f41
- whisper_jni.cpp에 `ProgressCallbackData` 구조체 + `whisper_progress_cb` 정적 함수 추가
- `nativeTranscribe` JNI 시그니처에 `jobject progressListener` 파라미터 추가
- whisper_full 호출 전 `params.progress_callback = whisper_progress_cb` 설정
- WhisperEngine.kt에 `@Volatile currentProgressCallback` 프로퍼티 + `onNativeProgress(Int)` 메서드 추가
- `transcribe()` 메서드에 `onProgress: (Float) -> Unit` 파라미터 추가, `this@WhisperEngine`을 progressListener로 전달
- `nativeTranscribe` external 선언에 `progressListener: Any?` 파라미터 추가

### Task 2: SttEngine 인터페이스 + Repository + 다른 엔진 시그니처 정합
- **Commit:** 9485e1e
- SttEngine 인터페이스 `transcribe()`에 `onProgress: (Float) -> Unit = {}` 기본 파라미터 추가
- GeminiSttEngine, MlKitEngine의 `transcribe()` 시그니처에 `onProgress` 파라미터 추가 (본문 변경 없음)
- TranscriptionRepository 인터페이스에 `onProgress` 기본 파라미터 추가
- TranscriptionRepositoryImpl의 `transcribe()` + `tryEngine()`에 `onProgress` 전달 체인 완성

## Data Flow

```
whisper.cpp whisper_full()
  -> progress_callback (whisper_progress_cb)
    -> JNI CallVoidMethod("onNativeProgress", progress)
      -> WhisperEngine.onNativeProgress(Int)
        -> currentProgressCallback(progress / 100f)
          -> TranscriptionRepositoryImpl.onProgress
            -> 호출자 (ViewModel 등)
```

## Deviations from Plan

None - 플랜 그대로 실행됨.

## Known Stubs

None - 모든 레이어에서 실제 콜백 동작.

## Decisions Made

1. **JNI GlobalRef 불사용**: whisper_full()이 같은 스레드에서 콜백을 호출하므로 GlobalRef 불필요. env도 그대로 사용 가능.
2. **기본 파라미터 = {}**: 인터페이스에 기본값을 설정하여 기존 호출자의 컴파일 호환성 보장.

## Self-Check: PASSED

- All 7 modified files exist on disk
- Commits fb37f41 and 9485e1e verified in git log
