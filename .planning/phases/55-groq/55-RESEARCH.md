# Phase 55: Groq 대용량 파일 자동 분할 전사 — Research

**Researched:** 2026-04-15
**Domain:** Android audio chunking + Groq Whisper API sequential transcription
**Confidence:** HIGH (API 제한/전략), MEDIUM (Android 분할 구현 경로)

## Summary

Groq Whisper API는 Free 티어 25MB, Dev 티어 100MB의 파일 크기 제한이 있다. 현재 `GroqSttEngine.transcribe()`는 25MB 초과 시 즉시 실패를 반환하는데(Line 85-92), Phase 55는 이 실패 경로를 "자동 분할 → 순차 전사 → 이어붙이기" 로직으로 대체한다.

핵심 설계 결정은 **분할 단위**다. 바이트 단위 단순 분할은 MP3/M4A 모두에서 프레임/샘플 경계를 깨뜨려 Groq 서버가 400 오류를 반환하거나 끊어진 단어를 토해낸다. 안전한 경로는 **재인코딩 없이 디코딩된 PCM을 시간 기반으로 잘라 WAV로 재작성**하거나, **MP3 프레임 헤더 파싱으로 프레임 경계에서 자르는 것**이다. 프로젝트에는 이미 두 가지 재료가 모두 있다 — `AudioConverter.kt`(M4A/MP3 → 16kHz mono PCM WAV 디코딩)와 `Mp3Merger.kt`(MP3 프레임 헤더 파서, `parseFrameHeader()` 등 internal 함수).

**Primary recommendation:** `AudioConverter`의 디코딩 파이프라인을 재사용해 입력 파일을 16kHz mono WAV로 변환한 뒤, **PCM 바이트 스트림을 시간 기반(기본 600초/청크, 10초 오버랩)으로 잘라 각 청크를 WAV 파일로 작성**하고 `GroqSttEngine`에 순차 전달한다. 재인코딩은 이미 Whisper 경로에서 사용 중이므로 코드 중복이 없다. 25MB 한계는 16kHz mono WAV 기준 약 13분에 해당하므로, **10분(600초) 청크 + 10초 오버랩이 25MB 안에 안전하게 들어간다**. 이어붙이기는 오버랩 구간에 대해 **LCS(Longest Common Subsequence) 기반 중복 제거**를 적용하되, 1차 구현은 단순 concat + 경고 로그로 시작하고 품질 이슈가 드러나면 개선한다.

## User Constraints (from CONTEXT.md)

CONTEXT.md 없음 — 이 Phase는 `/gsd:discuss-phase`를 건너뛰고 바로 연구 단계에 진입했다. Phase 설명의 "Key context"와 기존 코드베이스가 제약을 정의한다:

### Locked Decisions (from Phase description + codebase)
- 기존 `GroqSttEngine`의 25MB 제한 오류 경로를 자동 분할로 교체 (요구사항 GROQ-01)
- 분할된 청크는 **순서대로** Groq에 전사 요청 (GROQ-02) — 병렬 호출 불가(순서 보장 + Free 티어 RPM 20 리스크)
- 결과 청크 텍스트는 **순서대로 이어붙여 단일 문자열 반환** (GROQ-03)
- 대상 포맷: M4A(Plaud 녹음기 출력) + MP3 (홈 화면 파일 입력, Share Intent 합쳐진 결과)
- 최소 SDK API 31, 타겟 API 36, Kotlin + Coroutines/Flow
- `SttEngine.transcribe(audioFilePath, onProgress)` 인터페이스 유지 — 호출자 변경 없이 내부 동작만 교체

### Claude's Discretion
- 분할 알고리즘 선택: (A) PCM 디코딩 후 시간 기반 WAV 청크, (B) MP3 프레임 경계 분할, (C) MediaExtractor + MediaMuxer 트랙 복사
- 청크 크기 기본값(초)과 오버랩 크기(초)
- 오버랩 기반 stitching 전략 (단순 concat vs LCS 기반 중복 제거)
- 분할 결과 임시 파일 저장 위치(`context.cacheDir` vs meeting audio dir)와 정리 시점
- 청크 간 지연(rate limit 방어) 필요 여부

