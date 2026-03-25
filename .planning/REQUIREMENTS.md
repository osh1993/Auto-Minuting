# Requirements: Auto Minuting

**Defined:** 2026-03-25
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## v2.0 Requirements

Requirements for v2.0 milestone. Each maps to roadmap phases.

### 파일 관리 (File Management)

- [ ] **FILE-01**: 사용자가 회의 레코드를 삭제하면 DB 레코드와 연관 파일(오디오, 전사, 회의록)이 함께 정리된다

### 삼성 녹음기 연동 (Samsung Recorder)

- [ ] **SREC-01**: 사용자가 삼성 녹음앱에서 전사 텍스트를 공유하면 Auto Minuting이 이를 수신하여 회의록 생성 파이프라인에 진입시킨다
- [ ] **SREC-02**: 삼성 녹음앱 전사 완료 시 자동 감지 가능성을 실기기에서 검증한다 (ContentObserver/FileObserver 스파이크, 48시간 타임박스)

### NotebookLM 연동

- [ ] **NLMK-01**: 사용자가 회의록을 NotebookLM 앱에 공유 Intent로 전달할 수 있다
- [ ] **NLMK-02**: 사용자가 앱 내에서 Custom Tabs로 NotebookLM 웹을 열 수 있다
- [ ] **NLMK-03**: NotebookLM MCP 서버 API를 통한 노트북 생성/소스 추가 기능의 앱 내 구현 가능성을 검토한다

### Gemini 인증 (Auth)

- [ ] **AUTH-01**: 사용자가 설정 화면에서 Gemini API 키를 입력/변경할 수 있고, 암호화되어 저장된다
- [ ] **AUTH-02**: 사용자가 Google OAuth로 로그인하여 API 키 없이 Gemini를 사용할 수 있다 (기술 검증 포함)

### Plaud BLE

- [ ] **PLUD-01**: Plaud 녹음기와 BLE 연결을 실기기에서 디버깅하여 실제 오디오 파일 수신이 동작하도록 한다

## Future Requirements

Deferred to future release. Tracked but not in current roadmap.

### 파일 관리

- **FILE-F01**: Room DB exportSchema 활성화 및 마이그레이션 인프라 정비

### 삼성 녹음기 연동

- **SREC-F01**: 삼성 녹음앱 자동 감지 본구현 (스파이크 결과에 따라 v2.1 이후)

### NotebookLM 연동

- **NLMK-F01**: MCP 서버 API 본구현 — 노트북 자동 생성, 소스 추가, 회의록 등록 (검토 결과에 따라)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| iOS 지원 | Android 전용 (Galaxy AI, BLE 시스템 통합) |
| 실시간 스트리밍 전사 | 녹음 완료 후 배치 처리 방식 |
| 자체 STT 엔진 훈련 | Whisper 사전훈련 모델 활용 |
| Accessibility Service 활용 | Google Play Store 정책 위반 리스크 |
| NotebookLM REST API 직접 호출 | 공식 API 미존재 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FILE-01 | — | Pending |
| SREC-01 | — | Pending |
| SREC-02 | — | Pending |
| NLMK-01 | — | Pending |
| NLMK-02 | — | Pending |
| NLMK-03 | — | Pending |
| AUTH-01 | — | Pending |
| AUTH-02 | — | Pending |
| PLUD-01 | — | Pending |

**Coverage:**

- v2.0 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9

---

*Requirements defined: 2026-03-25*
*Last updated: 2026-03-25 after initial definition*
