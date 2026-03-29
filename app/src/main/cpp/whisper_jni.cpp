#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

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

    // 모델 로드
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);

    if (ctx == nullptr) {
        LOGE("모델 로드 실패: %s", model_path);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(audioPath, audio_path);
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }

    LOGD("모델 로드 완료");

    // WAV 파일 읽기 (16kHz mono PCM)
    std::vector<float> pcmf32;
    {
        // WAV 헤더 파싱
        FILE *f = fopen(audio_path, "rb");
        if (!f) {
            LOGE("오디오 파일 열기 실패: %s", audio_path);
            whisper_free(ctx);
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
            whisper_free(ctx);
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
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.temperature = temperature;
    params.n_threads = 4;
    params.print_progress = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.single_segment = false;

    // 진행률 콜백 설정
    ProgressCallbackData cb_data = {nullptr, nullptr, nullptr, 0};
    if (progressListener != nullptr) {
        jclass listenerClass = env->GetObjectClass(progressListener);
        jmethodID onProgress = env->GetMethodID(listenerClass, "onNativeProgress", "(I)V");
        if (onProgress != nullptr) {
            cb_data.env = env;
            cb_data.callback_obj = progressListener;
            cb_data.method_id = onProgress;
            params.progress_callback = whisper_progress_cb;
            params.progress_callback_user_data = &cb_data;
            LOGD("진행률 콜백 등록 완료");
        }
    }

    LOGD("전사 시작 (samples=%zu, threads=%d)", pcmf32.size(), params.n_threads);

    // 전사 실행
    int result = whisper_full(ctx, params, pcmf32.data(), pcmf32.size());

    if (result != 0) {
        LOGE("전사 실패: error code %d", result);
        whisper_free(ctx);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(audioPath, audio_path);
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }

    // 결과 텍스트 수집
    int n_segments = whisper_full_n_segments(ctx);
    LOGD("전사 완료: %d segments", n_segments);

    std::string full_text;
    for (int i = 0; i < n_segments; i++) {
        const char *segment_text = whisper_full_get_segment_text(ctx, i);
        if (segment_text) {
            full_text += segment_text;
            full_text += "\n";
        }
    }

    whisper_free(ctx);

    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(audioPath, audio_path);
    env->ReleaseStringUTFChars(language, lang);

    LOGD("전사 결과: %zu자", full_text.length());

    if (full_text.empty()) {
        return nullptr;
    }

    return env->NewStringUTF(full_text.c_str());
}
