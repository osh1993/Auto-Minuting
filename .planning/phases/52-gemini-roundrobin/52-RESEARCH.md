# Phase 52: Gemini 라운드로빈 + 오류 자동 전환 - Research

**Researched:** 2026-04-15
**Domain:** Android Kotlin — 다중 API 키 라운드로빈 / 오류 자동 전환 / 백그라운드 알림
**Confidence:** HIGH (코드베이스 직접 검증 + Android 공식 패턴)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GEMINI-02 | 앱이 Gemini STT 또는 회의록 생성 호출 시 등록된 키를 라운드로빈 순서로 순환하여 사용한다 | GeminiKeyRotator 신규 컴포넌트 + EncryptedSharedPreferences 동기 인덱스 저장 |
| GEMINI-03 | 특정 API 키가 오류(권한 오류, 할당량 초과 등)를 반환하면 사용자에게 알림을 표시하고 자동으로 다음 키로 전환한다 | Worker 컨텍스트에서 PipelineNotificationHelper 직접 호출 패턴 확인 |

</phase_requirements>

---

## Summary

Phase 51에서 `SecureApiKeyRepository.getAllGeminiApiKeyValues()`와 `GEMINI_ROUNDROBIN_INDEX_KEY` 상수가 이미 준비되어 있다. Phase 52는 이 구조 위에 실제 라운드로빈 순환 로직과 오류 자동 전환을 구현한다.

핵심 설계 결정은 두 가지다. 첫째, 라운드로빈 인덱스 저장소로 DataStore 대신 **EncryptedSharedPreferences 동기 저장**을 사용한다 — 이미 `encryptedPrefs`가 `SecureApiKeyRepository`에 있으므로 같은 파일에 `ROUNDROBIN_INDEX`를 추가하면 Worker 컨텍스트에서 suspend 없이 원자적으로 읽고 쓸 수 있다. (DataStore는 `GEMINI_ROUNDROBIN_INDEX_KEY` 상수만 선언된 상태이며 실제 사용은 Phase 52 담당이지만, Worker에서 Flow를 사용하려면 `.first()` suspend가 필요해 코드가 복잡해진다.) 둘째, 라운드로빈 로직을 **전용 `GeminiKeyRotator` 클래스**로 분리하여 `GeminiEngine`과 `GeminiSttEngine` 양쪽에 Hilt로 주입한다.

**Primary recommendation:** `GeminiKeyRotator` 신규 클래스를 `data/security/` 패키지에 두고 `@Singleton`으로 주입한다. 키 전환 오류 알림은 `PipelineNotificationHelper`에 신규 `notifyApiKeyError()` 메서드를 추가하여 Worker에서 직접 호출한다.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| EncryptedSharedPreferences | 기존 설치됨 | 라운드로빈 인덱스 동기 저장 | Worker에서 suspend 없이 원자적 read/write 가능 |
| Hilt | 2.56+ | GeminiKeyRotator DI | 프로젝트 표준 DI |
| NotificationCompat | 기존 설치됨 | 키 오류 알림 | PipelineNotificationHelper 기존 패턴 재사용 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| DataStore intPreferencesKey | 기존 설치됨 | GEMINI_ROUNDROBIN_INDEX_KEY 상수 | 상수는 선언되어 있으나 인덱스 실제 저장은 EncryptedSharedPreferences 사용 권장 |
| OkHttp | 4.12.+ | GeminiSttEngine HTTP 호출 | 이미 사용 중 — 변경 없음 |
| Google AI Client SDK | 기존 설치됨 | GeminiEngine generateContent | 이미 사용 중 — 변경 없음 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| EncryptedSharedPreferences 인덱스 | DataStore intPreferencesKey | DataStore는 suspend/coroutine 필수 → Worker doWork() 내에서 `.first()` 호출 필요, 복잡도 증가. EncryptedSharedPreferences는 동기 API라 단순 |
| GeminiKeyRotator 신규 클래스 | GeminiEngine 내부 인라인 | 인라인이면 GeminiSttEngine에서 코드 중복 발생. 분리하면 단일 책임, 테스트 용이 |
| PipelineNotificationHelper 확장 | Toast/Snackbar | Worker 백그라운드에서 Toast/Snackbar 직접 호출 불가. Notification이 유일한 백그라운드 UI 알림 수단 |

