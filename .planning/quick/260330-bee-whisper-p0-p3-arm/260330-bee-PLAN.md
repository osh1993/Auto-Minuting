---
phase: quick
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/cpp/whisper_jni.cpp
  - app/src/main/cpp/CMakeLists.txt
  - app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
  - app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt
autonomous: true
requirements: []
must_haves:
  truths:
    - "whisper_context가 동일 모델 경로로 재호출 시 재로드 없이 재사용된다"
    - "n_threads=4로 복원되어 Phase 04 원본과 동일하다"
    - "ARM dotprod+fp16 최적화 플래그가 컴파일에 적용된다"
    - "양자화 모델(ggml-small-q5_1.bin)을 다운로드하고 사용한다"
  artifacts:
    - path: "app/src/main/cpp/whisper_jni.cpp"
      provides: "싱글톤 캐싱 + mutex 보호 + n_threads=4"
      contains: "static whisper_context"
    - path: "app/src/main/cpp/CMakeLists.txt"
      provides: "ARM 최적화 컴파일 플래그"
      contains: "armv8-a+dotprod+fp16"
    - path: "app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt"
      provides: "양자화 모델 파일명 참조"
      contains: "ggml-small-q5_1.bin"
    - path: "app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt"
      provides: "양자화 모델 다운로드 URL"
      contains: "ggml-small-q5_1.bin"
  key_links:
    - from: "WhisperEngine.kt"
      to: "WhisperModelManager.kt"
      via: "MODEL_FILE 상수 일치"
      pattern: "ggml-small-q5_1\\.bin"
    - from: "whisper_jni.cpp"
      to: "whisper_context 싱글톤"
      via: "g_ctx 전역 변수"
      pattern: "static whisper_context.*g_ctx"
---

<objective>
Whisper 전사 성능 4가지 개선 (P0~P3) 일괄 적용.

Purpose: 모델 재로드 제거(P0), 스레드 수 복원(P1), ARM NEON 최적화(P2), 양자화 모델로 메모리/속도 개선(P3)을 통해 전사 속도를 크게 향상시킨다.
Output: 성능 최적화가 적용된 4개 파일 수정.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/cpp/whisper_jni.cpp
@app/src/main/cpp/CMakeLists.txt
@app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
@app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: P0+P1 — whisper_context 싱글톤 캐싱 및 n_threads 복원</name>
  <files>app/src/main/cpp/whisper_jni.cpp</files>
  <action>
