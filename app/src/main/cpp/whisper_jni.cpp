#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <mutex>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// P0: 모델 컨텍스트 싱글톤 캐싱 — 동일 모델 재로드 방지
static whisper_context *g_ctx = nullptr;
static std::string g_cached_model_path;
static std::mutex g_ctx_mutex;

// 진행률 콜백 데이터 구조체 — JNI를 통해 Kotlin으로 진행률 전달
struct ProgressCallbackData {
    JNIEnv *env;
    jobject callback_obj;   // WhisperEngine 인스턴스 (같은 스레드이므로 Global Ref 불필요)
    jmethodID method_id;    // onNativeProgress(int) 메서드 ID
    int last_progress;      // 이전 진행률 (1% 이상 변경 시만 콜백)
};

// whisper.cpp progress_callback에 등록할 정적 함수
static void whisper_progress_cb(
    struct whisper_context *ctx,
    struct whisper_state *state,
    int progress,
    void *user_data
) {
    (void)ctx;
    (void)state;
    auto *data = (ProgressCallbackData *)user_data;
    if (progress - data->last_progress >= 1) {
        data->env->CallVoidMethod(data->callback_obj, data->method_id, (jint)progress);
        data->last_progress = progress;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_autominuting_data_stt_WhisperEngine_nativeTranscribe(
    JNIEnv *env, jobject thiz,
    jstring modelPath, jstring audioPath,
    jstring language, jfloat temperature,
    jobject progressListener) {

    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char *audio_path = env->GetStringUTFChars(audioPath, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    LOGD("전사 시작: model=%s, audio=%s, lang=%s, temp=%.1f", model_path, audio_path, lang, temperature);

    // P0: mutex로 동시 접근 보호
    std::lock_guard<std::mutex> lock(g_ctx_mutex);

    // P0: 모델 싱글톤 캐싱 — 동일 모델 경로면 재사용, 다른 경로면 교체
    std::string model_path_str(model_path);
    if (g_ctx != nullptr && g_cached_model_path == model_path_str) {
        LOGD("모델 캐시 히트: %s", model_path);
    } else {
        if (g_ctx != nullptr) {
            LOGD("모델 교체: %s → %s", g_cached_model_path.c_str(), model_path);
            whisper_free(g_ctx);
            g_ctx = nullptr;
        }

        struct whisper_context_params cparams = whisper_context_default_params();
        g_ctx = whisper_init_from_file_with_params(model_path, cparams);

        if (g_ctx == nullptr) {
            LOGE("모델 로드 실패: %s", model_path);
            g_cached_model_path = "";
            env->ReleaseStringUTFChars(modelPath, model_path);
            env->ReleaseStringUTFChars(audioPath, audio_path);
            env->ReleaseStringUTFChars(language, lang);
            return nullptr;
        }

        g_cached_model_path = model_path_str;
        LOGD("모델 로드 완료: %s", model_path);
    }

    // WAV 파일 읽기 (16kHz mono PCM)
    std::vector<float> pcmf32;
    {
        // WAV 헤더 파싱
        FILE *f = fopen(audio_path, "rb");
        if (!f) {
            LOGE("오디오 파일 열기 실패: %s", audio_path);
            env->ReleaseStringUTFChars(modelPath, model_path);
            env->ReleaseStringUTFChars(audioPath, audio_path);
            env->ReleaseStringUTFChars(language, lang);
            return nullptr;
        }

        // RIFF 헤더 건너뛰기 (44 bytes)
        char header[44];
        size_t header_read = fread(header, 1, 44, f);
        if (header_read < 44) {
            LOGE("WAV 헤더 읽기 실패");
            fclose(f);
            env->ReleaseStringUTFChars(modelPath, model_path);
            env->ReleaseStringUTFChars(audioPath, audio_path);
            env->ReleaseStringUTFChars(language, lang);
            return nullptr;
        }

        // PCM 데이터 읽기 (16-bit signed int → float 변환)
        fseek(f, 0, SEEK_END);
        long file_size = ftell(f);
        long data_size = file_size - 44;
        fseek(f, 44, SEEK_SET);

        int num_samples = data_size / 2; // 16-bit = 2 bytes per sample
        pcmf32.resize(num_samples);

        std::vector<int16_t> pcm16(num_samples);
        size_t samples_read = fread(pcm16.data(), sizeof(int16_t), num_samples, f);
        fclose(f);

        LOGD("오디오 로드: %zu samples (%.1f초)", samples_read, (float)samples_read / 16000.0f);

        // int16 → float32 정규화
        for (size_t i = 0; i < samples_read; i++) {
            pcmf32[i] = (float)pcm16[i] / 32768.0f;
        }
        pcmf32.resize(samples_read);
    }

    // Whisper 전사 파라미터 설정
    // n_threads=4: Phase 04 원본 복원. ARM big.LITTLE 빅코어 4개 활용
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.temperature = temperature;
    params.n_threads = 4;
    params.print_progress = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.single_segment = false;

    // 진행률 콜백 설정 (청크 기반 진행률 직접 관리 — whisper 내부 콜백은 미등록)
    ProgressCallbackData cb_data = {nullptr, nullptr, nullptr, 0};
    if (progressListener != nullptr) {
        jclass listenerClass = env->GetObjectClass(progressListener);
        jmethodID onProgress = env->GetMethodID(listenerClass, "onNativeProgress", "(I)V");
        if (onProgress != nullptr) {
            cb_data.env = env;
            cb_data.callback_obj = progressListener;
            cb_data.method_id = onProgress;
            // whisper 내부 progress_callback은 청크 루프에서 직접 관리하므로 등록 불필요
            LOGD("진행률 콜백 등록 완료 (청크 기반)");
        }
    }

    // 청크 크기: 30초 × 16000 샘플/초 = 480000 샘플
    // 긴 파일을 한 번에 처리하면 OOM이 발생하므로 30초 단위로 나눠 처리한다
    const size_t CHUNK_SAMPLES = 30 * WHISPER_SAMPLE_RATE;
    size_t total_samples = pcmf32.size();
    size_t chunks = (total_samples + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES;
    if (chunks < 1) chunks = 1;

    LOGD("전사 시작 (samples=%zu, threads=%d, chunks=%zu)", total_samples, params.n_threads, chunks);

    std::string full_text;

    for (size_t chunk_idx = 0; chunk_idx < chunks; chunk_idx++) {
        size_t start = chunk_idx * CHUNK_SAMPLES;
        size_t end = start + CHUNK_SAMPLES;
        if (end > total_samples) end = total_samples;
        size_t chunk_size = end - start;

        // 진행률 콜백: 청크 기반 진행률 전달
        if (cb_data.env != nullptr) {
            int chunk_progress = (int)((chunk_idx * 100) / chunks);
            cb_data.env->CallVoidMethod(cb_data.callback_obj, cb_data.method_id, (jint)chunk_progress);
        }

        int result = whisper_full(g_ctx, params, pcmf32.data() + start, (int)chunk_size);

        if (result != 0) {
            LOGE("전사 실패 (chunk %zu/%zu): error code %d", chunk_idx + 1, chunks, result);
            // P0: 전사 실패 시에도 캐시 유지 — whisper_free 호출 안 함
            env->ReleaseStringUTFChars(modelPath, model_path);
            env->ReleaseStringUTFChars(audioPath, audio_path);
            env->ReleaseStringUTFChars(language, lang);
            return nullptr;
        }

        // 이 청크의 결과 텍스트 수집
        int n_segments = whisper_full_n_segments(g_ctx);
        for (int i = 0; i < n_segments; i++) {
            const char *segment_text = whisper_full_get_segment_text(g_ctx, i);
            if (segment_text) {
                full_text += segment_text;
                full_text += "\n";
            }
        }

        LOGD("청크 %zu/%zu 완료: %d segments", chunk_idx + 1, chunks, n_segments);
    }

    LOGD("전사 완료: 전체 %zu 청크 처리", chunks);

    // P0: whisper_free 제거 — 앱 종료 시까지 모델 캐시 유지

    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(audioPath, audio_path);
    env->ReleaseStringUTFChars(language, lang);

    LOGD("전사 결과: %zu자", full_text.length());

    if (full_text.empty()) {
        return nullptr;
    }

    return env->NewStringUTF(full_text.c_str());
}
