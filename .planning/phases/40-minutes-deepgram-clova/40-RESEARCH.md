# Phase 40: 회의록 엔진 확장 (Deepgram Intelligence / Naver CLOVA Summary) - Research

**Researched:** 2026-03-31
**Domain:** 텍스트 요약 REST API (Deepgram Text Intelligence, Naver CLOVA Summary/Studio)
**Confidence:** MEDIUM

## Summary

Phase 40은 기존 Gemini 회의록 엔진에 추가로 Deepgram Audio Intelligence와 Naver CLOVA Summary를 회의록 엔진으로 사용할 수 있도록 확장하는 작업이다. 전사 텍스트를 받아 REST API로 요약 후 회의록으로 저장하는 구조이다.

**핵심 발견 사항:** Deepgram Text Intelligence API(`/v1/read`)는 텍스트 입력을 받아 요약을 수행하지만, **현재 영어만 지원하며 한국어를 지원하지 않는다.** 이 프로젝트는 한국어 회의록이 주 대상이므로, Deepgram 엔진은 영어 전사 텍스트 한정으로 동작하거나 사용자에게 제한 사항을 명시해야 한다. Naver CLOVA Summary는 한국어를 네이티브 지원하지만, 구형 API는 **2,000자 제한**이 있어 긴 전사 텍스트를 처리하려면 분할(chunking) 전략이 필요하다. CLOVA Studio Summarization v2는 더 높은 토큰 한도를 가질 수 있으나 인증 체계가 다르다 (Bearer Token 기반).

**Primary recommendation:** 두 API 모두 구현하되, Deepgram은 영어 한정 경고를 포함하고, CLOVA는 CLOVA Studio Summarization v2 API를 우선 사용 (토큰 한도가 더 넉넉함). 기존 MinutesEngine 인터페이스를 그대로 구현하고, MinutesEngineType enum을 신설하여 MinutesEngineSelector의 엔진 선택 로직을 확장한다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MIN6-01 | 사용자가 설정에서 Deepgram Audio Intelligence를 회의록 엔진으로 선택할 수 있다 | Deepgram Text Intelligence `/v1/read` API 확인, MinutesEngineType enum 신설로 선택 지원 |
| MIN6-02 | 사용자가 설정에서 Naver CLOVA Summary를 회의록 엔진으로 선택할 수 있다 | CLOVA Summary/Studio Summarization API 확인, MinutesEngineType enum에 추가 |
| MIN6-03 | 각 회의록 엔진이 전사 텍스트를 받아 요약 결과를 회의록으로 저장한다 | MinutesEngine 인터페이스 `generate()` 구현 패턴 확인, MinutesRepositoryImpl 저장 로직 재사용 |
</phase_requirements>

## Standard Stack

### Core (이미 프로젝트에 포함)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.+ | HTTP 클라이언트 | 프로젝트 기존 스택, Retrofit과 함께 사용 |
| Retrofit | 2.11.+ | REST API 호출 | 프로젝트 기존 스택 |
| Hilt | 2.56+ | DI | 프로젝트 기존 스택, 엔진 바인딩 |
| Kotlinx Coroutines | 1.10.+ | 비동기 처리 | 프로젝트 기존 스택 |
| EncryptedSharedPreferences | 기존 | API 키 암호화 저장 | SecureApiKeyRepository에서 이미 사용 |
| DataStore | 1.2.1 | 엔진 설정 저장 | UserPreferencesRepository에서 이미 사용 |

### 신규 라이브러리 불필요
두 API 모두 표준 REST 호출이므로 기존 OkHttp/Retrofit 스택으로 충분하다. 신규 라이브러리 추가 없음.

## Architecture Patterns

### 기존 MinutesEngine 패턴 분석

현재 구조:
```
MinutesEngine (interface)
  ├── GeminiEngine (@Singleton, @Inject)
  ├── GeminiOAuthEngine (@Singleton, @Inject)
  └── MinutesEngineSelector (@Singleton, @Inject) — implements MinutesEngine
        ├── authMode(API_KEY/OAUTH) 기반으로 Gemini 엔진 선택
        └── RepositoryModule에서 MinutesEngine으로 바인딩
```

**MinutesEngine 인터페이스 시그니처:**
```kotlin
interface MinutesEngine {
    suspend fun generate(transcriptText: String, customPrompt: String? = null): Result<String>
    fun engineName(): String
    fun isAvailable(): Boolean
}
```