### Deferred Ideas (OUT OF SCOPE)
- 사용자가 청크 크기를 조정하는 UI 설정 (REQUIREMENTS.md "Future Requirements" 명시)
- 병렬 청크 전사 (순서 보장 + rate limit 리스크로 배제)
- Groq Dev 티어(100MB) 전용 경로 — Free 25MB 기준으로만 구현

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GROQ-01 | Groq STT 선택 시 25MB 초과 파일 자동 청크 분할 | `transcribe()` 진입부 파일 크기 검사 후 `AudioChunker.split(path, chunkSec=600, overlapSec=10)` 호출 — 아래 "Architecture Patterns" 참조 |
| GROQ-02 | 분할된 각 청크가 순서대로 Groq Whisper API에 전사 요청 | 청크 리스트를 `forEachIndexed`로 순차 순회, 청크 간 300ms delay로 RPM 20(Free) 안전 마진 |
| GROQ-03 | 전사된 청크 결과들이 순서대로 이어붙여져 하나의 전사 텍스트로 출력 | `buildString { … }` 또는 `joinToString("\n")` — 오버랩 중복 제거는 옵션 |

## Standard Stack

### Core (이미 프로젝트에 존재 — 재사용)
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| MediaExtractor | Platform API | 입력 오디오 디코딩 엔트리 | `AudioConverter.kt`에서 이미 사용, M4A/MP3 모두 지원 |
| MediaCodec | Platform API | 디코더(AAC/MP3 → PCM) | `AudioConverter.kt`에서 이미 사용 |
| Kotlin Coroutines | 1.10.+ | 비동기 분할/순차 전사 | `withContext(Dispatchers.IO)` 기존 패턴 |
| OkHttp + Retrofit | 4.12+/2.11+ | Groq API 호출 (기존) | `GroqSttEngine`이 OkHttp 직접 사용 중 |

### New (Phase 55에서 추가)
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| 내부 `AudioChunker` util | — | 입력 파일 → 청크 WAV 리스트 | `GroqSttEngine.transcribe()`에서 파일 > 25MB일 때만 |
| JUnit 5 + MockK | 기존 | 청커 단위 테스트 | `Mp3MergerTest` 패턴 재사용 가능 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| PCM 재디코딩 → WAV 청크 | MP3 프레임 경계 분할 | 장점: 재인코딩 없음, MP3만 동작. 단점: M4A는 별도 경로 필요, CBR/VBR 프레임 크기 계산 복잡, 25MB 맞추려면 프레임 수 역산 필요. **기각**: 이중 구현 |
| PCM → WAV | PCM → FLAC | 장점: 파일 크기 ~50% 감소로 청크당 더 긴 오디오. 단점: FLAC 인코더(libFLAC) 종속성 추가, Android Framework는 FLAC 디코더만 제공(인코더 없음). **기각**: 의존성 부담 > 이득 |
| MediaExtractor/Muxer 트랙 복사 | PCM 재디코딩 | 장점: 재인코딩 없음. 단점: MediaMuxer는 MP4/M4A만 출력 가능(MP3 출력 미지원), MP3 입력을 M4A로 리패키징해야 함(포맷 혼란), 샘플 경계에서 정확한 25MB 자르기 어려움. **기각**: M4A만 가능하고 정밀도 낮음 |
| 단순 concat stitching | LCS 중복 제거 | 장점: 구현 단순. 단점: 오버랩 10초분이 중복 텍스트로 나타남. **1차에선 단순 concat**, 품질 이슈 발생 시 LCS 도입 |

**Installation:** 외부 의존성 추가 없음. 기존 Gradle 구성 그대로 사용.

**Version verification:** 해당 없음(플랫폼 API 사용).

## Groq API Facts (HIGH confidence, 공식 문서 확인)

