# Phase 6: 파이프라인 통합 및 자동화 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-24
**Phase:** 06-pipeline-integration
**Areas discussed:** 회의록 형식 선택, 자동화 모드, 파이프라인 알림, 공유 기능
**Mode:** --auto (all decisions auto-selected)

---

## 회의록 형식 선택

| Option | Description | Selected |
|--------|-------------|----------|
| 3종 프리셋만 | 구조화/요약/액션아이템 프리셋, 커스텀은 v2 | ✓ |
| 프리셋 + 커스텀 편집 | 사용자가 프롬프트를 직접 수정 가능 | |
| 형식 선택 없이 고정 | 현재처럼 구조화된 회의록만 | |

**User's choice:** [auto] 3종 프리셋만 (recommended default)
**Notes:** v1 스코프에 적합, 커스텀은 ADV-03으로 추적 중

---

## 자동화 모드

| Option | Description | Selected |
|--------|-------------|----------|
| 완전 자동(기본) + 하이브리드(전사 후 1회 확인) | 2단계 모드, 전사 완료 시점에서 분기 | ✓ |
| 3단계 모드 | 전사 전/후, 회의록 생성 전 각각 확인 | |
| 완전 자동만 | 하이브리드 모드 미구현 | |

**User's choice:** [auto] 완전 자동(기본) + 하이브리드(전사 후 1회 확인) (recommended default)
**Notes:** Core Value "수동 작업 없이"에 부합하면서도 선택권 제공

---

## 파이프라인 알림

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 알림 패턴 재사용 + 파이프라인 채널 추가 | AudioCollectionService 패턴 기반 | ✓ |
| 별도 알림 시스템 구축 | 커스텀 알림 매니저 | |
| 알림 없이 앱 내 표시만 | 시스템 알림 미사용 | |

**User's choice:** [auto] 기존 알림 패턴 재사용 (recommended default)
**Notes:** 일관성 확보, 코드 재사용 극대화

---

## 공유 기능

| Option | Description | Selected |
|--------|-------------|----------|
| text/plain Share Intent | 텍스트만 공유, 가장 범용적 | ✓ |
| text/markdown + 파일 첨부 | .md 파일 첨부 공유 | |
| 다중 포맷 선택 | 공유 시 포맷 선택 다이얼로그 | |

**User's choice:** [auto] text/plain Share Intent (recommended default)
**Notes:** 카카오톡/이메일 등 대부분의 앱에서 즉시 사용 가능

---

## Claude's Discretion

- 알림 아이콘/색상, 설정 화면 레이아웃, 프리셋 프롬프트 세부 문구, 하이브리드 모드 확인 UI

## Deferred Ideas

- 커스텀 프롬프트 편집 (v2 ADV-03)
- 파이프라인 실행 이력/통계 (v2)
- 회의록 취소/재생성 기능
