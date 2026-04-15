# Roadmap: Auto Minuting

## Milestones

- ✅ **v1.0 Auto Minuting MVP** — Phases 1-7 (shipped 2026-03-24)
- ✅ **v2.0 실동작 파이프라인 + 기능 확장** — Phases 8-13 (shipped 2026-03-26)
- ✅ **v2.1 안정화** — Phases 14-18 (shipped 2026-03-26)
- ✅ **v3.0 기능 확장 및 UX 개선** — Phases 19-23 (shipped 2026-03-28; Phase 19 폐기됨)
- ✅ **v3.1 UX 개선 및 정보 표시 강화** — Phases 24-28 (shipped 2026-03-29)
- ✅ **v4.0 파이프라인 고도화 및 GUI 품질 개선** — Phases 29-35 (shipped 2026-03-30)
- ✅ **v5.0 전사-회의록 독립 아키텍처** — Phases 36-38
- ✅ **v6.0 멀티 엔진 확장** — Phases 39-42 (shipped 2026-04-03)
- ✅ **v7.0 Drive 연동 + UX 개선** — Phases 43-49 (shipped 2026-04-05)
- ✅ **v8.0 다중 파일 합치기** — Phase 50 (shipped 2026-04-06) — [archive](.planning/milestones/v8.0-ROADMAP.md)
- [ ] **v9.0 다중 Gemini 계정 + 파일 입력 확장 + Groq 대용량 처리** — Phases 51-55

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
### Phase 19: 수동 회의록 생성 및 음성 공유 처리 — **폐기됨**

**폐기 사유:**
- 수동 회의록 생성 UI → Phase 20, 26, 29에서 완전히 구현됨 (MoreVert 메뉴, 회의록 생성 다이얼로그)
- 음성 공유 수신 → Phase 9(삼성 공유), Phase 32(Plaud 링크)에서 구현됨
- 별도 구현 불필요 — 모든 목표 기능이 다른 Phase에서 달성됨

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
- [x] whisper.cpp NDK 빌드 (libwhisper.so + JNI 브릿지) — 완료 2026-03-29

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
**Plans**: 1 plan

Plans:
- [x] 27-01-PLAN.md — URL 음성 다운로드 + 전사 파이프라인 진입

**UI hint**: yes

### Phase 28: 설정 정리
**Goal**: 설정 메뉴가 정리되고 테스트 도구 코드가 제거되어 앱이 깔끔해진다
**Depends on**: Nothing (독립 작업)
**Requirements**: UX-02
**Success Criteria** (what must be TRUE):
  1. 설정 화면의 메뉴 항목이 논리적으로 그룹화되어 표시된다
  2. 대시보드의 테스트 도구 코드가 프로덕션 빌드에서 완전히 제거된다
**Plans:** 1/1 plans complete

Plans:
- [x] 28-01-PLAN.md — 설정 화면 섹션 그룹화 + 대시보드 테스트 도구 제거

**UI hint**: yes

## Phase Details (v4.0)

### Phase 29: 전사 카드 UX 개선
**Goal**: 하이브리드 모드에서 전사 완료 후 회의록 생성 확인 플로우가 추가되고, 전사 카드의 회의록 관련 버튼이 메뉴로 정리된다
**Depends on**: Nothing (기존 전사 카드 UI 위에 동작 변경)
**Requirements**: PIPE-01, PIPE-02
**Success Criteria** (what must be TRUE):
  1. 하이브리드 모드에서 전사가 완료되면 대시보드에 "회의록 생성" 확인 버튼이 나타난다
  2. 사용자가 확인 버튼을 누르면 회의록 생성이 시작되고, 무시하면 전사만 유지된다
  3. 전사 카드의 "회의록 작성" 및 "재생성" 버튼이 MoreVert 드롭다운 메뉴 안에 위치한다
  4. 전사 카드의 카드 면적이 줄어들어 목록 가독성이 향상된다
**Plans**: 1 plan

