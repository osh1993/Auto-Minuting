---
phase: quick
plan: 260330-cml-p4-ggml-vulkan
subsystem: whisper-stt
tags: [vulkan, gpu, ggml, whisper, ndk]
dependency_graph:
  requires: [260330-bee-whisper-p0-p3-arm]
  provides: [vulkan-backend-code]
  affects: [whisper_jni, cmake-build, manifest]
tech_stack:
  added: [ggml-vulkan]
  patterns: [gpu-fallback]
key_files:
  created: []
  modified:
    - app/src/main/cpp/CMakeLists.txt
    - app/src/main/cpp/whisper_jni.cpp
    - app/src/main/AndroidManifest.xml
decisions:
  - Vulkan 1.3 (0x400003) 최소 버전 지정
  - required=false로 선택적 기능 선언
  - g_use_vulkan 전역 플래그로 최초 1회만 가용성 확인
metrics:
  duration: 2min
  completed: "2026-03-30T00:09:34Z"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 3
---

# Quick Task 260330-cml: ggml Vulkan 백엔드 코드 준비 Summary

ggml Vulkan 백엔드 활성화를 위한 CMake 빌드 설정, JNI Vulkan 초기화/CPU 폴백, AndroidManifest 기능 선언 3건 코드 변경 완료 (빌드 미실행)

## Task Results

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CMakeLists.txt Vulkan 소스/링크 추가 | 727beb6 | app/src/main/cpp/CMakeLists.txt |
| 2 | whisper_jni.cpp Vulkan 초기화 + CPU 폴백 | 4767ec4 | app/src/main/cpp/whisper_jni.cpp |
| 3 | AndroidManifest.xml Vulkan 기능 선언 | 41f5136 | app/src/main/AndroidManifest.xml |

## Changes Detail

### Task 1: CMakeLists.txt Vulkan 소스/링크 추가
- `GGML_VULKAN_DIR` 변수 추가 (ggml/src/ggml-vulkan 경로)
- `GGML_SOURCES`에 `ggml-vulkan.cpp` 소스 파일 추가
- `target_include_directories`에 Vulkan include 경로 추가
- `target_compile_definitions`에 `GGML_USE_VULKAN` 정의 추가
- `target_link_libraries`에 `vulkan` 라이브러리 링크 추가

### Task 2: whisper_jni.cpp Vulkan 초기화 + CPU 폴백
- `ggml-vulkan.h` include 추가
- `g_use_vulkan` 전역 변수로 Vulkan 가용성 상태 캐싱
- `ggml_backend_vk_get_device_count()` 호출로 GPU 디바이스 존재 확인 (최초 1회)
- Vulkan 가용 시 `cparams.gpu_device = 0` 설정으로 GPU 사용
- Vulkan 불가 시 기존 CPU 경로로 자동 폴백

### Task 3: AndroidManifest.xml Vulkan 기능 선언
- `android.hardware.vulkan.version` 1.3 (0x400003) 기능 선언
- `required="false"` 설정으로 Vulkan 미지원 기기 호환성 유지

## Deviations from Plan

None - 플랜 그대로 실행 완료.

## Known Stubs

없음.

## Notes

- 빌드 미실행: Vulkan 셰이더 헤더 부재로 컴파일 에러가 예상됨. 셰이더 컴파일 파이프라인 구축 후 빌드 테스트 필요.
- ggml-vulkan.cpp 소스 파일이 whisper.cpp submodule에 존재하는지 확인 필요.