| 항목 | 값 | 출처 |
|------|----|------|
| Free tier 파일 크기 제한 | **25 MB** | console.groq.com/docs/speech-to-text |
| Dev tier 파일 크기 제한 | 100 MB | 동일 |
| 지원 포맷 | flac, mp3, mp4, mpeg, mpga, **m4a**, ogg, wav, webm | 동일 |
| 최소 오디오 길이 | 0.01초 (과금은 10초 기준) | 동일 |
| 멀티 트랙 파일 | 첫 번째 오디오 트랙만 처리 | 동일 |
| 권장 전처리 | **16kHz mono** | 동일 |
| 권장 압축 | FLAC 무손실 | 동일 |
| Free tier RPM | **20 requests/min** (whisper-large-v3-turbo) | console.groq.com/docs/rate-limits |
| Free tier RPD | 2,000 requests/day | 동일 |
| Free tier Audio Seconds/Hour | 7,200 (=2시간 오디오) | 동일 |
| Free tier Audio Seconds/Day | 28,800 (=8시간 오디오) | 동일 |

**시사점:**
- 10분(600초) 청크로 나누면 1시간 오디오 = 6청크 = RPM 20 안에 충분히 여유
- **ASH 7,200초는 시간당 2시간치 오디오로, 1회 긴 회의(2시간 이상) 전사는 한도 초과 가능** — 분할 자체가 한도를 늘려주지는 않음(원본 오디오 길이가 과금 대상)
- 청크 간 delay는 RPM(20) 방어용으로 **3초/청크 이하면 20 RPM 안전** → 실질적으로 짧은 delay(예: 300ms) 불필요하지만, 안전 마진과 연속 429 방지 측면에서 **500~1000ms 권장**

## Architecture Patterns

### Recommended Project Structure

```
app/src/main/java/com/autominuting/
├── data/stt/
│   ├── GroqSttEngine.kt          # 수정: 25MB 초과 시 AudioChunker 호출
│   └── AudioConverter.kt         # 재사용: 16kHz mono PCM 디코딩
└── util/
    └── AudioChunker.kt            # NEW: 입력 파일 → 청크 WAV 파일 리스트
```

테스트:
```
app/src/test/java/com/autominuting/util/
    └── AudioChunkerTest.kt        # NEW: 청크 크기/오버랩/경계 테스트
```

### Pattern 1: AudioChunker — PCM 디코딩 후 시간 기반 WAV 청크 생성

**What:** 입력 오디오(M4A/MP3)를 16kHz mono PCM으로 디코딩하고, 지정된 초 단위로 잘라 각 청크를 독립적인 WAV 파일로 저장한다. 각 청크는 자체 WAV 헤더를 가진 **유효한 독립 파일**이어서 Groq에 그대로 전송 가능하다.

**When to use:** 입력 파일 크기가 `MAX_FILE_SIZE`(25MB)를 초과할 때만. 그 이하면 기존 경로(단일 요청) 유지.

**Why:** 기존 `AudioConverter.decodeAudioToPcm()`이 이미 M4A와 MP3를 동일하게 처리하므로 포맷별 분기가 불필요하다. PCM 바이트 수는 `sampleRate × channels × bytesPerSample × seconds`로 정확히 계산되므로 **25MB 경계를 샘플 단위 정밀도로 맞출 수 있다**.

**핵심 계산:**
- 16kHz mono 16-bit PCM → `16,000 × 1 × 2 = 32,000 bytes/sec` = 31.25 KB/sec
- WAV 오버헤드(44바이트) 무시 가능
- 25MB ÷ 31.25 KB/sec ≈ **838초(약 13분 58초)**
- **안전 마진 포함 600초(10분) 청크 권장** → 약 18.3 MB, 25MB의 73%

