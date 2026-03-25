# Roadmap: Auto Minuting

## Milestones

- ✅ **v1.0 Auto Minuting MVP** — Phases 1-7 (shipped 2026-03-24)
- 🚧 **v2.0 실동작 파이프라인 + 기능 확장** — Phases 8-13 (in progress)

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

### 🚧 v2.0 실동작 파이프라인 + 기능 확장 (In Progress)

**Milestone Goal:** 삼성 녹음기 전사 연동, NotebookLM 통합, 파일 관리, Gemini 인증 개선 후, Plaud BLE 실연결을 수정하여 실제 동작하는 앱으로 완성

- [ ] **Phase 8: 기반 강화** - 파일 삭제 + Gemini API 키 설정 UI + DB 마이그레이션 + 인증 추상화
- [ ] **Phase 9: 삼성 공유 수신** - 삼성 녹음앱 공유 Intent로 전사 텍스트를 파이프라인에 진입
- [x] **Phase 10: NotebookLM 반자동 연동** - 공유 Intent + Custom Tabs + MCP 검토 (2일 타임박스) (completed 2026-03-25)
- [x] **Phase 11: 삼성 자동 감지 스파이크** - ContentObserver/FileObserver 실기기 검증 (48시간 타임박스) (completed 2026-03-25)
- [ ] **Phase 12: Google OAuth 인증** - Credential Manager로 API 키 없이 Gemini 사용 경로 추가
- [ ] **Phase 13: Plaud BLE 실기기 디버깅** - BLE 연결 안정화 및 오디오 파일 수신 E2E 동작 확인

## Phase Details

### Phase 8: 기반 강화
**Goal**: 사용자가 회의 데이터를 정리하고 자신의 Gemini API 키로 앱을 독립적으로 사용할 수 있다
**Depends on**: Phase 7 (v1.0 완료)
**Requirements**: FILE-01, AUTH-01
**Success Criteria** (what must be TRUE):
  1. 사용자가 회의 레코드를 삭제하면 DB 레코드와 연관 파일(오디오, 전사, 회의록)이 모두 삭제된다
  2. 사용자가 설정 화면에서 Gemini API 키를 입력/변경할 수 있고, 앱 재시작 후에도 유지된다
  3. API 키가 암호화되어 저장되며, BuildConfig 하드코딩 없이 동적으로 사용된다
  4. Room DB v1에서 v2로 마이그레이션이 기존 데이터 손실 없이 수행된다
**Plans**: 2 plans
Plans:
- [x] 08-01-PLAN.md — DB 마이그레이션 (v1->v2, source 필드) + 회의 삭제 기능 (파일+DB 정합성, long-press UX)
- [ ] 08-02-PLAN.md — API 키 암호화 저장 (SecureApiKeyRepository) + GeminiEngine 인증 추상화 + 설정 UI
**UI hint**: yes

### Phase 9: 삼성 공유 수신
**Goal**: 사용자가 삼성 녹음앱에서 전사 텍스트를 공유하면 회의록이 자동 생성된다
**Depends on**: Phase 8
**Requirements**: SREC-01
**Success Criteria** (what must be TRUE):
  1. 삼성 녹음앱의 공유 버튼에서 Auto Minuting이 대상 앱으로 표시된다
  2. 공유된 전사 텍스트가 STT 단계를 건너뛰고 Gemini 회의록 생성 파이프라인에 진입한다
  3. 공유로 생성된 회의록이 기존 회의 목록에 출처(삼성 공유)와 함께 표시된다
**Plans**: 1 plans
Plans:
- [ ] 09-01-PLAN.md — ShareReceiverActivity 생성 + Manifest intent-filter 등록 + 출처 뱃지 UI

### Phase 10: NotebookLM 반자동 연동
**Goal**: 사용자가 회의록을 NotebookLM에 전달하여 AI 분석을 활용할 수 있다
**Depends on**: Phase 8
**Requirements**: NLMK-01, NLMK-02, NLMK-03
**Success Criteria** (what must be TRUE):
  1. 사용자가 회의록 화면에서 NotebookLM 앱으로 공유 Intent를 보낼 수 있다
  2. 사용자가 앱 내에서 Custom Tabs로 NotebookLM 웹을 열 수 있다
  3. MCP 서버 API를 통한 노트북 생성/소스 추가 가능성이 검토 문서로 정리된다