---

## Architecture Patterns

### 추천 구조

```
data/security/
├── SecureApiKeyRepository.kt    # 기존 — getAllGeminiApiKeyValues() 이미 있음
├── GeminiApiKeyEntry.kt         # 기존 — 변경 없음
└── GeminiKeyRotator.kt          # 신규 — 라운드로빈 + 오류 전환 전담

data/minutes/
└── GeminiEngine.kt              # 수정 — GeminiKeyRotator 주입

data/stt/
└── GeminiSttEngine.kt           # 수정 — GeminiKeyRotator 주입

service/
└── PipelineNotificationHelper.kt # 수정 — notifyApiKeyError() 추가
```

### Pattern 1: GeminiKeyRotator — 라운드로빈 + 오류 전환

**What:** 등록된 Gemini API 키 목록에서 현재 인덱스의 키를 반환하고, 오류 발생 시 다음 인덱스로 전환한다.
**When to use:** GeminiEngine과 GeminiSttEngine이 API 키를 가져올 때마다 호출.

```kotlin
// Source: 프로젝트 코드 패턴 직접 분석 (SecureApiKeyRepository 기존 구조 기반)

@Singleton
class GeminiKeyRotator @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) {
    companion object {
        private const val TAG = "GeminiKeyRotator"
        // EncryptedSharedPreferences 내 인덱스 저장 키 (동기 접근용)
        private const val KEY_RR_INDEX = "gemini_roundrobin_index"
    }

    /**
     * 현재 라운드로빈 인덱스의 API 키를 반환한다.
     * 키 목록이 비어있으면 null 반환.
     */
    fun getCurrentKey(): String? {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return null
        val idx = getRawIndex().coerceIn(0, keys.size - 1)
        return keys[idx]
    }

    /**
     * 현재 키가 오류를 반환하면 다음 키로 전환한다.
     * 모든 키가 오류이면 null 반환.
     *
     * @param failedKeyIndex 오류 발생한 키의 인덱스
     * @return 다음 유효 키 값, 모두 실패 시 null
     */
    fun rotateToNext(failedKeyIndex: Int): String? {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return null
        val nextIdx = (failedKeyIndex + 1) % keys.size
        saveIndex(nextIdx)
        Log.i(TAG, "키 전환: $failedKeyIndex → $nextIdx (등록 키 ${keys.size}개)")
        return keys[nextIdx]
    }

    /** 현재 인덱스를 반환한다. */
    fun getCurrentIndex(): Int {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return 0
        return getRawIndex().coerceIn(0, keys.size - 1)
    }

    /** 성공 시 인덱스를 다음으로 전진(라운드로빈 사전 순환)한다. */
    fun advance() {
        val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
        if (keys.isEmpty()) return
        val nextIdx = (getRawIndex() + 1) % keys.size
        saveIndex(nextIdx)
    }

    private fun getRawIndex(): Int =
        secureApiKeyRepository.getRoundRobinIndex()

    private fun saveIndex(idx: Int) =
        secureApiKeyRepository.saveRoundRobinIndex(idx)
}
```

### Pattern 2: GeminiEngine 수정 — 라운드로빈 키 사용 + 오류 전환

**What:** 기존 `secureApiKeyRepository.getGeminiApiKey()` 호출을 `GeminiKeyRotator.getCurrentKey()`로 교체하고, 오류 발생 시 `rotateToNext()` 호출.

