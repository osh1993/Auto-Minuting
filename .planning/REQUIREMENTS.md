# Requirements: v7.0 UX 개선 + Google Drive 연동

**Defined:** 2026-04-03
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## Milestone Goal

이름 변경 UX 개선, Google Drive 자동 업로드 파이프라인, 회의록 편집 기능, Groq 버그 수정, API 사용량 대시보드, 설정 화면 정비를 통해 실사용 완성도를 높인다.

---

## v7.0 Requirements

### UX 개선

- [ ] **UX-01**: 사용자가 전사목록/회의록 탭에서 카드를 터치하면 해당 파일 상세 화면으로 이동할 수 있다
- [ ] **UX-02**: 사용자가 전사목록/회의록 탭에서 점3개(overflow) 메뉴를 통해 이름을 변경할 수 있다

### Google Drive 연동

- [x] **DRIVE-01**: 사용자가 설정 화면에서 Google 계정으로 로그인/로그아웃할 수 있다 (OAuth 2.0)
- [x] **DRIVE-02**: 전사 파일(BLE 수신 파일 및 공유받은 파일)이 파이프라인 완료 후 설정된 Google Drive 폴더에 자동으로 업로드된다
- [x] **DRIVE-03**: 생성된 회의록 파일이 파이프라인 완료 후 설정된 Google Drive 폴더에 자동으로 업로드된다
- [x] **DRIVE-04**: 사용자가 설정 화면에서 전사 파일 업로드 폴더와 회의록 파일 업로드 폴더를 각각 지정할 수 있다

### 텍스트 편집

- [x] **EDIT-01**: 사용자가 회의록 상세 화면에서 텍스트를 편집하고 저장할 수 있다

### 버그 수정

- [ ] **BUG-01**: Groq Whisper STT 엔진이 정상적으로 한국어 전사를 수행한다

### API 사용량 대시보드

- [ ] **DASH-01**: 사용자가 전용 탭/화면에서 엔진별 API 호출 횟수와 예상 비용을 확인할 수 있다

### 설정 UI 정비

- [ ] **SETTINGS-01**: 설정 화면의 구조와 정보 배치가 분석되고 수정안이 제시된 후 승인을 거쳐 적용된다

---

## Future Requirements

- 실시간 스트리밍 전사 — 현재 배치 처리 방식 유지
- iOS 지원 — Android 전용
- 커스텀 Gemini 프롬프트 — v1에서 3종 프리셋으로 고정

## Out of Scope

- Google Drive 파일 목록 탐색 UI — 폴더 ID 직접 입력 또는 기본 폴더 사용
- 오프라인 Drive 캐싱 — 네트워크 연결 시 즉시 업로드
- 다른 클라우드 스토리지(OneDrive, Dropbox 등) — Google Drive만 지원

---

## Traceability

| REQ-ID | Phase | Status |
| ------ | ----- | ------ |
| UX-01 | Phase 43 | Pending |
| UX-02 | Phase 43 | Pending |
| DRIVE-01 | Phase 45 | Complete |
| DRIVE-02 | Phase 46 | Complete |
| DRIVE-03 | Phase 46 | Complete |
| DRIVE-04 | Phase 46 | Complete |
| EDIT-01 | Phase 47 | Complete |
| BUG-01 | Phase 44 | Pending |
| DASH-01 | Phase 48 | Pending |
| SETTINGS-01 | Phase 49 | Pending |
