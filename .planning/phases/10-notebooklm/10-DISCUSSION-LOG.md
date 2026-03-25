# Phase 10: NotebookLM 반자동 연동 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-03-26
**Phase:** 10-notebooklm
**Areas discussed:** NotebookLM 공유 방식, Custom Tabs 통합, MCP 검토 범위

---

## NotebookLM 공유 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 공유 버튼 활용 | Share 버튼이 이미 ACTION_SEND 사용. NotebookLM 앱 설치 시 자동 표시. | |
| 별도 NotebookLM 버튼 | NotebookLM 전용 버튼 추가. 특정 패키지로 직접 Intent 전송. | |
| 둘 다 | 기존 공유 대상에도 표시 + 별도 전용 버튼도 추가. | ✓ |

**User's choice:** 둘 다
**Notes:** 앱 미설치 시 Custom Tabs로 폴백

## Custom Tabs 통합

| Option | Description | Selected |
|--------|-------------|----------|
| 회의록 상세 + 설정 | 두 곳에서 접근 가능 | ✓ |
| 회의록 상세만 | 회의록 상세화면에서만 | |
| 설정화면만 | 설정화면에 링크만 | |

**User's choice:** 회의록 상세 + 설정 (추천)

## MCP 검토 범위

| Option | Description | Selected |
|--------|-------------|----------|
| 문서 검토만 | API 가능성 검토 문서만 작성 | |
| 문서 + PoC 코드 | 검토 문서 + 간단한 PoC | |
| 문서 + 실동작 통합 | 검토 문서 + 앱 내 MCP 통합 코드 | ✓ |

**User's choice:** 문서 + 실동작 통합

## Claude's Discretion

- NotebookLM 버튼 아이콘/디자인
- Custom Tabs 색상 테마
- MCP 통합 에러 처리/인증
- 공유 텍스트 형식

## Deferred Ideas

None
