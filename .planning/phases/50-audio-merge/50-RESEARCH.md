# Phase 50: 다중 파일 합치기 - Research

**Researched:** 2026-04-05
**Domain:** Android Share Intent 다중 오디오 파일 수신 + WAV 바이트 이어붙이기
**Confidence:** HIGH

## Summary

Phase 50은 외부 앱에서 ACTION_SEND_MULTIPLE Intent로 여러 오디오 파일을 공유받았을 때 자동으로 하나의 WAV 파일로 합쳐 기존 STT -> 회의록 파이프라인에 그대로 전달하는 기능이다. 기존 `ShareReceiverActivity`에는 이미 ACTION_SEND_MULTIPLE 처리 코드가 있지만, 텍스트 파일 다중 공유만 지원하며 오디오 다중 공유는 미구현 상태다. 현재 `isAudioShare` 감지 로직은 단일 `EXTRA_STREAM` URI만 검사하므로 SEND_MULTIPLE + audio 조합은 텍스트 경로로 빠진다.

핵심 구현은 세 부분으로 나뉜다: (1) SEND_MULTIPLE에서 다중 audio URI 감지, (2) WAV 헤더 재계산을 포함한 PCM 데이터 이어붙이기, (3) 합쳐진 단일 파일을 기존 `processSharedAudio()`와 동일한 경로로 파이프라인에 진입시키기. 결정된 사항에 따라 바이트 단순 이어붙이기를 사용하며 사용자 확인 없이 즉시 처리한다.

**Primary recommendation:** `ShareReceiverActivity.onCreate()`에서 SEND_MULTIPLE + audio MIME 조합을 감지하는 분기를 추가하고, 별도의 `WavMerger` 유틸리티 클래스에서 WAV 헤더 파싱/재계산/데이터 concat을 수행한 뒤, 기존 `processSharedAudio()` 메서드를 재사용하여 파이프라인에 진입시킨다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-01 | Share Intent로 여러 오디오 파일을 공유했을 때 앱이 자동으로 하나의 파일로 합쳐 처리 | ShareReceiverActivity에 SEND_MULTIPLE + audio 분기 추가 + WavMerger 유틸 |
| MERGE-02 | 합쳐진 파일의 파일명은 첫 번째 파일의 파일명 사용 | getDisplayName(firstUri)로 첫 번째 URI에서 파일명 추출 — 기존 메서드 재사용 |
| MERGE-03 | 합쳐진 단일 파일이 기존 STT -> 회의록 파이프라인에 그대로 전달 | processSharedAudio()가 이미 Meeting 생성 + TranscriptionTriggerWorker enqueue 수행 — 합친 파일 URI만 전달하면 됨 |
</phase_requirements>

## Architecture Patterns

### 기존 코드 구조 분석

**ShareReceiverActivity** (핵심 수정 대상):
- 위치: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
- `onCreate()` 흐름:
  1. ACTION_SEND / ACTION_SEND_MULTIPLE 검증 (라인 102-108)
  2. 단일 `EXTRA_STREAM` URI에서 audio MIME 감지 → `isAudioShare` (라인 110-125)
  3. `isAudioShare == true` → `processSharedAudio(audioUri)` (라인 127-148)
  4. 텍스트 처리 경로 (라인 150 이후) — SEND_MULTIPLE 텍스트 합치기 포함
- **현재 문제**: `isAudioShare`는 단일 `EXTRA_STREAM`만 검사. SEND_MULTIPLE에서 오디오 URI 리스트가 올 때 `streamUri`가 null이 되어 텍스트 경로로 빠짐

**processSharedAudio()** (재사용 대상):
- 라인 353-426
- ContentResolver로 오디오를 앱 내부 저장소(`filesDir/audio/`)에 복사
- Meeting 엔티티 생성 (source="SAMSUNG_SHARE", status=AUDIO_RECEIVED)
- TranscriptionTriggerWorker enqueue
- 단일 `android.net.Uri` 파라미터 → 합친 파일의 로컬 경로 File을 Uri로 변환하거나, 별도 오버로드 필요

**AndroidManifest.xml** (수정 불필요):
- `SEND_MULTIPLE` + `*/*` intent-filter 이미 등록됨 (라인 84-88)

### 추천 프로젝트 구조

```
app/src/main/java/com/autominuting/
├── presentation/share/
│   └── ShareReceiverActivity.kt    # SEND_MULTIPLE + audio 분기 추가
└── util/
    └── WavMerger.kt                # WAV 합치기 유틸리티 (신규)
```