```kotlin
// Source: 기존 GeminiEngine.kt generate() 내부 패턴 기반

override suspend fun generate(
    transcriptText: String,
    customPrompt: String?
): Result<String> {
    val keys = secureApiKeyRepository.getAllGeminiApiKeyValues()
    if (keys.isEmpty()) {
        return Result.failure(IllegalStateException("등록된 Gemini API 키가 없습니다"))
    }

    // 모든 키 순환 시도
    val startIndex = keyRotator.getCurrentIndex()
    var currentIndex = startIndex
    var triedCount = 0

    while (triedCount < keys.size) {
        val apiKey = keys[currentIndex]
        val model = GenerativeModel(modelName = MODEL_NAME, apiKey = apiKey)
        try {
            val response = model.generateContent(buildPrompt(transcriptText, customPrompt))
            val text = response.text
            if (!text.isNullOrBlank()) {
                keyRotator.advance() // 성공 시 다음 키로 사전 전진 (라운드로빈)
                return Result.success(text)
            }
        } catch (e: QuotaExceededException) {
            // 할당량 초과 — 다음 키로 전환
            val label = secureApiKeyRepository.getGeminiApiKeys()
                .getOrNull(currentIndex)?.label ?: "키 #$currentIndex"
            onKeyError(label, currentIndex, "할당량 초과")
            currentIndex = (currentIndex + 1) % keys.size
            triedCount++
            continue
        } catch (e: Exception) {
            if (isPermissionError(e)) {
                val label = secureApiKeyRepository.getGeminiApiKeys()
                    .getOrNull(currentIndex)?.label ?: "키 #$currentIndex"
                onKeyError(label, currentIndex, "권한 오류")
                currentIndex = (currentIndex + 1) % keys.size
                triedCount++
                continue
            }
            return Result.failure(e)
        }
        triedCount++
        currentIndex = (currentIndex + 1) % keys.size
    }

    // 모든 키 실패
    return Result.failure(IllegalStateException("등록된 모든 Gemini API 키가 오류를 반환했습니다"))
}
```

### Pattern 3: 오류 알림 — Worker 컨텍스트에서 Notification 사용

**What:** Worker 내에서 UI를 직접 업데이트할 수 없으므로 `PipelineNotificationHelper`를 통해 알림으로 전달.
**Why:** 기존 코드베이스가 이미 이 패턴을 사용한다 (`notifyQuotaExceeded`, `notifyFileTooLarge`).

```kotlin
// PipelineNotificationHelper에 추가할 메서드

fun notifyApiKeyError(context: Context, keyLabel: String, reason: String, nextKeyLabel: String?) {
    val bodyText = if (nextKeyLabel != null) {
        "'$keyLabel' 키 $reason — '$nextKeyLabel' 키로 자동 전환합니다"
    } else {
        "'$keyLabel' 키 $reason — 다른 키가 없어 파이프라인이 중단됩니다"
    }
    val notification = NotificationCompat.Builder(context, PIPELINE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Gemini API 키 오류")
        .setContentText(bodyText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
        .setAutoCancel(true)
        .setOngoing(false)
        .setSilent(false)
        .build()
    // ...notify()
}
```

### Anti-Patterns to Avoid

- **Toast/Snackbar from Worker:** Worker는 백그라운드 스레드, UI 스레드 접근 불가. Notification만 사용.
- **DataStore.collect() in GeminiKeyRotator:** GeminiKeyRotator는 suspend-free 동기 API가 필요하다. DataStore는 Flow/suspend 전용이므로 인덱스 저장에 부적합.
- **GeminiEngine @Singleton + mutable state 내부 저장:** Singleton에 mutable currentIndex를 멤버 변수로 두면 멀티 스레드 경쟁 조건 발생. 인덱스는 항상 EncryptedSharedPreferences에 영속화한다.
- **키 오류 시 무한 재시도:** 각 키를 정확히 1회씩 시도한다. `triedCount < keys.size` 가드로 무한 루프 방지.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 백그라운드 알림 | 별도 BroadcastReceiver / Service | PipelineNotificationHelper (기존) | 채널·퍼미션·ID 이미 관리됨 |
| 암호화 저장 | 자체 암호화 | EncryptedSharedPreferences (기존 SecureApiKeyRepository) | AES256, OEM KeyStore 방어 이미 구현됨 |
| 인덱스 원자적 저장 | Room/DataStore 트랜잭션 | EncryptedSharedPreferences.edit().putInt().apply() | 간단한 int 하나에 DB/Flow 불필요 |

