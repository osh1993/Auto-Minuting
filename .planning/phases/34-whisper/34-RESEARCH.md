# Phase 34: Whisper 전사 진행률 표시 — Research

**Researched:** 2026-03-29
**Status:** Complete

## 1. 현재 상태 분석

### Whisper 전사 파이프라인 (현재)

```
WhisperEngine.transcribe()
  → AudioConverter.convertToWhisperFormat() (16kHz mono WAV 변환)
  → nativeTranscribe() (JNI → whisper_full() 호출)
  → 결과 문자열 반환 (전체 세그먼트 합산)
```

**핵심 문제:** `whisper_full()` 호출은 블로킹이며 진행률 콜백을 사용하지 않음. UI에서는 `"전사 중..."` 텍스트만 표시하고 실제 퍼센트 진행률 없음.

### 현재 진행 상태 표시

| 위치 | 현재 표시 | 상세 |
|------|-----------|------|
| TranscriptsScreen | "전사 중" 텍스트 배지 | 퍼센트 없음, 스피너만 |
| DashboardScreen | "전사 중..." | 텍스트만 |
| Notification | "전사 중..." (ongoing) | PipelineNotificationHelper.updateProgress() |
| ShareReceiverActivity | "음성 파일 전사 중..." | Toast + notification |

### whisper.cpp 콜백 API (미사용)

`whisper.h`에 정의된 사용 가능한 콜백:

```c
// 진행률 콜백 — 0~100% 정수 진행률 제공
typedef void (*whisper_progress_callback)(
    struct whisper_context * ctx,
    struct whisper_state * state,
    int progress,       // 0-100
    void * user_data
);

// 새 세그먼트 콜백 — 세그먼트 디코딩 완료 시마다 호출
typedef void (*whisper_new_segment_callback)(
    struct whisper_context * ctx,
    struct whisper_state * state,
    int n_new,
    void * user_data
);
```

`whisper_full_params` 구조체 내 필드:
- `params.progress_callback` — 현재 NULL
- `params.progress_callback_user_data` — 현재 NULL
- `params.new_segment_callback` — 현재 NULL

## 2. 기술적 접근 방식

### JNI 콜백 → Kotlin 전달 방법

**접근 1: JNI에서 Java 메서드 직접 호출 (권장)**

```
C++ progress_callback
  → JNIEnv->CallVoidMethod(kotlinObject, methodId, progress)
  → Kotlin: onProgress(progress: Int)  // 0-100
  → StateFlow 업데이트
```

구현 순서:
1. `whisper_jni.cpp`에서 progress_callback 등록
2. 콜백 내에서 JNI로 Kotlin 메서드 호출
3. WhisperEngine에 `onNativeProgress(progress: Int)` 메서드 추가
4. StateFlow<Int>로 진행률 노출

**주의 사항:**
- `whisper_full()`는 호출 스레드에서 콜백을 실행함 → JNI 콜백도 같은 스레드
- `nativeTranscribe()`는 `Dispatchers.IO`에서 실행되므로 콜백도 IO 스레드에서 호출됨
- JNIEnv는 스레드 로컬 → 콜백용 JNIEnv를 전달하거나 AttachCurrentThread 사용
- **`whisper_full()`는 같은 스레드에서 콜백을 호출하므로 추가 스레드 동기화 불필요**

### JNI 콜백 구현 상세

```cpp
// 콜백 사용자 데이터 구조체
struct ProgressCallbackData {
    JNIEnv *env;
    jobject callback_obj;   // Kotlin 콜백 객체 (Global Ref)
    jmethodID method_id;    // onProgress(int) 메서드 ID
};

// whisper progress_callback 구현
void whisper_progress_cb(
    struct whisper_context *ctx,
    struct whisper_state *state,
    int progress,
    void *user_data
) {
    auto *data = (ProgressCallbackData *)user_data;
    data->env->CallVoidMethod(data->callback_obj, data->method_id, progress);
}
```

**nativeTranscribe 시그니처 변경:**
```kotlin
// 기존
private external fun nativeTranscribe(
    modelPath: String, audioPath: String,
    language: String, temperature: Float
): String?

// 변경 — 진행률 콜백 객체 추가
private external fun nativeTranscribe(
    modelPath: String, audioPath: String,
    language: String, temperature: Float,
    progressCallback: Any?  // WhisperEngine 자신 또는 별도 인터페이스
): String?
```

### 진행률 전달 체인

```
whisper.cpp progress_callback (C++, 0-100 int)
  → JNI CallVoidMethod → WhisperEngine.onNativeProgress(progress: Int)
    → _transcriptionProgress: MutableStateFlow<Float> 업데이트 (0.0~1.0)
      → TranscriptionRepository.transcriptionProgress: StateFlow<Float>
        → TranscriptionTriggerWorker setProgress(Data)
          → 알림 업데이트 (PipelineNotificationHelper)
          → UI 업데이트 (TranscriptsScreen, DashboardScreen)
```

