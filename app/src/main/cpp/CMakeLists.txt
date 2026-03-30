cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(GGML_VULKAN_DIR ${WHISPER_DIR}/ggml/src/ggml-vulkan)

# ggml 핵심 소스
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
    # Vulkan 백엔드
    ${GGML_VULKAN_DIR}/ggml-vulkan.cpp
)

# Vulkan 셰이더 SPIR-V 임베딩 소스 (vulkan-shaders-gen으로 사전 컴파일)
file(GLOB VULKAN_SHADER_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/vulkan-shaders/*.cpp)
list(APPEND GGML_SOURCES ${VULKAN_SHADER_SOURCES})

# whisper 소스
set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

# whisper 정적 라이브러리
add_library(whisper_lib STATIC ${GGML_SOURCES} ${WHISPER_SOURCES})

target_include_directories(whisper_lib PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch
    ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm
    ${WHISPER_DIR}/src
    ${GGML_VULKAN_DIR}
    # vulkan-hpp/ : vulkan.hpp(C++ wrapper) + vk_video(av1 포함) — NDK sysroot보다 우선
    ${CMAKE_CURRENT_SOURCE_DIR}/vulkan-hpp
    # ggml-vulkan-shaders.hpp (vulkan-shaders-gen으로 생성된 SPIR-V 임베딩)
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(whisper_lib PRIVATE
    GGML_USE_CPU
    GGML_USE_VULKAN
    GGML_VERSION="unknown"
    GGML_COMMIT="unknown"
    WHISPER_VERSION="unknown"
    WHISPER_COMMIT="unknown"
    # Vulkan-Hpp 동적 디스패치 설정
    VULKAN_HPP_DISPATCH_LOADER_DYNAMIC=1
    # Android Vulkan 플랫폼
    VK_USE_PLATFORM_ANDROID_KHR
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
    ${GGML_VULKAN_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/vulkan-hpp
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(whisper
    whisper_lib
    log
    dl
    vulkan   # Android NDK 내장 Vulkan 라이브러리
)
