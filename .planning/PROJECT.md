# Auto Minuting

## What This Is

Plaud 녹음기에서 동기화되는 음성 파일을 가로채어 로컬에 저장하고, 삼성 Galaxy AI 온보드 전사 기능으로 텍스트로 변환한 뒤, NotebookLM에 소스로 등록하여 회의록을 자동 생성하는 Android 네이티브 앱. 회의 참석 후 녹음기를 연결하면 회의록이 자동으로 완성되는 원클릭 파이프라인을 목표로 한다.

## Core Value

녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Plaud 앱 리버스 엔지니어링을 통한 음성 파일 로컬 저장 경로/BLE 통신 분석
- [ ] Plaud 녹음기에서 전송되는 음성 파일을 훅킹하여 스마트폰 로컬에 저장
- [ ] 저장 완료 시 Galaxy AI 전사 기능으로 한국어 음성을 텍스트로 변환
- [ ] 전사된 텍스트를 NotebookLM에 소스로 등록 (지정 노트 또는 새 노트 선택 가능)
- [ ] NotebookLM 프롬프팅으로 회의록 자동 생성
- [ ] 생성된 회의록을 스마트폰 로컬에 저장
- [ ] 회의록 형식 선택 기능 (구조화된 회의록 / 요약 / 커스텀)
- [ ] 자동화 수준 설정 (완전 자동 / 하이브리드 모드)

### Out of Scope

- iOS 지원 — 삼성 Galaxy AI 온보드 기능에 의존하므로 Android 전용
- 자체 STT 엔진 구현 — Galaxy AI 온보드 전사 기능 활용
- Plaud 클라우드 연동 — 로컬 파이프라인으로 처리
- 실시간 스트리밍 전사 — 녹음 완료 후 배치 처리 방식

## Context

- **Plaud 녹음기**: BLE로 스마트폰과 통신하며, Plaud 앱 실행 시 녹음 파일을 동기화. 로컬 저장 경로가 불확실하여 리버스 엔지니어링 필요
- **Galaxy AI 전사**: 삼성 갤럭시 스마트폰 내장 온보드 AI 기능으로 음성→텍스트 변환. 네트워크 불필요
- **NotebookLM**: Google의 AI 노트 서비스. MCP 서버가 현재 설정되어 있으며, API 연동 방식은 리서치 후 결정
- **타깃 언어**: 한국어 녹음/전사가 주 대상
- **타깃 디바이스**: 삼성 갤럭시 스마트폰 (Galaxy AI 지원 모델)

## Constraints

- **플랫폼**: Android 네이티브 (Kotlin) — Galaxy AI API 접근성 및 시스템 레벨 통합 필요
- **하드웨어 의존**: Plaud 녹음기 + 삼성 갤럭시 스마트폰 필수
- **리버스 엔지니어링 리스크**: Plaud 앱 업데이트 시 파일 경로/통신 프로토콜 변경 가능성
- **Galaxy AI 가용성**: Galaxy AI 전사 기능이 지원되는 기기/OS 버전에 한정

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Android 네이티브 (Kotlin) | Galaxy AI 온보드 기능 접근 및 시스템 레벨 BLE/파일 접근 필요 | — Pending |
| Plaud 앱 리버스 엔지니어링 접근 | 공식 API 미제공, 파일 전달 경로 파악 필요 | — Pending |
| NotebookLM 연동 방식 | MCP 서버 vs API 직접 호출 — 리서치 후 결정 | — Pending |
| 자동화 수준 설정 가능 | 사용자별 워크플로우 선호도가 다를 수 있으므로 완전자동/하이브리드 선택 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-24 after initialization*
