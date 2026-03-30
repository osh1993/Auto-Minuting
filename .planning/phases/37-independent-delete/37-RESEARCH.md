# Phase 37: 전사-회의록 독립 삭제/재생성 - Research

**Researched:** 2026-03-30
**Domain:** Room DB FK 동작 + ViewModel 삭제/재생성 로직
**Confidence:** HIGH

## Summary

Phase 36에서 Minutes 테이블이 Meeting과 분리되고 FK SET_NULL이 설정된 결과, Phase 37의 3개 요구사항(IND-01, IND-02, IND-03)의 **데이터 레이어 로직은 이미 대부분 구현 완료**된 상태다. 현재 코드를 정밀 분석한 결과, 실제 "독립 삭제" 동작은 이미 올바르게 작동하나 **UI 텍스트의 오해 소지**와 **전사 삭제 후 pipelineStatus 표시 불일치** 등 프레젠테이션 레이어의 마무리 작업이 필요하다.

핵심적으로 남은 작업은: (1) 전사 삭제 확인 다이얼로그의 오해 유발 텍스트 수정, (2) 회의록 재생성 다이얼로그의 "기존 삭제" 문구를 "추가"로 변경, (3) 전사 삭제 후 회의록 목록에서 orphan minutes(meetingId=NULL)가 출처 정보 없이 표시되는 UX 문제 대응, (4) "모든 회의록 삭제 후 전사에서 다시 회의록 생성 가능" 시나리오의 검증이다.

**Primary recommendation:** 데이터 레이어 변경 없이 UI/UX 보정 + 통합 시나리오 검증만으로 Phase 37을 완료할 수 있다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IND-01 | 전사 파일을 삭제해도 연결된 회의록 파일은 삭제되지 않는다 | FK SET_NULL + MeetingRepositoryImpl.deleteMeeting()이 audio/transcript만 삭제 -- 이미 구현됨. UI 다이얼로그 텍스트 확인 필요 |
| IND-02 | 회의록을 삭제해도 전사 파일과 Meeting 상태는 변경되지 않는다 | MinutesDataRepositoryImpl.deleteMinutes()가 Minutes Row+파일만 삭제 -- 이미 구현됨. Meeting 상태 변경 없음 확인됨 |
| IND-03 | 전사 파일로 회의록을 재생성하면 기존 회의록은 그대로 유지되고 새 회의록이 추가된다 | MinutesGenerationWorker가 minutesDao.insert()로 새 Row INSERT -- 이미 구현됨. 재생성 다이얼로그 텍스트 수정 필요 |
</phase_requirements>

## Architecture Patterns

### 현재 삭제 흐름 분석

#### 전사 삭제 (IND-01 관련)

```
TranscriptsScreen → AlertDialog("전사 파일 삭제", "회의록은 보존됩니다")
  → TranscriptsViewModel.deleteTranscript(meetingId)
    → MeetingRepository.deleteMeeting(meetingId)
      → MeetingRepositoryImpl:
        1. meetingDao.getMeetingByIdOnce(id) -- entity 조회
        2. File(audioFilePath).delete() -- 오디오 파일 삭제
        3. File(transcriptPath).delete() -- 전사 파일 삭제
        4. meetingDao.delete(id) -- Meeting Row 삭제
           → Room FK SET_NULL 트리거
           → minutes.meetingId = NULL (Minutes Row 보존)
```

**현황:** 데이터 레이어 완벽 구현. UI 다이얼로그 텍스트도 "회의록은 보존됩니다"로 올바름.

#### 회의록 삭제 (IND-02 관련)

```
MinutesScreen → DeleteConfirmationDialog
  → MinutesViewModel.deleteMinutes(minutesId)
    → MinutesDataRepository.deleteMinutes(id)
      → MinutesDataRepositoryImpl:
        1. minutesDao.getMinutesByIdOnce(id) -- entity 조회
        2. File(minutesPath).delete() -- 회의록 파일 삭제
        3. minutesDao.delete(id) -- Minutes Row 삭제
```

**현황:** Meeting Row에 아무 영향 없음. pipelineStatus도 변경 안 됨. **완벽 구현**.

단, 한 가지 고려사항: 모든 회의록을 삭제한 후에도 Meeting의 pipelineStatus가 COMPLETED로 남는다. 이것이 문제인지 의도된 것인지 결정 필요.

#### 회의록 재생성 (IND-03 관련)

```
TranscriptsScreen → AlertDialog("기존 회의록을 삭제하고 새로 생성할까요?") ← 오해 유발 텍스트!
  → TranscriptsViewModel.regenerateMinutes(meetingId)
    → enqueueMinutesWorker(meetingId) -- 기존 Minutes 건드리지 않음
      → MinutesGenerationWorker.doWork()
        → minutesDao.insert(newMinutesEntity) -- 새 Row INSERT
```

**현황:** 데이터 레이어는 올바르게 "추가" 동작을 수행하지만, **UI 다이얼로그 텍스트가 "삭제하고 새로 생성"이라 사용자를 오도**한다.

### 필요한 변경 사항 정리