---

## Common Pitfalls

### Pitfall 1: 인덱스 범위 초과 (키 삭제 후)
**What goes wrong:** 인덱스 3이 저장된 상태에서 키를 삭제하면 `getAllGeminiApiKeyValues()` 반환 목록이 2개뿐 → IndexOutOfBoundsException.
**Why it happens:** `removeGeminiApiKey(index)`가 인덱스 재정렬을 하지만 저장된 라운드로빈 인덱스는 건드리지 않는다.
**How to avoid:** `getCurrentKey()` 호출 시 항상 `coerceIn(0, keys.size - 1)` 적용. 키 삭제 후 인덱스 재설정은 `removeGeminiApiKey()` 호출 시점에 `GeminiKeyRotator.resetIndex()` 호출하거나, rotator에서 매번 clamp한다.
**Warning signs:** `keys[idx]` 호출 시 예외.

### Pitfall 2: 단일 키 등록 시 무한 루프
**What goes wrong:** 키 1개가 등록되어 있고 그 키가 오류이면 `(0+1) % 1 = 0` → 같은 키 무한 순환.
**Why it happens:** 모듈러 산술이 단일 원소에서는 제자리.
**How to avoid:** `triedCount < keys.size` 카운터로 최대 `keys.size`회만 시도. 모두 실패 시 즉시 `Result.failure()`.

### Pitfall 3: GeminiSttEngine의 getApiKey() 교체 누락
**What goes wrong:** GeminiEngine만 수정하고 GeminiSttEngine의 `private fun getApiKey()` (줄 212)를 그대로 두면 STT 경로는 여전히 단일 키 사용.
**Why it happens:** 두 엔진이 각자 독립적으로 `secureApiKeyRepository.getGeminiApiKey()`를 호출하고 있다.
**How to avoid:** GeminiSttEngine도 GeminiKeyRotator를 Hilt 주입받도록 수정. `transcribe()` 내부 `var apiKey = keyRotator.getCurrentKey()` 패턴 적용.

### Pitfall 4: QuotaExceededException vs HTTP 429 감지 방식 불일치
**What goes wrong:** GeminiEngine은 `QuotaExceededException` (SDK 예외)을 잡고, GeminiSttEngine은 `response.code == 429` (HTTP 상태코드)를 잡는다. 두 경로에서 오류 감지 로직이 다르다.
**Why it happens:** GeminiEngine은 Google AI Client SDK를 사용하고, GeminiSttEngine은 OkHttp로 REST API를 직접 호출한다.
**How to avoid:** 각 엔진의 오류 감지 방식을 유지하되, GeminiKeyRotator의 `onKeyError()` 콜백 시그니처를 통일하여 양쪽에서 동일한 알림 흐름을 따른다.

### Pitfall 5: 403 권한 오류 감지
**What goes wrong:** HTTP 403은 `response.isSuccessful`이 false지만 기존 GeminiSttEngine 코드는 `if (!response.isSuccessful)` 시 즉시 `Result.failure()` 반환 — 키 전환 없이 실패.
**Why it happens:** 기존 코드는 단일 키 전제로 작성됨.
**How to avoid:** `response.code == 403 || response.code == 429` 감지 시 키 전환 분기 추가. 그 외 4xx/5xx는 기존처럼 즉시 실패.

---

## Code Examples

### SecureApiKeyRepository에 추가할 인덱스 저장 메서드