**Example (의사 코드):**
```kotlin
// Source: 프로젝트 AudioConverter 패턴 참조
object AudioChunker {
    private const val CHUNK_SECONDS = 600          // 10분
    private const val OVERLAP_SECONDS = 10         // 10초 오버랩
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val BYTES_PER_SECOND = 16_000 * 1 * 2  // mono, 16-bit

    suspend fun split(
        inputPath: String,
        outputDir: File,
        audioConverter: AudioConverter
    ): List<File> = withContext(Dispatchers.IO) {
        // 1. 16kHz mono WAV로 변환 (AudioConverter 재사용)
        val convertedPath = audioConverter.convertToWhisperFormat(inputPath, outputDir.absolutePath)
        val wavBytes = File(convertedPath).readBytes()
        val pcm = wavBytes.copyOfRange(44, wavBytes.size)  // WAV 헤더 44바이트 제거

        // 2. PCM을 시간 기반 청크로 분할 (오버랩 포함)
        val chunkBytes = CHUNK_SECONDS * BYTES_PER_SECOND
        val overlapBytes = OVERLAP_SECONDS * BYTES_PER_SECOND
        val stepBytes = chunkBytes - overlapBytes

        val chunks = mutableListOf<File>()
        var offset = 0
        var index = 0
        while (offset < pcm.size) {
            val end = minOf(offset + chunkBytes, pcm.size)
            val chunkPcm = pcm.copyOfRange(offset, end)
            val chunkFile = File(outputDir, "chunk_${index}.wav")
            writeWavFile(chunkFile, chunkPcm)  // AudioConverter의 writeWavFile 패턴
            chunks += chunkFile
            index++
            if (end >= pcm.size) break
            offset += stepBytes
        }
        chunks
    }
}
```

### Pattern 2: GroqSttEngine — 크기 분기 + 순차 전사

```kotlin
override suspend fun transcribe(
    audioFilePath: String,
    onProgress: (Float) -> Unit
): Result<String> = withContext(Dispatchers.IO) {
    val audioFile = File(audioFilePath)
    val fileSize = audioFile.length()

    if (fileSize <= MAX_FILE_SIZE) {
        // 기존 단일 요청 경로
        return@withContext transcribeSingle(audioFile, onProgress)
    }

    // 분할 경로 (GROQ-01)
    val tempDir = File(audioFile.parentFile, "groq_chunks_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
        val chunks = audioChunker.split(audioFilePath, tempDir, audioConverter)
        val transcripts = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            // GROQ-02: 순서대로 전사
            val result = transcribeSingle(chunk, onProgress = {
                // 전체 진행률 = (index + chunkProgress) / totalChunks
                onProgress((index + it) / chunks.size)
            })
            if (result.isFailure) return@withContext result
            transcripts += result.getOrThrow()

            // RPM 20 안전 마진
            if (index < chunks.size - 1) delay(500)
        }

        // GROQ-03: 순서대로 이어붙임
        Result.success(transcripts.joinToString("\n"))
    } finally {
        tempDir.deleteRecursively()  // 임시 청크 정리
    }
}
```

### Anti-Patterns to Avoid

- **바이트 단위 `File.readBytes()` 후 25MB씩 `copyOfRange`**: M4A는 MP4 컨테이너 구조상 `moov` 원자/샘플 테이블을 깨뜨림 → 디코더가 즉시 실패. MP3도 프레임 경계가 아니면 재생/디코딩 시 첫 프레임 손실.
- **청크별로 `OkHttpClient` 새로 생성**: Phase 44의 수정사항(커넥션 풀 공유)을 그대로 유지해야 함 — 이미 `client`는 클래스 멤버이므로 문제없음.
- **MediaMuxer로 MP3 출력 시도**: Android `MediaMuxer`는 MP4(H.264/AAC), WEBM, OGG, AAC_ADTS 출력만 지원 — MP3 출력 불가.
- **병렬 전사(`chunks.map { async { … } }`)**: 순서 보장이 깨지고 RPM 20 리스크, GROQ-02 요구사항 "순서대로" 위반.
- **임시 청크 파일 미정리**: 내부 저장소 고갈. `try { … } finally { tempDir.deleteRecursively() }` 필수.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| M4A/MP3 디코딩 | 커스텀 MP3 프레임 디코더 또는 AAC 디코더 | 기존 `AudioConverter.decodeAudioToPcm()` | 이미 MediaCodec/MediaExtractor로 구현되어 있고 Whisper 엔진에서 검증됨 |
| WAV 파일 작성 | 직접 RIFF 헤더 바이트 작성 | 기존 `AudioConverter.writeWavFile()` 패턴 복사 또는 internal로 열기 | 44바이트 헤더 순서/byte-order 버그 위험, 이미 존재 |
| MP3 프레임 헤더 파싱(옵션 경로) | 커스텀 비트 파싱 | 기존 `Mp3Merger.parseFrameHeader()` (internal) | Phase 53에서 검증된 코드 |
| Rate limit 대응 | 복잡한 백오프 스케줄러 | 기존 `RETRY_DELAY_MS = 20_000L` + 429 재시도 | `GroqSttEngine`에 이미 있음 — 청크 전사도 같은 메서드 호출 시 자동 적용 |
| HTTP 재시도 | 자체 재시도 로직 | 기존 `transcribeSingle()` 내 for 루프 | Phase 44에서 버그 수정 완료 |