Plans:
- [x] 29-01-PLAN.md — 대시보드 하이브리드 확인 플로우 + 전사 카드 회의록 버튼 메뉴 이동

**UI hint**: yes

### Phase 30: 프롬프트 템플릿 선택
**Goal**: 회의록 생성 시 프롬프트 템플릿을 선택할 수 있고, 기본 템플릿 설정으로 자동 생성이 가능하다
**Depends on**: Phase 29 (회의록 생성 버튼 동작이 변경되므로 순서 필요)
**Requirements**: PIPE-03, PIPE-04
**Success Criteria** (what must be TRUE):
  1. 회의록 생성 시 프롬프트 템플릿 목록이 다이얼로그로 표시되어 사용자가 선택할 수 있다
  2. 기존 3종 프리셋(구조화/요약/액션아이템)이 템플릿 목록에 포함된다
  3. 설정에서 기본 프롬프트 템플릿을 지정하면 다이얼로그 없이 자동으로 해당 템플릿으로 생성된다
  4. 자동 모드에서도 기본 템플릿 설정에 따라 회의록이 자동 생성된다
**Plans**: 1 plan

Plans:
- [x] 30-01-PLAN.md — 프롬프트 템플릿 선택 다이얼로그 + 기본 템플릿 DataStore 설정 + 자동/수동 파이프라인 연동

**UI hint**: yes

### Phase 31: Gemini 쿼터 관리
**Goal**: 사용자가 Gemini Free 쿼터 사용량을 확인하고 한도 초과 전에 알림을 받을 수 있다
**Depends on**: Nothing (독립 기능, 대시보드에 위젯 추가)
**Requirements**: QUOTA-01, QUOTA-02
**Success Criteria** (what must be TRUE):
  1. 대시보드에서 Gemini Free 쿼터의 현재 사용량과 잔여량을 확인할 수 있다
  2. 쿼터 사용량이 90%를 초과하면 알림(Snackbar 또는 배너)이 표시된다
  3. 쿼터 표시가 전사(STT)와 회의록 생성(Minutes) 호출을 구분하여 보여준다
**Plans:** 1/1 plans complete

Plans:
- [x] 31-01-PLAN.md — GeminiQuotaTracker 데이터 레이어 + 대시보드 쿼터 위젯 + 90% 경고 배너

**UI hint**: yes

### Phase 32: Plaud 공유 링크 수신
**Goal**: 다른 앱에서 Plaud 공유 링크를 공유받으면 오디오를 자동 추출하여 전사 파이프라인에 진입시킨다
**Depends on**: Nothing (독립 기능, 기존 공유 수신 인프라 활용)
**Requirements**: SHARE-01
**Success Criteria** (what must be TRUE):
  1. 다른 앱에서 web.plaud.ai 링크를 공유하면 앱이 수신 대상으로 표시된다
  2. 공유받은 링크에서 오디오 파일 URL이 자동 추출된다
  3. 추출된 오디오가 다운로드되어 전사 파이프라인에 자동 진입한다
**Plans**: 1 plan

Plans:
- [x] 32-01-PLAN.md — Plaud 공유 링크 수신 + WebView 오디오 추출 + 전사 파이프라인 진입

### Phase 33: GUI 일관성 개선
**Goal**: 앱 전반의 GUI가 일관되고 접근성 표준을 준수한다
**Depends on**: Phase 29 (전사 카드 UI 변경 후 TopAppBar 등 적용)
**Requirements**: GUI-01, GUI-02, GUI-03, GUI-04
**Success Criteria** (what must be TRUE):
  1. DashboardScreen과 TranscriptsScreen에 TopAppBar가 표시되어 다른 화면과 일관된 헤더를 갖는다
  2. 앱의 모든 아이콘에 적절한 contentDescription이 설정되어 TalkBack으로 읽힌다
  3. 전사 목록과 회의록 목록의 빈 상태가 아이콘+안내 텍스트로 통일되어 표시된다
  4. 전사 목록과 회의록 목록의 날짜가 yyyy.MM.dd HH:mm 포맷으로 통일되어 표시된다
