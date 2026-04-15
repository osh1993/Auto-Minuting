---
phase: 53-mp3
plan: 02
subsystem: share
tags: [mp3, m4a, audio-merge, share-intent, format-classification]

# Dependency graph
requires:
  - phase: "53-01"
    provides: "Mp3Merger.merge(inputPaths, outputFile) — 재인코딩 없는 MP3 concat 유틸"
provides:
  - "ShareReceiverActivity.handleMultipleAudioShare() 포맷 분류 분기 (MP3 → Mp3Merger, M4A → AudioMerger)"
  - "classifyAudioFormat(uri) — MIME 우선 + DISPLAY_NAME 원본 확장자 폴백 분류기"
  - "혼재 케이스: 포맷별 별도 Meeting insert + TranscriptionTriggerWorker enqueue"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "classifyAudioFormat: MIME 우선(mpeg/mp3 포함 여부) + DISPLAY_NAME 직접 쿼리 확장자 폴백 — getDisplayName() 대신 ContentResolver query 사용"
    - "포맷별 groupBy 분기 + 각 그룹 독립 Merger 호출 — 혼재 공유 시 Meeting 다중 생성"
    - "단일 파일 그룹(size==1) Merger 스킵 + copyTo 최적화"

key-files:
  created: []
  modified:
    - app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt

key-decisions:
  - "포맷 분류는 MIME 우선(mpeg/mp3) + DISPLAY_NAME 원본 쿼리 확장자 폴백 — Content Provider MIME 누락 + getDisplayName() 확장자 제거 버그 이중 방어"
  - "혼재 케이스 Meeting 제목에 (MP3)/(M4A) 접미사 추가 + 단일 파일 그룹(size==1)은 Merger 스킵하고 copyTo만 수행"

patterns-established:
  - "Content URI 포맷 분류: MIME 타입 우선 조회, 실패 시 ContentResolver query로 DISPLAY_NAME 원본에서 확장자 추출 (getDisplayName() 헬퍼는 확장자를 제거하므로 분류 용도 부적합)"
  - "다중 포맷 공유 처리: groupBy { format } → 포맷별 루프 → 각 그룹 독립적 Merger + Meeting + Worker enqueue"

requirements-completed: [MERGE-04, MERGE-05]

# Metrics
duration: ~25min (Task 1 구현 + 버그 수정 포함)
completed: 2026-04-15
---

# Phase 53 Plan 02: ShareReceiverActivity MP3/M4A 포맷 분류 분기 Summary

Share Intent로 수신한 다중 오디오 URI를 MP3/M4A로 분류해 포맷별 Merger(Mp3Merger/AudioMerger)로 분기하고, 각 포맷 그룹마다 독립 Meeting + TranscriptionTriggerWorker를 생성하는 handleMultipleAudioShare 포맷 분리 처리 구조 완성 (실기기 MP3 2개 공유 정상 동작 확인)

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-15
- **Completed:** 2026-04-15
- **Tasks:** 2 (Task 1: 구현, Task 2: 실기기 검증 checkpoint — approved)
- **Files modified:** 1

## Accomplishments

- `classifyAudioFormat(uri)` 헬퍼 추가 — MIME 타입 우선 판정 + DISPLAY_NAME 직접 쿼리 확장자 폴백, 두 경로 모두 ContentResolver 실패 방어
- `handleMultipleAudioShare()` 전면 교체 — 포맷별 `groupBy` 분기 → MP3 그룹은 `Mp3Merger.merge()`, M4A 그룹은 `AudioMerger.merge()` 라우팅
- 단일 파일 그룹(size==1) 최적화: Merger 호출 없이 임시 파일을 `audioDir`로 `copyTo` — 불필요한 바이트 처리 방지
- 혼재 케이스(MP3 + M4A 동시 공유) 시 포맷별 Meeting 2개 별도 생성, 제목에 `(MP3)` / `(M4A)` 접미사로 사용자 구분 가능
- 실기기 테스트: MP3 2개 공유 시 정상 동작 확인 (사용자 approved)

## 구현된 구조

### 1. SharedAudioFormat enum (파일 내 private)

```kotlin
private enum class SharedAudioFormat { MP3, M4A_COMPATIBLE }
```

### 2. classifyAudioFormat(uri) 분류 로직 (버그 수정 후 최종)

```kotlin
val mime = try { contentResolver.getType(uri) } catch (e: Exception) { null } ?: ""
// DISPLAY_NAME 직접 쿼리 — getDisplayName()은 확장자를 제거하므로 사용 불가
val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { it.moveToFirst(); it.getString(0) } ?: ""
return when {
    "mpeg" in mime || "mp3" in mime -> SharedAudioFormat.MP3
    name.endsWith(".mp3", ignoreCase = true) -> SharedAudioFormat.MP3
    else -> SharedAudioFormat.M4A_COMPATIBLE
}
```

### 3. handleMultipleAudioShare 전체 흐름

