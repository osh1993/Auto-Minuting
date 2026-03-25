# Phase 8: 기반 강화 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-25
**Phase:** 08-foundation
**Areas discussed:** 삭제 UX, API 키 설정 UI, 인증 추상화

---

## 삭제 UX

### 삭제 트리거

| Option | Description | Selected |
|--------|-------------|----------|
| 길게 누르기 (long press) | 목록에서 카드를 길게 눌러 삭제 옵션 표시 (Material 3 표준 패턴) | ✓ |
| 스와이프 삭제 | 카드를 왼쪽으로 스와이프하면 삭제 (빠르지만 실수 위험) | |
| 상세 화면 메뉴 | 회의 상세 화면에서 메뉴/버튼으로 삭제 | |
| Claude 재량 | 가장 적합한 방식을 선택해서 구현 | |

**User's choice:** 길게 누르기 (long press)

### 삭제 확인

| Option | Description | Selected |
|--------|-------------|----------|
| 확인 대화상자 표시 (추천) | "이 회의록을 삭제할까요?" 확인 후 삭제 — 실수 방지 | ✓ |
| 즉시 삭제 + Undo 스낵바 | 바로 삭제하고 하단에 "3초 내 되돌리기" 스낵바 표시 | |

**User's choice:** 확인 대화상자 표시

---

## API 키 설정 UI

### UI 배치

| Option | Description | Selected |
|--------|-------------|----------|
| 설정 화면에 섹션 추가 (추천) | 기존 설정 화면에 'Gemini API' 섹션 추가 | ✓ |
| 별도 API 설정 화면 | 설정에서 네비게이션으로 이동하는 별도 화면 | |
| Claude 재량 | 적합한 방식을 선택해서 구현 | |

**User's choice:** 설정 화면에 섹션 추가

### API 키 유효성 검증

| Option | Description | Selected |
|--------|-------------|----------|
| 저장 시 Gemini API 테스트 호출 (추천) | 키 저장 시 간단한 API 호출로 유효성 확인 | ✓ |
| 형식만 검증 | 비어있는지만 확인하고 저장 | |
| Claude 재량 | 적합한 방식을 선택해서 구현 | |

**User's choice:** 저장 시 Gemini API 테스트 호출

---

## 인증 추상화

### GeminiEngine 인증 구조

| Option | Description | Selected |
|--------|-------------|----------|
| API키 우선 단순 구조 (추천) | DataStore API키 우선, BuildConfig 폴백. Phase 12에서 OAuth 추가 시 interface 도입 | ✓ |
| Strategy 패턴 선행 구현 | AuthStrategy interface를 지금 만들고, ApiKeyStrategy 구현 | |
| Claude 재량 | Phase 12를 고려한 최적 구조 설계 | |

**User's choice:** API키 우선 단순 구조

---

## Claude's Discretion

- Room migration 전략 구체적 구현
- API 키 암호화 저장 방식 세부사항
- 삭제 확인 대화상자 문구

## Deferred Ideas

None
