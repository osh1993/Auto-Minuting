# Phase 38: 독립 아키텍처 UI 반영 - Research

**Researched:** 2026-03-30
**Domain:** Jetpack Compose UI / Room DAO 쿼리 / 1:N 관계 표시
**Confidence:** HIGH

## Summary

Phase 38은 Phase 36-37에서 완성된 전사-회의록 1:N 독립 데이터 모델을 UI에 반영하는 작업이다. 두 가지 핵심 요구사항이 있다: (1) 회의록 목록에서 각 카드에 출처 전사 이름 표기, (2) 전사 목록 카드에 연결된 회의록 수 badge 표시.

현재 코드 분석 결과, MinutesScreen은 이미 Minutes 테이블 기반으로 동작하지만 출처 전사 정보를 전혀 표시하지 않는다. TranscriptsScreen의 MinutesStatusBadge는 PipelineStatus 기반으로만 "회의록 완료/미작성"을 표시하며, 실제 Minutes 테이블의 count를 반영하지 않는다. 두 화면 모두 수정이 필요하다.

**Primary recommendation:** MinutesDao에 JOIN 쿼리를 추가하여 Minutes + Meeting.title을 한 번에 가져오고, TranscriptsViewModel에 meetingId별 minutes count Map을 StateFlow로 expose하여 badge에 사용한다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UI5-01 | 회의록 목록 화면에서 각 회의록이 독립 카드로 표시되며 어느 전사에서 왔는지 표기된다 | MinutesDao JOIN 쿼리 + MinutesScreen 카드 UI에 출처 전사명 Row 추가 |
| UI5-02 | 전사 목록 화면의 카드에 연결된 회의록 수(badge)가 표시된다 | MinutesDao의 기존 getMinutesCountByMeetingId 활용 또는 일괄 count 쿼리 추가 |
</phase_requirements>

## Architecture Patterns

### 현재 코드 상태 분석

#### MinutesScreen (회의록 목록)
- **모델**: Minutes 도메인 모델 기반 (Phase 36에서 전환 완료)
- **ViewModel**: `MinutesViewModel`이 `MinutesDataRepository.getAllMinutes()` 사용
- **카드 표시 항목**: minutesTitle, createdAt, MoreVert 메뉴 (공유/삭제)
- **문제점**: Minutes.meetingId는 있지만 Meeting.title을 가져오는 로직 없음. 출처 전사 이름 미표시
- **선택 모드**: 다중 선택 + 일괄 삭제 지원 (유지해야 함)

#### TranscriptsScreen (전사 목록)
- **모델**: Meeting 도메인 모델 기반
- **ViewModel**: `TranscriptsViewModel`이 `MeetingRepository.getMeetings()` 사용
- **카드 표시 항목**: title, recordedAt, FileTypeIcon, TranscriptionStatusBadge, MinutesStatusBadge, MoreVert 메뉴
- **문제점**: MinutesStatusBadge가 PipelineStatus 기반으로 "회의록 완료/미작성" 표시. 실제 Minutes count 미반영
- **회의록 수**: MinutesDataRepository에 `getMinutesCountByMeetingId(meetingId)` 메서드 이미 존재하나 TranscriptsViewModel에서 사용하지 않음

### Pattern 1: MinutesWithMeetingTitle 복합 모델

**What:** Minutes 도메인 모델에 출처 Meeting 제목을 포함하는 데이터 클래스
**When to use:** 회의록 목록에서 출처 전사 이름을 함께 표시할 때

두 가지 접근법이 있다:

**접근법 A: DAO JOIN 쿼리 (권장)**
```kotlin
// MinutesDao에 추가
data class MinutesWithMeetingTitle(
    val id: Long,
    val meetingId: Long?,
    val minutesPath: String,
    val minutesTitle: String?,
    val templateId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val meetingTitle: String?  // LEFT JOIN으로 가져옴
)

@Query("""
    SELECT m.*, mt.title AS meetingTitle
    FROM minutes m
    LEFT JOIN meetings mt ON m.meetingId = mt.id
    ORDER BY m.createdAt DESC
""")
fun getAllMinutesWithMeetingTitle(): Flow<List<MinutesWithMeetingTitle>>
```

장점: DB 레벨에서 한 번에 조회, N+1 문제 없음
단점: 새로운 POJO 클래스 필요

**접근법 B: ViewModel에서 combine**
```kotlin
// MinutesViewModel에서 Minutes + Meeting을 combine
val minutesWithTitles = combine(
    minutesDataRepository.getAllMinutes(),
    meetingRepository.getMeetings()
) { minutesList, meetingsList ->
    val meetingMap = meetingsList.associateBy { it.id }
    minutesList.map { minutes ->
        MinutesUiModel(
            minutes = minutes,
            meetingTitle = minutes.meetingId?.let { meetingMap[it]?.title }
        )
    }
}
```

장점: 기존 DAO 변경 없음
단점: 두 테이블 전체 로드 필요, 메모리 비효율

**권장: 접근법 A (DAO JOIN 쿼리)**. 데이터가 많아질수록 효율적이며, Room의 표준 패턴이다.