**Plans**: 2 plans
Plans:
- [ ] 10-01-PLAN.md — NotebookLM 공유 Intent + Custom Tabs 폴백 구현 (NotebookLmHelper + UI 버튼)
- [ ] 10-02-PLAN.md — MCP 서버 API 검토 문서 작성 (실동작 테스트 + Android 통합 가능성 평가)
**UI hint**: yes

### Phase 11: 삼성 자동 감지 스파이크
**Goal**: 삼성 녹음기 전사 완료 시 자동 감지 가능 여부가 실기기에서 검증된다
**Depends on**: Phase 9
**Requirements**: SREC-02
**Success Criteria** (what must be TRUE):
  1. 실기기에서 삼성 녹음앱 전사 파일 저장 경로와 MediaStore 등록 여부가 확인된다
  2. ContentObserver 또는 FileObserver로 전사 완료 이벤트 감지 가능 여부가 Go/No-Go로 판정된다
  3. Go 판정 시 프로토타입이 동작하고, No-Go 판정 시 Phase 9의 공유 방식이 기본 경로로 확정된다
**Plans**: 2 plans
Plans:
- [ ] 11-01-PLAN.md — ContentObserver + Foreground Service + 스파이크 로그 UI 프로토타입
- [ ] 11-02-PLAN.md — 실기기 검증 시나리오 실행 + Go/No-Go 판정 문서 작성

### Phase 12: Google OAuth 인증
**Goal**: 사용자가 Google 계정으로 로그인하여 API 키 입력 없이 Gemini를 사용할 수 있다
**Depends on**: Phase 8
**Requirements**: AUTH-02
**Success Criteria** (what must be TRUE):
  1. 사용자가 설정 화면에서 Google 계정으로 로그인할 수 있다
  2. OAuth 인증 시 API 키 없이 Gemini 회의록 생성이 동작한다
  3. API 키 모드와 OAuth 모드가 공존하며 사용자가 선택할 수 있다
**UI hint**: yes

### Phase 13: Plaud BLE 실기기 디버깅
**Goal**: Plaud 녹음기와 BLE 연결이 실기기에서 동작하여 오디오 파일을 수신한다
**Depends on**: Phase 8
**Requirements**: PLUD-01
**Success Criteria** (what must be TRUE):
  1. 실기기에서 Plaud 녹음기 BLE 스캔 및 연결이 성공한다
  2. 연결된 녹음기에서 오디오 파일이 앱으로 전송되어 로컬에 저장된다
  3. Plaud BLE 수신 -> Whisper STT -> Gemini 회의록 E2E 파이프라인이 동작한다

## Progress

**Execution Order:**
Phases execute in numeric order: 8 -> 9 -> 10 -> 11 -> 12 -> 13

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. PoC — 기술 가능성 검증 | v1.0 | 4/4 | Complete | 2026-03-24 |
| 2. 앱 기반 구조 | v1.0 | 3/3 | Complete | 2026-03-24 |
| 3. 오디오 수집 | v1.0 | 2/2 | Complete | 2026-03-24 |
| 4. 전사 엔진 | v1.0 | 2/2 | Complete | 2026-03-24 |
| 5. 회의록 생성 | v1.0 | 2/2 | Complete | 2026-03-24 |
| 6. 파이프라인 통합 및 자동화 | v1.0 | 3/3 | Complete | 2026-03-24 |
| 7. UI 완성 | v1.0 | 2/2 | Complete | 2026-03-24 |
| 8. 기반 강화 | v2.0 | 1/2 | In Progress|  |
| 9. 삼성 공유 수신 | v2.0 | 0/1 | Not started | - |
| 10. NotebookLM 반자동 연동 | v2.0 | 0/2 | Complete    | 2026-03-25 |
| 11. 삼성 자동 감지 스파이크 | v2.0 | 0/2 | Complete    | 2026-03-25 |
| 12. Google OAuth 인증 | v2.0 | 0/? | Not started | - |
| 13. Plaud BLE 실기기 디버깅 | v2.0 | 0/? | Not started | - |
