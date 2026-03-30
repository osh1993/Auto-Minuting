cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml 핵심 소스 (Vulkan 제외 — vulkan.hpp가 NDK에 포함되지 않음)
set(GGML_SOURCES
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-dl.cpp
    ${WHISPER_DIR}/ggml/src/ggml-opt.cpp
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_DIR}/ggml/src/ggml-threading.cpp
    # ggml-cpu
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/binary-ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/unary-ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/vec.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/quants.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/traits.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/repack.cpp
    # ARM aarch64 최적화
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/quants.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/repack.cpp
)

# whisper 소스
set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

# Vulkan 스텁 (vulkan.hpp 미포함 환경에서 링크 오류 방지)
set(STUB_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml-vulkan-stub.cpp
)

# whisper 정적 라이브러리
add_library(whisper_lib STATIC ${GGML_SOURCES} ${WHISPER_SOURCES} ${STUB_SOURCES})

target_include_directories(whisper_lib PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm
    ${WHISPER_DIR}/src
)

target_compile_definitions(whisper_lib PRIVATE
    GGML_USE_CPU
    GGML_VERSION="unknown"
    GGML_COMMIT="unknown"
    WHISPER_VERSION="unknown"
    WHISPER_COMMIT="unknown"
)

target_compile_options(whisper_lib PRIVATE
    -O3
    -DNDEBUG
    -fPIC
    -pthread
    -march=armv8-a+dotprod+fp16
)

# JNI 브릿지
add_library(whisper SHARED whisper_jni.cpp)

target_include_directories(whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
)

target_link_libraries(whisper
    whisper_lib
    log
    dl
)
