# Phase 53: MP3 파일 합치기 지원 - Research

**Researched:** 2026-04-15
**Domain:** Android Share Intent 다중 MP3 파일 수신 + 재인코딩 없는 MP3 바이너리 연결 + 포맷 분리 처리
**Confidence:** HIGH

## Summary

Phase 53은 Phase 50(다중 M4A 합치기)의 병렬 확장이다. 현재 `ShareReceiverActivity.handleMultipleAudioShare()`는 모든 `audio/*` MIME 파일을 `AudioMerger`(MediaExtractor + MediaMuxer MP4 컨테이너)로 합치려 시도한다. 그러나 **MediaMuxer는 MP3 출력 포맷을 지원하지 않는다** (MP4/WebM/3GP/OGG/HEIF만 지원). 따라서 MP3 파일을 MediaMuxer로 출력 컨테이너에 담는 경로는 불가능하며, MP3 합치기는 별도 전략이 필요하다.

다행히 **MP3 포맷은 프레임 단위로 self-contained**하기 때문에 단순 바이너리 이어붙이기(ID3 태그 스트립 후 프레임 연결)만으로 재인코딩 없이 합칠 수 있다. 동일 비트레이트/샘플레이트/채널 레이아웃이 전제 조건이며, 첫 파일의 ID3v2 태그만 남기고 이후 파일들의 ID3 태그는 제거하는 것이 안전하다.

M4A + MP3 혼재 케이스(MERGE-05)는 포맷 변환 없이 **포맷별로 분리 처리**한다 (CLAUDE 제약 "MP3 → M4A 자동 변환 — 포맷 변환 없이 포맷별 처리"). 즉 MP3 파일들은 MP3로 합쳐 하나의 Meeting으로, M4A 파일들은 M4A로 합쳐 또 다른 Meeting으로 각각 파이프라인에 진입시킨다.

**Primary recommendation:** `util/Mp3Merger.kt` 신규 유틸리티를 만들어 ID3v2 태그 스트립 + MP3 프레임 바이너리 연결을 구현하고, `ShareReceiverActivity.handleMultipleAudioShare()`를 포맷별로 파일을 분류(MIME 또는 확장자 기반)한 뒤 MP3 그룹은 `Mp3Merger`로, M4A/기타 그룹은 기존 `AudioMerger`로 각각 처리하도록 리팩터링한다. 단일 MP3 공유(파일 1개)는 기존 `processSharedAudio()` 경로를 그대로 사용한다.

<user_constraints>
## User Constraints (from CLAUDE.md + REQUIREMENTS.md)

### Locked Decisions
- **MP3 → M4A 자동 변환 금지**: 포맷 변환 없이 포맷별로 분리 처리한다 (REQUIREMENTS.md "Out of Scope")
- **재인코딩 없이 합칠 것**: MERGE-04는 "재인코딩 없이"를 명시 — 디코딩→재인코딩 경로 금지
- **병렬 구현**: Phase 51/52와 독립적으로 진행 가능 (Gemini 키 관리와 무관)
- **기존 WavMerger/AudioMerger 패턴과 병렬**: util/ 디렉토리에 별도 object로 분리, ShareReceiverActivity와 관심사 분리

### Claude's Discretion
- MP3 프레임 파서 구현 방식 (자체 구현 vs 외부 라이브러리)
- ID3 태그 처리 세부 정책 (모두 제거 vs 첫 파일만 유지)
- 포맷 분류 기준 (Content-Type MIME vs 파일 확장자 vs magic byte)
- 포맷 혼재 시 두 Meeting을 동시 생성할지 순차 생성할지

### Deferred Ideas (OUT OF SCOPE)
- MP3 → M4A 자동 변환
- 다른 포맷(OGG/FLAC/WAV) 합치기 확장
- 비트레이트/샘플레이트가 다른 MP3 파일들의 정규화
- 사용자에게 포맷 혼재 시 합치기 vs 분리 선택지 제공
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MERGE-04 | Share Intent로 여러 MP3 파일 공유 시 앱이 자동으로 하나의 MP3로 합쳐 처리 (재인코딩 없이) | Mp3Merger 유틸: ID3v2 태그 스트립 + MP3 프레임 바이너리 concat, MediaMuxer 미사용 |
| MERGE-05 | M4A + MP3 혼재 공유 시 포맷별 분리 로직 | ShareReceiverActivity.handleMultipleAudioShare()에서 MIME/확장자로 그룹핑 후 그룹별 Merger 호출 |
</phase_requirements>