## 3. UI 표시 방안

### TranscriptsScreen 변경

현재 "전사 중" 배지 → **LinearProgressIndicator + "전사 중 45%"** 텍스트:

```kotlin
// 현재
PipelineStatus.TRANSCRIBING -> Triple("전사 중", ...)

// 변경
PipelineStatus.TRANSCRIBING -> {
    // progress가 0이면 indeterminate, > 0이면 determinate
    LinearProgressIndicator(progress = { meetingProgress })
    Text("전사 중 ${(meetingProgress * 100).toInt()}%")
}
```

### DashboardScreen 변경

현재 "전사 중..." → **"전사 중 45%"** 텍스트:

### Notification 변경

`PipelineNotificationHelper`에 progress 파라미터 추가:
```kotlin
fun updateProgress(context: Context, text: String, progress: Int = -1) {
    // progress >= 0이면 setProgress(100, progress, false)
    // progress < 0이면 setProgress(0, 0, true) (indeterminate)
}
```

## 4. WorkManager 연동

### setProgress() 사용

WorkManager의 `setProgress(Data)` API로 Worker에서 UI로 진행률 전달:

```kotlin
// TranscriptionTriggerWorker 내
val progressData = workDataOf("progress" to progress)
setProgress(progressData)  // LiveData/Flow로 관찰 가능
```

### 대안: Meeting DB 필드

Meeting 엔티티에 `transcriptionProgress: Float?` 필드 추가 → Room Flow로 자동 UI 갱신.
**장점:** 앱 종료 후 재진입 시에도 마지막 진행률 표시, 모든 화면에서 일관된 접근.
**단점:** DB I/O 빈번 (진행률 업데이트마다 write).

**권장:** Worker `setProgress()` + 알림 업데이트 조합. DB 갱신은 불필요 (전사 완료/실패 시만 상태 변경).

## 5. SttEngine 인터페이스 영향

현재 `SttEngine` 인터페이스:
```kotlin
interface SttEngine {
    fun engineName(): String
    suspend fun isAvailable(): Boolean
    suspend fun transcribe(audioFilePath: String): Result<String>
}
```

진행률 지원을 위해 변경 옵션:
- **옵션 A:** `transcribe(audioFilePath, onProgress: (Float) -> Unit)` — 콜백 추가
- **옵션 B:** `transcriptionProgress: StateFlow<Float>` 프로퍼티 추가
- **옵션 C:** WhisperEngine에만 진행률 추가, GeminiSttEngine은 indeterminate

**권장:** 옵션 A — 인터페이스에 기본 구현 파라미터 추가 (`onProgress: (Float) -> Unit = {}`). GeminiSttEngine은 기본값 사용, WhisperEngine만 실제 콜백 전달.

## 6. 리스크 및 고려사항

| 리스크 | 영향 | 대응 |
|--------|------|------|
| JNI 콜백 빈도 과다 | UI 과부하 | 진행률 변경이 1% 이상일 때만 전달 |
| whisper_full() 내부에서 콜백 호출 시점 비균등 | 진행률 점프 | 정상 동작 — whisper.cpp 내부 구현 의존 |
| GeminiSttEngine은 진행률 미지원 | 인터페이스 불일치 | 기본 파라미터로 해결 |
| nativeTranscribe JNI 시그니처 변경 | ABI 호환성 | 기존 메서드 대체 (기존 호출부 모두 수정) |

## 7. 파일 변경 범위

| 파일 | 변경 내용 |
|------|-----------|
| `app/src/main/cpp/whisper_jni.cpp` | progress_callback 등록, JNI 콜백 호출 |
| `app/src/main/java/com/.../stt/WhisperEngine.kt` | onNativeProgress(), nativeTranscribe 시그니처 변경, StateFlow 추가 |
| `app/src/main/java/com/.../stt/SttEngine.kt` | transcribe() onProgress 파라미터 추가 |
| `app/src/main/java/com/.../stt/GeminiSttEngine.kt` | 시그니처 맞춤 (기본값 사용) |
| `app/src/main/java/com/.../stt/MlKitEngine.kt` | 시그니처 맞춤 (있다면) |
| `app/src/main/java/com/.../repository/TranscriptionRepositoryImpl.kt` | 진행률 전달 |
| `app/src/main/java/com/.../worker/TranscriptionTriggerWorker.kt` | setProgress() + 알림 업데이트 |
| `app/src/main/java/com/.../service/PipelineNotificationHelper.kt` | progress 파라미터 추가 |
| `app/src/main/java/com/.../presentation/transcripts/TranscriptsScreen.kt` | LinearProgressIndicator 표시 |
| `app/src/main/java/com/.../presentation/dashboard/DashboardScreen.kt` | 진행률 텍스트 표시 |

## RESEARCH COMPLETE