**GeminiEngine 구현 패턴:**
- `@Singleton` + `@Inject constructor(secureApiKeyRepository: SecureApiKeyRepository)`
- `generate()`: API 키 조회 -> 프롬프트 구성 -> API 호출 -> Result 반환
- `isAvailable()`: API 키 존재 여부로 판단
- 쿼터 초과 시 재시도 로직 (Gemini 전용이므로 새 엔진에는 불필요)

**MinutesEngineSelector 현재 한계:**
- `authMode` (API_KEY/OAUTH)만으로 Gemini 엔진 2종 중 선택
- 새 엔진(Deepgram, CLOVA)을 추가하려면 선택 로직을 `MinutesEngineType` enum 기반으로 변경해야 함

### 확장 아키텍처 (권장)

```
MinutesEngine (interface — 변경 없음)
  ├── GeminiEngine (기존)
  ├── GeminiOAuthEngine (기존)
  ├── DeepgramMinutesEngine (신규)
  ├── NaverClovaMinutesEngine (신규)
  └── MinutesEngineSelector (수정)
        ├── MinutesEngineType enum으로 엔진 선택
        ├── GEMINI → 기존 authMode 기반 Gemini 엔진 선택
        ├── DEEPGRAM → DeepgramMinutesEngine
        └── NAVER_CLOVA → NaverClovaMinutesEngine
```

### MinutesEngineType enum (신설)

SttEngineType 패턴을 따라 `domain/model/` 패키지에 생성:

```kotlin
package com.autominuting.domain.model

enum class MinutesEngineType {
    /** Gemini 2.5 Flash (기본값) */
    GEMINI,
    /** Deepgram Text Intelligence (영어만 지원) */
    DEEPGRAM,
    /** Naver CLOVA Studio Summarization (한국어 네이티브) */
    NAVER_CLOVA
}
```

### UserPreferencesRepository 확장

SttEngineType과 동일한 패턴으로 추가:
```kotlin
val MINUTES_ENGINE_KEY = stringPreferencesKey("minutes_engine")

val minutesEngineType: Flow<MinutesEngineType> = dataStore.data.map { prefs ->
    val name = prefs[MINUTES_ENGINE_KEY] ?: MinutesEngineType.GEMINI.name
    try { MinutesEngineType.valueOf(name) } catch (_: Exception) { MinutesEngineType.GEMINI }
}

suspend fun setMinutesEngineType(type: MinutesEngineType) { ... }
suspend fun getMinutesEngineTypeOnce(): MinutesEngineType = ...
```

### SecureApiKeyRepository 현황

이미 Deepgram과 CLOVA 키 저장 메서드가 구현되어 있음 (Phase 39 대비):
- `getDeepgramApiKey()` / `saveDeepgramApiKey()`
- `getClovaInvokeUrl()` / `saveClovaInvokeUrl()`
- `getClovaSecretKey()` / `saveClovaSecretKey()`

**주의:** CLOVA Studio Summarization v2는 Bearer Token 인증을 사용하므로, 기존 `clovaSecretKey`를 CLOVA Studio API Key로 활용하거나 별도 키를 추가해야 할 수 있다.

### Hilt 바인딩 변경

RepositoryModule에서 `bindMinutesEngine`을 유지하되, MinutesEngineSelector 내부에서 Provider를 통해 새 엔진을 주입:

```kotlin
@Singleton
class MinutesEngineSelector @Inject constructor(
    private val geminiEngineProvider: Provider<GeminiEngine>,
    private val geminiOAuthEngineProvider: Provider<GeminiOAuthEngine>,
    private val deepgramEngineProvider: Provider<DeepgramMinutesEngine>,  // 추가
    private val clovaEngineProvider: Provider<NaverClovaMinutesEngine>,   // 추가
    private val userPreferencesRepository: UserPreferencesRepository
) : MinutesEngine
```

## API 상세 정보

### Deepgram Text Intelligence API

| 항목 | 상세 |
|------|------|
| **엔드포인트** | `POST https://api.deepgram.com/v1/read?summarize=true&language=en` |
| **인증** | `Authorization: Token YOUR_DEEPGRAM_API_KEY` |
| **Request Body** | `{"text": "전사 텍스트"}` (JSON) |
| **Response** | `results.summary.text` 경로에 요약 텍스트 |
| **Content-Type** | `application/json` |
| **언어 지원** | **영어만** (한국어 미지원) |
| **최소 입력** | 50단어 이상 (미만 시 원본 텍스트 반환) |
| **최대 입력** | 150K 토큰 |
| **가격** | $0.0003/1K input tokens + $0.0006/1K output tokens |
| **Confidence** | HIGH (공식 문서 확인) |