## Standard Stack

### Core (기존 재사용)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.3.20 | 메인 언어 | 프로젝트 표준 |
| Android Platform API | API 31+ | MIME 감지, ContentResolver | Min SDK 일치 |
| WorkManager | 2.11.1 | TranscriptionTriggerWorker | 기존 STT 파이프라인 진입점 |
| Room | 2.8.4 | Meeting 엔티티 저장 | 기존 스키마 그대로 |

### 신규 추가 (없음)
외부 라이브러리 추가 없음 — 순수 Kotlin + `java.io.*`로 MP3 바이너리 처리 가능.

### Alternatives Considered
| Instead of | Could Use | Why Not |
|------------|-----------|---------|
| 자체 MP3 파서 | JAudioTagger / mp3agic | APK 크기 증가(~200KB+), 프로젝트의 "Don't Hand-Roll" 원칙보다 단순 bye-concat 특성이 강함 |
| MediaMuxer MP3 출력 | — | **불가능** — MediaMuxer는 MP3 output container 미지원 (MP4/WebM/3GP/OGG/HEIF만 가능) |
| MediaExtractor + MediaCodec 디코드/인코드 | — | "재인코딩 없이" 요구사항 위반 |
| FFmpeg Mobile | mobile-ffmpeg / ffmpeg-kit | APK 크기 +20~40MB, 과도한 의존성. MP3 concat은 단순 bye-level 작업 |

**Installation:** 추가 설치 불필요.

**Version verification (이미 사용 중):**
- androidx.work:work-runtime-ktx:2.11.1 (app/build.gradle.kts 확인 필요)
- Android MediaMuxer/MediaExtractor: Platform API 18+ — 기존 AudioMerger에서 이미 사용

## Architecture Patterns

### 기존 코드 구조 분석

**ShareReceiverActivity.handleMultipleAudioShare()** (수정 대상):
- 위치: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` (라인 415-491)
- 현재 흐름:
  1. URI 리스트 → 임시 파일(cacheDir/merge_temp/)로 복사
  2. `AudioMerger.merge(paths, merged.m4a)` — 항상 MP4 컨테이너로 합침
  3. Meeting(AUDIO_RECEIVED) insert → TranscriptionTriggerWorker enqueue
  4. finally에서 임시 파일 정리, finish()
- **문제점**:
  - MP3 입력을 M4A(MP4 컨테이너)로 muxing하려 하면 MediaMuxer가 오류를 반환하거나 잘못된 출력 생성
  - 혼재 케이스에서 첫 번째 파일 포맷 기준으로 출력 컨테이너가 결정되어 뒷 파일들이 손실/손상됨

**AudioMerger** (재사용, 수정 없음):
- 위치: `app/src/main/java/com/autominuting/util/AudioMerger.kt`
- MediaExtractor + MediaMuxer(MUXER_OUTPUT_MPEG_4) 기반
- M4A/AAC/WAV(MP4 컨테이너에 담길 수 있는 코덱) 전용으로 사용
- MP3는 MP4 컨테이너에 담길 수 있으나 Android MediaMuxer 자체가 MP3 코덱을 MP4 컨테이너에 muxing하는 것을 공식 지원하지 않음 (AAC, AMR-NB/WB만 공식 지원)

**WavMerger** (패턴 참고):
- 위치: `app/src/main/java/com/autominuting/util/WavMerger.kt`
- object 선언 + `merge(inputStreams, outputFile)` 시그니처
- 헤더 파싱 → fmt 일치 검증 → 헤더 재작성 → PCM 데이터 concat
- **Mp3Merger의 설계 템플릿으로 삼기 적합**

### 추천 프로젝트 구조

```
app/src/main/java/com/autominuting/
├── presentation/share/
│   └── ShareReceiverActivity.kt    # handleMultipleAudioShare() 포맷 분류 분기 추가
└── util/
    ├── WavMerger.kt                # 기존
    ├── AudioMerger.kt              # 기존 (M4A 전용으로 용도 재한정)
    └── Mp3Merger.kt                # 신규 — MP3 ID3 스트립 + 프레임 concat