```kotlin
// Source: SecureApiKeyRepository.kt 기존 패턴 기반 (Phase 51 완료 코드 확인)

companion object {
    // 기존 상수에 추가
    private const val KEY_RR_INDEX = "gemini_roundrobin_index"
}

/** 라운드로빈 현재 인덱스를 반환한다. 없으면 0. */
fun getRoundRobinIndex(): Int =
    encryptedPrefs?.getString(KEY_RR_INDEX, "0")?.toIntOrNull() ?: 0

/** 라운드로빈 인덱스를 저장한다. */
fun saveRoundRobinIndex(index: Int) {
    encryptedPrefs?.edit()?.putString(KEY_RR_INDEX, index.toString())?.apply()
}
```

**설계 노트:** EncryptedSharedPreferences는 String 타입만 안전하게 지원하므로 `putString(index.toString())`을 사용한다. (`putInt()` 지원은 OEM별 차이 있음 — 기존 코드도 `KEY_GEMINI_KEY_COUNT`를 String으로 저장하는 동일 패턴을 쓰고 있다.)

### GeminiKeyRotator Hilt 등록

```kotlin
// di/RepositoryModule.kt 또는 별도 SecurityModule.kt에 추가 불필요
// GeminiKeyRotator가 @Singleton + @Inject constructor이면 Hilt가 자동 제공

// GeminiEngine.kt 수정
@Singleton
class GeminiEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val keyRotator: GeminiKeyRotator  // 추가
) : MinutesEngine { ... }

// GeminiSttEngine.kt 수정
@Singleton
class GeminiSttEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository,
    private val quotaTracker: GeminiQuotaTracker,
    private val apiUsageTracker: ApiUsageTracker,
    private val keyRotator: GeminiKeyRotator  // 추가
) : SttEngine { ... }
```

### 모든 키 실패 시 PipelineNotificationHelper 호출