**Plans**: 1 plan

Plans:
- [x] 33-01-PLAN.md — TopAppBar 추가 + contentDescription + 빈 상태 통일 + 날짜 포맷 통일

**UI hint**: yes

### Phase 34: Whisper 전사 진행률 표시 — completed 2026-03-30
**Goal**: Whisper 온디바이스 전사 시 진행률이 알림과 UI에 실시간 표시된다
**Requirements**: (v4.0 추가)
**Depends on**: Phase 33
**Plans**: 2/2 plans complete

Plans:
- [x] 34-01-PLAN.md — JNI progress_callback + Worker 진행률 + 알림 표시
- [x] 34-02-PLAN.md — DashboardScreen/TranscriptsScreen 진행률 UI

### Phase 35: 회의록 설정 구조 개편 — completed 2026-03-30
**Goal**: 설정 화면의 회의록 관련 설정이 논리적으로 재배치되고, 레거시 코드가 제거되며, 직접 입력 프롬프트를 기본으로 설정할 수 있다
**Requirements**: SET-01, SET-02, SET-03
**Depends on**: Phase 34
**Plans**: 3/3 plans complete

Plans:
- [x] 35-01-PLAN.md — MinutesFormat 전면 제거 + 자동모드 회의록 설정 이동
- [x] 35-02-PLAN.md — 직접 입력 커스텀 프롬프트 기본 설정 기능
- [x] 35-03-PLAN.md — MinutesFormat 참조 제거 및 자동모드 Switch 이동 (gap closure)

## Phase Details (v5.0)

### Phase 36: Minutes 데이터 모델 분리
**Goal**: 회의록이 전사(Meeting)와 독립된 테이블에 저장되어 1:N 관계로 관리된다
**Depends on**: Nothing (데이터 레이어 리팩토링, 기존 기능 위에 작업)
**Requirements**: DATA-01, DATA-02
**Success Criteria** (what must be TRUE):
  1. Room DB에 Minutes 테이블이 존재하고 meetingId FK로 Meeting과 연결된다
  2. 하나의 전사에서 회의록을 생성하면 Minutes 테이블에 별도 Row로 저장된다
  3. 같은 전사에서 회의록을 다시 생성하면 기존 회의록 Row는 유지되고 새 Row가 추가된다
  4. 기존 데이터(minutesPath/minutesTitle)가 v5 마이그레이션으로 Minutes 테이블에 이관된다
  5. Meeting 테이블에서 minutesPath/minutesTitle 컬럼이 제거되고 MINUTES_ONLY 워크어라운드가 정리된다
**Plans**: 3 plans

Plans:
- [x] 36-01-PLAN.md — MinutesEntity/MinutesDao/Minutes 도메인 모델 신설 + Room DB v5 마이그레이션 + MeetingEntity/MeetingDao/PipelineStatus 정리
- [x] 36-02-PLAN.md — MinutesDataRepository 신설 + MeetingRepository 정리 + MinutesGenerationWorker/TranscriptsViewModel 로직 교체
- [x] 36-03-PLAN.md — MinutesViewModel/MinutesDetailViewModel + Screen/Navigation을 Minutes 테이블 기반으로 교체 + 빌드 검증

### Phase 37: 전사-회의록 독립 삭제
**Goal**: 전사와 회의록을 각각 독립적으로 삭제하거나 재생성해도 상대방에 영향이 없다
**Depends on**: Phase 36 (Minutes 테이블이 분리되어야 독립 삭제 로직 구현 가능)
**Requirements**: IND-01, IND-02, IND-03
**Success Criteria** (what must be TRUE):
  1. 전사를 삭제하면 Meeting Row와 오디오/전사 파일이 삭제되지만 연결된 Minutes Row와 회의록 파일은 그대로 남아있다
  2. 회의록을 삭제하면 해당 Minutes Row와 회의록 파일만 삭제되고 Meeting 상태와 전사 파일은 변경되지 않는다
  3. 전사에서 회의록을 재생성하면 기존 회의록 목록에 새 회의록이 추가되어 여러 버전이 공존한다
  4. 모든 회의록을 삭제한 전사가 정상적으로 전사 목록에 표시되고 다시 회의록을 생성할 수 있다