```

### Pattern 1: MP3 파일 구조

**What:** MP3 파일 = 선택적 ID3v2 헤더 + MP3 프레임 시퀀스(+ 선택적 ID3v1 트레일러 128 바이트)

**MP3 파일 레이아웃:**
```
[ID3v2 header — "ID3" magic + synchsafe size (10 bytes + payload)]  ← 선택적
[MPEG audio frame 1]
[MPEG audio frame 2]
...
[MPEG audio frame N]
[ID3v1 tag — "TAG" magic + 125 bytes]                               ← 선택적, 파일 끝
```

- **ID3v2 헤더**: 파일 첫 10바이트의 ["ID3", version(2), flags(1), size(4 synchsafe)]. size는 ID3 payload 크기로, synchsafe 정수(각 바이트 MSB=0)로 인코딩.
- **MP3 프레임**: 각 프레임은 4바이트 프레임 헤더로 시작 (11비트 frame sync `0xFFE`). 각 프레임이 독립적으로 디코딩 가능 — 이것이 단순 바이너리 concat이 가능한 이유.
- **ID3v1 트레일러**: 파일 끝 128바이트, "TAG"로 시작. 있으면 제거 후 concat 권장.

**Pattern (의사코드):**
```kotlin
object Mp3Merger {
    fun merge(inputPaths: List<String>, outputFile: File) {
        outputFile.outputStream().buffered().use { out ->
            inputPaths.forEachIndexed { index, path ->
                val bytes = File(path).readBytes()
                val (audioStart, audioEnd) = stripTags(bytes)
                if (index == 0) {
                    // 첫 파일은 ID3v2 태그 유지 (플레이어가 메타데이터 읽을 수 있도록)
                    out.write(bytes, 0, audioEnd)
                } else {
                    // 이후 파일은 ID3 태그 스트립, 오디오 프레임만
                    out.write(bytes, audioStart, audioEnd - audioStart)
                }
            }
        }
    }

    private fun stripTags(bytes: ByteArray): Pair<Int, Int> {
        var start = 0
        var end = bytes.size

        // ID3v2 헤더 감지 + skip
        if (bytes.size > 10 && bytes[0] == 'I'.code.toByte()
            && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val size = synchsafeToInt(bytes, 6)  // bytes[6..9]
            start = 10 + size
        }

        // ID3v1 트레일러 감지 + skip
        if (bytes.size >= 128
            && bytes[bytes.size - 128] == 'T'.code.toByte()
            && bytes[bytes.size - 127] == 'A'.code.toByte()
            && bytes[bytes.size - 126] == 'G'.code.toByte()) {
            end = bytes.size - 128
        }

        return start to end
    }

