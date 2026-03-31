# Phase 39: STT 엔진 확장 (Groq / Deepgram / Naver CLOVA) - Research

**Researched:** 2026-03-31
**Domain:** REST API 기반 STT 엔진 통합 (Android/Kotlin/OkHttp)
**Confidence:** HIGH

## Summary

Phase 39는 기존 SttEngine 인터페이스를 구현하는 3개의 새로운 클라우드 STT 엔진(Groq Whisper, Deepgram Nova-3, Naver CLOVA Speech)을 추가하는 작업이다. 세 엔진 모두 REST API 기반이며, 프로젝트에 이미 OkHttp 4.12.0이 포함되어 있으므로 추가 라이브러리 없이 구현 가능하다.

핵심 패턴은 GeminiSttEngine에서 이미 확립되어 있다: SecureApiKeyRepository로 API 키 조회, OkHttp로 HTTP 요청, JSON 응답 파싱, Result<String> 반환. 세 엔진 모두 이 패턴을 따르되 요청 형식(multipart vs binary body)과 인증 헤더가 다르다.

주의사항: Groq은 Free 티어 25MB 파일 제한이 있어 긴 오디오(WAV 30분+)는 실패할 수 있다. CLOVA Speech는 invoke URL이 사용자별로 다르므로 API 키 외에 invoke URL도 저장해야 한다.

**Primary recommendation:** GeminiSttEngine 패턴을 그대로 복제하여 각 엔진별 REST 호출만 교체한다. OkHttp MultipartBody(Groq, CLOVA) 또는 binary RequestBody(Deepgram) 사용.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| STT6-01 | Groq Whisper API STT 엔진 선택 + API 키 입력 | Groq API 엔드포인트/인증/요청형식 조사 완료, SttEngineType enum 확장 패턴 확인 |
| STT6-02 | Deepgram Nova-3 STT 엔진 선택 + API 키 입력 | Deepgram API 엔드포인트/인증/Korean 지원 조사 완료 |
| STT6-03 | Naver CLOVA Speech STT 엔진 선택 + API 키 입력 | CLOVA Speech local upload API 조사 완료, 이중 인증(invoke URL + secret key) 확인 |
| STT6-04 | 각 엔진이 한국어 오디오를 실제 전사 | 세 엔진 모두 한국어(ko/ko-KR) 지원 확인, 응답 JSON에서 텍스트 추출 경로 문서화 |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **언어**: 한국어 응답, 한국어 코드 주석, 한국어 커밋 메시지
- **변수명/함수명**: 영어
- **플랫폼**: Android 네이티브 (Kotlin)
- **스택**: OkHttp 4.12.0, Hilt DI, DataStore, EncryptedSharedPreferences
- **GSD Workflow**: 파일 변경 전 GSD 명령어를 통해 작업

## Standard Stack

### Core (이미 프로젝트에 포함)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | HTTP 클라이언트 | 이미 사용 중, MultipartBody/RequestBody 지원 |
| org.json | Platform API | JSON 파싱 | Android 플랫폼 내장, GeminiSttEngine에서 이미 사용 |
| EncryptedSharedPreferences | 1.0.0 | API 키 암호화 저장 | SecureApiKeyRepository에서 이미 사용 |
| DataStore | 1.2.1 | 엔진 선택 설정 | UserPreferencesRepository에서 이미 사용 |
| Hilt | 2.59.2 | DI | @Singleton @Inject 패턴 이미 확립 |

### 추가 라이브러리 불필요
세 엔진 모두 OkHttp + org.json으로 구현 가능. Retrofit 인터페이스 정의는 과잉 — 단일 엔드포인트 호출이므로 OkHttp 직접 사용이 적절 (GeminiSttEngine 패턴과 동일).

## Architecture Patterns

### 기존 프로젝트 구조 (변경 대상)
```
com.autominuting/
├── data/stt/
│   ├── SttEngine.kt                    # 인터페이스 (변경 없음)
│   ├── GeminiSttEngine.kt              # 기존 (참고 패턴)
│   ├── WhisperEngine.kt                # 기존 (변경 없음)
│   ├── GroqSttEngine.kt                # [신규] Groq Whisper API
│   ├── DeepgramSttEngine.kt            # [신규] Deepgram Nova-3
│   └── NaverClovaSttEngine.kt          # [신규] Naver CLOVA Speech
├── data/security/
│   └── SecureApiKeyRepository.kt       # [수정] Groq/Deepgram/Naver 키 추가
├── data/preferences/
│   └── UserPreferencesRepository.kt    # 변경 없음 (SttEngineType enum 확장으로 자동 반영)
├── data/repository/
│   └── TranscriptionRepositoryImpl.kt  # [수정] when 분기 추가
├── domain/model/
│   └── SttEngineType.kt                # [수정] GROQ, DEEPGRAM, NAVER_CLOVA 추가
└── di/
    └── SttModule.kt                    # 변경 없음 (@Inject constructor로 자동 등록)
```