**Plans**: 1 plan

Plans:
- [x] 37-01-PLAN.md — 재생성 다이얼로그 텍스트 수정 + assembleDebug 빌드 검증

### Phase 38: 독립 아키텍처 UI 반영
**Goal**: 전사-회의록 1:N 독립 구조가 화면에 반영되어 사용자가 관계를 직관적으로 파악할 수 있다
**Depends on**: Phase 37 (독립 삭제/재생성 로직이 완성된 후 UI 반영)
**Requirements**: UI5-01, UI5-02
**Success Criteria** (what must be TRUE):
  1. 회의록 목록 화면에서 각 회의록이 독립 카드로 표시되며 출처 전사 이름이 표기된다
  2. 같은 전사에서 생성된 여러 회의록이 각각 별도 카드로 나뉜다
  3. 전사 목록 화면의 카드에 연결된 회의록 수가 badge로 표시된다 (0개면 badge 미표시)
  4. 회의록 카드에서 출처 전사를 탭하면 해당 전사 상세로 이동할 수 있다
**Plans:** 2/2 plans complete

Plans:
- [x] 38-01-PLAN.md — MinutesDao JOIN/count 쿼리 + Repository/ViewModel 데이터 계약
- [x] 38-02-PLAN.md — MinutesScreen 출처 전사 표시 + TranscriptsScreen badge + assembleDebug 빌드 검증

**UI hint**: yes

## Phase Details (v6.0)

### Phase 39: STT 엔진 확장 (Groq / Deepgram / Naver CLOVA)
**Goal**: 사용자가 Groq Whisper, Deepgram Nova-3, Naver CLOVA Speech 중 하나를 STT 엔진으로 선택하여 전사할 수 있다
**Depends on**: Nothing (기존 SttEngine 인터페이스 확장)
**Requirements**: STT6-01, STT6-02, STT6-03, STT6-04
**Success Criteria** (what must be TRUE):
  1. 설정에서 STT 엔진 목록에 Groq / Deepgram / Naver CLOVA가 표시된다
  2. 각 엔진을 선택 후 해당 API 키를 입력하면 실제 전사가 동작한다
  3. 기존 Gemini / Whisper 온디바이스 엔진은 그대로 동작한다
**Plans**: 3 plans

Plans:
- [ ] 39-01-PLAN.md — GroqSttEngine 구현 (multipart/form-data, REST)
- [ ] 39-02-PLAN.md — DeepgramSttEngine 구현 (REST, Nova-3 한국어)
- [ ] 39-03-PLAN.md — NaverClovaSttEngine 구현 (REST, NEST 모델) + SttEngineType enum 확장 + TranscriptionRepositoryImpl 선택 로직 + Hilt 바인딩

### Phase 40: 회의록 엔진 확장 (Deepgram Intelligence / Naver CLOVA Summary)
**Goal**: 사용자가 Deepgram Audio Intelligence 또는 Naver CLOVA Summary를 회의록 엔진으로 선택할 수 있다
**Depends on**: Phase 39 (API 키 관리 패턴 재사용)
**Requirements**: MIN6-01, MIN6-02, MIN6-03
**Success Criteria** (what must be TRUE):
  1. 설정에서 회의록 엔진 목록에 Deepgram Intelligence / Naver CLOVA Summary가 표시된다
  2. 각 엔진이 전사 텍스트를 받아 요약 결과를 회의록으로 저장한다
  3. 기존 Gemini 회의록 엔진은 그대로 동작한다
