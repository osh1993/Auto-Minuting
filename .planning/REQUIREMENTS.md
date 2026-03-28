# Requirements: Auto Minuting

**Defined:** 2026-03-26
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## v2.1 Requirements

Requirements for v2.1 milestone. Each maps to roadmap phases.

### Plaud 연결 분석

- [x] **PLUD-02**: Plaud 앱의 녹음기 연결 프로토콜을 리버스 엔지니어링하여 연결 방식(BLE/Wi-Fi/기타)을 파악한다 *(No-Go 판정: E2EE + 서버 인증으로 SDK 의존 유지)*

### 회의록 생성 (Minutes)

- [x] **MINS-01**: 사용자가 전사 파일을 선택하고 생성 버튼으로 회의록을 수동 생성할 수 있다 (프롬프트 템플릿 선택 또는 수기 입력)
- [ ] **MINS-02**: 사용자가 프롬프트 템플릿을 추가/삭제/편집하여 관리할 수 있다

### 파일 관리 (File)

- [ ] **FILE-02**: 사용자가 회의록을 다중 선택하여 삭제할 수 있고, 전사 파일은 보존된다
- [x] **FILE-03**: 사용자가 전사 파일을 별도로 삭제할 수 있다

### UI/UX 개선

- [ ] **UI-01**: 앱 아이콘이 새롭게 디자인되어 교체된다
- [ ] **UI-02**: NotebookLM 열기 버튼이 설정 화면에서 메인 화면으로 이동한다
- [ ] **UI-03**: 테스트 도구(spike 패키지) 코드가 삭제된다

### 전사 엔진 (STT)

- [x] **STT-01**: SpeechRecognizer 대신 Gemini STT 클라우드 엔진으로 음성 파일 전사가 동작한다
- [x] **STT-02**: 사용자가 설정에서 STT 엔진(Gemini/Whisper)을 선택할 수 있다
- [ ] **STT-03**: Whisper 온디바이스 전사가 NDK 빌드를 통해 동작한다

### 인증 수정

- [ ] **AUTH-03**: Google OAuth Web Client ID 미설정 오류가 해결되어 Google 계정 로그인이 동작한다

## v3.1 Requirements

Requirements for v3.1 milestone. Each maps to roadmap phases.

### 카드 정보 표시 (Card)

- [x] **CARD-01**: 전사 카드에 파일 종류 아이콘(텍스트/음원)이 표시된다
- [x] **CARD-02**: 전사 카드에 전사 완료/미완료 상태 배지가 표시된다
- [x] **CARD-03**: 전사 카드에 회의록 작성 완료/미작성 상태 배지가 표시된다

### 이름 관리 (Name)

- [ ] **NAME-01**: 공유받은 파일의 원본 파일명이 전사 카드 제목으로 자동 설정된다
- [ ] **NAME-02**: 사용자가 전사 카드의 이름을 편집할 수 있다
- [ ] **NAME-03**: 회의록 생성 시 Gemini가 생성한 제목이 회의록 카드 제목으로 자동 설정된다
- [ ] **NAME-04**: 사용자가 회의록 카드의 이름을 편집할 수 있다

### 음성 다운로드 (Download)

- [ ] **DL-01**: 사용자가 대시보드에서 URL을 입력하여 음성 파일을 다운로드하고 전사 파이프라인에 진입시킬 수 있다

### UX 개선 (v3.0 이관)

- [ ] **UX-01**: 회의록 목록에서 삭제, 공유 액션 메뉴를 사용할 수 있다
- [ ] **UX-02**: 설정 메뉴가 정리되고 테스트 도구(spike) 코드가 삭제된다

## Future Requirements

Deferred to future release.

- **PLUD-F01**: Plaud 리버스 엔지니어링 결과에 따른 연결 본구현
- **SREC-F01**: 삼성 녹음앱 자동 감지 본구현 (v2.0 스파이크 Partial Go)
- **NLMK-F01**: MCP 서버 API 본구현 (REST API 미존재 — 대기)
- **STT-03**: Whisper 온디바이스 전사 NDK 빌드

## Out of Scope

| Feature | Reason |
|---------|--------|
| iOS 지원 | Android 전용 |
| 실시간 스트리밍 전사 | 배치 처리 방식 |
| Plaud 클라우드 연동 | 로컬 파이프라인 |
| Accessibility Service | Play Store 정책 위반 리스크 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLUD-02 | Phase 14 | Complete (No-Go) |
| MINS-01 | Phase 15 | Complete |
| MINS-02 | Phase 15 | Pending |
| FILE-02 | Phase 16 | Pending |
| FILE-03 | Phase 16 | Complete |
| UI-01 | Phase 17 | Pending |
| UI-02 | Phase 17 | Pending |
| UI-03 | Phase 17 | Pending |
| AUTH-03 | Phase 18 | Pending |
| STT-01 | Phase 20/23 | Complete |
| STT-02 | Phase 23 | Complete |
| CARD-01 | Phase 24 | Complete |
| CARD-02 | Phase 24 | Complete |
| CARD-03 | Phase 24 | Complete |
| NAME-01 | Phase 25 | Pending |
| NAME-02 | Phase 25 | Pending |
| NAME-03 | Phase 26 | Pending |
| NAME-04 | Phase 26 | Pending |
| UX-01 | Phase 26 | Pending |
| DL-01 | Phase 27 | Pending |
| UX-02 | Phase 28 | Pending |

**Coverage:**

- v3.1 requirements: 10 total
- Mapped to phases: 10/10
- Unmapped: 0

---

*Requirements defined: 2026-03-26, v3.1 추가: 2026-03-28*
