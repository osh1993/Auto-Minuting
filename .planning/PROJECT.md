# Auto Minuting

## What This Is

Plaud 녹음기에서 BLE로 수신한 음성 파일을 로컬에 저장하고, Whisper 온디바이스 STT로 한국어 텍스트로 전사한 뒤, Gemini 2.5 Flash API로 구조화된 회의록을 자동 생성하는 Android 네이티브 앱. 녹음기를 연결하면 회의록이 자동으로 완성되는 원클릭 파이프라인.

## Core Value

녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## Current State

**Shipped: v6.0 멀티 엔진 확장** (2026-04-03)
**42 phases 완료** | **69 plans** | **13,489 Kotlin LOC**

- Tech stack: Kotlin 2.3.20, Jetpack Compose (BOM 2026.03), Hilt 2.56, Room 2.8.4, WorkManager, whisper.cpp (NDK/JNI)
- 파이프라인: Plaud SDK BLE → STT 5종 선택 (Whisper 온디바이스 / Gemini / Groq / Deepgram / Naver CLOVA) → 회의록 엔진 3종 선택 (Gemini / Deepgram Intelligence / Naver CLOVA Summary) → Markdown 회의록
- UI: Material 3 Dynamic Color, Bottom Navigation 4탭, Markdown 뷰어, 아카이브 검색
- 데이터: Room DB v5, 전사(Transcript)-회의록(Minutes) 독립 1:N 구조
- 릴리스: AutoMinuting-v6.0-release.apk (build.gradle.kts archivesName 자동 생성)

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

### Validated (v6.0)

- ✓ Groq Whisper API STT 엔진 선택 및 API 키 관리 — v6.0
- ✓ Deepgram Nova-3 STT 엔진 선택 및 API 키 관리 — v6.0
- ✓ Naver CLOVA Speech STT 엔진 선택 및 API 키 관리 — v6.0
- ✓ STT 5종 실제 한국어 전사 동작 — v6.0
- ✓ Deepgram Audio Intelligence 회의록 엔진 선택 — v6.0
- ✓ Naver CLOVA Summary 회의록 엔진 선택 — v6.0
- ✓ 전사 텍스트 → 요약 결과 회의록 저장 — v6.0
- ✓ 설정 화면 STT/회의록 엔진 독립 선택 — v6.0
- ✓ Groq/Deepgram/Naver API 키 암호화 저장 — v6.0
- ✓ AutoMinuting-v6.0-release.apk 버전 번호 포함 APK 빌드 — v6.0

### Active

(다음 마일스톤에서 정의 예정)

### Out of Scope

- iOS 지원 — Android 전용 (Galaxy AI, BLE 시스템 통합)
- 실시간 스트리밍 전사 — 녹음 완료 후 배치 처리 방식
- Plaud 클라우드 연동 — 로컬 파이프라인으로 처리
- 자체 STT 엔진 훈련 — Whisper 사전훈련 모델 활용

## Context

- **오디오 수집**: Plaud SDK v0.2.8 (공식, MIT)로 BLE 연결. Cloud API를 폴백으로 보유
- **STT (5종)**: Whisper(whisper.cpp small) 온디바이스 / Gemini 클라우드 / Groq Whisper / Deepgram Nova-3 / Naver CLOVA Speech — 설정에서 선택
- **회의록 생성 (3종)**: Gemini 2.5 Flash / Deepgram Audio Intelligence / Naver CLOVA Summary — 설정에서 선택
- **API 키 관리**: EncryptedSharedPreferences로 Gemini/Groq/Deepgram/Naver 키 암호화 저장
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

## Current Milestone

v6.0 완료. 다음 마일스톤은 `/gsd:new-milestone`으로 시작.

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

**v1.0 Requirements archived to:** `.planning/milestones/v1.0-REQUIREMENTS.md`
**v1.0 Roadmap archived to:** `.planning/milestones/v1.0-ROADMAP.md`

---
*Last updated: 2026-03-31 — v5.0 완료, v6.0 멀티 엔진 확장 시작*
