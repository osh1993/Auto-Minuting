# Requirements: Auto Minuting

**Defined:** 2026-03-26
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## v2.1 Requirements

Requirements for v2.1 milestone. Each maps to roadmap phases.

### Plaud 연결 분석

- [x] **PLUD-02**: Plaud 앱의 녹음기 연결 프로토콜을 리버스 엔지니어링하여 연결 방식(BLE/Wi-Fi/기타)을 파악한다 *(No-Go 판정: E2EE + 서버 인증으로 SDK 의존 유지)*

### 회의록 생성 (Minutes)

- [ ] **MINS-01**: 사용자가 전사 파일을 선택하고 생성 버튼으로 회의록을 수동 생성할 수 있다 (프롬프트 템플릿 선택 또는 수기 입력)
- [ ] **MINS-02**: 사용자가 프롬프트 템플릿을 추가/삭제/편집하여 관리할 수 있다

### 파일 관리 (File)

- [ ] **FILE-02**: 사용자가 회의록을 다중 선택하여 삭제할 수 있고, 전사 파일은 보존된다
- [ ] **FILE-03**: 사용자가 전사 파일을 별도로 삭제할 수 있다

### UI/UX 개선

- [ ] **UI-01**: 앱 아이콘이 새롭게 디자인되어 교체된다
- [ ] **UI-02**: NotebookLM 열기 버튼이 설정 화면에서 메인 화면으로 이동한다
- [ ] **UI-03**: 테스트 도구(spike 패키지) 코드가 삭제된다

### 인증 수정

- [ ] **AUTH-03**: Google OAuth Web Client ID 미설정 오류가 해결되어 Google 계정 로그인이 동작한다

## Future Requirements

Deferred to future release.

- **PLUD-F01**: Plaud 리버스 엔지니어링 결과에 따른 연결 본구현
- **SREC-F01**: 삼성 녹음앱 자동 감지 본구현 (v2.0 스파이크 Partial Go)
- **NLMK-F01**: MCP 서버 API 본구현 (REST API 미존재 — 대기)

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
| MINS-01 | Phase 15 | Pending |
| MINS-02 | Phase 15 | Pending |
| FILE-02 | Phase 16 | Pending |
| FILE-03 | Phase 16 | Pending |
| UI-01 | Phase 17 | Pending |
| UI-02 | Phase 17 | Pending |
| UI-03 | Phase 17 | Pending |
| AUTH-03 | Phase 18 | Pending |

**Coverage:**

- v2.1 requirements: 9 total
- Mapped to phases: 9
- Unmapped: 0

---

*Requirements defined: 2026-03-26*
