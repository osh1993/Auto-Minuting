# Auto Minuting

## What This Is

Plaud 녹음기에서 BLE로 수신한 음성 파일을 로컬에 저장하고, Whisper 온디바이스 STT로 한국어 텍스트로 전사한 뒤, Gemini 2.5 Flash API로 구조화된 회의록을 자동 생성하는 Android 네이티브 앱. 녹음기를 연결하면 회의록이 자동으로 완성되는 원클릭 파이프라인.

## Core Value

녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## Current State

**Shipped: v1.0 MVP** (2026-03-24)

- 58개 Kotlin 파일, 5,500+ LOC
- Tech stack: Kotlin 2.3.20, Jetpack Compose (BOM 2026.03), Hilt 2.56, Room 2.8.4, WorkManager
- 파이프라인: Plaud SDK BLE → Whisper STT → Gemini 2.5 Flash → Markdown 회의록
- UI: Material 3 Dynamic Color, Bottom Navigation 4탭, Markdown 뷰어, 아카이브 검색

## Requirements

### Validated (v1.0)

- ✓ Plaud SDK BLE로 오디오 파일 자동 수신 및 로컬 저장 — v1.0
- ✓ Whisper 온디바이스 STT로 한국어 음성 전사 (ML Kit 폴백) — v1.0
- ✓ Gemini 2.5 Flash API로 구조화된 회의록 생성 — v1.0
- ✓ 회의록 3종 형식 선택 (구조화/요약/액션아이템) — v1.0
- ✓ 회의록 Markdown 뷰어 (AnnotatedString 기반 렌더링) — v1.0
- ✓ 과거 회의록 제목/날짜 검색 및 브라우징 — v1.0
- ✓ Android Share Intent로 외부 앱 공유 — v1.0
- ✓ 파이프라인 단계별 진행 알림 — v1.0
- ✓ 완전 자동 / 하이브리드 모드 설정 — v1.0
- ✓ 전사 텍스트 편집 기능 — v1.0
- ✓ Clean Architecture + Hilt DI + Room DB + WorkManager 인프라 — v1.0

### Active

(v2 마일스톤에서 정의 예정)

### Out of Scope

- iOS 지원 — Android 전용 (Galaxy AI, BLE 시스템 통합)
- 실시간 스트리밍 전사 — 녹음 완료 후 배치 처리 방식
- Plaud 클라우드 연동 — 로컬 파이프라인으로 처리
- 자체 STT 엔진 훈련 — Whisper 사전훈련 모델 활용

## Context

- **오디오 수집**: Plaud SDK v0.2.8 (공식, MIT)로 BLE 연결. Cloud API를 폴백으로 보유
- **STT**: Whisper(whisper.cpp small) 온디바이스 1차, ML Kit GenAI 2차. Galaxy AI는 서드파티 접근 불가로 폐기
- **회의록 생성**: Google AI Client SDK로 Gemini 2.5 Flash 직접 호출. NotebookLM MCP는 PC 의존으로 폴백 유지
- **타깃 언어**: 한국어 녹음/전사가 주 대상
- **타깃 디바이스**: 삼성 갤럭시 스마트폰 (minSdk 31, Android 12+)

## Constraints

- **플랫폼**: Android 네이티브 (Kotlin) — 시스템 레벨 BLE/파일 접근 필요
- **하드웨어 의존**: Plaud 녹음기 + Android 스마트폰 필수
- **Plaud SDK 의존**: SDK 업데이트 시 BLE 프로토콜 변경 가능성, appKey 발급 필요
- **Gemini API 키 필요**: Google AI Studio에서 API 키 발급 필요

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Android 네이티브 (Kotlin) | 시스템 레벨 BLE/파일 접근 필요 | ✓ 확정 |
| Plaud SDK 1차 채택 | 공식 SDK 발견으로 역공학 불필요 | ✓ 채택 |
| Whisper 온디바이스 STT | Galaxy AI 서드파티 접근 불가, 네트워크 불필요 | ✓ 채택 |
| Gemini API 직접 호출 | 모바일 독립, 공식 API, 무료 티어 | ✓ 채택 |
| 3종 프리셋 형식 | 커스텀 프롬프트는 v2로 미룸 | ✓ v1.0 |
| DataStore 설정 관리 | SharedPreferences 대체, Coroutine/Flow 지원 | ✓ v1.0 |
| AnnotatedString Markdown 렌더링 | 외부 라이브러리 없이 직접 구현 | ✓ v1.0 |
| Room LIKE 검색 | v1 데이터 규모에 FTS 불필요 | ✓ v1.0 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**v1.0 Requirements archived to:** `.planning/milestones/v1.0-REQUIREMENTS.md`
**v1.0 Roadmap archived to:** `.planning/milestones/v1.0-ROADMAP.md`

---
*Last updated: 2026-03-25 after v1.0 milestone*