### Pattern 1: SEND_MULTIPLE 오디오 감지 분기

**What:** `onCreate()`에서 기존 `isAudioShare` 감지 이후, SEND_MULTIPLE + audio MIME 조합을 별도로 감지
**When to use:** intent.action == ACTION_SEND_MULTIPLE 이고 EXTRA_STREAM 리스트의 모든 URI가 audio/* MIME인 경우

```kotlin
// onCreate() 내 isAudioShare 블록 이후에 추가
if (!isAudioShare && intent?.action == Intent.ACTION_SEND_MULTIPLE) {
    @Suppress("DEPRECATION")
    val streamUris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
    if (!streamUris.isNullOrEmpty()) {
        val allAudio = streamUris.all { uri ->
            val type = try { contentResolver.getType(uri) } catch (e: Exception) { null }
                ?: intent.type
            type?.startsWith("audio/") == true
        }
        if (allAudio) {
            // 다중 오디오 합치기 경로
            handleMultipleAudioShare(streamUris)
            return
        }
    }
}
```

### Pattern 2: WAV 바이트 이어붙이기 (WavMerger)

**What:** 여러 WAV 파일의 PCM 데이터를 하나로 합치고 헤더를 재계산
**When to use:** 동일 포맷(샘플레이트, 채널, 비트뎁스) WAV 파일 합치기

WAV 헤더 구조 (44바이트 표준):
```
Offset  Size  Field
0       4     "RIFF"
4       4     ChunkSize (파일 전체 크기 - 8)
8       4     "WAVE"
12      4     "fmt "
16      4     SubChunk1Size (보통 16)
20      2     AudioFormat (1 = PCM)
22      2     NumChannels
24      4     SampleRate
28      4     ByteRate
32      2     BlockAlign
34      2     BitsPerSample
36      4     "data"
40      4     SubChunk2Size (PCM 데이터 크기)
44      ...   PCM data
```

```kotlin
// util/WavMerger.kt
object WavMerger {

    /**
     * 여러 WAV 파일의 PCM 데이터를 이어붙여 단일 WAV 파일로 합친다.
     * 첫 번째 파일의 fmt 정보(샘플레이트, 채널, 비트뎁스)를 기준으로 헤더를 재작성한다.
     *
     * @param inputStreams 합칠 WAV 파일들의 InputStream 리스트 (순서대로)
     * @param outputFile 합쳐진 결과 파일
     * @throws IOException 파일 읽기/쓰기 실패 시
     * @throws IllegalArgumentException WAV 형식이 아니거나 fmt 불일치 시
     */
    fun merge(inputStreams: List<InputStream>, outputFile: File) {
        // 1. 첫 번째 파일에서 fmt 청크 읽기 (44바이트 헤더 파싱)
        // 2. 나머지 파일들의 fmt 검증 (샘플레이트/채널/비트뎁스 일치 확인)
        // 3. 각 파일에서 data 청크 이후 PCM 바이트만 추출
        // 4. 총 PCM 데이터 크기 계산
        // 5. 새 WAV 헤더 작성 (ChunkSize, SubChunk2Size 재계산)
        // 6. PCM 데이터 순차 기록
    }
}
```

핵심 재계산 공식:
- `SubChunk2Size` = 모든 파일의 PCM 데이터 크기 합
- `ChunkSize` = 36 + SubChunk2Size
- `ByteRate` = SampleRate * NumChannels * BitsPerSample / 8
- `BlockAlign` = NumChannels * BitsPerSample / 8

### Pattern 3: 합친 파일로 파이프라인 진입

**What:** 합쳐진 파일을 기존 processSharedAudio()와 동일한 방식으로 처리
**When to use:** WavMerger로 파일 합치기 완료 후

```kotlin
private fun handleMultipleAudioShare(uris: List<android.net.Uri>) {
    lifecycleScope.launch {
        try {
            // 1. 첫 번째 URI에서 파일명 추출 (MERGE-02)
            val firstName = getDisplayName(uris.first()) ?: "merged_audio"

            // 2. 각 URI에서 InputStream 열기
            val inputStreams = uris.map { uri ->
                contentResolver.openInputStream(uri)
                    ?: throw IOException("URI를 열 수 없습니다: $uri")
            }

            // 3. 합친 파일 생성
            val audioDir = File(filesDir, "audio")
            audioDir.mkdirs()
            val mergedFile = File(audioDir, "share_${System.currentTimeMillis()}.wav")
            WavMerger.merge(inputStreams, mergedFile)

            // 4. 기존 파이프라인 진입 (processSharedAudio와 동일 로직)
            // Meeting 생성 시 title = firstName 사용
            processSharedAudioFromFile(mergedFile, firstName)
        } catch (e: Exception) {
            Log.e(TAG, "다중 오디오 합치기 실패", e)
            Toast.makeText(this@ShareReceiverActivity, "오디오 합치기 실패", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}
```

### Anti-Patterns to Avoid
- **InputStream을 전부 메모리에 로드하지 말 것:** 대용량 오디오 파일은 스트리밍 방식으로 처리. `readBytes()`로 전체를 메모리에 올리면 OOM 위험
- **processSharedAudio()를 직접 호출하지 말 것:** 해당 메서드는 content URI를 받아 복사하는 로직 포함. 이미 로컬에 합친 파일은 URI 변환 없이 File 기반 별도 메서드 필요
- **WAV 헤더를 하드코딩하지 말 것:** 첫 번째 파일의 실제 fmt 청크를 읽어서 사용. 채널 수, 샘플레이트가 다를 수 있음

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WAV 헤더 파싱 | 바이트 오프셋 직접 계산 | 구조화된 WavHeader data class + ByteBuffer | 엔디안 처리 실수 방지, 가독성 |
| 대용량 파일 복사 | readBytes() 전체 로드 | BufferedInputStream + 8KB 버퍼 copyTo | OOM 방지 |
| MIME 타입 감지 | 확장자 기반 추정 | ContentResolver.getType() | Content URI에서 확장자 불확실 |

**Key insight:** WAV 합치기 자체는 간단하지만(헤더 44바이트 + PCM concat), 헤더의 Little-Endian 바이트 순서와 청크 크기 재계산에서 실수하기 쉽다. `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`을 반드시 사용해야 한다.

## Common Pitfalls

### Pitfall 1: Content URI 수명
**What goes wrong:** SEND_MULTIPLE로 받은 content:// URI는 Activity가 살아 있는 동안만 유효. 비동기 처리 중 Activity가 finish()되면 URI 접근 불가
**Why it happens:** ContentProvider grant는 receiving component의 lifecycle에 바인딩됨
**How to avoid:** URI에서 데이터를 읽는 작업(InputStream 열기/복사)을 finish() 호출 전에 완료. WavMerger.merge()는 반드시 finish() 이전에 실행
**Warning signs:** `SecurityException: Permission Denial` 로그

### Pitfall 2: fmt 청크 불일치
**What goes wrong:** 합칠 파일들의 샘플레이트, 채널 수, 비트뎁스가 다르면 합친 결과물이 깨진 오디오가 됨
**Why it happens:** 사용자가 다른 설정으로 녹음한 파일들을 합치려 함
**How to avoid:** 첫 번째 파일의 fmt와 나머지 파일의 fmt를 비교 검증. 불일치 시 에러 메시지 표시하고 중단. (REQUIREMENTS.md Out of Scope에 "다른 포맷 간 변환 불가" 명시됨)
**Warning signs:** 합친 파일 재생 시 속도가 다르거나 노이즈 발생

### Pitfall 3: WAV가 아닌 오디오 파일
**What goes wrong:** m4a, mp3, ogg 등 압축 포맷은 단순 바이트 이어붙이기 불가
**Why it happens:** 압축 포맷은 자체 컨테이너 구조가 있어 raw 바이트 concat이 안 됨
**How to avoid:** 첫 번째 파일 헤더 4바이트가 "RIFF"인지 검증. WAV가 아닌 경우 "WAV 파일만 합치기를 지원합니다" 토스트 표시
**Warning signs:** 파일이 .m4a/.mp3 확장자이거나 MIME이 audio/mp4, audio/mpeg

### Pitfall 4: data 청크 이전에 다른 청크가 있는 경우
**What goes wrong:** WAV 파일에 LIST, fact, PEAK 등 비표준 청크가 fmt와 data 사이에 올 수 있음
**Why it happens:** 일부 녹음 소프트웨어가 메타데이터 청크를 삽입
**How to avoid:** 44바이트 고정 오프셋 대신, "data" 문자열을 순차 검색하여 data 청크 시작점을 동적으로 찾기
**Warning signs:** 44바이트 오프셋에서 "data" 문자열이 아닌 다른 값

### Pitfall 5: 단일 파일 SEND_MULTIPLE
**What goes wrong:** SEND_MULTIPLE이지만 URI가 1개만 있을 수 있음
**Why it happens:** 일부 파일 관리자가 단일 선택도 SEND_MULTIPLE로 보냄
**How to avoid:** URI 리스트 크기가 1이면 합치기 없이 기존 단일 파일 경로로 처리

### Pitfall 6: 임시 합친 파일 정리 누락
**What goes wrong:** 합친 WAV 파일이 `filesDir/audio/`에 영구 보존됨
**Why it happens:** processSharedAudio()가 이미 같은 디렉토리에 저장하므로 별도 정리 로직이 없음
**How to avoid:** 합친 파일은 기존 단일 파일과 동일하게 `filesDir/audio/`에 저장되어 앱의 기존 파일 관리 정책을 따름. 별도 정리 불필요 — 기존 파일 삭제 기능(Phase 16)에서 관리

## Code Examples

### WAV 헤더 파싱 (ByteBuffer 활용)

```kotlin
// Source: WAV format spec (http://soundfile.sapp.org/doc/WaveFormat/)
data class WavHeader(
    val audioFormat: Short,    // 1 = PCM
    val numChannels: Short,
    val sampleRate: Int,
    val byteRate: Int,
    val blockAlign: Short,
    val bitsPerSample: Short,
    val dataSize: Int,         // data 청크의 크기
    val dataOffset: Int        // data 청크 시작 위치 (PCM 데이터 시작점)
)

fun parseWavHeader(inputStream: InputStream): WavHeader {
    val headerBytes = ByteArray(44)
    val bytesRead = inputStream.read(headerBytes)
    require(bytesRead >= 44) { "WAV 헤더를 읽을 수 없습니다" }

    val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF 검증
    val riff = String(headerBytes, 0, 4)
    require(riff == "RIFF") { "WAV 파일이 아닙니다: $riff" }

    // WAVE 검증
    val wave = String(headerBytes, 8, 4)
    require(wave == "WAVE") { "WAVE 형식이 아닙니다: $wave" }

    // fmt 청크
    val audioFormat = buffer.getShort(20)
    val numChannels = buffer.getShort(22)
    val sampleRate = buffer.getInt(24)
    val byteRate = buffer.getInt(28)
    val blockAlign = buffer.getShort(32)
    val bitsPerSample = buffer.getShort(34)

    // data 청크 찾기 (44바이트 고정이 아닐 수 있음)
    // "data" 서브청크 ID를 순차 검색
    var dataOffset = 36
    var dataSize = 0
    // 간단한 경우: 표준 44바이트 헤더
    val dataId = String(headerBytes, 36, 4)
    if (dataId == "data") {
        dataSize = buffer.getInt(40)
        dataOffset = 44
    }
    // TODO: 비표준 청크가 있는 경우 순차 검색 구현

    return WavHeader(
        audioFormat = audioFormat,
        numChannels = numChannels,
        sampleRate = sampleRate,
        byteRate = byteRate,
        blockAlign = blockAlign,
        bitsPerSample = bitsPerSample,
        dataSize = dataSize,
        dataOffset = dataOffset
    )
}
```

### WAV 합치기 핵심 로직

```kotlin
fun mergeWavFiles(inputs: List<InputStream>, output: File) {
    // 1. 모든 파일의 헤더 파싱 + fmt 일치 검증
    val headers = inputs.map { parseWavHeader(it) }
    val first = headers.first()
    headers.drop(1).forEachIndexed { index, h ->
        require(h.sampleRate == first.sampleRate &&
                h.numChannels == first.numChannels &&
                h.bitsPerSample == first.bitsPerSample) {
            "파일 ${index + 1}의 오디오 포맷이 첫 번째 파일과 다릅니다"
        }
    }

    // 2. 총 PCM 데이터 크기 계산
    val totalDataSize = headers.sumOf { it.dataSize.toLong() }

    // 3. 합친 WAV 파일 작성
    output.outputStream().buffered().use { out ->
        // RIFF 헤더
        val headerBuf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.put("RIFF".toByteArray())
        headerBuf.putInt((36 + totalDataSize).toInt())  // ChunkSize
        headerBuf.put("WAVE".toByteArray())
        headerBuf.put("fmt ".toByteArray())
        headerBuf.putInt(16)  // SubChunk1Size (PCM)
        headerBuf.putShort(first.audioFormat)
        headerBuf.putShort(first.numChannels)
        headerBuf.putInt(first.sampleRate)
        headerBuf.putInt(first.byteRate)
        headerBuf.putShort(first.blockAlign)
        headerBuf.putShort(first.bitsPerSample)
        headerBuf.put("data".toByteArray())
        headerBuf.putInt(totalDataSize.toInt())  // SubChunk2Size
        out.write(headerBuf.array())

        // 4. 각 파일의 PCM 데이터 순차 기록
        inputs.forEachIndexed { index, stream ->
            // 헤더 이미 읽었으므로 data 이후 바이트만 복사
            // (parseWavHeader에서 44바이트 이미 소비됨)
            stream.copyTo(out, bufferSize = 8192)
        }
    }
}
```

### SEND_MULTIPLE 오디오 URI 수집

```kotlin
// ShareReceiverActivity.onCreate() 내부
@Suppress("DEPRECATION")
val streamUris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
// API 33+에서는 getParcelableArrayListExtra(key, Uri::class.java) 사용 가능
// 단, minSdk 31이므로 @Suppress("DEPRECATION") 유지
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| getParcelableExtra() | getParcelableExtra(key, class) | API 33 (Android 13) | minSdk 31이므로 deprecated 버전 사용, @Suppress 필요 |
| MediaCodec으로 오디오 변환 합치기 | PCM WAV 단순 concat | (이 프로젝트 결정) | WAV만 지원하되 구현 복잡도 최소화 |

## Open Questions

1. **WAV가 아닌 파일이 섞여 올 때 UX**
   - What we know: REQUIREMENTS.md에서 동일 포맷만 지원으로 결정
   - What's unclear: 사용자에게 어떤 메시지를 보여줄지 (토스트? 다이얼로그?)
   - Recommendation: 토스트로 "WAV 파일만 합치기를 지원합니다" 표시 후 finish()

2. **2GB WAV 파일 크기 제한**
   - What we know: WAV 헤더의 ChunkSize는 4바이트(unsigned) = 최대 ~4GB. 하지만 Java int는 signed 32bit = 최대 ~2GB
   - What's unclear: 실제 사용에서 2GB 이상 합치기가 발생할 가능성
   - Recommendation: 일반 회의 녹음은 1시간 = ~600MB (16bit/44.1kHz/stereo). 2GB 초과는 극히 드물어 int 범위로 충분. 문제 발생 시 에러 처리

3. **InputStream 재사용 불가**
   - What we know: ContentResolver.openInputStream()은 한 번 소비하면 끝. parseWavHeader()에서 헤더를 읽은 뒤 같은 스트림에서 data를 읽어야 함
   - What's unclear: 없음
   - Recommendation: 각 URI에 대해 openInputStream() 한 번만 호출하고 헤더 파싱 + 데이터 복사를 순차적으로 수행. 또는 먼저 전부 로컬에 복사한 뒤 합치기

## Sources

### Primary (HIGH confidence)
- ShareReceiverActivity.kt 소스코드 직접 분석 — 기존 Share Intent 처리 흐름 전체 파악
- TranscriptionTriggerWorker.kt 소스코드 직접 분석 — 파이프라인 진입점 확인
- AndroidManifest.xml 직접 분석 — SEND_MULTIPLE intent-filter 이미 등록 확인
- [WAV Format Spec (soundfile.sapp.org)](http://soundfile.sapp.org/doc/WaveFormat/) — WAV 헤더 구조 44바이트 표준
- [Microsoft RIFF Reference](https://learn.microsoft.com/en-us/windows/win32/xaudio2/resource-interchange-file-format--riff-) — RIFF 청크 공식 문서

### Secondary (MEDIUM confidence)
- [WAV Wikipedia](https://en.wikipedia.org/wiki/WAV) — WAV 파일 일반 정보
- [Android ContentResolver docs](https://developer.android.com/reference/android/content/ContentResolver) — openInputStream() API

## Project Constraints (from CLAUDE.md)

- **언어**: 한국어 응답, 한국어 코드 주석, 한국어 커밋 메시지
- **변수명/함수명**: 영어
- **플랫폼**: Android 네이티브 (Kotlin), minSdk 31, targetSdk 36
- **DI**: Hilt
- **비동기**: Coroutines/Flow
- **백그라운드**: WorkManager
- **GSD Workflow**: 직접 repo 편집 금지 — GSD 명령어 통해 작업

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 기존 프로젝트 스택 그대로 사용, 새 라이브러리 불필요
- Architecture: HIGH - 기존 ShareReceiverActivity 코드 직접 분석 완료, 수정 지점 명확
- Pitfalls: HIGH - WAV 포맷 스펙 문서화 완료, Content URI 수명 문제는 기존 코드에서도 동일 패턴

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (안정적 — 플랫폼 API/WAV 스펙 변경 없음)