**Key insight:** 이 Phase는 "새 기능" 이전에 "기존 코드 재배열"이다. `AudioConverter`(디코딩), `Mp3Merger`(프레임 파싱 참고), `GroqSttEngine.transcribeSingle`(재시도 포함 단일 요청)이 이미 있다 — **새로 작성해야 하는 것은 `AudioChunker`(시간 기반 PCM 자르기) 하나**다.

## Runtime State Inventory

해당 없음 — 이 Phase는 순수 신규 기능으로 rename/refactor/migration이 아니다. 기존 런타임 상태(DB, SOPS, OS 등록 등)를 건드리지 않는다.

- **Stored data:** 없음 — 임시 청크 파일만 생성(로컬 파일시스템, 세션 내 정리)
- **Live service config:** 없음
- **OS-registered state:** 없음
- **Secrets/env vars:** Groq API 키(`SecureApiKeyRepository`) — 기존 그대로, 변경 없음
- **Build artifacts:** 없음

## Common Pitfalls

### Pitfall 1: WAV 헤더의 dataSize 필드
**What goes wrong:** 청크 파일을 생성했는데 Groq이 "malformed audio" 또는 일부만 전사.
**Why it happens:** WAV 헤더 `data` 청크의 `dataSize`(4바이트 LE)가 실제 PCM 바이트 수와 불일치. 플레이어는 관용적이지만 Whisper 입력 파서는 엄격.
**How to avoid:** `writeWavFile`을 각 청크별로 호출하고 `dataSize = chunkPcm.size`로 정확히 설정. `fileSize = 36 + dataSize`도 마찬가지.
**Warning signs:** Groq 응답이 "text" 빈 문자열 또는 앞부분만 전사.

### Pitfall 2: 16kHz mono 변환으로 인한 파일 크기 역산 오류
**What goes wrong:** 원본이 이미 16kHz mono WAV면 `AudioConverter.convertToWhisperFormat`는 **원본 경로를 그대로 반환**(L50). 청커가 반환값을 "변환된 파일"로 가정하고 삭제하면 원본이 삭제됨.
**Why it happens:** `isAlreadyWhisperFormat()` 단락.
**How to avoid:** 청커에서 반환 경로가 `inputPath`와 같으면 복사하여 임시 디렉토리로 옮기거나, PCM 자르기만 하고 **파일 자체를 건드리지 않음**. 청크 파일만 삭제 대상으로 관리.
**Warning signs:** 다음 실행에서 원본 오디오 파일 없음 오류.

### Pitfall 3: 오버랩으로 인한 전사 중복
**What goes wrong:** 최종 전사에 "…회의를 시작하겠습니다. 회의를 시작하겠습니다…"처럼 10초분 중복 텍스트.
**Why it happens:** 청크 A의 마지막 10초 = 청크 B의 첫 10초(의도된 오버랩). 단순 concat 시 두 전사 모두에 같은 내용 포함.
**How to avoid:** 1차는 단순 concat + 청크 사이에 `\n\n[---청크 N---]\n\n` 구분자로 명시 / 2차 개선 옵션: 청크 B의 첫 문장과 청크 A의 마지막 문장에서 LCS 찾아 중복 제거.
**Warning signs:** 청크 경계에서 동일 문장 반복.

### Pitfall 4: 디스크 공간 고갈
**What goes wrong:** 1시간 M4A(~30MB) → 16kHz mono WAV(~110MB) → 6청크(~110MB 총합) = 원본 대비 **7배 일시적 사용량**.
**Why it happens:** PCM은 압축되지 않으므로 WAV 파일이 원본 M4A보다 3~5배 크다.
**How to avoid:** 청크 생성 위치를 `context.cacheDir`에 두고, 전사 완료 청크는 즉시 삭제(다음 청크 넘기기 전). `try/finally`에서 `tempDir.deleteRecursively()`. 청크 생성 전 `StatFs`로 여유 공간 확인 권장.
**Warning signs:** IOException "No space left on device".