### Pattern 2: 전사 카드 회의록 수 Badge

**What:** 전사 카드에 연결된 회의록 수를 표시하는 badge
**When to use:** TranscriptsScreen의 각 Meeting 카드

두 가지 접근법이 있다:

**접근법 A: 일괄 count 쿼리 (권장)**
```kotlin
// MinutesDao에 추가
@Query("SELECT meetingId, COUNT(*) AS count FROM minutes WHERE meetingId IS NOT NULL GROUP BY meetingId")
fun getMinutesCountPerMeeting(): Flow<List<MinutesCountPerMeeting>>

data class MinutesCountPerMeeting(
    val meetingId: Long,
    val count: Int
)
```

TranscriptsViewModel에서:
```kotlin
val minutesCountMap: StateFlow<Map<Long, Int>> = minutesDataRepository
    .getMinutesCountPerMeeting()
    .map { list -> list.associate { it.meetingId to it.count } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
```

장점: 한 번의 쿼리로 모든 meeting의 count를 가져옴
단점: MinutesDao/MinutesDataRepository에 새 메서드 필요

**접근법 B: 개별 count 쿼리**
기존 `getMinutesCountByMeetingId(meetingId)` 호출을 meeting마다 하는 방식.
단점: N+1 문제, meeting 수만큼 쿼리 발생 -- 비권장

**권장: 접근법 A (일괄 count 쿼리)**

### Pattern 3: meetingId NULL 처리 (전사 삭제됨)

Minutes.meetingId가 NULL인 경우 (원본 전사가 삭제됨):
- 출처 전사 이름: "삭제된 전사" 또는 빈 텍스트로 표시
- 전사 상세 이동: 클릭 비활성화 (meetingId == null이면 네비게이션 불가)

```kotlin
// MinutesCard에서
Text(
    text = meetingTitle ?: "삭제된 전사",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

### 필요한 변경 파일 목록

| 파일 | 변경 내용 |
|------|-----------|
| `MinutesDao.kt` | `getAllMinutesWithMeetingTitle()` JOIN 쿼리 추가, `getMinutesCountPerMeeting()` 일괄 count 쿼리 추가 |
| `MinutesDataRepository.kt` | 새 메서드 인터페이스 추가 |
| `MinutesDataRepositoryImpl.kt` | 새 메서드 구현 |
| `MinutesViewModel.kt` | `MeetingRepository` 의존성 제거, JOIN 쿼리 결과 사용으로 전환 |
| `MinutesScreen.kt` | MinutesCard에 출처 전사 이름 Row 추가, meetingId 기반 네비게이션 추가 |
| `TranscriptsViewModel.kt` | `MinutesDataRepository` 의존성 추가, minutesCountMap StateFlow 추가 |
| `TranscriptsScreen.kt` | MinutesStatusBadge를 실제 count 기반으로 교체, badge에 count 표시 |

### Anti-Patterns to Avoid
- **N+1 쿼리**: Meeting마다 개별 count 쿼리를 날리지 말 것. GROUP BY로 일괄 조회
- **두 테이블 전체 로드 combine**: minutes와 meetings를 각각 전체 로드하여 ViewModel에서 합치지 말 것. DAO JOIN이 더 효율적

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Minutes + Meeting JOIN | ViewModel에서 수동 매핑 | Room @Query LEFT JOIN | DB 레벨에서 최적화, 타입 안전 |
| 일괄 count 조회 | Meeting마다 개별 Flow<Int> | GROUP BY count 쿼리 | N+1 방지, 단일 Flow |
| Badge UI | 커스텀 Canvas 드로잉 | Material 3 Badge 컴포넌트 | 표준 디자인, 접근성 내장 |

## Common Pitfalls

### Pitfall 1: MinutesWithMeetingTitle POJO 매핑 오류
**What goes wrong:** Room JOIN 쿼리의 결과 POJO 필드명이 쿼리의 alias와 불일치
**Why it happens:** `SELECT m.*` 사용 시 Room이 필드를 자동 매핑하는데, 추가 컬럼(meetingTitle)의 alias가 정확해야 함
**How to avoid:** POJO의 프로퍼티명과 SQL alias를 정확히 일치시킬 것. `AS meetingTitle` → `val meetingTitle: String?`
**Warning signs:** 컴파일 오류 또는 항상 null 반환

### Pitfall 2: Badge 숫자 실시간 갱신 누락
**What goes wrong:** 회의록 생성/삭제 후 전사 카드의 badge 숫자가 업데이트되지 않음
**Why it happens:** Flow가 아닌 일회성 쿼리 사용 시 데이터 변경 감지 불가
**How to avoid:** `Flow<List<MinutesCountPerMeeting>>`로 반환하여 Room의 자동 invalidation 활용
**Warning signs:** 회의록 추가 후 전사 탭으로 돌아가도 badge 숫자 변화 없음

### Pitfall 3: TranscriptsViewModel DI 변경 누락
**What goes wrong:** TranscriptsViewModel에 MinutesDataRepository를 주입하려 했으나 Hilt 모듈에 바인딩이 없어 런타임 크래시
**Why it happens:** MinutesDataRepository는 이미 Hilt 모듈에 바인딩되어 있을 가능성이 높지만 확인 필요
**How to avoid:** RepositoryModule에서 MinutesDataRepository 바인딩 확인 후 추가
**Warning signs:** `MissingBinding` 컴파일 오류 (Hilt는 컴파일 타임 검증)

### Pitfall 4: MinutesStatusBadge 회의록 수 0 vs 배지 미표시
**What goes wrong:** count가 0일 때 Badge를 표시하면 "0"이 보여 혼란
**Why it happens:** UI5-02 요구사항에 "0개면 badge 미표시" 명시
**How to avoid:** `if (count > 0)` 조건으로 Badge 렌더링 제어
**Warning signs:** count == 0인 카드에 "0" badge 노출

## Code Examples

### Room JOIN 쿼리 + POJO

```kotlin
// MinutesDao.kt에 추가할 POJO + 쿼리
data class MinutesWithMeetingTitle(
    val id: Long,
    val meetingId: Long?,
    val minutesPath: String,
    val minutesTitle: String?,
    val templateId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val meetingTitle: String?
)