### Pattern 1: SttEngine 구현 패턴 (GeminiSttEngine 기반)
**What:** 각 클라우드 STT 엔진을 SttEngine 인터페이스로 구현
**When to use:** 새로운 STT 엔진 추가 시
**Example:**
```kotlin
// GeminiSttEngine 패턴을 따름
@Singleton
class GroqSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : SttEngine {

    override suspend fun transcribe(
        audioFilePath: String,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureApiKeyRepository.getGroqApiKey() ?: ""
        if (apiKey.isBlank()) return@withContext Result.failure(...)

        val audioFile = File(audioFilePath)
        // OkHttp multipart/form-data 요청
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name,
                audioFile.asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("language", "ko")
            .addFormDataPart("response_format", "verbose_json")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        // ... execute, parse JSON response.text
    }

    override suspend fun isAvailable(): Boolean =
        secureApiKeyRepository.getGroqApiKey()?.isNotBlank() == true

    override fun engineName(): String = "Groq Whisper (Cloud)"
}
```

### Pattern 2: SecureApiKeyRepository 확장 패턴
**What:** EncryptedSharedPreferences에 새 API 키 상수 추가
**When to use:** 새 외부 서비스 API 키 저장 시
**Example:**
```kotlin
// 기존 KEY_GEMINI_API 패턴과 동일
private const val KEY_GROQ_API = "groq_api_key"
private const val KEY_DEEPGRAM_API = "deepgram_api_key"
private const val KEY_NAVER_CLIENT_ID = "naver_client_id"
private const val KEY_NAVER_CLIENT_SECRET = "naver_client_secret"
private const val KEY_CLOVA_INVOKE_URL = "clova_invoke_url"
private const val KEY_CLOVA_SECRET_KEY = "clova_secret_key"

fun getGroqApiKey(): String? = encryptedPrefs?.getString(KEY_GROQ_API, null)
fun saveGroqApiKey(apiKey: String) { ... }
// ... 동일 패턴으로 Deepgram, Naver
```

### Pattern 3: TranscriptionRepositoryImpl 엔진 선택 확장
**What:** when 분기에 새 엔진 타입 추가
**Example:**
```kotlin
val engine: SttEngine = when (selectedEngine) {
    SttEngineType.GEMINI -> geminiSttEngine
    SttEngineType.WHISPER -> whisperEngine
    SttEngineType.GROQ -> groqSttEngine        // 신규
    SttEngineType.DEEPGRAM -> deepgramSttEngine  // 신규
    SttEngineType.NAVER_CLOVA -> naverClovaSttEngine // 신규
}
```

### Anti-Patterns to Avoid
- **Retrofit 인터페이스 정의**: 단일 엔드포인트 호출에 Retrofit은 과잉. OkHttp 직접 사용이 GeminiSttEngine 패턴과 일치.
- **Base class 추상화**: 세 엔진의 요청 형식(multipart vs binary vs multipart+params)이 다르므로 공통 base class는 오히려 복잡도를 높인다. 각각 독립 구현이 단순.
- **API 키를 BuildConfig에 하드코딩**: 외부 서비스 키는 반드시 SecureApiKeyRepository 경유.

## API Research: Groq Whisper

**Confidence:** HIGH (공식 문서 확인)

### 엔드포인트
- **URL:** `https://api.groq.com/openai/v1/audio/transcriptions`
- **Method:** POST
- **Content-Type:** multipart/form-data

### 인증
- **Header:** `Authorization: Bearer {GROQ_API_KEY}`

### 요청 파라미터 (multipart form fields)
| Field | Type | Required | Value |
|-------|------|----------|-------|
| file | binary | Yes | 오디오 파일 바이너리 |
| model | string | Yes | `whisper-large-v3-turbo` (빠름, $0.04/hr) 또는 `whisper-large-v3` (정확, $0.111/hr) |
| language | string | Optional | `ko` (ISO-639-1) |
| response_format | string | Optional | `json` (기본), `verbose_json`, `text` |
| temperature | float | Optional | 0 (기본, 추천) |