### Pitfall 5: onProgress 콜백 누적 반올림
**What goes wrong:** 진행률이 1.0을 초과하거나 역행.
**Why it happens:** `(index + subProgress) / chunks.size`에서 `transcribeSingle`가 onProgress를 호출하지 않으면 subProgress 초기값 0 유지 → 각 청크 시작 시 점프.
**How to avoid:** 단일 청크 내부 진행률 대신 **청크 완료 기반 이산 진행률**을 사용: `onProgress((index + 1).toFloat() / chunks.size)`를 청크 전사 완료 후 호출.

### Pitfall 6: Free tier RPD(2,000) 초과
**What goes wrong:** 하루 여러 긴 회의 처리 시 에러.
**Why it happens:** 10분 청크 × 많은 회의. 보통은 문제없지만 엣지 케이스.
**How to avoid:** 1차 구현에서는 방어 로직 불필요(현실적 사용 빈도 낮음). 429 응답이 오면 기존 20초 재시도로 자연스럽게 처리됨.

## Code Examples

### Example 1: 청크 PCM → WAV 작성 (AudioConverter 패턴 복제)
```kotlin
// Source: app/src/main/java/com/autominuting/data/stt/AudioConverter.kt writeWavFile() 라인 240-268
internal fun writeWavChunk(outputFile: File, pcmData: ByteArray) {
    val sampleRate = 16_000
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcmData.size
    val fileSize = 36 + dataSize

    FileOutputStream(outputFile).use { fos ->
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(fileSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)  // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }
        fos.write(header.array())
        fos.write(pcmData)
    }
}
```

### Example 2: 순차 청크 전사 + 진행률
```kotlin
val chunks = audioChunker.split(audioFilePath, tempDir, audioConverter)
val transcripts = mutableListOf<String>()
chunks.forEachIndexed { i, chunk ->
    val r = transcribeSingle(chunk, onProgress = { /* no-op */ })
    if (r.isFailure) return@withContext r
    transcripts += r.getOrThrow()
    onProgress((i + 1f) / chunks.size)
    if (i < chunks.lastIndex) delay(500)  // RPM 안전 마진
}
Result.success(transcripts.joinToString("\n"))
```

