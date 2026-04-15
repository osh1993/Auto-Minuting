---
phase: 53-mp3
plan: 01
subsystem: util
tags: [mp3, merge, audio, utility]
requires: []
provides:
  - "com.autominuting.util.Mp3Merger.merge(inputPaths: List<String>, outputFile: File)"
  - "com.autominuting.util.Mp3FrameInfo"
affects: []
tech_stack:
  added: []
  patterns:
    - "object singleton + data class FrameInfo"
    - "순수 JVM 유닛 테스트 (Android Framework 의존성 없음)"
key_files:
  created:
    - app/src/main/java/com/autominuting/util/Mp3Merger.kt
    - app/src/test/java/com/autominuting/util/Mp3MergerTest.kt
  modified: []
decisions:
  - "JUnit 5 (@TempDir) 사용 — 프로젝트 컨벤션 준수 (plan의 JUnit 4 예시를 WavMergerTest 패턴으로 전환)"
  - "전체 파일을 메모리에 올려 처리 — ID3v1 트레일러를 파일 끝에서 감지해야 하므로 스트리밍보다 단순"
  - "첫 파일 ID3v2만 유지 — 플레이어 메타데이터 호환성 확보"
metrics:
  duration: "~8 min"
  tasks_completed: 1
  files_created: 2
  tests_added: 6
  completed_date: "2026-04-15"
---

# Phase 53 Plan 01: Mp3Merger 유틸 신규 생성 Summary

**One-liner:** MP3 프레임 self-contained 특성을 활용해 ID3v2/ID3v1 태그를 스트립하고 바이트 단위 concat으로 재인코딩 없이 MP3 파일을 합치는 순수 JVM 유틸리티 (MediaMuxer/MediaCodec 미사용).

## Public API

```kotlin
object Mp3Merger {
    /**
     * @throws IllegalArgumentException MP3 frame sync 없거나 bitrate/sampleRate/channel 불일치
     * @throws java.io.IOException 파일 I/O 실패
     */
    fun merge(inputPaths: List<String>, outputFile: File)
}

data class Mp3FrameInfo(
    val mpegVersion: Int,
    val layer: Int,
    val bitrateIdx: Int,
    val sampleRateIdx: Int,
    val channelMode: Int
)
```

## 검증된 태그 처리 정책

| 입력 파일 위치   | ID3v2 (앞) | ID3v1 (뒤, 128B) |
| ---------------- | ---------- | ---------------- |
| 1번 파일 (첫)    | **유지**   | 제거             |
| 2번 이후 파일    | 제거       | 제거             |

- ID3v2 size 필드는 **synchsafe** 32-bit (`synchsafeToInt`)로 파싱 (각 바이트 7비트 유효)
- ID3v2 footer flag (bytes[5] bit 4)가 켜져 있으면 +10 바이트 추가 스트립
- ID3v1 트레일러는 파일 끝 128바이트 시작이 `"TAG"` 매직이면 제거

## Frame Header 일치 검증 규칙

첫 파일의 첫 frame header와 나머지 파일들이 **모두 다음 5개 필드 일치**해야 함:

| 필드           | 비트 위치       | 의미                              |
| -------------- | --------------- | --------------------------------- |
| mpegVersion    | byte1[4:3]      | MPEG1/2/2.5                       |
| layer          | byte1[2:1]      | Layer1/2/3                        |
| bitrateIdx     | byte2[7:4]      | 비트레이트 인덱스 (0..15)         |
| sampleRateIdx  | byte2[3:2]      | 샘플레이트 인덱스 (44.1/48/32kHz) |
| channelMode    | byte3[7:6]      | Stereo/JointStereo/Dual/Mono      |

불일치 시 `IllegalArgumentException(파일 N의 MP3 포맷이 첫 번째 파일과 다릅니다 ...)`.

## Plan 02 호출 예시

```kotlin
// ShareReceiverActivity에서 다중 MP3 공유 시
import com.autominuting.util.Mp3Merger

val mp3Paths: List<String> = sharedMp3Uris.map { copyUriToTempFile(it).absolutePath }
val outputFile = File(cacheDir, "merged_${System.currentTimeMillis()}.mp3")

try {
    Mp3Merger.merge(mp3Paths, outputFile)
    // outputFile을 기존 STT → 회의록 파이프라인에 투입
} catch (e: IllegalArgumentException) {
    Toast.makeText(this, "MP3 합치기 실패: ${e.message}", Toast.LENGTH_LONG).show()
}
```

## Verification Results

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 23s
com.autominuting.util.Mp3MergerTest: tests=6 failures=0 errors=0 skipped=0
```

6개 테스트 모두 통과:
1. 첫 파일의 ID3v2 태그는 유지되고 두 번째 파일의 ID3v2는 제거된다
2. 모든 파일의 ID3v1 트레일러는 제거된다
3. 태그가 없는 순수 frame 파일들의 concat 크기는 입력 합과 같다
4. sampleRate가 다른 파일들은 IllegalArgumentException
5. 빈 리스트는 IllegalArgumentException
6. frame sync가 없는 파일은 IllegalArgumentException

추가 검증:
- `Mp3Merger.kt`에 `import android.*` 미존재 → 순수 JVM 확인
- WavMerger.kt 패턴과 `object` 선언 + `merge()` 시그니처 일관성 유지

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JUnit 4 → JUnit 5 테스트 전환**
- **Found during:** Task 1 RED 작성 중
- **Issue:** PLAN이 제공한 테스트 예시가 JUnit 4 API(`@Rule`, `TemporaryFolder`, `org.junit.Assert.*`) 기반이었으나, `app/build.gradle.kts`와 `libs.versions.toml`은 JUnit 5만 설정됨 (`useJUnitPlatform()`, `junit5-api 5.11.4`). JUnit 4 import는 컴파일 실패.
- **Fix:** 기존 `WavMergerTest.kt`의 JUnit 5 패턴(`@TempDir lateinit var tempDir: File`, `org.junit.jupiter.api.*`)을 그대로 채용. 테스트 본문 로직과 크기 검증 assertion은 PLAN 원안 유지.
- **Files modified:** `app/src/test/java/com/autominuting/util/Mp3MergerTest.kt`
- **Commit:** `308682e` (RED)

나머지 구현은 PLAN과 완전히 일치.

## Known Stubs

없음 — Mp3Merger는 독립 유틸이며 모든 분기가 구현됨.

## Commits

- `308682e`: test(53-01): Mp3Merger 단위 테스트 RED — 6개 테스트 추가
- `d17f64a`: feat(53-01): Mp3Merger 유틸 구현 — 재인코딩 없는 MP3 concat

## Self-Check: PASSED

- FOUND: app/src/main/java/com/autominuting/util/Mp3Merger.kt
- FOUND: app/src/test/java/com/autominuting/util/Mp3MergerTest.kt
- FOUND: commit 308682e
- FOUND: commit d17f64a
- `object Mp3Merger` ✓
- `fun merge(inputPaths: List<String>, outputFile: File)` ✓
- `data class Mp3FrameInfo` ✓
- `synchsafeToInt` ✓
- `parseFrameHeader` ✓
- `@Test` count = 6 ✓
- `import android.*` 없음 ✓
- BUILD SUCCESSFUL ✓