### 지원 오디오 형식
FLAC, MP3, MP4, MPEG, MPGA, M4A, OGG, WAV, WebM

### 파일 크기 제한
- **Free 티어:** 25MB
- **Dev 티어:** 100MB
- 최소 0.01초, 16KHz mono로 다운샘플됨

### 응답 JSON (response_format=json)
```json
{
  "text": "전사된 한국어 텍스트..."
}
```

### 응답 JSON (response_format=verbose_json)
```json
{
  "text": "전사된 한국어 텍스트...",
  "segments": [
    {
      "start": 0.0,
      "end": 5.2,
      "text": "세그먼트 텍스트",
      "avg_logprob": -0.35,
      "no_speech_prob": 0.01
    }
  ]
}
```

### 한국어 지원
Whisper large-v3는 99개+ 언어 지원. 한국어(`ko`)는 공식 지원. language 파라미터 지정 시 정확도/지연 개선.

### Rate Limits
- Free: 20 RPM, 7,000 RPD
- Dev: RPM/RPD 상향

### 구현 핵심
- OkHttp `MultipartBody.Builder()` 사용
- `response_format=verbose_json` 추천 (세그먼트 타임스탬프 활용 가능)
- 응답에서 `text` 필드 추출

## API Research: Deepgram Nova-3

**Confidence:** HIGH (공식 문서 확인)

