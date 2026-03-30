// Vulkan 백엔드 스텁 — Android NDK에 vulkan.hpp가 없어 ggml-vulkan.cpp를 컴파일할 수 없으므로
// 링크 오류를 방지하기 위한 빈 구현을 제공한다.
#include <stddef.h>

extern "C" {

int ggml_backend_vk_get_device_count(void) {
    return 0;
}

void ggml_backend_vk_get_device_description(int /*device*/, char * description, size_t description_size) {
    if (description && description_size > 0) {
        description[0] = '\0';
    }
}

void ggml_backend_vk_get_device_memory(int /*device*/, size_t * free, size_t * total) {
    if (free) *free = 0;
    if (total) *total = 0;
}

} // extern "C"