**Plans**: 2 plans

Plans:
- [ ] 40-01-PLAN.md — DeepgramMinutesEngine 구현 (Text Intelligence /v1/read, 영어 지원 안내 포함)
- [ ] 40-02-PLAN.md — NaverClovaMinutesEngine 구현 (CLOVA Summary API) + MinutesEngineType enum 신설 + MinutesEngineSelector 확장 + Hilt 바인딩

### Phase 41: 설정 UI 확장 (엔진 선택 + API 키 관리)
**Goal**: 설정 화면에서 STT 엔진과 회의록 엔진을 독립적으로 선택하고, 각 서비스의 API 키를 입력/저장할 수 있다
**Depends on**: Phase 39, Phase 40 (엔진 구현 후 UI 연결)
**Requirements**: SET6-01, SET6-02
**Success Criteria** (what must be TRUE):
  1. 설정 화면 STT 섹션에서 5개 엔진(Whisper 온디바이스, Gemini, Groq, Deepgram, Naver) 중 하나를 선택할 수 있다
  2. 설정 화면 회의록 섹션에서 3개 엔진(Gemini, Deepgram Intelligence, Naver CLOVA) 중 하나를 선택할 수 있다
  3. Groq / Deepgram / Naver API 키 입력 필드가 각각 표시되고 암호화 저장된다
  4. 선택한 엔진의 API 키가 없으면 안내 메시지가 표시된다
**Plans**: 1 plan

Plans:
- [ ] 41-01-PLAN.md — SettingsScreen STT/회의록 엔진 드롭다운 확장 + Groq/Deepgram/Naver API 키 입력 UI

### Phase 42: 버전 번호 포함 APK 빌드
**Goal**: Release APK 파일명에 버전 번호가 자동으로 포함된다 (AutoMinuting-v6.0-release.apk)
**Depends on**: Phase 41 (전체 기능 완성 후 릴리스 빌드)
**Requirements**: BUILD-01
**Success Criteria** (what must be TRUE):
  1. `./gradlew assembleRelease` 실행 시 `AutoMinuting-v6.0-release.apk` 파일이 생성된다
  2. APK 버전명이 versionName과 일치한다
**Plans**: 1 plan

Plans:
- [ ] 42-01-PLAN.md — build.gradle.kts archivesName + versionName v6.0 설정 + assembleRelease 검증

## Phase Details (v7.0)

### Phase 43: UX 개선 (카드 터치 + 이름 변경 메뉴) — completed 2026-04-03
**Goal**: 전사/회의록 카드가 터치로 상세 이동되고, 이름 변경이 드롭다운 메뉴로 접근된다
**Depends on**: Nothing
**Requirements**: UX7-01, UX7-02
**Plans**: 1/1 plans complete

Plans:
- [x] 43-01-PLAN.md — 카드 터치 네비게이션 + 이름 변경 메뉴 이동

### Phase 44: Groq Whisper STT 버그 수정 — completed 2026-04-03
**Goal**: Groq Whisper STT 엔진의 실제 동작 버그가 수정되어 전사가 정상 동작한다
**Depends on**: Nothing
**Requirements**: BUG7-01
**Plans**: 1/1 plans complete

Plans:
- [x] 44-01-PLAN.md — Groq Whisper STT 버그 수정

### Phase 45: Google Drive 인증
**Goal**: 사용자가 설정 화면에서 Google 계정으로 로그인/로그아웃할 수 있다 (OAuth 2.0, Google Drive API 접근 목적)
**Depends on**: Nothing (기존 GoogleAuthRepository 위에 Drive 스코프 추가)
**Requirements**: DRIVE-01
**Success Criteria** (what must be TRUE):
  1. 설정 화면에서 "Google Drive 연결" 버튼을 탭할 수 있다 (Google 계정 로그인 상태에서)
  2. 탭 시 Google 계정 선택 → Drive 접근 동의 화면이 표시된다
  3. 동의 후 설정 화면에 연결된 계정 이메일이 표시된다
  4. "연결 해제" 버튼으로 Drive 인증을 해제할 수 있다
  5. Drive 인증 상태가 DataStore에 저장되어 앱 재시작 후 복원된다
  6. assembleDebug BUILD SUCCESSFUL