### Example 3: 단위 테스트 (MockK 불필요, 순수 유틸)
```kotlin
// Source: Mp3MergerTest 패턴 참조
class AudioChunkerTest {
    @Test
    fun `600초 청크 사이즈 계산이 25MB 안에 들어간다`() {
        val bytesPerSec = 16_000 * 1 * 2
        val chunkBytes = 600 * bytesPerSec
        assertTrue(chunkBytes + 44 < 25 * 1024 * 1024)
    }

    @Test
    fun `1800초 입력을 3청크로 분할한다 (오버랩 10초)`() {
        // 1800초 = 600 + (590*2) = 3청크 (step = 590s, 마지막은 short chunk)
        val pcm = ByteArray(1800 * 16_000 * 2)
        val chunks = AudioChunker.splitPcmForTest(pcm, chunkSec = 600, overlapSec = 10)
        assertEquals(3, chunks.size)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 25MB 초과 시 사용자에게 Dev 플랜 업그레이드 안내 | 자동 분할 전사 | Phase 55 | 사용자 경험 — "실패" 대신 "조금 느리게라도 완료" |
| 단일 파일 단일 요청 | N청크 순차 요청 | Phase 55 | Groq RPM 20 여유 있음, 1시간 오디오 = 6요청 |

**Deprecated/outdated:**
- `GroqSttEngine.MAX_FILE_SIZE` 오류 경로(L85-92): 분할 경로로 대체 — 완전히 제거하지 않고 **분기점**으로 유지(`if (fileSize > MAX) chunkAndTranscribe() else single()`).

## Open Questions

1. **오버랩 중복 제거 품질**
   - What we know: 단순 concat 시 오버랩 10초분이 두 번 등장. Groq 커뮤니티는 LCS 기반 stitching 권장.
   - What's unclear: 한국어 Whisper 출력에서 LCS 매칭이 얼마나 안정적인지, 오버랩 구간이 짧게(10초) 끝날 때 의미 있는 중복 제거가 가능한지.
   - Recommendation: **1차 구현은 단순 concat + 청크 구분 로그만**. 실제 사용 시 중복이 심하면 Phase 55.X로 LCS 개선. 요구사항(GROQ-03)은 "이어붙여져"이지 "중복 제거"를 명시하지 않음.

2. **임시 파일 위치 — cacheDir vs 회의 폴더**
   - What we know: `context.cacheDir`은 시스템 회수 가능, 회의 폴더는 영속적.
   - What's unclear: 전사 중 앱이 종료되면 cacheDir이 비워질 수 있어 WorkManager 재시도 시 청크 재생성 필요.
   - Recommendation: 회의 폴더 하위의 `groq_chunks_<timestamp>/` 사용. `finally`에서 정리.

3. **`AudioConverter.convertToWhisperFormat`이 단락(原本이 이미 16kHz mono WAV)인 경우 처리**
   - What we know: 현재 `AudioConverter`는 단락 시 입력 경로를 그대로 반환 → `AudioChunker`가 받은 경로를 "임시 파일"로 착각하면 안됨.
   - Recommendation: `AudioChunker`가 변환 결과 파일을 항상 **새 경로**로 받도록 `AudioConverter`에 플래그 추가(`forceCopy = true`) 또는 청커가 결과 경로를 `inputPath`와 비교하여 복사.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android MediaCodec/Extractor | AudioChunker 디코딩 | 있음 | API 31+ | — |
| Groq API 연결 | 청크 전사 | 런타임 의존 | — | 기존 429/타임아웃 재시도 |
| 내부 저장소 여유 공간 | 청크 임시 파일 | 런타임 확인 | — | `StatFs` 사전 검사 실패 시 에러 반환 |

외부 의존성 없음 — 모두 플랫폼 API 또는 기존 코드베이스.

## Sources

### Primary (HIGH confidence)
- https://console.groq.com/docs/speech-to-text — 파일 크기 25MB/100MB, 지원 포맷, 16kHz mono 권장
- https://console.groq.com/docs/rate-limits — whisper-large-v3-turbo Free tier RPM 20, RPD 2000, ASH 7200
- `app/src/main/java/com/autominuting/data/stt/AudioConverter.kt` — 기존 디코딩/WAV 작성 패턴
- `app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt` — 기존 단일 요청 구현
- `app/src/main/java/com/autominuting/util/Mp3Merger.kt` — MP3 프레임 파싱 참고
- `.planning/REQUIREMENTS.md` — GROQ-01/02/03 요구사항
- `.planning/STATE.md` — Phase 44 Groq 버그 수정 이력, Phase 53 Mp3Merger 패턴

### Secondary (MEDIUM confidence)
- https://community.groq.com/t/chunking-longer-audio-files-for-whisper-models-on-groq/162 — 600초 청크 + 10초 오버랩 권장, LCS stitching 알고리즘
- https://developer.android.com/reference/kotlin/android/media/MediaMuxer — MediaMuxer 출력 포맷 제한(MP3 출력 불가)

### Tertiary (LOW confidence — 참고용)
- https://github.com/Bklieger/groqnotes/issues/8 — 커뮤니티 구현 힌트(25MB 자동 분할 이슈 트래킹)

## Metadata

**Confidence breakdown:**
- Groq API 제한/rate limit: HIGH — 공식 문서 직접 확인
- 분할 전략(PCM → WAV 청크): HIGH — 기존 `AudioConverter`가 동일 파이프라인을 검증된 상태로 제공
- 600초/10초 기본값: MEDIUM — Groq 커뮤니티 권장이지만 한국어 회의 오디오에서 경험적 검증 필요
- Stitching 품질(단순 concat 충분성): MEDIUM — 요구사항은 "이어붙이기"만 명시, 중복 제거는 품질 개선 이슈

**Research date:** 2026-04-15
**Valid until:** 2026-05-15 (Groq API 사양이 분기별로 변경될 가능성 있음 — Whisper 모델이나 파일 한도 조정 시 재확인)
