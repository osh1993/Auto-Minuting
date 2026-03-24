# Phase 1: PoC — 기술 가능성 검증 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-24
**Phase:** 01-poc
**Areas discussed:** Plaud 파일 획득 전략, STT 전사 경로

---

## Plaud 파일 획득 전략

### Q1: Plaud 녹음기에서 음성 파일을 가져오는 방법

| Option | Description | Selected |
|--------|-------------|----------|
| APK 디컴파일 우선 | Plaud APK를 디컴파일해서 파일 저장 경로/BLE UUID/통신 프로토콜 분석 후, FileObserver로 감시 | ✓ |
| BLE 스니핑 우선 | nRF Connect 등으로 BLE 트래픽 분석 후, 직접 BLE 통신으로 파일 수신 | |
| 병렬 시도 | APK 디컴파일 + BLE 스니핑을 동시에 진행하여 가능한 경로 모두 확인 | |

**User's choice:** APK 디컴파일 우선
**Notes:** 없음

### Q2: Plaud 파일 획득 불가 시 폴백

| Option | Description | Selected |
|--------|-------------|----------|
| 수동 파일 선택 | 사용자가 녹음 파일을 직접 선택하여 업로드 (Storage Access Framework) | |
| Plaud 클라우드 다운로드 | Plaud 클라우드에서 다운로드한 파일을 입력으로 사용 | ✓ |
| You decide | 기술적 타당성에 따라 클로드가 결정 | |

**User's choice:** Plaud 클라우드 다운로드
**Notes:** 없음

### Q3: Plaud 녹음기 오디오 포맷

| Option | Description | Selected |
|--------|-------------|----------|
| 모름 | 리버스 엔지니어링으로 확인 필요 | |
| WAV/MP3 | 일반적인 오디오 포맷으로 알고 있음 | ✓ |
| 다른 포맷 | 설명할게요 | |

**User's choice:** WAV/MP3
**Notes:** 없음

---

## STT 전사 경로

### Q1: Galaxy AI 전사 기능 호출 방법

| Option | Description | Selected |
|--------|-------------|----------|
| SpeechRecognizer API | Android 표준 SpeechRecognizer를 삼성 온디바이스 모드로 호출 시도 | |
| 삼성 녹음 앱 Intent | 삼성 녹음/노트 앱의 전사 기능을 Intent로 호출 | |
| 조사 후 결정 | 가능한 방법을 모두 조사해서 추천 | ✓ |

**User's choice:** 조사 후 결정
**Notes:** 모든 가능한 경로를 조사하여 최선의 방법 추천

### Q2: Galaxy AI 불가 시 폴백 STT

| Option | Description | Selected |
|--------|-------------|----------|
| Whisper 온디바이스 | OpenAI Whisper를 안드로이드에서 로컬 실행 (프라이버시 유지) | ✓ |
| Google Cloud STT | Google Cloud Speech-to-Text API 호출 (네트워크 필요) | |
| Google ML Kit | Google ML Kit 온디바이스 STT (한국어 지원 확인 필요) | |

**User's choice:** Whisper 온디바이스
**Notes:** 프라이버시 중시

### Q3: 온디바이스 vs 클라우드 우선순위

| Option | Description | Selected |
|--------|-------------|----------|
| 온디바이스 우선 | 프라이버시 중시, 네트워크 불요. 품질이 낮을 수 있음 | ✓ |
| 클라우드 우선 | 정확도 우선, 네트워크 필요. 비용 발생 가능 | |
| 사용자 선택 | 설정에서 온디바이스/클라우드 선택 가능 | |

**User's choice:** 온디바이스 우선
**Notes:** 프라이버시가 핵심 가치

---

## Claude's Discretion

- PoC 검증 순서 (3개 의존성 중 어떤 것을 먼저 검증할지)
- 각 의존성별 성공/실패 판단 기준의 구체적 수치
- BLE 스니핑 도구 선택
- Whisper 모델 크기 선택
- NotebookLM 연동 방식 (MCP vs Gemini API)

## Deferred Ideas

None
