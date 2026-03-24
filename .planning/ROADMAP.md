# Roadmap: Auto Minuting

## Overview

Plaud 녹음기에서 회의록까지의 자동화 파이프라인을 단계적으로 구축한다. 먼저 3개 핵심 외부 의존성(Plaud BLE, Galaxy AI, NotebookLM)의 기술 가능성을 검증한 뒤, 앱 기반 구조를 쌓고, 파이프라인 각 단계(오디오 수집 → 전사 → 회의록 생성)를 순서대로 구현하고, 마지막에 통합·자동화·UI 폴리싱으로 완성한다.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: PoC — 기술 가능성 검증** - 3개 핵심 외부 의존성의 기술 가능성을 검증하고 아키텍처를 확정한다 (completed 2026-03-24)
- [ ] **Phase 2: 앱 기반 구조** - Clean Architecture 뼈대, Room DB, DI, Compose Navigation 설정
- [ ] **Phase 3: 오디오 수집** - Plaud 녹음기에서 오디오 파일을 자동으로 로컬에 저장
- [ ] **Phase 4: 전사 엔진** - 저장된 오디오를 텍스트로 전사하고 로컬에 저장
- [ ] **Phase 5: 회의록 생성** - 전사 텍스트를 AI로 처리하여 구조화된 회의록 생성 및 저장
- [ ] **Phase 6: 파이프라인 통합 및 자동화** - 전체 파이프라인 자동 체이닝, 형식 선택, 공유, 진행 알림
- [ ] **Phase 7: UI 완성** - 회의록 뷰어, 아카이브·검색, 전반적인 UX 폴리싱

## Phase Details

### Phase 1: PoC — 기술 가능성 검증
**Goal**: 3개 핵심 외부 의존성(Plaud 오디오 수집 방법, STT 접근 경로, NotebookLM/Gemini 회의록 생성)의 기술 가능성을 확인하고 아키텍처 경로를 확정한다
**Depends on**: Nothing (first phase)
**Requirements**: POC-01, POC-02, POC-03, POC-04
**Success Criteria** (what must be TRUE):
  1. Plaud 오디오 파일을 Android에서 가져올 수 있는 방법(BLE 직접 / FileObserver / APK 분석)이 하나 이상 확인된다
  2. 한국어 음성 파일을 프로그래밍적으로 텍스트로 전사할 수 있는 방법(Galaxy AI / ML Kit / Whisper)이 하나 이상 확인된다
  3. 텍스트를 입력으로 회의록을 생성할 수 있는 방법(NotebookLM MCP / Gemini API)이 하나 이상 확인된다
  4. 각 의존성별 채택 경로와 폴백 경로가 결정되어 PROJECT.md Key Decisions에 기록된다
**Plans:** 4/4 plans complete
Plans:
- [x] 01-01-PLAN.md — Plaud 오디오 파일 획득 경로 검증 (APK 분석 + SDK 평가 + Cloud API)
- [x] 01-02-PLAN.md — STT 전사 경로 검증 (Galaxy AI 조사 + ML Kit/Whisper 평가)
- [x] 01-03-PLAN.md — 회의록 생성 경로 검증 (Gemini API + NotebookLM MCP)
- [x] 01-04-PLAN.md — 3개 의존성 종합 결정 문서화 및 PROJECT.md 업데이트

### Phase 2: 앱 기반 구조
**Goal**: 이후 모든 파이프라인 구현이 올라갈 수 있는 Clean Architecture 앱 뼈대가 동작한다
**Depends on**: Phase 1
**Requirements**: APP-01, APP-02, APP-03, APP-04
**Success Criteria** (what must be TRUE):
  1. Android 앱이 빌드되어 기기에 설치되고 4개 빈 화면(대시보드/전사목록/회의록목록/설정)이 탐색 가능하다
  2. Room DB가 초기화되고 MeetingEntity 및 PipelineStatus 상태 머신 스키마가 마이그레이션 없이 동작한다
  3. Hilt DI 그래프가 컴파일 타임 오류 없이 구성된다
  4. WorkManager가 초기화되어 테스트 Worker가 백그라운드에서 실행·완료된다
**Plans:** 3 plans
Plans:
- [x] 02-01-PLAN.md — Gradle 프로젝트 초기화 + Clean Architecture 패키지 + Hilt DI
- [x] 02-02-PLAN.md — Room DB 스키마 + Domain 인터페이스 + Repository DI 바인딩
- [x] 02-03-PLAN.md — Compose Navigation + 4개 빈 화면 + WorkManager 초기화

### Phase 3: 오디오 수집
**Goal**: Plaud 녹음기에서 전송되는 오디오 파일이 앱 내부 저장소에 자동으로 저장된다
**Depends on**: Phase 2
**Requirements**: AUD-01, AUD-02, AUD-03
**Success Criteria** (what must be TRUE):
  1. Plaud 녹음기를 연결하면 오디오 파일이 앱 내부 저장소에 저장된다
  2. 오디오 수집이 Foreground Service로 백그라운드에서 동작하며 앱을 닫아도 계속 실행된다
  3. 새 오디오 파일이 감지되면 파이프라인 다음 단계(전사)가 자동으로 시작된다
