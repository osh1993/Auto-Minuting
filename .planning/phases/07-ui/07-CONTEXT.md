# Phase 7: UI 완성 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

회의록 뷰어를 Markdown 타이포그래피 렌더링으로 업그레이드하고, 아카이브 검색/브라우징 기능을 추가하여, 앱이 일상 사용에 적합한 수준의 UX를 갖추게 한다.

</domain>

<decisions>
## Implementation Decisions

### Markdown 뷰어 렌더링 (UI-01)
- **D-01:** Compose Text + AnnotatedString으로 직접 구현. 외부 Markdown 라이브러리 미사용
- **D-02:** 렌더링 범위: 헤딩 크기/볼드, 테이블 렌더링, 불릿/숫자 목록, 구분선. 코드 블록/이미지는 미지원 (회의록에 불필요)
- **D-03:** 추가 기능 없이 기본만: 스크롤 + 텍스트 선택(SelectionContainer). 섹션 접기/펼치기, 복사 버튼 등 미구현
- **D-04:** 기존 MinutesDetailScreen의 플레인텍스트 Text를 Markdown 렌더 Composable로 교체 (Phase 5에서 예고된 업그레이드)

### 아카이브 검색 (UI-03)
- **D-05:** Room LIKE 쿼리로 제목/날짜 검색. 전문 검색(FTS) 미사용 — v1 데이터 규모에 불필요
- **D-06:** MeetingDao에 searchMeetings 쿼리 추가, MeetingRepository에 search 메서드 추가
- **D-07:** MinutesScreen 상단에 SearchBar 추가, 실시간 필터링 (debounce 적용)

### UX 폴리싱
- **D-08:** 로딩/에러/빈 상태 패턴 통일 — 기존 화면들의 일관성 점검 및 보완
- **D-09:** 화면 전환 애니메이션, 아이콘/색상 일관성은 Claude 재량

### Claude's Discretion
- Markdown 파서 세부 구현 방식 (정규식 vs 라인별 파싱)
- 검색바 UI 디자인 (Material 3 SearchBar 컴포넌트 활용 권장)
- UX 폴리싱 세부 범위 (로딩 스켈레톤 vs CircularProgressIndicator 등)
- 날짜 범위 필터링 추가 여부

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 뷰어 교체 대상
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` — 현재 플레인텍스트 표시, Markdown 뷰어로 교체 대상
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailViewModel.kt` — minutesContent StateFlow 제공

### 검색 추가 대상
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` — 회의록 목록, 검색바 추가 대상
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesViewModel.kt` — getMeetings() Flow, 검색 로직 추가 대상
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` — searchMeetings 쿼리 추가 대상
- `app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt` — search 메서드 추가 대상

### 기존 패턴 참조
- `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` — MINUTES_PROMPT 출력 형식 (Markdown 구조 이해 필요)
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` — 기존 목록 UI 패턴 참조

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **MinutesDetailScreen**: SelectionContainer + verticalScroll 패턴 — Markdown 렌더로 교체
- **MinutesScreen**: LazyColumn + SuggestionChip 패턴 — 검색바 추가 위치
- **Material 3 SearchBar**: Compose Material 3에 SearchBar 컴포넌트 내장

### Established Patterns
- StateFlow + collectAsStateWithLifecycle UI 상태 관리
- Hilt DI + @HiltViewModel
- Room DAO + Flow 반환 패턴

### Integration Points
- MeetingDao에 searchMeetings LIKE 쿼리 추가
- MeetingRepository/Impl에 searchMeetings 메서드 추가
- MinutesViewModel에 검색 상태 + debounce Flow 추가
- MinutesDetailScreen에서 Text → MarkdownText Composable 교체

</code_context>

<specifics>
## Specific Ideas

- GeminiEngine의 MINUTES_PROMPT가 생성하는 Markdown 구조(### 헤딩, 테이블, 목록)에 맞춰 렌더러 구현
- 검색은 debounce 300ms 정도로 실시간 필터링
- 빈 검색 결과 시 "검색 결과가 없습니다" 상태 표시

</specifics>

<deferred>
## Deferred Ideas

- 전문 검색(FTS) — v2에서 데이터 규모 증가 시
- Markdown 코드 블록/이미지 렌더링 — 회의록에 불필요
- 섹션 접기/펼치기, 복사 버튼 등 고급 뷰어 기능

</deferred>

---

*Phase: 07-ui*
*Context gathered: 2026-03-24*