**중요 제한:** 한국어 텍스트를 보내면 `unsupported language` 오류 발생. 영어 전사 텍스트에서만 정상 동작.

### Naver CLOVA Summary API (Legacy)

| 항목 | 상세 |
|------|------|
| **엔드포인트** | `POST https://naveropenapi.apigw.ntruss.com/text-summary/v1/summarize` |
| **인증 헤더** | `X-NCP-APIGW-API-KEY-ID: {Client ID}`, `X-NCP-APIGW-API-KEY: {Client Secret}` |
| **Request Body** | 아래 참조 |
| **Response** | `{"summary": "요약 텍스트"}` |
| **언어 지원** | 한국어(ko), 일본어(ja) |
| **최대 입력** | **2,000자** (회의 전사 텍스트에 매우 부족) |
| **Confidence** | MEDIUM (Medium 블로그 + 커뮤니티 게시물 기반) |

```json
{
  "document": {
    "title": "회의 제목",
    "content": "전사 텍스트 (최대 2000자)"
  },
  "option": {
    "language": "ko",
    "model": "general",
    "tone": "2",
    "summaryCount": "3"
  }
}
```

### Naver CLOVA Studio Summarization v2 (권장)

| 항목 | 상세 |
|------|------|
| **엔드포인트** | `POST https://clovastudio.stream.ntruss.com/v1/api-tools/summarization/v2` |
| **인증** | `Authorization: Bearer {CLOVA Studio API Key}` |
| **추가 헤더** | `X-NCP-CLOVASTUDIO-REQUEST-ID: {UUID}` |
| **Request Body** | `{"texts": ["전사 텍스트"]}` |
| **Response** | `{"status": {"code": "20000"}, "result": {"text": "요약", "inputTokens": N}}` |
| **언어 지원** | 한국어 네이티브 |
| **토큰 한도** | Legacy보다 높을 것으로 추정 (정확한 수치 미확인) |
| **Confidence** | LOW (공식 문서 직접 접근 실패, 검색 결과 기반 추정) |

**권장사항:** CLOVA Studio v2를 우선 구현하되, 인증 헤더가 다르므로 SecureApiKeyRepository에 `getClovaStudioApiKey()` 추가가 필요할 수 있다. 또는 기존 `clovaSecretKey`를 Studio API Key 용도로 재활용할 수 있다 (Phase 39에서 CLOVA Speech용으로 사용하는 키와 동일한지 확인 필요).

**2,000자 제한 대응 전략 (Legacy API 사용 시):**
- 전사 텍스트를 2,000자 단위로 분할
- 각 청크를 요약 -> 청크 요약들을 다시 합쳐 최종 요약 생성
- 구현 복잡도가 높으므로 CLOVA Studio v2 우선 사용 권장

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP 클라이언트 | Raw HttpURLConnection | OkHttp + Retrofit | 이미 프로젝트에 포함, 재시도/인터셉터 지원 |
| JSON 직렬화 | 수동 JSONObject 파싱 | Moshi 또는 Gson (Retrofit converter) | 타입 안전, 보일러플레이트 감소 |
| API 키 암호화 | 직접 암호화 | SecureApiKeyRepository (이미 존재) | EncryptedSharedPreferences 기반 |
| 텍스트 분할 | 글자 수로 단순 자르기 | 문장 경계 기준 분할 | 문장 중간 절단 시 요약 품질 저하 |

## Common Pitfalls

### Pitfall 1: Deepgram 한국어 미지원
**What goes wrong:** 한국어 전사 텍스트를 Deepgram `/v1/read`에 보내면 400 오류 또는 의미 없는 영어 요약 반환
**Why it happens:** Deepgram Text Intelligence는 현재 영어만 지원
**How to avoid:** `isAvailable()` 또는 엔진 선택 시 한국어 경고 표시. 영어 전사 결과가 아닌 경우 사용자에게 "Deepgram은 영어 전사 텍스트만 지원합니다" 안내
**Warning signs:** API 응답 400 에러, 또는 요약 결과가 비어있거나 의미 없음

### Pitfall 2: CLOVA Summary 2,000자 제한
**What goes wrong:** 30분 회의 전사 텍스트(수천~수만자)를 CLOVA Legacy API에 보내면 오류
**Why it happens:** Legacy CLOVA Summary API는 최대 2,000자 제한
**How to avoid:** CLOVA Studio v2 API를 우선 사용하거나, Legacy 사용 시 텍스트 분할 로직 구현
**Warning signs:** API 응답 오류, 잘린 요약 결과

