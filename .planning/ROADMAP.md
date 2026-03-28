# Roadmap: Auto Minuting

## Milestones

- ✅ **v1.0 Auto Minuting MVP** — Phases 1-7 (shipped 2026-03-24)
- ✅ **v2.0 실동작 파이프라인 + 기능 확장** — Phases 8-13 (shipped 2026-03-26)
- ✅ **v2.1 안정화** — Phases 14-18 (shipped 2026-03-26)
- **v3.0 기능 확장 및 UX 개선** — Phases 19-23
- **v3.1 UX 개선 및 정보 표시 강화** — Phases 24-28

## Phases

### Phase 1: PoC — 기술 가능성 검증 (4/4 plans) — completed 2026-03-24
### Phase 2: 앱 기반 구조 (3/3 plans) — completed 2026-03-24
### Phase 3: 오디오 수집 (2/2 plans) — completed 2026-03-24
### Phase 4: 전사 엔진 (2/2 plans) — completed 2026-03-24
### Phase 5: 회의록 생성 (2/2 plans) — completed 2026-03-24
### Phase 6: 파이프라인 통합 및 자동화 (3/3 plans) — completed 2026-03-24
### Phase 7: UI 완성 (2/2 plans) — completed 2026-03-24
### Phase 8: 기반 강화 (2/2 plans) — completed 2026-03-25
### Phase 9: 삼성 공유 수신 (1/1 plans) — completed 2026-03-26
### Phase 10: NotebookLM 반자동 연동 (2/2 plans) — completed 2026-03-25
### Phase 11: 삼성 자동 감지 스파이크 (2/2 plans) — completed 2026-03-25
### Phase 12: Google OAuth 인증 (2/3 plans) — completed 2026-03-25
### Phase 13: Plaud BLE 실기기 디버깅 (2/3 plans) — completed 2026-03-25
### Phase 14: Plaud 프로토콜 분석 (2/2 plans) — completed 2026-03-26
### Phase 15: 수동 회의록 생성 (2/2 plans) — completed 2026-03-26
### Phase 16: 파일 삭제 (1/1 plans) — completed 2026-03-26
### Phase 17: UI 정리 (1/1 plans) — completed 2026-03-26
### Phase 18: OAuth 수정 (1/1 plans) — completed 2026-03-26
### Phase 19: 수동 회의록 생성 및 음성 공유 처리 (0/1 plans)

**Plans:** 1 plan

Plans:
- [ ] 19-01-PLAN.md — 전사 목록 수동 회의록 생성 UI + 음성 공유 intent-filter 정비

### Phase 20: 전사 목록 액션 메뉴 (삭제, 전사, 공유) (1/1 plans) — completed 2026-03-27

**Goal:** 전사 목록 카드에 DropdownMenu 기반 액션 메뉴(삭제, 재전사, 공유)를 추가하여 사용성 개선

**Plans:** 1/1 plans complete

Plans:
- [x] 20-01-PLAN.md — 전사 카드 MoreVert 드롭다운 메뉴 + ViewModel 재전사/공유 로직

**Post-phase fixes (GSD 외부 작업, 2026-03-28):**
- SpeechRecognizer→GeminiSttEngine 교체 (파일 전사 지원)
- 전사 목록 삭제를 deleteMeeting으로 변경 (전체 삭제)
- FAILED 상태에서 transcriptPath 없으면 회의록 작성 버튼 숨김
- AUDIO_RECEIVED 상태 전사 목록 표시 + 전사 대기 칩
- RECORD_AUDIO 권한 추가 + 런타임 권한 요청

### Phase 23: STT 엔진 선택 (Gemini/Whisper) — completed 2026-03-28

**Goal:** 사용자가 설정에서 STT 엔진(Gemini 클라우드 / Whisper 온디바이스)을 선택할 수 있도록 하여 전사 방식 유연성 제공

**Note:** GSD 워크플로우 외부에서 직접 구현 후 역추적 등록. NDK 빌드(Phase 4)는 미완료.

**구현 내용:**
- SttEngineType enum (GEMINI, WHISPER)
- UserPreferencesRepository STT 엔진 설정 (DataStore)
- TranscriptionRepositoryImpl: 선택 엔진 → 폴백 로직
- WhisperModelManager: 500MB 모델 다운로드/관리
- 설정 UI: STT 엔진 드롭다운 + Whisper 모델 다운로드

**Post-phase fixes (2026-03-28):**
- AudioConverter O(n^2) ByteArray 복사 → ByteArrayOutputStream (30분+ 오디오 OOM 방지)
- Gemini API 할당량 초과 시 20초 간격 최대 3회 자동 재시도
- 재전사 실패 시 기존 전사 파일 보존 + 이전 상태 복원

**잔여 작업:**
- [ ] whisper.cpp NDK 빌드 (libwhisper.so + JNI 브릿지)

### Phase 21: 회의록 목록 액션 메뉴 (삭제, 공유) — v3.1로 이관 (→ Phase 26)
### Phase 22: 설정 메뉴 및 테스트 도구 이동 — v3.1로 이관 (→ Phase 28)

## Phase Details (v3.1)