| 카테고리 | 현재 상태 | 변경 필요 여부 | 상세 |
|----------|----------|---------------|------|
| FK SET_NULL | MinutesEntity에 onDelete=SET_NULL 설정 | 불필요 | 이미 구현 |
| MeetingRepositoryImpl.deleteMeeting | audio/transcript만 삭제 | 불필요 | 이미 구현 |
| MinutesDataRepositoryImpl.deleteMinutes | Minutes Row+파일만 삭제 | 불필요 | 이미 구현 |
| MinutesGenerationWorker | minutesDao.insert()로 새 Row | 불필요 | 이미 구현 |
| 전사 삭제 다이얼로그 텍스트 | "회의록은 보존됩니다" | 불필요 | 이미 올바름 |
| **재생성 다이얼로그 텍스트** | "기존 회의록을 삭제하고 새로 생성할까요?" | **필요** | "새 회의록이 추가됩니다"로 변경 |
| **pipelineStatus after all minutes deleted** | COMPLETED 유지 | **검토 필요** | 사용자 혼란 가능 (완료인데 회의록 없음) |
| **orphan minutes 표시** | meetingId=NULL이면 출처 전사 정보 없음 | **Phase 38에서 처리** | UI5-01에서 "출처 전사 이름" 표기 예정 |

### Anti-Patterns to Avoid

- **pipelineStatus를 Minutes 삭제 시 변경하지 말 것:** Meeting의 pipelineStatus는 "전사 파이프라인 진행 상태"이지 "회의록 존재 여부"가 아니다. 회의록을 삭제해도 전사는 완료된 상태이므로 COMPLETED 유지가 맞다.
- **재생성 시 기존 Minutes를 지우지 말 것:** IND-03 요구사항이 명시적으로 "기존 회의록은 그대로 유지되고 새 회의록이 추가"를 요구한다.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| FK cascade 동작 | 수동 Minutes 삭제 로직 | Room FK SET_NULL | DB 레벨에서 자동 처리, 앱 크래시에도 안전 |
| 파일 삭제 | 트랜잭션으로 묶으려는 시도 | 현재 패턴 유지 (try-catch 무시) | 파일 삭제 실패해도 DB 정합성은 유지되어야 함 |

## Common Pitfalls

### Pitfall 1: 재생성 다이얼로그 오해
**What goes wrong:** 사용자가 "기존 회의록을 삭제하고 새로 생성할까요?"를 보고 기존 회의록이 삭제될 것으로 예상하지만 실제로는 보존됨
**Why it happens:** Phase 36 이전 코드의 다이얼로그 텍스트가 Phase 36 데이터 모델 변경 후 업데이트되지 않음
**How to avoid:** 다이얼로그 텍스트를 "새 회의록이 추가됩니다. 기존 회의록은 유지됩니다."로 변경
**Warning signs:** TranscriptsScreen.kt L240 근처 AlertDialog 텍스트

### Pitfall 2: 전사 삭제 후 "모든 회의록 삭제 + 재생성" 불가
**What goes wrong:** 전사(Meeting Row)를 삭제하면 meetingId가 NULL이 되어, orphan Minutes에서 "재생성" 버튼이 없어짐
**Why it happens:** 재생성은 TranscriptsScreen의 Meeting 기반 메뉴에서만 가능하고, MinutesScreen에는 재생성 기능이 없음
**How to avoid:** 이것은 정상 동작. 전사가 없으면 재생성 불가 (transcriptPath 필요). Phase 38 UI 개선에서 고려 가능
**Warning signs:** 요구사항 4번 ("모든 회의록을 삭제한 전사가 정상 표시 + 다시 생성 가능")과 관련

### Pitfall 3: 전사 삭제 확인 시 연결된 회의록 수 미표시
**What goes wrong:** 사용자가 3개의 회의록이 연결된 전사를 삭제할 때 "회의록은 보존됩니다"만 보고, 몇 개가 orphan이 되는지 모름
**Why it happens:** 현재 다이얼로그에 연결된 회의록 수가 표시되지 않음
**How to avoid:** 삭제 확인 시 `getMinutesCountByMeetingId(meetingId)`로 연결 회의록 수를 조회하여 표시
**Warning signs:** UX 개선 사항, 필수는 아님

### Pitfall 4: Meeting pipelineStatus COMPLETED인데 회의록 0개
**What goes wrong:** 회의록을 모두 삭제해도 Meeting.pipelineStatus가 COMPLETED로 남아 전사 카드에 "회의록 완료" 배지가 표시됨
**Why it happens:** IND-02 원칙 ("Meeting 상태 변경 없음")을 따르면 자연스러운 결과
**How to avoid:** 두 가지 접근 가능:
  - (A) 그대로 유지 — "파이프라인은 완료됨" 의미로 해석. 카드 배지를 Minutes 테이블 기반으로 변경하는 것은 Phase 38 범위
  - (B) 회의록 수 기반 배지로 변경 — 이 경우 TranscriptsScreen의 회의록 배지 로직을 Minutes 카운트 Flow로 교체 필요
**Warning signs:** 접근 (B)는 Phase 38 UI5-02와 겹치므로 Phase 37에서는 (A) 유지 권장