**Plans**: 1 plan

Plans:
- [x] 45-01-PLAN.md — DriveAuthState 신설 + GoogleAuthRepository.authorizeDrive() + SettingsScreen Drive 인증 UI + SettingsViewModel 연결 + assembleDebug 빌드 검증

### Phase 46: Google Drive 업로드 파이프라인
**Goal**: 회의록 생성 완료 시 Drive에 자동 업로드된다
**Depends on**: Phase 45 (Drive 인증 완료 후)
**Requirements**: DRIVE-02, DRIVE-03, DRIVE-04
**Plans**: 2 plans

Plans:
- [ ] 46-01-PLAN.md — DriveUploadRepository(OkHttp multipart) + UserPreferencesRepository 폴더 ID 키 추가
- [ ] 46-02-PLAN.md — DriveUploadWorker(@HiltWorker) + Worker 독립 enqueue 추가 + SettingsScreen 폴더 ID 입력 UI + assembleDebug 빌드 검증

### Phase 47: 회의록 편집 기능
**Goal**: 사용자가 생성된 회의록 내용을 앱 내에서 직접 편집할 수 있다
**Depends on**: Nothing
**Requirements**: EDIT7-01
**Plans**: 1 plan

Plans:
- [x] 47-01-PLAN.md — 회의록 상세 화면 편집 모드 + 저장

### Phase 48: API 사용량 대시보드
**Goal**: 사용자가 각 엔진별 API 사용량을 한눈에 확인할 수 있다
**Depends on**: Nothing
**Requirements**: USAGE7-01
**Plans**: 1 plan

Plans:
- [x] 48-01-PLAN.md — API 사용량 추적 레이어 + 대시보드 위젯

### Phase 49: 설정 UI 정비 (3/3 plans) — completed 2026-04-05
**Goal**: 설정 화면이 v7.0 신규 기능을 포함하여 논리적으로 재구성된다
**Depends on**: Phase 45, Phase 46, Phase 47, Phase 48
**Requirements**: SET7-01
**Plans**: 3/3 plans complete

Plans:
- [x] 49-01-PLAN.md — 설정 화면 구조 분석 및 재구성 수정안 작성
- [x] 49-02-PLAN.md — 재구성안 사용자 승인 체크포인트
- [x] 49-03-PLAN.md — 승인된 재구성안 적용 및 시각 확인

## Phase Details (v9.0)

### Phase 51: Gemini 다중 API 키 설정 UI
**Goal**: 사용자가 설정 화면에서 Gemini API 키를 여러 개 추가/삭제할 수 있고, 등록된 키가 암호화 저장된다
**Depends on**: Nothing (기존 Gemini API 키 설정 UI 확장)
**Requirements**: GEMINI-01, GEMINI-04
**Success Criteria** (what must be TRUE):
  1. 설정 화면 Gemini API 섹션에 키 목록이 표시되고 새 키를 추가하는 입력 필드가 있다
  2. 키를 추가하면 목록에 표시되고 앱 재시작 후 유지된다
  3. 목록에서 특정 키 옆 삭제 버튼으로 키를 제거할 수 있다
  4. 등록된 모든 키는 EncryptedSharedPreferences에 암호화 저장된다
**Plans**: TBD
**UI hint**: yes