    // synchsafe: 각 바이트의 MSB=0, 7비트씩 유효
    private fun synchsafeToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
               ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
               ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
               (bytes[offset + 3].toInt() and 0x7F)
    }
}
```

### Pattern 2: 포맷별 분리 처리 (MERGE-05)

**What:** `handleMultipleAudioShare()`를 MIME 또는 확장자 기반으로 그룹핑하여 각 그룹을 적절한 Merger로 처리

**When to use:** 2개 이상의 오디오 파일이 공유되고 포맷이 혼재할 수 있는 경우

**Pattern (의사코드):**
```kotlin
private fun handleMultipleAudioShare(uris: List<Uri>) {
    lifecycleScope.launch {
        try {
            // Content URI → 임시 파일 복사 (기존 로직 유지)
            val tempFiles: List<TempAudio> = copyToCacheWithFormat(uris)
            //  TempAudio(file: File, format: AudioFormat)  // MP3 | M4A_COMPATIBLE

            // 포맷별 그룹핑
            val grouped = tempFiles.groupBy { it.format }

            // 각 그룹을 별도 Meeting으로 처리
            grouped.forEach { (format, group) ->
                val merged = when (format) {
                    AudioFormat.MP3 -> mergeMp3Group(group)
                    AudioFormat.M4A_COMPATIBLE -> mergeM4aGroup(group)
                }
                createMeetingAndEnqueue(merged, firstName = getDisplayName(group.first().sourceUri))
            }
        } finally {
            cleanup(tempFiles)
            finish()
        }
    }
}
```

### Pattern 3: 포맷 감지 (MIME + 확장자 폴백)

**What:** MIME 타입으로 먼저 감지, 실패 시 파일 확장자로 폴백

```kotlin
private fun detectFormat(uri: Uri, displayName: String?): AudioFormat {
    val mime = try { contentResolver.getType(uri) } catch (e: Exception) { null }
    return when {
        mime?.contains("mpeg") == true || mime?.contains("mp3") == true -> AudioFormat.MP3
        displayName?.endsWith(".mp3", ignoreCase = true) == true -> AudioFormat.MP3
        else -> AudioFormat.M4A_COMPATIBLE  // m4a, mp4, aac, wav 등
    }
}
```

### Anti-Patterns to Avoid

- **MediaMuxer로 MP3 output 시도**: `MUXER_OUTPUT_MPEG_4`에 MP3 트랙을 `addTrack`하면 `IllegalStateException` 가능. `MUXER_OUTPUT_MP3` 상수 자체가 없음.
- **ID3v2 헤더를 남긴 채 N개 파일 concat**: 중간 ID3 헤더가 MP3 프레임 sync와 충돌하여 일부 플레이어에서 재생 중단 또는 잘못된 duration 표시.
- **synchsafe 정수를 일반 big-endian int로 읽기**: ID3v2 size는 각 바이트 MSB=0인 7비트 인코딩 — 일반 readInt는 잘못된 크기 반환.
- **혼재 파일들을 단일 Meeting으로 강제 합치기**: 포맷 변환 없이는 불가능하고, "Out of Scope" 제약 위반.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MP3 태그 읽기(복잡 메타데이터) | 전체 ID3v2 프레임 파서 | ID3v2 **size만** 읽어 skip하는 최소 로직 | 이 phase는 메타데이터 보존이 아닌 "태그 경계 식별"만 필요 |
| MP3 bitrate/samplerate 변환 | 자체 MP3 디코더 | **시도하지 말 것** — "재인코딩 금지" | 다른 포맷의 파일은 분리 처리 (MERGE-05) |
| 컨테이너 muxing | MediaMuxer를 MP3에 억지 적용 | 평문 바이너리 concat | MP3는 프레임 self-contained — 컨테이너 불필요 |
| 포맷 감지 | magic byte 자체 파서 | `ContentResolver.getType()` + 확장자 폴백 | 기존 코드 패턴과 일관성 (ShareReceiverActivity 라인 127-134) |

**Key insight:** MP3는 프레임 단위로 독립 디코딩 가능한 "feature"를 활용하면 바이트 단위 concat이라는 매우 단순한 해법이 가능하다. 컨테이너(MP4/OGG)에 담는 포맷들(M4A/WAV)과 근본적으로 다른 전략이 필요하다.

## Runtime State Inventory

이 phase는 **신규 기능 추가**이며 rename/refactor 아님.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — 기존 Meeting 스키마(audioFilePath, source) 그대로 사용 | 없음 |
| Live service config | None | 없음 |
| OS-registered state | None — AndroidManifest.xml intent-filter는 Phase 9/50에서 이미 `*/*` 허용 | 없음 |
| Secrets/env vars | None | 없음 |
| Build artifacts | None — 외부 의존성 추가 없음 | 없음 |

## Common Pitfalls

### Pitfall 1: 첫 번째 파일 MIME만 믿고 분류하기
**What goes wrong:** 기존 `handleMultipleAudioShare` 진입 조건 `allAudio`는 모든 파일이 `audio/*`인지만 확인 — 서브타입 혼재는 감지 못함
**Why it happens:** `audio/mpeg` + `audio/mp4` 조합도 `audio/*`로 통과함
**How to avoid:** 각 URI마다 서브타입(MP3 vs M4A/MP4) 개별 판정하여 그룹핑
**Warning signs:** AudioMerger가 "오디오 트랙을 찾을 수 없습니다" 또는 MediaMuxer stop() 시 illegal state

### Pitfall 2: Content URI 수명 관리
**What goes wrong:** Activity finish() 후 content URI 접근 실패
**Why it happens:** 공유한 앱이 권한 revoke
**How to avoid:** **이미 기존 코드가 처리함** — cacheDir/merge_temp/로 먼저 복사한 뒤 절대 경로로 작업 (ShareReceiverActivity 라인 425-434 참조). 새 Mp3Merger도 이 임시 파일 경로 기반으로 호출.
**Warning signs:** "URI를 열 수 없습니다" IOException

### Pitfall 3: VBR MP3의 Xing/VBRI 헤더 처리
**What goes wrong:** VBR MP3의 첫 프레임은 실제 오디오가 아닌 Xing/Info 헤더(총 프레임 수, TOC). concat 시 첫 파일의 Xing 헤더가 남아 총 길이가 잘못 계산됨
**Why it happens:** VBR 인코더가 넣은 "ghost frame"이 MP3 frame sync로 인식되지만 실제 오디오 아님
**How to avoid:**
- 완벽한 해결: Xing 헤더 감지 후 제거 또는 재계산 (복잡)
- **실용적 해결**: 첫 파일의 Xing 헤더는 유지, 이후 파일들의 Xing 프레임만 skip. 또는 Xing 헤더 존재 시 무시하고 concat — 대부분의 플레이어는 duration을 재계산함.
- STT 엔진은 duration 메타데이터 대신 실제 오디오 데이터를 읽으므로 Xing 헤더 불일치가 전사 결과에 영향 없음 (전사 정확도만 보장되면 됨)
**Warning signs:** 합쳐진 파일의 재생 시간이 실제보다 짧게 표시되거나 일부 플레이어에서 seek 실패

### Pitfall 4: 비트레이트/샘플레이트가 다른 MP3 연결
**What goes wrong:** 32kbps 모노와 128kbps 스테레오를 concat하면 재생 시 속도/피치 오류
**Why it happens:** 플레이어는 첫 프레임 헤더 기준으로 디코딩 상태 초기화, 도중 포맷 변경을 감지 못할 수 있음
**How to avoid:**
- 첫 프레임 헤더 파싱 → 4바이트 frame header에서 bitrate/samplerate/channel 추출 → 일치 검증 → 불일치 시 예외 (WavMerger의 fmt 검증 패턴 그대로 차용)
- 또는 경고만 로깅하고 진행 (Phase 53 스코프는 "합치기 성공" 우선)
**Warning signs:** Toast "재생 속도 이상" 사용자 피드백

### Pitfall 5: SEND_MULTIPLE 단일 파일 케이스
**What goes wrong:** 단일 MP3를 `ACTION_SEND_MULTIPLE`로 공유했을 때 합치기 로직으로 들어가면 불필요한 복사/merge
**Why it happens:** 일부 런처/앱이 파일 1개만 있어도 SEND_MULTIPLE 사용
**How to avoid:** **이미 기존 코드가 처리함** — ShareReceiverActivity 라인 161-183에서 `size == 1`이면 `processSharedAudio()`로 위임. 이 로직은 MP3에도 그대로 적용되므로 Success Criteria 4번("단일 MP3 공유는 기존과 동일") 자동 충족.

### Pitfall 6: ID3v2 extended header / footer
**What goes wrong:** 드문 케이스지만 ID3v2.4 extended header 또는 footer가 있으면 `size` 필드만으로는 실제 태그 끝을 못 찾음
**Why it happens:** ID3v2.4 spec의 선택적 footer (추가 10바이트)
**How to avoid:**
- 실용적: size 필드만 신뢰 (99% 케이스 커버)
- 완벽: ID3v2 header flag 바이트의 footer 비트 체크 (bit 4)
- MP3 파일의 ID3v2.4 footer 사용은 매우 드물어 실용적 접근으로 충분

## Code Examples

### MP3 프레임 헤더 파싱 (선택 검증용)
```kotlin
// MP3 frame header: 4 bytes
// AAAA AAAA AAAB BCCD EEEE FFGH IIJJ KLMM
// A=sync(11), B=version(2), C=layer(2), D=CRC(1)
// E=bitrate(4), F=samplerate(2), G=padding(1), H=private(1)
// I=channel mode(2), J=mode ext(2), K=copyright(1), L=original(1), M=emphasis(2)

data class Mp3FrameInfo(val version: Int, val layer: Int, val bitrateIdx: Int, val sampleRateIdx: Int, val channelMode: Int)

fun parseFrameHeader(bytes: ByteArray, offset: Int): Mp3FrameInfo? {
    if (offset + 4 > bytes.size) return null
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    val b2 = bytes[offset + 2].toInt() and 0xFF
    val b3 = bytes[offset + 3].toInt() and 0xFF
    // frame sync check: b0=0xFF, b1 상위 3비트=0b111
    if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
    return Mp3FrameInfo(
        version = (b1 shr 3) and 0x03,
        layer = (b1 shr 1) and 0x03,
        bitrateIdx = (b2 shr 4) and 0x0F,
        sampleRateIdx = (b2 shr 2) and 0x03,
        channelMode = (b3 shr 6) and 0x03
    )
}
```

### 포맷 분류 헬퍼
```kotlin
enum class SharedAudioFormat { MP3, M4A_COMPATIBLE }

private fun classify(uri: Uri): SharedAudioFormat {
    val mime = try { contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
    val name = getDisplayName(uri) ?: ""
    return when {
        "mpeg" in mime || "mp3" in mime -> SharedAudioFormat.MP3
        name.endsWith(".mp3", ignoreCase = true) -> SharedAudioFormat.MP3
        else -> SharedAudioFormat.M4A_COMPATIBLE
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 모든 오디오를 MediaMuxer로 muxing (Phase 50) | MP3는 바이너리 concat, M4A는 MediaMuxer (Phase 53) | 2026-04 | MP3 지원 추가 + 포맷별 올바른 처리 |
| `isAudioShare`가 단일 URI만 검사 | SEND_MULTIPLE 다중 URI 그룹핑 (Phase 50에서 이미 적용) | 2026-04-05 | — |

**Deprecated/outdated:** 없음 — 기존 WavMerger/AudioMerger는 각자 역할 유지.

## Open Questions

1. **혼재 케이스에서 두 Meeting 동시 생성 시 사용자 UX**
   - What we know: 현재 파이프라인은 단일 Meeting → 단일 회의록 생성
   - What's unclear: MP3 그룹 + M4A 그룹이 각각 Meeting 생성되면 사용자가 혼란스러울 수 있음 — 제목이 둘 다 "첫 파일명"일 때 구분 어려움
   - Recommendation: Meeting title에 `(MP3)` / `(M4A)` 접미사 추가, 또는 Toast 메시지에 "2개 파이프라인 시작" 명시. 구체 UX는 Plan 작성 시 결정.

2. **VBR MP3 Xing 헤더의 실제 비중**
   - What we know: 스마트폰 녹음기의 MP3 출력은 대부분 CBR (일정 비트레이트)
   - What's unclear: 사용자가 외부 음원 등을 공유하는 케이스에서 VBR 출현 빈도
   - Recommendation: MVP는 Xing 헤더 처리 생략, 실제 버그 리포트 시 추가 처리 고려

3. **MP3 비트레이트 불일치 시 처리 정책**
   - What we know: 프레임 헤더로 검증 가능
   - What's unclear: 사용자에게 에러로 돌려줄지, 경고 Toast 후 진행할지
   - Recommendation: Plan 작성 시 WavMerger 패턴 차용 — 불일치 시 IllegalArgumentException throw, UI에서 Toast "비트레이트가 다른 파일들은 합칠 수 없습니다" 표시

## Environment Availability

이 phase는 code-only 변경이며 외부 도구/서비스 의존성 없음. (JDK 17, Android SDK, Gradle — 기존 빌드 환경 그대로)

## Sources

### Primary (HIGH confidence)
- [Android MediaMuxer API reference](https://developer.android.com/reference/android/media/MediaMuxer.OutputFormat) — MP3 output 미지원 확인
- `app/src/main/java/com/autominuting/util/AudioMerger.kt` — 기존 M4A merger 구현
- `app/src/main/java/com/autominuting/util/WavMerger.kt` — object 패턴 + fmt 검증 참고
- `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` — 다중 오디오 공유 진입 경로 (라인 139-185, 415-491)
- `.planning/phases/50-audio-merge/50-RESEARCH.md` — Phase 50 설계 문서 (패턴 재사용)

### Secondary (MEDIUM confidence)
- [mp3binder: MP3 바이너리 concat 도구 (Go)](https://github.com/crra/mp3binder) — ID3 스트립 + 프레임 concat 접근법 검증
- [MP3 losslessly concatenation guide](https://lyncd.com/2009/02/how-to-merge-mp3-files/) — 실무 관행 확인
- [SkyScribe: Merge MP3 Without Re-encoding](https://www.sky-scribe.com/en/blog/merge-mp3-without-reencoding) — 비트레이트 일치 요구 확인
- ID3v2 spec (synchsafe integer, tag layout) — 공개 포맷 스펙, 훈련 데이터 + 다수 소스 교차 확인

### Tertiary (LOW confidence)
- Android 특정 MP3 플레이어의 VBR Xing 헤더 취급 — 플레이어마다 다름, 실측 필요

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 외부 의존성 추가 없음, 기존 패턴 재사용
- Architecture: HIGH — Phase 50 완료 코드가 직접 확장 대상, 분기 위치 명확
- Pitfalls: MEDIUM — VBR 헤더 / 비트레이트 불일치는 실제 테스트 필요
- MP3 포맷 지식: HIGH — 표준 포맷, 다수 소스로 교차 검증됨
- MediaMuxer MP3 미지원 확인: HIGH — 공식 문서 명시

**Research date:** 2026-04-15
**Valid until:** 2026-05-15 (Android Platform API 안정, MP3 포맷은 변하지 않음)
