---
phase: quick
plan: 260330-cml-p4-ggml-vulkan
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/cpp/CMakeLists.txt
  - app/src/main/cpp/whisper_jni.cpp
  - app/src/main/AndroidManifest.xml
autonomous: true
must_haves:
  truths:
    - "CMakeLists.txt에 Vulkan 소스, include, 정의, 링크가 추가되어 있다"
    - "whisper_jni.cpp에 Vulkan 가용성 확인 및 GPU 디바이스 설정 코드가 있다"
    - "AndroidManifest.xml에 Vulkan 기능 선언이 required=false로 있다"
  artifacts:
    - path: "app/src/main/cpp/CMakeLists.txt"
      contains: "GGML_USE_VULKAN"
    - path: "app/src/main/cpp/whisper_jni.cpp"
      contains: "ggml_backend_vk_get_device_count"
    - path: "app/src/main/AndroidManifest.xml"
      contains: "android.hardware.vulkan.version"
  key_links:
    - from: "CMakeLists.txt"
      to: "ggml-vulkan.cpp"
      via: "GGML_SOURCES 목록에 Vulkan 소스 추가"
    - from: "whisper_jni.cpp"
      to: "ggml-vulkan.h"
      via: "#include 및 API 호출"
---

<objective>
ggml Vulkan 백엔드를 활성화하기 위한 코드 변경 3건 (빌드 미실행).

Purpose: Whisper 전사에 GPU 가속을 활용하기 위한 Vulkan 백엔드 기반 코드 준비
Output: CMakeLists.txt, whisper_jni.cpp, AndroidManifest.xml 수정 완료 (각 atomic commit)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/cpp/CMakeLists.txt
@app/src/main/cpp/whisper_jni.cpp
@app/src/main/AndroidManifest.xml
</context>

<tasks>

<task type="auto">
  <name>Task 1: CMakeLists.txt Vulkan 소스/링크 추가</name>
  <files>app/src/main/cpp/CMakeLists.txt</files>
  <action>
다음 4가지 변경을 수행한다:

1. GGML_SOURCES 목록 위(line 7 전)에 Vulkan 디렉토리 변수 추가:
   set(GGML_VULKAN_DIR ${WHISPER_DIR}/ggml/src/ggml-vulkan)

2. GGML_SOURCES 목록 끝(line 29 ARM repack.cpp 다음, 닫는 괄호 전)에 Vulkan 소스 추가:
   # ggml-vulkan
   ${GGML_VULKAN_DIR}/ggml-vulkan.cpp

3. target_include_directories(whisper_lib ...) 블록에 Vulkan 디렉토리 추가 (line 47 ${WHISPER_DIR}/src 다음):
   ${GGML_VULKAN_DIR}

4. target_compile_definitions(whisper_lib ...) 블록에 추가 (line 51 GGML_USE_CPU 다음):
   GGML_USE_VULKAN

5. target_link_libraries(whisper ...) 블록에 추가 (line 77 dl 다음):
   vulkan

주의: 기존 코드 구조(들여쓰기, 주석 스타일) 유지. 빌드 실행 금지.
  </action>
  <verify>
    <automated>grep -c "GGML_USE_VULKAN\|ggml-vulkan\|GGML_VULKAN_DIR" app/src/main/cpp/CMakeLists.txt</automated>
  </verify>
  <done>CMakeLists.txt에 GGML_VULKAN_DIR 변수, ggml-vulkan.cpp 소스, Vulkan include, GGML_USE_VULKAN 정의, vulkan 링크가 모두 추가됨</done>
</task>

<task type="auto">
  <name>Task 2: whisper_jni.cpp Vulkan 초기화 + CPU 폴백</name>
  <files>app/src/main/cpp/whisper_jni.cpp</files>
  <action>
다음 3가지 변경을 수행한다:

1. 상단 #include 블록(line 7 "whisper.h" 다음)에 추가:
   #include "ggml-vulkan.h"

2. 전역 변수 영역(line 16 g_ctx_mutex 다음)에 추가:
   static bool g_use_vulkan = false;

3. nativeTranscribe 함수 내 mutex lock 직후(line 56 lock_guard 다음, line 59 model_path_str 전)에 Vulkan 가용성 확인 코드 추가:
   // P4: Vulkan 백엔드 가용성 확인 (최초 1회)
   if (!g_use_vulkan) {
       g_use_vulkan = (ggml_backend_vk_get_device_count() > 0);
       if (g_use_vulkan) {
           char desc[256];
           ggml_backend_vk_get_device_description(0, desc, sizeof(desc));
           LOGD("Vulkan 백엔드 활성화: %s", desc);
       } else {
           LOGD("Vulkan 불가 — CPU 폴백");
       }
   }

4. 모델 로드 시 whisper_context_params 설정 직후(line 69 cparams 생성 다음, line 70 whisper_init 전)에 GPU 디바이스 설정 추가:
   // Vulkan 백엔드 사용 시 GPU 디바이스 설정
   if (g_use_vulkan) {
       cparams.gpu_device = 0;
   }

주의: 기존 progress_callback, 청크 루프 로직, g_ctx 싱글톤 캐싱 코드 일체 변경 금지.
  </action>
  <verify>
    <automated>grep -c "ggml-vulkan.h\|g_use_vulkan\|ggml_backend_vk_get_device_count\|gpu_device" app/src/main/cpp/whisper_jni.cpp</automated>
  </verify>
  <done>whisper_jni.cpp에 ggml-vulkan.h include, g_use_vulkan 전역변수, Vulkan 가용성 확인 로직, GPU 디바이스 설정이 추가됨. 기존 로직 미변경.</done>
</task>

<task type="auto">
  <name>Task 3: AndroidManifest.xml Vulkan 기능 선언</name>
  <files>app/src/main/AndroidManifest.xml</files>
  <action>
uses-permission 블록 마지막(line 25 RECORD_AUDIO 다음, line 27 queries 전)에 Vulkan 기능 선언 추가:

    <!-- Vulkan GPU 가속 (선택적 — 없으면 CPU 폴백) -->
    <uses-feature
        android:name="android.hardware.vulkan.version"
        android:version="0x400003"
        android:required="false" />

주의: required="false" 필수 — Vulkan 미지원 기기에서도 앱 설치 가능해야 함.
  </action>
  <verify>
    <automated>grep -c "android.hardware.vulkan.version" app/src/main/AndroidManifest.xml</automated>
  </verify>
  <done>AndroidManifest.xml에 Vulkan 1.3 기능 선언이 required=false로 추가됨</done>
</task>

</tasks>

<verification>
- CMakeLists.txt: GGML_VULKAN_DIR, ggml-vulkan.cpp, GGML_USE_VULKAN, vulkan 링크 확인
- whisper_jni.cpp: ggml-vulkan.h include, g_use_vulkan, Vulkan 확인 로직, gpu_device 설정 확인
- AndroidManifest.xml: uses-feature vulkan.version required=false 확인
- 빌드 실행하지 않음 (셰이더 헤더 부재로 컴파일 에러 예상)
</verification>

<success_criteria>
3개 파일 모두 Vulkan 관련 코드가 정확히 추가되고, 각 태스크마다 atomic commit 완료
</success_criteria>

<output>
After completion, create `.planning/quick/260330-cml-p4-ggml-vulkan/260330-cml-SUMMARY.md`
</output>