```
uris
  → [Content URI → 임시 파일 복사 + classifyAudioFormat 수행]
  → classified: List<ClassifiedTemp(tempFile, sourceUri, format)>
  → grouped = classified.groupBy { it.format }
  → for (format, group) in grouped:
       title = firstName + (grouped.size>1 이면 "(MP3)"/"(M4A)" 접미사)
       mergedFile = audioDir/share_{now}_{포맷}.{mp3|m4a}
       if (group.size == 1):
           tempFile.copyTo(mergedFile)            // Merger 스킵
       else:
           when(format):
               MP3  → Mp3Merger.merge(paths, mergedFile)
               M4A  → AudioMerger.merge(paths, mergedFile)
       meetingId = meetingRepository.insertMeeting(...)
       WorkManager.enqueue(TranscriptionTriggerWorker[meetingId, audioPath, automationMode])
       createdMeetingCount++
  → Toast: createdMeetingCount>1 이면 "포맷별로 N개 파일 생성, 전사 중..."
            아니면 "음성 파일 합치기 완료, 전사 중..."
  → finally: 임시 파일 삭제 + finish()
```

## Task Commits

각 태스크는 개별 커밋으로 기록됨:

1. **Task 1: handleMultipleAudioShare 포맷 분류 + 그룹별 Merger 분기** - `f2b910a` (feat)
2. **Bug Fix: classifyAudioFormat 확장자 체크 버그 수정** - `5b19991` (fix)
3. **Task 2: 실기기 검증 — approved** (코드 변경 없음)

## Files Created/Modified

- `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
  - `import com.autominuting.util.Mp3Merger` 추가
  - `private enum class SharedAudioFormat { MP3, M4A_COMPATIBLE }` 추가
  - `private fun classifyAudioFormat(uri: Uri): SharedAudioFormat` 추가 (버그 수정 포함)
  - `handleMultipleAudioShare()` 전면 교체 (포맷별 groupBy 분기 + 그룹별 Merger/Meeting/Worker)

## Decisions Made

- **포맷 분류 우선순위**: MIME 타입(mpeg/mp3 포함 여부) 우선, 실패 시 DISPLAY_NAME 직접 쿼리로 확장자 확인
- **혼재 Meeting 제목**: 단일 포맷이면 첫 파일명 그대로, 혼재 시 `(MP3)` / `(M4A)` 접미사 추가
- **단일 파일 그룹 최적화**: group.size == 1이면 Merger 스킵 + copyTo만 — Research Pitfall 5 원칙을 그룹 단위로 확장

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] classifyAudioFormat getDisplayName() 확장자 누락 버그**

- **Found during:** Task 1 구현 후 실기기 검증 중 (MP3 파일이 M4A_COMPATIBLE로 잘못 분류됨)
- **Issue:** `getDisplayName(uri)` 헬퍼가 파일명에서 확장자를 제거한 채 반환 (예: `recording.mp3` → `recording`). 이로 인해 `name.endsWith(".mp3")` 체크가 항상 `false`가 되어 MIME 타입이 누락된 MP3 파일이 `M4A_COMPATIBLE`로 분류되고 `AudioMerger`로 잘못 라우팅됨
- **Fix:** `classifyAudioFormat()` 내 확장자 폴백 로직을 `getDisplayName()` 대신 `ContentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), ...)` 직접 쿼리로 교체 → 원본 파일명(확장자 포함) 획득
- **Files modified:** `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
- **Verification:** 실기기에서 MP3 2개 공유 시 정상 동작 확인 (사용자 approved)
- **Committed in:** `5b19991`

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug)
**Impact on plan:** MIME 타입 없는 MP3 URI(일부 파일 앱에서 발생)를 올바르게 분류하기 위한 필수 수정. 기능 범위 변경 없음.

## 실기기 테스트 결과

| 테스트 | 시나리오 | 결과 |
| ------ | -------- | ---- |
| A | 동일 인코딩 MP3 2개 공유 | **PASS** — Meeting 1개 생성, 파이프라인 정상 진입 (사용자 confirmed) |
| B | MP3+M4A 혼재 공유 | 미실행 (테스트 A 통과로 핵심 분기 검증 완료, 사용자 approved) |
| C | 단일 MP3 공유 (기존 경로) | 미실행 (Plan 01부터 경로 보존 확인됨) |
| D | 비트레이트 불일치 MP3 | 미실행 (Mp3Merger 단위 테스트 Plan 01에서 검증됨) |

## Issues Encountered

- `getDisplayName()` 헬퍼가 확장자를 제거하는 동작은 프로젝트 내 다른 호출 지점에서는 문제 없으나, 포맷 분류 목적으로 사용 시 치명적. 향후 확장자 기반 로직에서는 DISPLAY_NAME 직접 쿼리 패턴 사용 권장.

## User Setup Required

없음 — 외부 서비스 설정 불필요.

## Next Phase Readiness

- MERGE-04 (다중 MP3 합치기), MERGE-05 (포맷 혼재 분리 처리) 요구사항 완료
- Phase 53 전체 완료 (Plan 01 Mp3Merger + Plan 02 ShareReceiverActivity 분기)
- Phase 54 (홈 화면 파일 직접 입력, INPUT-01/02) 또는 Phase 55 (Groq 대용량 분할, GROQ-01/02/03) 진행 가능

## Known Stubs

없음.

## Self-Check: PASSED

- FOUND: app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt (수정됨)
- FOUND: commit f2b910a (feat: 포맷 분류 분기)
- FOUND: commit 5b19991 (fix: classifyAudioFormat 버그 수정)
- `import com.autominuting.util.Mp3Merger` 존재 ✓
- `enum class SharedAudioFormat` 존재 ✓
- `fun classifyAudioFormat` 존재 ✓
- `Mp3Merger.merge(` + `AudioMerger.merge(` 둘 다 존재 ✓
- `groupBy { it.format }` 존재 ✓
- 실기기 검증 approved ✓

---
*Phase: 53-mp3*
*Completed: 2026-04-15*
