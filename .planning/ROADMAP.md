# Roadmap: Auto Minuting

## Milestones

- ✅ **v1.0 Auto Minuting MVP** — Phases 1-7 (shipped 2026-03-24)
- ✅ **v2.0 실동작 파이프라인 + 기능 확장** — Phases 8-13 (shipped 2026-03-26)
- 🚧 **v2.1 안정화 + UX 개선** — Phases 14-18 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-7) — SHIPPED 2026-03-24</summary>

- [x] Phase 1: PoC — 기술 가능성 검증 (4/4 plans) — completed 2026-03-24
- [x] Phase 2: 앱 기반 구조 (3/3 plans) — completed 2026-03-24
- [x] Phase 3: 오디오 수집 (2/2 plans) — completed 2026-03-24
- [x] Phase 4: 전사 엔진 (2/2 plans) — completed 2026-03-24
- [x] Phase 5: 회의록 생성 (2/2 plans) — completed 2026-03-24
- [x] Phase 6: 파이프라인 통합 및 자동화 (3/3 plans) — completed 2026-03-24
- [x] Phase 7: UI 완성 (2/2 plans) — completed 2026-03-24

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 실동작 파이프라인 + 기능 확장 (Phases 8-13) — SHIPPED 2026-03-26</summary>

- [x] Phase 8: 기반 강화 (2/2 plans) — completed 2026-03-25
- [x] Phase 9: 삼성 공유 수신 (1/1 plans) — completed 2026-03-26
- [x] Phase 10: NotebookLM 반자동 연동 (2/2 plans) — completed 2026-03-25
- [x] Phase 11: 삼성 자동 감지 스파이크 (2/2 plans) — completed 2026-03-25
- [x] Phase 12: Google OAuth 인증 (2/3 plans) — completed 2026-03-25
- [x] Phase 13: Plaud BLE 실기기 디버깅 (2/3 plans) — completed 2026-03-25

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

### 🚧 v2.1 안정화 + UX 개선

**Milestone Goal:** 실기기 테스트 피드백 반영, Plaud 연결 프로토콜 분석, 회의록/전사 관리 UX 개선

- [ ] **Phase 14: Plaud 연결 프로토콜 분석** - Plaud 앱의 실제 연결 방식을 리버스 엔지니어링하여 파악
- [ ] **Phase 15: 수동 회의록 생성 + 프롬프트 템플릿** - 전사 파일 기반 수동 회의록 생성과 프롬프트 템플릿 관리
- [ ] **Phase 16: 파일 삭제 개선** - 회의록 다중 삭제와 전사 파일 별도 삭제 기능
- [ ] **Phase 17: UI/UX 정리** - 앱 아이콘 교체, UI 레이아웃 개선, spike 코드 제거
- [ ] **Phase 18: OAuth 인증 수정** - Google OAuth Web Client ID 설정 오류 해결

## Phase Details

### Phase 14: Plaud 연결 프로토콜 분석
**Goal**: Plaud 녹음기가 실제로 어떤 프로토콜(BLE/Wi-Fi/USB 등)로 통신하는지 파악하여 향후 연결 본구현의 기초 자료를 확보한다
**Depends on**: Nothing (독립적 조사 작업)
**Requirements**: PLUD-02
**Success Criteria** (what must be TRUE):
  1. Plaud 앱이 녹음기와 통신할 때 사용하는 프로토콜(BLE/Wi-Fi/기타)이 식별된다
  2. 파일 전송 흐름(연결 → 파일 목록 조회 → 다운로드)의 단계별 동작이 문서화된다
  3. 분석 결과를 바탕으로 자체 구현 가능 여부에 대한 Go/No-Go 판정이 내려진다
**Plans**: TBD

### Phase 15: 수동 회의록 생성 + 프롬프트 템플릿
**Goal**: 사용자가 이미 전사된 파일을 선택하고 원하는 프롬프트로 회의록을 수동 생성할 수 있다
**Depends on**: Nothing (기존 전사/회의록 인프라 활용)
**Requirements**: MINS-01, MINS-02
**Success Criteria** (what must be TRUE):
  1. 사용자가 전사 완료된 파일 목록에서 하나를 선택하고 "회의록 생성" 버튼을 누를 수 있다
  2. 생성 시 프롬프트 템플릿을 선택하거나 수기로 프롬프트를 입력할 수 있다
  3. 프롬프트 템플릿을 추가/삭제/편집하는 관리 화면이 존재한다
  4. 수동 생성된 회의록이 기존 자동 생성 회의록과 동일하게 목록에 표시되고 열람된다
**Plans**: TBD
**UI hint**: yes

### Phase 16: 파일 삭제 개선
**Goal**: 사용자가 회의록과 전사 파일을 독립적으로 관리(삭제)할 수 있다
**Depends on**: Nothing (기존 삭제 인프라 확장)
**Requirements**: FILE-02, FILE-03
**Success Criteria** (what must be TRUE):
  1. 사용자가 회의록 목록에서 여러 항목을 다중 선택하여 한번에 삭제할 수 있다
  2. 회의록을 삭제해도 연결된 전사 파일은 보존된다
  3. 사용자가 전사 파일을 별도로 선택하여 삭제할 수 있다
**Plans**: TBD
**UI hint**: yes

### Phase 17: UI/UX 정리
**Goal**: 앱의 외관과 네비게이션을 정리하고 테스트용 코드를 제거하여 배포 품질에 도달한다
**Depends on**: Phase 15, Phase 16 (UI 변경이 겹치지 않도록)
**Requirements**: UI-01, UI-02, UI-03
**Success Criteria** (what must be TRUE):
  1. 앱 런처에 새로운 아이콘이 표시된다
  2. NotebookLM 열기 버튼이 메인 화면(홈 탭)에서 접근 가능하다
  3. spike 패키지 및 관련 코드가 프로젝트에서 완전히 제거되어 빌드에 포함되지 않는다
**Plans**: TBD
**UI hint**: yes

### Phase 18: OAuth 인증 수정
**Goal**: Google OAuth 로그인이 정상 동작하여 Gemini API를 OAuth 토큰으로 호출할 수 있다
**Depends on**: Nothing (독립적 버그 수정)
**Requirements**: AUTH-03
**Success Criteria** (what must be TRUE):
  1. Google 계정 로그인 버튼을 누르면 계정 선택 화면이 정상 표시된다
  2. 로그인 완료 후 Gemini API 호출이 OAuth 토큰으로 성공한다
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 14 → 15 → 16 → 17 → 18

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-7 | v1.0 | 18/18 | Complete | 2026-03-24 |
| 8-13 | v2.0 | 11/13 | Complete | 2026-03-26 |
| 14. Plaud 연결 프로토콜 분석 | v2.1 | 0/? | Not started | - |
| 15. 수동 회의록 생성 + 프롬프트 템플릿 | v2.1 | 0/? | Not started | - |
| 16. 파일 삭제 개선 | v2.1 | 0/? | Not started | - |
| 17. UI/UX 정리 | v2.1 | 0/? | Not started | - |
| 18. OAuth 인증 수정 | v2.1 | 0/? | Not started | - |