```kotlin
// GeminiEngine.generate() 또는 GeminiSttEngine.transcribe() 내부
if (triedCount >= keys.size) {
    // 마지막 키도 실패 — 전체 실패 알림 (applicationContext 없으므로 반환값으로 처리)
    return Result.failure(
        GeminiAllKeysFailedException("등록된 모든 Gemini API 키(${keys.size}개)가 오류를 반환했습니다")
    )
}

// Worker(MinutesGenerationWorker, TranscriptionTriggerWorker)에서:
if (error is GeminiAllKeysFailedException) {
    PipelineNotificationHelper.notifyAllApiKeysExhausted(applicationContext)
    meetingDao.updatePipelineStatus(id = meetingId, status = PipelineStatus.FAILED.name, ...)
    return Result.failure()
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 단일 getGeminiApiKey() 사용 | getAllGeminiApiKeyValues() 라운드로빈 | Phase 52 | STT + 회의록 양쪽에 적용 |
| 쿼터 초과 시 동일 키 재시도 (MAX_RETRIES 3회) | 쿼터 초과 시 즉시 다음 키로 전환 | Phase 52 | 대기 없이 다음 키 사용, 응답 지연 감소 |

**기존 재시도 로직 처리:**
- GeminiEngine의 `repeat(MAX_QUOTA_RETRIES)` 루프와 GeminiSttEngine의 `for (attempt in 1..MAX_RETRIES)` 루프는 라운드로빈 도입 후 제거하거나 단순화한다. 여러 키가 있으면 재시도 대신 키 전환이 우선이다.
- 단, 키가 1개뿐인 경우에는 기존 재시도(delay + retry) 로직을 유지하는 것이 합리적이다.

---

## Open Questions

1. **GeminiEngine의 `isAvailable()` 조건 변경 여부**
   - What we know: 현재 `getGeminiApiKey() != null`로 단일 키 확인
   - What's unclear: 다중 키 구조에서 `getAllGeminiApiKeyValues().isNotEmpty()`로 변경해야 하는가?
   - Recommendation: `isAvailable()`을 `getAllGeminiApiKeyValues().isNotEmpty()`로 변경. 레거시 `getGeminiApiKey()`는 내부적으로 index 0을 반환하도록 Phase 51에서 마이그레이션 완료되어 있으므로 여전히 동작하지만, 의미 있는 체크는 다중 키 목록 기준이어야 한다.

2. **키 전환 시 오류 알림의 중복 방지**
   - What we know: 한 파이프라인 실행에서 여러 키가 오류이면 알림이 연속 발생
   - What's unclear: 각 키 오류마다 별도 알림을 보내야 하나, 아니면 마지막 요약 알림만?
   - Recommendation: 각 키 오류마다 알림 업데이트 (`notify(SAME_ID, ...)`) — 기존 `PIPELINE_NOTIFICATION_ID`를 재사용하면 동일 알림을 덮어써서 중복 방지됨.

3. **DataStore GEMINI_ROUNDROBIN_INDEX_KEY 상수의 역할**
   - What we know: `UserPreferencesRepository`에 상수가 선언되어 있지만 실제 저장/조회 메서드가 없다
   - What's unclear: 이 상수를 사용하는 DataStore 기반 인덱스 저장을 구현해야 하는가, 아니면 EncryptedSharedPreferences만 사용하는가?
   - Recommendation: EncryptedSharedPreferences 단일 저장소 사용. DataStore 상수는 미사용으로 남기거나 주석으로 "EncryptedSharedPreferences에서 관리됨"을 표시한다. 두 저장소 동기화 로직은 복잡도만 증가시킨다.

---

## Environment Availability

Step 2.6: SKIPPED (코드 전용 변경 — 외부 서비스/CLI 의존성 없음)

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockK |
| Config file | 기존 설정 사용 |
| Quick run command | `./gradlew test --tests "*.GeminiKeyRotatorTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GEMINI-02 | 라운드로빈 인덱스가 순차 증가하고 wrapping된다 | unit | `./gradlew test --tests "*.GeminiKeyRotatorTest"` | ❌ Wave 0 |
| GEMINI-02 | 키 2개 → 첫 호출은 키 0, 두 번째는 키 1, 세 번째는 키 0 | unit | `./gradlew test --tests "*.GeminiKeyRotatorTest"` | ❌ Wave 0 |
| GEMINI-03 | 키 0 오류 시 키 1로 전환되고 알림이 발생한다 | unit | `./gradlew test --tests "*.GeminiKeyRotatorTest"` | ❌ Wave 0 |
| GEMINI-03 | 모든 키 오류 시 GeminiAllKeysFailedException 반환 | unit | `./gradlew test --tests "*.GeminiKeyRotatorTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*.GeminiKeyRotatorTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/.../GeminiKeyRotatorTest.kt` — REQ GEMINI-02, GEMINI-03
- [ ] MockK로 `SecureApiKeyRepository` 모킹 — `getAllGeminiApiKeyValues()` 스터빙

---

## Sources

### Primary (HIGH confidence)
- 프로젝트 코드 직접 분석 — `SecureApiKeyRepository.kt`, `GeminiEngine.kt`, `GeminiSttEngine.kt`, `UserPreferencesRepository.kt`, `PipelineNotificationHelper.kt`, `MinutesGenerationWorker.kt`, `TranscriptionTriggerWorker.kt`
- Phase 51 CONTEXT.md — 저장 구조 및 Phase 52 인터페이스 계약 확인

### Secondary (MEDIUM confidence)
- Android 공식 패턴 — `EncryptedSharedPreferences` 동기 API, `NotificationCompat.Builder` Worker 컨텍스트 사용
- 기존 `notifyQuotaExceeded()`, `notifyFileTooLarge()` 패턴 — Worker에서 Context 기반 알림 발행 precedent 확인됨

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — 기존 코드베이스에서 직접 확인
- Architecture: HIGH — 기존 패턴을 확장하는 구조, 신규 외부 의존성 없음
- Pitfalls: HIGH — 코드 직접 분석으로 식별 (index OOB, 단일 키 루프, 엔진별 감지 방식 차이)

**Research date:** 2026-04-15
**Valid until:** 코드베이스 변경 전까지 (외부 API 의존성 없으므로 만료 없음)