### Phase 52: Gemini 라운드로빈 + 오류 자동 전환
**Goal**: Gemini 호출 시 등록된 키를 라운드로빈으로 순환하고, 오류 발생 시 알림 후 다음 키로 자동 전환된다
**Depends on**: Phase 51 (다중 키 저장 구조 필요)
**Requirements**: GEMINI-02, GEMINI-03
**Success Criteria** (what must be TRUE):
  1. Gemini STT 또는 회의록 생성 호출 시 등록된 키가 순서대로 순환 사용된다
  2. 특정 키가 할당량 초과 또는 권한 오류를 반환하면 사용자에게 토스트/알림이 표시된다
  3. 오류 발생 즉시 다음 키로 자동 전환되어 파이프라인이 중단 없이 계속된다
  4. 등록된 키가 모두 오류이면 파이프라인 실패 메시지가 표시된다
**Plans**: TBD

### Phase 53: MP3 파일 합치기 지원
**Goal**: Share Intent로 여러 MP3 파일 또는 M4A+MP3 혼재 파일을 공유했을 때 앱이 자동으로 합쳐 처리한다
**Depends on**: Nothing (기존 WavMerger/M4A 합치기 로직과 병렬 구현)
**Requirements**: MERGE-04, MERGE-05
**Success Criteria** (what must be TRUE):
  1. Share Intent로 여러 MP3 파일 공유 시 재인코딩 없이 하나의 MP3로 합쳐진다
  2. 합쳐진 MP3 파일이 기존 STT → 회의록 파이프라인을 정상 통과한다
  3. M4A와 MP3가 혼재된 공유 시 포맷별로 분리 처리되어 결과를 각각 내놓는다
  4. 단일 MP3 공유는 기존과 동일하게 처리된다 (합치기 로직 미적용)
**Plans:** 1/2 plans executed

Plans:
- [x] 53-01-PLAN.md — Mp3Merger 유틸 + 단위 테스트 (ID3 스트립 + 프레임 concat + 포맷 검증)
- [ ] 53-02-PLAN.md — ShareReceiverActivity 포맷 분류 분기 + 그룹별 Merger/Meeting/Worker enqueue

### Phase 54: 홈 화면 파일 직접 입력
**Goal**: 사용자가 홈 화면의 '파일 불러오기' 버튼으로 로컬 음성 파일을 선택하면 STT → 회의록 파이프라인이 실행된다
**Depends on**: Nothing (Share Intent 파이프라인 재사용)
**Requirements**: INPUT-01, INPUT-02
**Success Criteria** (what must be TRUE):
  1. 홈 화면에 '파일 불러오기' 버튼이 표시된다
  2. 버튼 탭 시 SAF 파일 피커가 열리고 M4A/MP3 파일만 선택 가능하다
  3. 파일 선택 후 기존 STT → 회의록 파이프라인이 자동으로 시작된다
  4. 파이프라인 진행 상태가 기존과 동일하게 표시된다
**Plans**: TBD
**UI hint**: yes

### Phase 55: Groq 대용량 파일 자동 분할 전사
**Goal**: Groq Whisper STT 선택 시 25MB 초과 파일이 자동으로 청크 분할되어 순서대로 전사된 뒤 하나의 텍스트로 이어붙여진다
**Depends on**: Nothing (기존 GroqSttEngine 확장)
**Requirements**: GROQ-01, GROQ-02, GROQ-03
**Success Criteria** (what must be TRUE):
  1. Groq STT 엔진 선택 시 25MB 이하 파일은 기존과 동일하게 단일 요청으로 처리된다
  2. 25MB 초과 파일은 앱이 자동으로 청크로 분할한다 (사용자 개입 불필요)
  3. 분할된 청크가 순서대로 Groq API에 전송되어 각각 전사된다
  4. 모든 청크 전사 결과가 순서대로 이어붙여져 하나의 완전한 전사 텍스트가 출력된다
  5. 전체 파이프라인이 단일 파일 전사와 동일하게 회의록 생성까지 진행된다
**Plans**: TBD

## Milestone Details