### Pitfall 3: MinutesEngineSelector authMode 충돌
**What goes wrong:** 기존 authMode(API_KEY/OAUTH) 기반 선택 로직과 새 MinutesEngineType 로직이 충돌
**Why it happens:** 기존 Selector가 authMode만으로 Gemini 엔진을 선택하고 있음
**How to avoid:** MinutesEngineType이 GEMINI일 때만 기존 authMode 로직 적용, 다른 타입이면 해당 엔진 직접 사용
**Warning signs:** Deepgram/CLOVA 선택했는데 Gemini로 동작

### Pitfall 4: customPrompt 처리 불일치
**What goes wrong:** Deepgram/CLOVA 엔진이 customPrompt를 무시하고 기본 요약만 반환
**Why it happens:** 외부 API는 자체 요약 로직을 사용하므로 사용자 정의 프롬프트를 적용할 방법이 없음
**How to avoid:** Deepgram/CLOVA 엔진의 `generate()` 구현에서 customPrompt 파라미터는 무시하거나 로그 경고. 사용자에게 "이 엔진은 커스텀 프롬프트를 지원하지 않습니다" 안내
**Warning signs:** 사용자가 커스텀 프롬프트를 설정했는데 반영되지 않음

### Pitfall 5: CLOVA 인증 방식 혼동
**What goes wrong:** CLOVA Summary(Legacy)와 CLOVA Studio의 인증 헤더가 다름
**Why it happens:** Legacy: `X-NCP-APIGW-API-KEY-ID` + `X-NCP-APIGW-API-KEY`, Studio: `Authorization: Bearer` + `X-NCP-CLOVASTUDIO-REQUEST-ID`
**How to avoid:** 어떤 API를 사용할지 결정하고 해당 인증 방식만 구현
**Warning signs:** 401 Unauthorized 응답

## Code Examples

### DeepgramMinutesEngine 구현 패턴

```kotlin
// Source: Deepgram 공식 문서 + 기존 GeminiEngine 패턴
@Singleton
class DeepgramMinutesEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "DeepgramMinutesEngine"
        private const val ENDPOINT = "https://api.deepgram.com/v1/read"
    }

    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?  // Deepgram은 커스텀 프롬프트 미지원 — 무시
    ): Result<String> {
        val apiKey = secureApiKeyRepository.getDeepgramApiKey()
            ?: return Result.failure(IllegalStateException("Deepgram API 키가 설정되지 않았습니다"))

        // OkHttp로 POST 호출
        // URL: "$ENDPOINT?summarize=true&language=en"
        // Header: "Authorization: Token $apiKey"
        // Body: {"text": "$transcriptText"}
        // Response 파싱: results.summary.text
        TODO("구현")
    }

    override fun engineName(): String = "Deepgram Text Intelligence"

    override fun isAvailable(): Boolean {
        return secureApiKeyRepository.getDeepgramApiKey()?.isNotBlank() == true
    }
}
```

### Retrofit 인터페이스 (CLOVA Studio)

```kotlin
// Source: CLOVA Studio 검색 결과 기반
interface ClovaSummaryApiService {
    @POST("v1/api-tools/summarization/v2")
    suspend fun summarize(
        @Header("Authorization") bearerToken: String,
        @Header("X-NCP-CLOVASTUDIO-REQUEST-ID") requestId: String,
        @Body request: ClovaSummaryRequest
    ): ClovaSummaryResponse
}

data class ClovaSummaryRequest(val texts: List<String>)

data class ClovaSummaryResponse(
    val status: ClovaStatus,
    val result: ClovaResult?
)

data class ClovaStatus(val code: String, val message: String)
data class ClovaResult(val text: String, val inputTokens: Int)
```

### MinutesEngineSelector 수정 패턴