### 엔드포인트
- **URL:** `https://api.deepgram.com/v1/listen`
- **Method:** POST
- **Content-Type:** audio/* (binary body) 또는 application/json (URL 방식)

### 인증
- **Header:** `Authorization: Token {DEEPGRAM_API_KEY}`
- 주의: `Bearer`가 아닌 `Token` 프리픽스

### 요청 방식 (binary audio body)
```
POST https://api.deepgram.com/v1/listen?model=nova-3&language=ko&smart_format=true
Authorization: Token {API_KEY}
Content-Type: audio/wav

[binary audio data]
```

### Query Parameters
| Param | Value | Purpose |
|-------|-------|---------|
| model | `nova-3` | 최신 모델 (한국어 WER 27% 개선) |
| language | `ko` | 한국어 |
| smart_format | `true` | 자동 구두점/대소문자 |
| punctuate | `true` | 구두점 추가 |
| diarize | `true` (optional) | 화자 분리 |
| utterances | `true` (optional) | 발화 단위 분할 |

### 지원 오디오 형식
대부분의 오디오 형식 지원 (WAV, MP3, MP4, M4A, FLAC, OGG 등)

### 파일 크기 제한
- 최대 2GB

### 응답 JSON
```json
{
  "results": {
    "channels": [
      {
        "alternatives": [
          {
            "transcript": "전사된 한국어 텍스트...",
            "confidence": 0.98,
            "words": [
              {
                "word": "안녕하세요",
                "start": 0.0,
                "end": 0.5,
                "confidence": 0.99,
                "punctuated_word": "안녕하세요."
              }
            ]
          }
        ]
      }
    ]
  }
}
```

### 텍스트 추출 경로
`response.results.channels[0].alternatives[0].transcript`

### 한국어 지원
Nova-3는 한국어 공식 지원. language 코드는 `ko`. Nova-2 대비 WER 27% 감소.

### 가격
- 신규 가입 시 $200 무료 크레딧
- Pay-as-you-go: $0.0043/분 (Nova-3)

### 구현 핵심
- OkHttp로 binary body 전송: `audioFile.asRequestBody("audio/wav".toMediaType())`
- Query parameter로 모델/언어 지정
- 인증 헤더 `Token` (Bearer 아님) 주의
- 응답 깊은 경로 파싱: `results.channels[0].alternatives[0].transcript`

## API Research: Naver CLOVA Speech

**Confidence:** MEDIUM (공식 문서 렌더링 실패, 블로그/검색 결과 교차 검증)

### 서비스 구분 (중요)
- **CLOVA Speech Recognition (CSR)**: 짧은 음성 (60초 미만), `X-NCP-APIGW-API-KEY-ID/KEY` 인증
- **CLOVA Speech (Long)**: 긴 음성 (60초+), `X-CLOVASPEECH-API-KEY` 인증, NEST 모델 사용
- **이 프로젝트에서는 CLOVA Speech (Long)을 사용** — 회의 녹음은 대부분 60초 이상

### 엔드포인트 (로컬 파일 업로드)
- **URL:** `https://clovaspeech-gw.ncloud.com/external/v1/{appId}/{secretKey}/recognizer/upload`
- **Method:** POST
- **Content-Type:** multipart/form-data
- 주의: URL에 appId와 부분 인증 정보가 포함됨. 사용자마다 고유한 invoke URL을 발급받아야 함.

### 인증
- **Header:** `X-CLOVASPEECH-API-KEY: {Secret Key}`
- Secret Key는 CLOVA Speech 앱 등록 시 발급됨

### 요청 (multipart form fields)
| Field | Type | Value |
|-------|------|-------|
| media | file | 오디오 파일 바이너리 |
| params | string (JSON) | `{"language":"ko-KR","completion":"sync","fullText":true}` |
| type | string | `application/json` |

### params JSON 필드
| Field | Value | Purpose |
|-------|-------|---------|
| language | `ko-KR` | 한국어 |
| completion | `sync` | 동기 응답 (즉시 결과 반환) |
| fullText | `true` | 전체 텍스트 포함 |
| diarization.enable | `true` (optional) | 화자 분리 |
| callback | `""` | 비동기 콜백 URL (sync 모드에서는 빈 문자열) |

### 응답 JSON
```json
{
  "result": "COMPLETED",
  "token": "...",
  "segments": [
    {
      "start": 0,
      "end": 5200,
      "text": "세그먼트 텍스트",
      "speaker": {
        "label": "1",
        "name": "A"
      },
      "confidence": 0.95
    }
  ],
  "text": "전체 전사 텍스트..."
}
```

### 텍스트 추출
- **fullText=true 시:** 최상위 `text` 필드에 전체 텍스트
- **세그먼트별:** `segments[].text`와 `segments[].speaker.label`

### 지원 오디오 형식
MP3, AAC, AC3, OGG, FLAC, WAV, M4A

### 파일 크기/길이 제한
- 최대 2시간 (sync 모드)
- 파일 크기는 명시적 제한 없으나 실질적으로 수백 MB까지 지원

### 가격
- 무료 플랜 없음 (유료만)
- Basic 요금제: 시간당 과금

### 구현 핵심 (중요)
1. **invoke URL 저장 필요**: 다른 엔진과 달리 API 키 하나가 아닌, invoke URL과 secret key 두 가지를 저장해야 함
2. SecureApiKeyRepository에 `getClovaInvokeUrl()`, `getClovaSecretKey()` 추가
3. OkHttp `MultipartBody.Builder()` 사용하되, params를 JSON 문자열로 form field에 추가
4. sync 모드 사용 시 응답이 길어질 수 있으므로 OkHttp readTimeout을 넉넉히 설정 (5분+)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP multipart 요청 | 수동 boundary 생성 | OkHttp `MultipartBody.Builder()` | 이미 의존성 포함, RFC 2046 준수 |
| API 키 암호화 저장 | 자체 암호화 | SecureApiKeyRepository (EncryptedSharedPreferences) | 이미 구현되어 있음 |
| JSON 파싱 | 수동 문자열 파싱 | `org.json.JSONObject` | Android 플랫폼 내장, GeminiSttEngine에서 이미 사용 |
| 재시도 로직 | 자체 재시도 루프 | GeminiSttEngine의 재시도 패턴 복사 | HTTP 429 처리 패턴 이미 검증됨 |

## Common Pitfalls

### Pitfall 1: Groq 25MB 파일 제한
**What goes wrong:** WAV 형식의 30분 오디오는 ~150MB로 Free 티어 25MB 제한 초과
**Why it happens:** WAV는 비압축 형식으로 파일 크기가 매우 큼
**How to avoid:**
- AudioConverter가 이미 프로젝트에 존재 — WAV→M4A/MP3 변환 고려
- 또는 에러 메시지에 파일 크기 제한 안내 포함
- Dev 티어($0.04/hr)로 100MB까지 지원
**Warning signs:** HTTP 413 Entity Too Large 응답

### Pitfall 2: Deepgram 인증 헤더 Token vs Bearer
**What goes wrong:** `Authorization: Bearer {key}` 사용 시 401 Unauthorized
**Why it happens:** Deepgram은 `Token` 프리픽스를 사용하지만 개발자가 습관적으로 `Bearer` 사용
**How to avoid:** `Authorization: Token {DEEPGRAM_API_KEY}` 명시

### Pitfall 3: CLOVA Speech invoke URL 혼동
**What goes wrong:** 사용자가 API 키만 입력하고 invoke URL을 입력하지 않아 요청 실패
**Why it happens:** 다른 서비스는 API 키 하나면 되지만 CLOVA Speech는 invoke URL + secret key 두 가지 필요
**How to avoid:**
- 설정 UI에서 invoke URL 입력 필드를 별도로 제공
- 안내 텍스트에 invoke URL 확인 방법 설명
- URL 형식 검증: `https://clovaspeech-gw.ncloud.com/external/v1/` 접두사 확인

### Pitfall 4: SttEngineType enum 호환성
**What goes wrong:** DataStore에 저장된 기존 enum 값이 새 enum 추가 후 역직렬화 실패
**Why it happens:** `SttEngineType.valueOf()` 호출 시 존재하지 않는 값이면 IllegalArgumentException
**How to avoid:**
- `UserPreferencesRepository.sttEngineType` Flow에서 try-catch 추가
- 알 수 없는 값 → GEMINI 기본값으로 폴백
- 기존 코드에서 이미 기본값 처리: `prefs[STT_ENGINE_KEY] ?: SttEngineType.GEMINI.name`

### Pitfall 5: CLOVA Speech sync 모드 타임아웃
**What goes wrong:** 긴 오디오(1시간+)에서 sync 모드 응답 대기 중 타임아웃
**Why it happens:** sync 모드는 서버가 전사 완료 후 응답하므로 긴 오디오는 수 분 이상 소요
**How to avoid:** OkHttp readTimeout을 10분 이상으로 설정. GeminiSttEngine에서 이미 5분으로 설정한 패턴 참고.

### Pitfall 6: TranscriptionRepositoryImpl 생성자 변경
**What goes wrong:** 새 엔진 3개를 생성자에 추가하면 Hilt 의존성 그래프가 변경됨
**Why it happens:** 각 엔진이 @Singleton @Inject constructor로 선언되어야 Hilt가 자동 주입
**How to avoid:** 각 엔진 클래스에 @Singleton + @Inject constructor 정확히 선언. SttModule에 추가 바인딩 불필요 (구체 클래스 직접 주입).

## Code Examples

### OkHttp Multipart 요청 (Groq 패턴)
```kotlin
// Source: OkHttp 4.x 공식 API
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType

val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart(
        "file",
        audioFile.name,
        audioFile.asRequestBody("audio/mpeg".toMediaType())
    )
    .addFormDataPart("model", "whisper-large-v3-turbo")
    .addFormDataPart("language", "ko")
    .addFormDataPart("response_format", "verbose_json")
    .build()

val request = Request.Builder()
    .url("https://api.groq.com/openai/v1/audio/transcriptions")
    .header("Authorization", "Bearer $apiKey")
    .post(requestBody)
    .build()
```

### OkHttp Binary Body 요청 (Deepgram 패턴)
```kotlin
// Source: Deepgram REST API 공식 문서
import okhttp3.RequestBody.Companion.asRequestBody

val mimeType = getMimeType(audioFile.extension) // "audio/wav", "audio/mpeg" 등
val requestBody = audioFile.asRequestBody(mimeType.toMediaType())

val url = "https://api.deepgram.com/v1/listen?model=nova-3&language=ko&smart_format=true"
val request = Request.Builder()
    .url(url)
    .header("Authorization", "Token $apiKey")
    .post(requestBody)
    .build()
```

### CLOVA Speech Multipart + JSON Params 요청
```kotlin
// Source: Naver CLOVA Speech API 문서 / 블로그 검증
val params = JSONObject().apply {
    put("language", "ko-KR")
    put("completion", "sync")
    put("fullText", true)
}.toString()

val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart(
        "media",
        audioFile.name,
        audioFile.asRequestBody("audio/mpeg".toMediaType())
    )
    .addFormDataPart("params", params)
    .build()

val request = Request.Builder()
    .url(invokeUrl) // 사용자별 고유 URL
    .header("X-CLOVASPEECH-API-KEY", secretKey)
    .post(requestBody)
    .build()
```

### SecureApiKeyRepository 확장 패턴
```kotlin
// 기존 getGeminiApiKey() 패턴 복사
private const val KEY_GROQ_API = "groq_api_key"
private const val KEY_DEEPGRAM_API = "deepgram_api_key"
private const val KEY_CLOVA_INVOKE_URL = "clova_invoke_url"
private const val KEY_CLOVA_SECRET_KEY = "clova_secret_key"

fun getGroqApiKey(): String? = encryptedPrefs?.getString(KEY_GROQ_API, null)
fun saveGroqApiKey(apiKey: String) {
    encryptedPrefs?.edit()?.putString(KEY_GROQ_API, apiKey)?.apply()
}
// ... Deepgram, CLOVA 동일 패턴
```

### SttEngineType enum 확장
```kotlin
enum class SttEngineType {
    GEMINI,
    WHISPER,
    GROQ,           // 신규
    DEEPGRAM,        // 신규
    NAVER_CLOVA      // 신규
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Whisper large-v3 only | whisper-large-v3-turbo | 2024 | 동일 정확도, 2배 속도, 1/3 가격 |
| Deepgram Nova-2 | Nova-3 | 2025 | 한국어 WER 27% 개선 |
| CLOVA CSR (60초 제한) | CLOVA Speech NEST | 2023 | 장문 오디오 지원, 화자 분리 |

## Open Questions

1. **CLOVA Speech invoke URL 형식 정확성**
   - What we know: URL 패턴은 `https://clovaspeech-gw.ncloud.com/external/v1/{appId}/{hash}/recognizer/upload`
   - What's unclear: appId 부분이 숫자인지, hash 부분의 정확한 형식
   - Recommendation: 사용자에게 "Naver Cloud Console에서 CLOVA Speech 앱의 invoke URL을 복사하세요" 안내. URL 전체를 입력받아 저장.

2. **CLOVA Speech 무료 플랜 존재 여부**
   - What we know: 검색 결과에서 명확한 무료 티어 확인 불가
   - What's unclear: 가입 시 무료 크레딧이 제공되는지
   - Recommendation: UI에 "유료 서비스" 안내 표시. LOW confidence — 실제 가입 필요.

3. **Groq Free 티어 25MB 실질적 영향**
   - What we know: WAV 30분 = ~150MB, M4A 30분 = ~15MB
   - What's unclear: 프로젝트에서 입력되는 오디오의 일반적 형식/크기
   - Recommendation: 에러 발생 시 파일 크기 제한 안내 메시지 표시. 향후 오디오 압축 변환은 Phase 42+ 이후 고려.

## Sources

### Primary (HIGH confidence)
- Groq Speech-to-Text 공식 문서: https://console.groq.com/docs/speech-to-text — 엔드포인트, 파라미터, 모델, 가격, 제한
- Groq API Reference: https://console.groq.com/docs/api-reference — 상세 파라미터 스펙
- Deepgram Pre-recorded Audio: https://developers.deepgram.com/docs/pre-recorded-audio — 엔드포인트, 인증
- Deepgram Models & Languages: https://developers.deepgram.com/docs/models-languages-overview — Nova-3 한국어 지원

### Secondary (MEDIUM confidence)
- Naver CLOVA Speech 로컬 파일 인식: https://api.ncloud-docs.com/docs/en/ai-application-service-clovaspeech-longsentence-local — 엔드포인트, 인증 (문서 렌더링 실패로 검색 결과에서 재구성)
- CLOVA Speech 개요: https://api.ncloud-docs.com/docs/ai-application-service-clovaspeech — 서비스 개요
- KT AIVLE 블로그 (CLOVA Speech 실사용 예제): https://velog.io/@ofohj/AIVLE20%EC%A3%BC%EC%B0%A8 — 응답 JSON 구조 확인

### Tertiary (LOW confidence)
- CLOVA Speech 가격 정책 — 공식 가격 페이지 확인 실패, 유료 서비스로 추정

## Metadata

**Confidence breakdown:**
- Groq API: HIGH - 공식 문서에서 모든 필드 확인
- Deepgram API: HIGH - 공식 문서 + 다수 검색 결과 교차 확인
- CLOVA Speech API: MEDIUM - 공식 문서 렌더링 실패, 블로그/검색 결과에서 재구성
- 기존 코드 패턴: HIGH - 소스 코드 직접 확인
- Architecture: HIGH - GeminiSttEngine 패턴 그대로 복제

**Research date:** 2026-03-31
**Valid until:** 2026-04-30 (API 엔드포인트/모델은 안정적, 가격은 변동 가능)