## Code Examples

### 현재 전사 삭제 코드 (이미 올바름)

```kotlin
// MeetingRepositoryImpl.kt L52-64
override suspend fun deleteMeeting(id: Long) {
    val entity = meetingDao.getMeetingByIdOnce(id) ?: return
    // 오디오/전사 파일만 삭제 (Minutes 파일은 FK SET_NULL로 보존)
    listOfNotNull(
        entity.audioFilePath.takeIf { it.isNotBlank() },
        entity.transcriptPath
    ).forEach { path ->
        try { File(path).delete() } catch (_: Exception) { }
    }
    meetingDao.delete(id) // → FK SET_NULL → minutes.meetingId = NULL
}
```

### 재생성 다이얼로그 수정 예시

```kotlin
// TranscriptsScreen.kt — 현재 (잘못된 텍스트)
text = { Text("기존 회의록을 삭제하고 새로 생성할까요?") }

// 수정 후
text = { Text("새 회의록이 추가됩니다.\n기존 회의록은 유지됩니다.") }
```

### 모든 회의록 삭제 후 재생성 가능 검증 시나리오

```
1. Meeting A (pipelineStatus=COMPLETED, transcriptPath="/transcripts/1.txt")
2. Minutes X (meetingId=A.id), Minutes Y (meetingId=A.id) 존재
3. MinutesViewModel.deleteMinutes(X.id) → Minutes X 삭제, Meeting A 변화 없음 ✓
4. MinutesViewModel.deleteMinutes(Y.id) → Minutes Y 삭제, Meeting A 변화 없음 ✓
5. Meeting A: pipelineStatus=COMPLETED, transcriptPath 유지 → 전사 카드에 표시 ✓
6. TranscriptsViewModel.regenerateMinutes(A.id) → Worker enqueue → 새 Minutes Z INSERT ✓
```

이 시나리오는 현재 코드에서 이미 동작한다. 이유:
- `deleteMinutes()`는 Meeting에 영향 없음
- `regenerateMinutes()`는 meeting.transcriptPath 존재 여부만 확인
- COMPLETED 상태에서 재생성 메뉴가 TranscriptsScreen에 표시됨 (L360: `if (meeting.pipelineStatus == PipelineStatus.COMPLETED)`)

## State of the Art

| Old Approach (Phase 36 이전) | Current Approach (Phase 36 이후) | Impact |
|------------------------------|--------------------------------|--------|
| Meeting.minutesPath 단일 필드 | Minutes 독립 테이블 (1:N) | 다중 회의록, 독립 삭제 가능 |
| 전사 삭제 시 회의록도 함께 삭제 | FK SET_NULL로 회의록 보존 | IND-01 충족 |
| 회의록 "재생성" = 덮어쓰기 | 새 Minutes Row INSERT | IND-03 충족 |
| MINUTES_ONLY pipelineStatus 워크어라운드 | 제거됨, COMPLETED로 통일 | 정리 완료 |

## Open Questions

1. **pipelineStatus COMPLETED + 회의록 0개 표시 정책**
   - What we know: IND-02에 의해 회의록 삭제 시 Meeting 상태 불변. 모든 회의록 삭제 후 COMPLETED 유지.
   - What's unclear: 전사 카드의 "회의록 완료" 배지를 pipelineStatus 기반으로 유지할지, Minutes 카운트 기반으로 변경할지
   - Recommendation: Phase 37에서는 pipelineStatus 기반 유지. Phase 38(UI5-02)에서 Minutes 카운트 badge로 전환하면 자연스럽게 해결됨.

2. **전사 삭제 확인 시 연결 회의록 수 표시 여부**
   - What we know: 현재 "회의록은 보존됩니다"만 표시
   - What's unclear: 연결된 N개 회의록 수를 조회하여 표시할지
   - Recommendation: 사용자 친화적이므로 구현 권장. "연결된 회의록 N개는 보존됩니다" 형태.

## Project Constraints (from CLAUDE.md)

- 기본 응답/코드 주석/커밋 메시지/문서: 한국어
- 변수명/함수명: 영어
- 플랫폼: Android 네이티브 (Kotlin)
- GSD 워크플로우 외 직접 편집 금지

## Sources

### Primary (HIGH confidence)
- 코드베이스 직접 분석: MinutesEntity.kt (FK SET_NULL 확인), MeetingRepositoryImpl.kt (audio/transcript만 삭제), MinutesDataRepositoryImpl.kt (Minutes만 삭제), MinutesGenerationWorker.kt (새 Row INSERT)
- Phase 36 검증 리포트: 36-VERIFICATION.md (6/6 성공 기준 충족)
- TranscriptsScreen.kt, MinutesScreen.kt (삭제 다이얼로그 텍스트 확인)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Phase 36에서 확립된 패턴 그대로 활용
- Architecture: HIGH - 데이터 레이어 코드 직접 분석으로 동작 확인
- Pitfalls: HIGH - 코드 분석 + UI 텍스트 직접 확인

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (안정적인 내부 아키텍처, 외부 의존성 없음)
