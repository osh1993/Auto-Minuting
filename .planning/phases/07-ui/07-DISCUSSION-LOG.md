# Phase 7: UI 완성 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-24
**Phase:** 07-ui
**Areas discussed:** Markdown 뷰어 렌더링

---

## 논의 영역 선택

| Option | Description | Selected |
|--------|-------------|----------|
| Markdown 뷰어 렌더링 | 플레인텍스트 → 구조화된 뷰어 업그레이드 | ✓ |
| 아카이브 검색/필터링 | Room LIKE 쿼리, 검색 UI | |
| UX 폴리싱 범위 | 로딩/에러/빈 상태, 애니메이션, 일관성 | |

**Notes:** 사용자가 Markdown 뷰어만 선택. 검색/UX는 Claude 재량.

---

## Markdown 뷰어 렌더링

### Q1: 렌더링 수준

| Option | Description | Selected |
|--------|-------------|----------|
| 기본 타이포그래피 (추천) | Compose Text + AnnotatedString, 헤딩/볼드/테이블/목록/구분선 | ✓ |
| 외부 Markdown 라이브러리 | compose-markdown 등 서드파티, 풀 Markdown 지원 | |
| 섹션별 카드 UI | Markdown 파싱 후 각 섹션을 Card로 표시 | |

**User's choice:** 기본 타이포그래피 (추천)
**Notes:** 외부 의존성 없이 직접 구현

### Q2: 추가 기능

| Option | Description | Selected |
|--------|-------------|----------|
| 섹션 접기/펼치기 | 긴 회의록에서 유용 | |
| 텍스트 복사 버튼 | 전체/섹션별 클립보드 복사 | |
| 기본만 (추천) | 스크롤 + SelectionContainer만 | ✓ |

**User's choice:** 기본만 (추천)

---

## Claude's Discretion

- 아카이브 검색 구현 방식 (Room LIKE, SearchBar)
- UX 폴리싱 범위 (로딩/에러/빈 상태 통일)
- Markdown 파서 세부 구현

## Deferred Ideas

- FTS 전문 검색 (v2)
- 코드 블록/이미지 렌더링
- 섹션 접기/펼치기, 복사 버튼