### Phase 24: 전사 카드 정보 표시
**Goal**: 전사 목록에서 각 카드의 파일 종류와 처리 상태를 한눈에 파악할 수 있다
**Depends on**: Nothing (기존 전사 목록 UI 위에 표시 요소 추가)
**Requirements**: CARD-01, CARD-02, CARD-03
**Success Criteria** (what must be TRUE):
  1. 사용자가 전사 카드에서 텍스트 공유인지 음성 파일인지 아이콘으로 구분할 수 있다
  2. 사용자가 전사 카드에서 전사 완료/미완료 상태를 배지로 확인할 수 있다
  3. 사용자가 전사 카드에서 회의록 작성 완료/미작성 상태를 배지로 확인할 수 있다
**Plans**: 1 plan

Plans:
- [x] 24-01-PLAN.md — 전사 카드 파일 종류 아이콘 + 전사/회의록 상태 배지 추가

**UI hint**: yes

### Phase 25: 전사 이름 관리
**Goal**: 전사 카드에 의미 있는 이름이 표시되고 사용자가 원하는 이름으로 변경할 수 있다
**Depends on**: Phase 24 (전사 카드 UI 변경이 선행되면 충돌 최소화)
**Requirements**: NAME-01, NAME-02
**Success Criteria** (what must be TRUE):
  1. 외부 앱에서 공유받은 파일의 원본 파일명이 전사 카드 제목에 자동으로 표시된다
  2. 사용자가 전사 카드의 이름을 탭하여 원하는 이름으로 편집할 수 있다
  3. 편집한 이름이 앱 재시작 후에도 유지된다
**Plans**: 1 plan

Plans:
- [x] 25-01-PLAN.md — 원본 파일명 자동 추출 + 이름 편집 다이얼로그

**UI hint**: yes

### Phase 26: 회의록 제목 및 액션
**Goal**: 회의록 카드에 내용 기반 자동 제목이 표시되고, 이름 편집과 삭제/공유 액션을 사용할 수 있다
**Depends on**: Phase 25 (이름 편집 UI 패턴 재사용)
**Requirements**: NAME-03, NAME-04, UX-01
**Success Criteria** (what must be TRUE):
  1. 회의록 생성 시 Gemini가 추출한 제목이 회의록 카드에 자동 표시된다
  2. 사용자가 회의록 카드의 이름을 원하는 이름으로 편집할 수 있다
  3. 사용자가 회의록 목록에서 삭제 액션으로 회의록을 삭제할 수 있다
  4. 사용자가 회의록 목록에서 공유 액션으로 회의록을 외부 앱에 공유할 수 있다
**Plans**: 2 plans

Plans:
- [x] 26-01-PLAN.md — minutesTitle 데이터 레이어 + Gemini 제목 추출
- [x] 26-02-PLAN.md — MinutesScreen MoreVert 메뉴 + 이름 편집 + minutesTitle 표시

**UI hint**: yes

### Phase 27: URL 음성 다운로드
**Goal**: 사용자가 URL을 입력하여 원격 음성 파일을 다운로드하고 전사 파이프라인에 진입시킬 수 있다
**Depends on**: Nothing (독립 기능)
**Requirements**: DL-01
**Success Criteria** (what must be TRUE):
  1. 사용자가 대시보드에서 URL 입력 필드에 음성 파일 URL을 붙여넣을 수 있다
  2. URL 입력 후 다운로드가 시작되고 진행 상태가 표시된다
  3. 다운로드 완료 후 자동으로 전사 파이프라인에 진입하여 전사가 시작된다
**Plans**: TBD
**UI hint**: yes

### Phase 28: 설정 정리
**Goal**: 설정 메뉴가 정리되고 테스트 도구 코드가 제거되어 앱이 깔끔해진다
**Depends on**: Nothing (독립 작업)
**Requirements**: UX-02
**Success Criteria** (what must be TRUE):
  1. 설정 화면의 메뉴 항목이 논리적으로 그룹화되어 표시된다
  2. spike 패키지의 테스트 도구 코드가 프로덕션 빌드에서 완전히 제거된다
**Plans**: TBD
**UI hint**: yes

## Milestone Details

- v1.0 (Phases 1-7): `.planning/milestones/v1.0-ROADMAP.md`
- v2.0 (Phases 8-13): `.planning/milestones/v2.0-ROADMAP.md`

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-7 | v1.0 | 18/18 | Complete | 2026-03-24 |
| 8-13 | v2.0 | 11/13 | Complete | 2026-03-26 |
| 14-18 | v2.1 | 7/7 | Complete | 2026-03-26 |
| 19-23 | v3.0 | 1/1 | In Progress | — |
| 24. 전사 카드 정보 표시 | v3.1 | 1/1 | Complete    | 2026-03-28 |
| 25. 전사 이름 관리 | v3.1 | 1/1 | Complete    | 2026-03-28 |
| 26. 회의록 제목 및 액션 | v3.1 | 2/2 | Complete    | 2026-03-28 |
| 27. URL 음성 다운로드 | v3.1 | 0/0 | Not started | - |
| 28. 설정 정리 | v3.1 | 0/0 | Not started | - |