**Plans:** 2 plans
Plans:
- [x] 03-01-PLAN.md — Gradle 의존성 + Plaud SDK/Cloud API 데이터 레이어 + AudioRepository 구현
- [x] 03-02-PLAN.md — Foreground Service + TranscriptionTriggerWorker + Application SDK 초기화

### Phase 4: 전사 엔진
**Goal**: 저장된 한국어 오디오 파일이 텍스트로 전사되어 로컬에 저장되며 사용자가 내용을 확인·수정할 수 있다
**Depends on**: Phase 3
**Requirements**: STT-01, STT-02, STT-03
**Success Criteria** (what must be TRUE):
  1. 저장된 오디오 파일이 한국어 텍스트로 전사되어 로컬에 저장된다
  2. 전사 완료 후 사용자가 전사 텍스트 화면에서 내용을 수정하고 저장할 수 있다
  3. STT 엔진 실패 시 대안 엔진(폴백)으로 자동 전환되어 전사가 완료된다
**Plans:** 2 plans
Plans:
- [x] 04-01-PLAN.md — Whisper/ML Kit STT 엔진 + TranscriptionRepositoryImpl + Worker 연동
- [x] 04-02-PLAN.md — 전사 목록 화면 + 전사 편집 화면 + Navigation 연동

### Phase 5: 회의록 생성
**Goal**: 전사된 텍스트가 AI를 통해 구조화된 회의록으로 변환되어 로컬에 저장된다
**Depends on**: Phase 4
**Requirements**: MIN-01, MIN-02, MIN-03, MIN-04
**Success Criteria** (what must be TRUE):
  1. 전사 텍스트가 NotebookLM 또는 Gemini API에 전달되어 회의록이 생성된다
  2. 사용자가 기존 노트 또는 새 노트를 선택하여 소스를 등록할 수 있다
  3. 생성된 회의록이 스마트폰 로컬에 파일로 저장된다
  4. 저장된 회의록을 앱 내에서 텍스트로 읽을 수 있다
**Plans:** 1/2 plans executed
Plans:
- [x] 05-01-PLAN.md — Gemini API 데이터 레이어 + MinutesRepositoryImpl + MinutesGenerationWorker
- [ ] 05-02-PLAN.md — 회의록 목록 화면 + 회의록 상세 읽기 화면 + Navigation 연동

### Phase 6: 파이프라인 통합 및 자동화
**Goal**: 오디오 감지부터 회의록 저장까지 전체 파이프라인이 사용자 개입 없이 자동으로 완료되며, 형식 선택·공유·진행 알림이 동작한다
**Depends on**: Phase 5
**Requirements**: MIN-05, MIN-06, UI-02, UI-04
**Success Criteria** (what must be TRUE):
  1. Plaud 녹음기를 연결하면 앱 개입 없이 회의록이 자동 생성·저장된다 (완전 자동 모드)
  2. 하이브리드 모드에서 각 파이프라인 단계마다 사용자 확인 후 다음 단계가 진행된다
  3. 파이프라인 진행 중 각 단계별 진행률이 알림으로 표시된다
  4. 회의록 형식(구조화된 회의록 / 요약 / 커스텀 템플릿)을 선택할 수 있다
  5. 생성된 회의록을 Android Share Intent로 외부 앱(카카오톡, 이메일 등)에 공유할 수 있다
**Plans:** 3 plans
Plans:
- [ ] 06-01-PLAN.md — 도메인 모델 + DataStore 설정 + GeminiEngine 프롬프트 확장 + 알림 헬퍼
- [ ] 06-02-PLAN.md — Worker 하이브리드 분기 + 형식 전달 + 알림 업데이트 + BroadcastReceiver
- [ ] 06-03-PLAN.md — 설정 화면 UI + 회의록 공유 + 대시보드 진행 배너
**UI hint**: yes

### Phase 7: UI 완성
**Goal**: 회의록 뷰어, 과거 아카이브 검색, 전반적인 UX가 완성되어 앱이 일상 사용에 적합한 수준이 된다
**Depends on**: Phase 6
**Requirements**: UI-01, UI-03
**Success Criteria** (what must be TRUE):
  1. 생성된 회의록이 구조화된 뷰어에서 읽기 쉽게 표시된다
  2. 과거 회의록 목록에서 제목/날짜/키워드로 검색하고 원하는 회의록을 열 수 있다
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. PoC — 기술 가능성 검증 | 4/4 | Complete   | 2026-03-24 |
| 2. 앱 기반 구조 | 0/3 | Not started | - |
| 3. 오디오 수집 | 0/2 | Not started | - |
| 4. 전사 엔진 | 1/2 | In progress | - |
| 5. 회의록 생성 | 1/2 | In Progress|  |
| 6. 파이프라인 통합 및 자동화 | 0/3 | Not started | - |
| 7. UI 완성 | 0/TBD | Not started | - |