- v1.0 (Phases 1-7): `.planning/milestones/v1.0-ROADMAP.md`
- v2.0 (Phases 8-13): `.planning/milestones/v2.0-ROADMAP.md`
- v8.0 (Phase 50): `.planning/milestones/v8.0-ROADMAP.md`
- v9.0 (Phases 51-55): TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-7 | v1.0 | 18/18 | Complete | 2026-03-24 |
| 8-13 | v2.0 | 11/13 | Complete | 2026-03-26 |
| 14-18 | v2.1 | 7/7 | Complete | 2026-03-26 |
| 19-23 | v3.0 | 1/1 | Complete | 2026-03-28 |
| 24. 전사 카드 정보 표시 | v3.1 | 1/1 | Complete    | 2026-03-28 |
| 25. 전사 이름 관리 | v3.1 | 1/1 | Complete    | 2026-03-28 |
| 26. 회의록 제목 및 액션 | v3.1 | 2/2 | Complete    | 2026-03-28 |
| 27. URL 음성 다운로드 | v3.1 | 1/1 | Complete    | 2026-03-28 |
| 28. 설정 정리 | v3.1 | 1/1 | Complete    | 2026-03-29 |
| 29. 전사 카드 UX 개선 | v4.0 | 1/1 | Complete    | 2026-03-29 |
| 30. 프롬프트 템플릿 선택 | v4.0 | 1/1 | Complete    | 2026-03-29 |
| 31. Gemini 쿼터 관리 | v4.0 | 1/1 | Complete    | 2026-03-29 |
| 32. Plaud 공유 링크 수신 | v4.0 | 1/1 | Complete    | 2026-03-29 |
| 33. GUI 일관성 개선 | v4.0 | 1/1 | Complete    | 2026-03-29 |
| 34. Whisper 전사 진행률 | v4.0 | 2/2 | Complete    | 2026-03-30 |
| 35. 회의록 설정 구조 개편 | v4.0 | 3/3 | Complete    | 2026-03-30 |
| 36. Minutes 데이터 모델 분리 | v5.0 | 3/3 | Complete | 2026-03-30 |
| 37. 전사-회의록 독립 삭제 | v5.0 | 1/1 | Complete | 2026-03-31 |
| 38. 독립 아키텍처 UI 반영 | v5.0 | 2/2 | Complete | 2026-03-31 |
| 39. STT 엔진 확장 | v6.0 | 0/3 | Pending | — |
| 40. 회의록 엔진 확장 | v6.0 | 0/2 | Pending | — |
| 41. 설정 UI 확장 | v6.0 | 0/1 | Pending | — |
| 42. 버전 번호 포함 APK 빌드 | v6.0 | 0/1 | Pending | — |
| 43. UX 개선 (카드 터치 + 이름 변경 메뉴) | v7.0 | 1/1 | Complete | 2026-04-03 |
| 44. Groq Whisper STT 버그 수정 | v7.0 | 1/1 | Complete   | 2026-04-03 |
| 45. Google Drive 인증 | v7.0 | 1/1 | Complete   | 2026-04-03 |
| 46. Google Drive 업로드 파이프라인 | v7.0 | 2/2 | Complete   | 2026-04-03 |
| 47. 회의록 편집 기능 | v7.0 | 1/1 | Complete   | 2026-04-03 |
| 48. API 사용량 대시보드 | v7.0 | 1/1 | Complete   | 2026-04-03 |
| 49. 설정 UI 정비 | v7.0 | 3/3 | Complete | 2026-04-05 |
| 50. 다중 파일 합치기 | v8.0 | 1/1 | Complete    | 2026-04-05 |
| 51. Gemini 다중 API 키 설정 UI | v9.0 | 1/1 | Complete   | 2026-04-15 |
| 52. Gemini 라운드로빈 + 오류 자동 전환 | v9.0 | 0/? | Not started | — |
| 53. MP3 파일 합치기 지원 | v9.0 | 1/2 | In Progress|  |
| 54. 홈 화면 파일 직접 입력 | v9.0 | 0/? | Not started | — |
| 55. Groq 대용량 파일 자동 분할 전사 | v9.0 | 0/? | Not started | — |