whisper_jni.cpp 상단(#include 블록 아래, ProgressCallbackData 위)에 다음 전역 변수 추가:

```cpp
#include <mutex>

// P0: 모델 컨텍스트 싱글톤 캐싱 — 동일 모델 재로드 방지
static whisper_context *g_ctx = nullptr;
static std::string g_cached_model_path;
static std::mutex g_ctx_mutex;
```

nativeTranscribe 함수 본문 시작 부분을 다음과 같이 수정:

1. 기존 JNI 문자열 획득 코드 직후에 `std::lock_guard<std::mutex> lock(g_ctx_mutex);` 추가
2. 기존 모델 로드 블록(49~61행)을 교체:
   - `std::string model_path_str(model_path);`로 문자열 변환
   - `if (g_ctx != nullptr && g_cached_model_path == model_path_str)` → 기존 ctx 재사용, LOGD("모델 캐시 히트")
   - else → 기존 g_ctx가 있으면 `whisper_free(g_ctx)` 후, `whisper_init_from_file_with_params`로 새로 로드, `g_cached_model_path = model_path_str` 갱신
   - 로컬 `whisper_context *ctx` 대신 `g_ctx` 직접 사용
3. 이후 모든 `ctx` 참조를 `g_ctx`로 변경 (whisper_full, whisper_full_n_segments, whisper_full_get_segment_text 호출)
4. 함수 끝의 `whisper_free(ctx);` (186행) 제거 — 앱 종료 시까지 유지
5. 에러 경로(전사 실패 시 164행)에서도 `whisper_free(ctx)` 제거 — 캐시 유지, 에러는 반환만 함. 단, 모델 로드 자체 실패 시(g_ctx == nullptr)는 g_cached_model_path를 빈 문자열로 리셋

P1: params.n_threads 변경:
- 117행 `params.n_threads = 2;` → `params.n_threads = 4;`
- 주석 변경: `// n_threads=4: Phase 04 원본 복원. ARM big.LITTLE 빅코어 4개 활용`

유지해야 할 것:
- ProgressCallbackData 구조체와 whisper_progress_cb 정적 함수 그대로
- 30초 청크 분할 루프 그대로
- WAV 파일 읽기 로직 그대로
- 진행률 콜백 설정 및 청크 기반 콜백 호출 그대로
  </action>
  <verify>
    <automated>cd D:/workspace/temp/Auto_Minuting && grep -n "static whisper_context" app/src/main/cpp/whisper_jni.cpp && grep -n "g_ctx_mutex" app/src/main/cpp/whisper_jni.cpp && grep -n "n_threads = 4" app/src/main/cpp/whisper_jni.cpp && echo "P0+P1 verified"</automated>
  </verify>
  <done>
    - g_ctx, g_cached_model_path, g_ctx_mutex 전역 변수가 선언됨
    - 동일 모델 경로 시 캐시 히트, 다른 경로 시 free 후 재로드
    - lock_guard로 동시 접근 보호
    - 전사 후 whisper_free 호출 없음
    - n_threads = 4로 복원됨
  </done>
</task>

<task type="auto">
  <name>Task 2: P2+P3 — ARM 최적화 플래그 + 양자화 모델 교체</name>
  <files>app/src/main/cpp/CMakeLists.txt, app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt, app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt</files>
  <action>
P2 — CMakeLists.txt (58~63행) target_compile_options 블록에 ARM 최적화 플래그 추가:

```cmake
target_compile_options(whisper_lib PRIVATE
    -O3
    -DNDEBUG
    -fPIC
    -pthread
    -march=armv8-a+dotprod+fp16
)
```

P3 — WhisperEngine.kt:
- 34행 `MODEL_FILE = "ggml-small.bin"` → `MODEL_FILE = "ggml-small-q5_1.bin"`

P3 — WhisperModelManager.kt:
- 34행 `MODEL_FILE = "ggml-small.bin"` → `MODEL_FILE = "ggml-small-q5_1.bin"`
- 35~36행 MODEL_URL 변경:
  ```kotlin
  private const val MODEL_URL =
      "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
  ```
- KDoc 주석(22~26행) 업데이트: `ggml-small.bin` → `ggml-small-q5_1.bin`, "약 500MB" → "약 200MB" (q5_1 양자화 모델 크기)

유지해야 할 것:
- WhisperModelManager의 다운로드 로직, StateFlow, 에러 처리 그대로
- WhisperEngine의 transcribe 메서드, onNativeProgress, isAvailable 그대로
- CMakeLists.txt의 나머지 구성 (소스 목록, include 디렉토리, 링크 라이브러리) 그대로
  </action>
  <verify>
    <automated>cd D:/workspace/temp/Auto_Minuting && grep -n "dotprod" app/src/main/cpp/CMakeLists.txt && grep -n "q5_1" app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt && grep -n "q5_1" app/src/main/java/com/autominuting/data/stt/WhisperModelManager.kt && echo "P2+P3 verified"</automated>
  </verify>
  <done>
    - CMakeLists.txt에 -march=armv8-a+dotprod+fp16 플래그 추가됨
    - WhisperEngine.kt MODEL_FILE이 ggml-small-q5_1.bin으로 변경됨
    - WhisperModelManager.kt MODEL_FILE과 MODEL_URL이 양자화 모델로 변경됨
    - 기존 다운로드/전사 파이프라인 정상 유지
  </done>
</task>

</tasks>

<verification>
1. `grep -rn "ggml-small.bin" app/src/main/` → 결과 없음 (모든 참조가 q5_1으로 변경)
2. `grep -n "whisper_free" app/src/main/cpp/whisper_jni.cpp` → 모델 로드 교체 시에만 존재 (전사 후 호출 없음)
3. `grep -n "n_threads" app/src/main/cpp/whisper_jni.cpp` → 4로 설정
4. 빌드 확인: `cd D:/workspace/temp/Auto_Minuting && ./gradlew assembleDebug` 성공
</verification>

<success_criteria>
- whisper_jni.cpp에 싱글톤 캐싱(g_ctx) + mutex 보호 + n_threads=4 적용
- CMakeLists.txt에 ARM dotprod+fp16 최적화 플래그 적용
- WhisperEngine.kt + WhisperModelManager.kt가 양자화 모델(ggml-small-q5_1.bin) 참조
- assembleDebug 빌드 성공
</success_criteria>

<output>
After completion, create `.planning/quick/260330-bee-whisper-p0-p3-arm/260330-bee-SUMMARY.md`
</output>