```kotlin
private suspend fun selectEngine(): MinutesEngine {
    val engineType = userPreferencesRepository.getMinutesEngineTypeOnce()

    return when (engineType) {
        MinutesEngineType.GEMINI -> {
            // 기존 authMode 기반 Gemini 엔진 선택 로직 유지
            val authMode = userPreferencesRepository.authMode.first()
            when (authMode) {
                AuthMode.OAUTH -> {
                    val oauthEngine = geminiOAuthEngineProvider.get()
                    if (oauthEngine.isAvailable()) oauthEngine
                    else geminiEngineProvider.get()
                }
                AuthMode.API_KEY -> geminiEngineProvider.get()
            }
        }
        MinutesEngineType.DEEPGRAM -> deepgramEngineProvider.get()
        MinutesEngineType.NAVER_CLOVA -> clovaEngineProvider.get()
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| CLOVA Summary (Legacy) | CLOVA Studio Summarization v2 | 2024+ | 더 높은 토큰 한도, Bearer 인증, 통합 플랫폼 |
| Deepgram Audio Intelligence (오디오 입력) | Deepgram Text Intelligence (텍스트 입력) | 2024 | 전사 텍스트 직접 분석 가능, `/v1/read` 엔드포인트 |

## Open Questions

1. **CLOVA Studio Summarization v2 정확한 토큰 한도**
   - What we know: Legacy CLOVA Summary는 2,000자 제한
   - What's unclear: Studio v2의 정확한 최대 입력 토큰 수
   - Recommendation: 구현 후 실제 테스트로 확인. 방어적으로 텍스트 분할 로직을 옵션으로 구현

2. **CLOVA 인증 키 재사용 여부**
   - What we know: Phase 39에서 CLOVA Speech용 invokeUrl + secretKey가 SecureApiKeyRepository에 있음
   - What's unclear: CLOVA Studio Summarization도 같은 인증 체계를 사용하는지, 별도 API Key가 필요한지
   - Recommendation: 구현 시 CLOVA Studio API Key를 별도로 저장하거나, 기존 키를 재활용 시도 후 실패하면 분리

3. **Deepgram 한국어 요약의 사용자 가치**
   - What we know: 영어만 지원
   - What's unclear: 이 앱의 사용자가 영어 회의를 자주 하는지
   - Recommendation: 구현은 하되, UI에서 "(영어만)" 경고를 명시. 향후 Deepgram이 한국어를 지원하면 자동으로 동작

4. **customPrompt 미지원 시 UX**
   - What we know: Deepgram/CLOVA는 고정 요약 로직이므로 커스텀 프롬프트 적용 불가
   - What's unclear: 사용자가 커스텀 프롬프트를 설정한 상태에서 Deepgram/CLOVA 엔진을 선택하면 어떻게 할지
   - Recommendation: `generate()` 호출 시 customPrompt를 무시하고, 로그에 경고 기록. UI에서는 Phase 41(설정 UI)에서 처리

## Project Constraints (from CLAUDE.md)

- **언어**: 한국어 응답, 한국어 주석, 한국어 커밋 메시지, 한국어 문서화
- **변수명/함수명**: 영어
- **플랫폼**: Android 네이티브 (Kotlin)
- **HTTP 클라이언트**: OkHttp/Retrofit (프로젝트 표준)
- **DI**: Hilt
- **API 키 저장**: EncryptedSharedPreferences (SecureApiKeyRepository)
- **설정 저장**: DataStore (UserPreferencesRepository)
- **GSD 워크플로우**: Edit/Write 전 GSD 명령 사용

## Sources

### Primary (HIGH confidence)
- [Deepgram Text Summarization Docs](https://developers.deepgram.com/docs/text-summarization) - 엔드포인트, 요청/응답 형식, 언어 지원 확인
- [Deepgram Text Intelligence Getting Started](https://developers.deepgram.com/docs/text-intelligence) - `/v1/read` 엔드포인트, 150K 토큰 한도 확인
- [Deepgram Language Support](https://developers.deepgram.com/docs/language) - 영어만 지원 확인

### Secondary (MEDIUM confidence)
- [CLOVA Summary Medium 블로그](https://medium.com/naver-cloud-platform/) - 엔드포인트, 요청 형식, 헤더 정보
- [Velog CLOVA Summary 사용기](https://velog.io/@vdoring/) - 2,000자 제한 확인, 응답 형식
- [CLOVA Studio Summarization API 검색 결과](https://api.ncloud-docs.com/docs/en/clovastudio-summarization) - v2 엔드포인트, Bearer 인증

### Tertiary (LOW confidence)
- CLOVA Studio Summarization v2 정확한 토큰 한도 — 공식 문서 직접 접근 실패, 검색 결과만으로 추정

## Metadata

**Confidence breakdown:**
- Deepgram API: HIGH - 공식 문서에서 직접 확인
- CLOVA Legacy API: MEDIUM - 블로그/커뮤니티 게시물 기반, 공식 문서 직접 접근 실패
- CLOVA Studio v2 API: LOW - 검색 결과만으로 추정, 상세 스펙 미확인
- Architecture Patterns: HIGH - 기존 코드베이스 직접 분석
- Pitfalls: MEDIUM - API 특성 분석 기반

**Research date:** 2026-03-31
**Valid until:** 2026-04-14 (API 스펙은 안정적이나 CLOVA Studio 변경 가능성 존재)