@Query("""
    SELECT m.*, mt.title AS meetingTitle
    FROM minutes m
    LEFT JOIN meetings mt ON m.meetingId = mt.id
    ORDER BY m.createdAt DESC
""")
fun getAllMinutesWithMeetingTitle(): Flow<List<MinutesWithMeetingTitle>>
```

### Material 3 Badge on Transcript Card

```kotlin
// TranscriptsScreen에서 회의록 수 badge
@Composable
fun MinutesCountBadge(count: Int) {
    if (count > 0) {
        SuggestionChip(
            onClick = {},
            label = {
                Text(
                    text = "회의록 ${count}개",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}
```

### MinutesCard에 출처 전사 이름 표기

```kotlin
// MinutesCard Column 내부에 추가
// 출처 전사 이름 (meetingTitle)
Text(
    text = meetingTitle ?: "삭제된 전사",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.clickable(enabled = meetingId != null) {
        meetingId?.let { onSourceClick(it) }
    }
)
```

### 일괄 count 쿼리

```kotlin
// MinutesDao.kt에 추가
data class MinutesCountPerMeeting(
    val meetingId: Long,
    val count: Int
)

@Query("SELECT meetingId, COUNT(*) AS count FROM minutes WHERE meetingId IS NOT NULL GROUP BY meetingId")
fun getMinutesCountPerMeeting(): Flow<List<MinutesCountPerMeeting>>
```

## 네비게이션 변경 사항

### 회의록 카드 -> 전사 상세 이동 (UI5-01 보너스)

Success Criteria #4: "회의록 카드에서 출처 전사를 탭하면 해당 전사 상세로 이동"

현재 MinutesScreen은 `onMinutesClick(minutesId)` 콜백만 있다. 전사 상세 이동을 위해:

1. MinutesScreen에 `onSourceTranscriptClick: (Long) -> Unit` 콜백 추가
2. AppNavigation에서 MinutesScreen 호출 시 해당 콜백에 `navController.navigate(Screen.TranscriptEdit.createRoute(meetingId))` 연결

```kotlin
// AppNavigation.kt 수정
composable(Screen.Minutes.route) {
    MinutesScreen(
        onMinutesClick = { minutesId ->
            navController.navigate(Screen.MinutesDetail.createRoute(minutesId))
        },
        onSourceTranscriptClick = { meetingId ->
            navController.navigate(Screen.TranscriptEdit.createRoute(meetingId))
        }
    )
}
```

## Project Constraints (from CLAUDE.md)

- 기본 응답/주석/커밋/문서: 한국어
- 변수명/함수명: 영어
- 플랫폼: Android 네이티브 (Kotlin)
- UI: Jetpack Compose + Material 3
- DI: Hilt
- DB: Room
- 상태 관리: ViewModel + StateFlow
- GSD 워크플로우 준수

## Sources

### Primary (HIGH confidence)
- 프로젝트 코드 직접 분석: MinutesScreen.kt, MinutesViewModel.kt, TranscriptsScreen.kt, TranscriptsViewModel.kt, MinutesDao.kt, MinutesDataRepository.kt, MinutesEntity.kt, Meeting.kt, Minutes.kt, AppNavigation.kt, Screen.kt
- Phase 37 검증 보고서: 독립 삭제/재생성 로직 완료 확인

### Secondary (MEDIUM confidence)
- Room @Query JOIN 패턴: Android 공식 문서 Room relationships (training data 기반이나 표준 패턴)

## Metadata

**Confidence breakdown:**
- 현재 코드 상태 분석: HIGH - 소스 코드 직접 확인
- JOIN 쿼리 패턴: HIGH - Room 표준 패턴, 프로젝트에서 이미 유사 패턴 사용
- UI 변경 범위: HIGH - 기존 코드 구조에서 명확한 수정 지점 파악
- 네비게이션: HIGH - Screen/AppNavigation 구조 확인 완료

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (코드 구조 안정)
