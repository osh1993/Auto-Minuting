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
- [ ] **v7.0 UX 개선 + Google Drive 연동** — Phases 43-49

## Phases

- [ ] **Phase 43: UX 개선 — 카드 터치 열기 + 이름 변경 메뉴 이동** - 전사/회의록 카드 터치로 상세 화면 이동, 이름 변경을 overflow 메뉴로 이동
- [ ] **Phase 44: Groq Whisper 버그 수정** - Groq Whisper STT 미동작 원인 파악 및 수정
- [ ] **Phase 45: Google Drive 인증** - Google Sign-In OAuth 2.0 로그인/로그아웃
- [ ] **Phase 46: Google Drive 업로드 파이프라인** - 전사/회의록 자동 업로드 + 폴더 지정 설정
- [ ] **Phase 47: 회의록 편집** - 회의록 상세 화면에서 텍스트 편집 및 저장
- [ ] **Phase 48: API 사용량 대시보드** - 엔진별 API 호출 횟수 및 예상 비용 화면
- [ ] **Phase 49: 설정 UI 정비** - 설정 화면 구조 분석, 수정안 제시, 승인 후 적용

## Phase Details (v7.0)

### Phase 43: UX 개선 — 카드 터치 열기 + 이름 변경 메뉴 이동
**Goal**: 사용자가 전사/회의록 카드를 터치하면 바로 상세 화면으로 이동하고, 이름 변경은 overflow 메뉴에서 수행할 수 있다
**Depends on**: Nothing (기존 UI 동작 변경)
**Requirements**: UX-01, UX-02
**Success Criteria** (what must be TRUE):
  1. 사용자가 전사목록 탭에서 카드를 터치하면 해당 전사의 상세 화면(전사 텍스트 뷰)으로 이동한다
  2. 사용자가 회의록 탭에서 카드를 터치하면 해당 회의록의 상세 화면(Markdown 뷰어)으로 이동한다
  3. 전사목록/회의록 탭의 카드에서 점3개(overflow) 메뉴를 열면 "이름 변경" 항목이 존재한다
  4. overflow 메뉴의 "이름 변경"을 선택하면 이름 편집 다이얼로그가 표시되고 저장이 정상 동작한다
**Plans**: 1 plan
Plans:
- [ ] 43-01-PLAN.md — 전사/회의록 카드 터치 개선 + overflow 이름 변경 이동
**UI hint**: yes

### Phase 44: Groq Whisper 버그 수정
**Goal**: Groq Whisper STT 엔진이 정상적으로 한국어 전사를 수행하여 사용자가 Groq 엔진을 선택해도 전사 결과를 받을 수 있다
**Depends on**: Nothing (독립 버그 수정, 이후 파이프라인 작업 전에 완료)
**Requirements**: BUG-01
**Success Criteria** (what must be TRUE):
  1. 설정에서 Groq Whisper를 STT 엔진으로 선택하고 오디오 파일을 전사하면 한국어 텍스트가 정상 반환된다
  2. Groq API 에러 발생 시 사용자에게 명확한 에러 메시지가 표시된다
**Plans**: TBD

### Phase 45: Google Drive 인증
**Goal**: 사용자가 설정 화면에서 Google 계정으로 로그인/로그아웃하여 Google Drive 접근 권한을 관리할 수 있다
**Depends on**: Phase 44 (버그 수정 완료 후 새 기능 시작)
**Requirements**: DRIVE-01
**Success Criteria** (what must be TRUE):
  1. 사용자가 설정 화면에서 "Google 로그인" 버튼을 탭하면 Google OAuth 2.0 인증 플로우가 시작된다
  2. 인증 완료 후 설정 화면에 로그인된 Google 계정 이메일이 표시된다
  3. 사용자가 "로그아웃" 버튼을 탭하면 Google 인증 토큰이 제거되고 로그아웃 상태로 돌아간다
  4. Google Drive 파일 접근 scope가 올바르게 요청되어 이후 업로드가 가능하다
**Plans**: TBD
**UI hint**: yes

### Phase 46: Google Drive 업로드 파이프라인
**Goal**: 파이프라인 완료 후 전사 파일과 회의록 파일이 지정된 Google Drive 폴더에 자동 업로드되고, 사용자가 업로드 폴더를 설정할 수 있다
**Depends on**: Phase 45 (Google OAuth 인증이 완료되어야 Drive API 호출 가능)
**Requirements**: DRIVE-02, DRIVE-03, DRIVE-04
**Success Criteria** (what must be TRUE):
  1. 전사 파이프라인 완료 후 전사 파일이 설정된 Google Drive 폴더에 자동으로 업로드된다
  2. 회의록 생성 완료 후 회의록 파일이 설정된 Google Drive 폴더에 자동으로 업로드된다
  3. 사용자가 설정 화면에서 전사 파일 업로드 폴더와 회의록 파일 업로드 폴더를 각각 지정할 수 있다
  4. Google Drive 미로그인 상태에서는 업로드가 스킵되고 에러 없이 파이프라인이 완료된다
  5. 네트워크 오류 시 업로드가 실패해도 로컬 파일은 정상 유지되며 재시도 가능하다
**Plans**: TBD
**UI hint**: yes

### Phase 47: 회의록 편집
**Goal**: 사용자가 회의록 상세 화면에서 텍스트를 편집하고 저장할 수 있다
**Depends on**: Nothing (독립 기능, 기존 전사 편집 패턴 재사용)
**Requirements**: EDIT-01
**Success Criteria** (what must be TRUE):
  1. 사용자가 회의록 상세 화면에서 "편집" 버튼을 탭하면 텍스트 편집 모드로 전환된다
  2. 편집 모드에서 회의록 텍스트를 수정하고 "저장" 버튼을 탭하면 변경 사항이 파일에 반영된다
  3. 편집 저장 후 회의록 상세 화면으로 돌아오면 수정된 내용이 Markdown으로 렌더링된다
**Plans**: TBD
**UI hint**: yes

### Phase 48: API 사용량 대시보드
**Goal**: 사용자가 전용 화면에서 엔진별 API 호출 횟수와 예상 비용을 확인할 수 있다
**Depends on**: Nothing (독립 기능, 기존 GeminiQuotaTracker 패턴 확장)
**Requirements**: DASH-01
**Success Criteria** (what must be TRUE):
  1. 사용자가 전용 탭 또는 화면에서 각 엔진(Gemini, Groq, Deepgram, Naver)별 API 호출 횟수를 확인할 수 있다
  2. 각 엔진별 예상 비용이 표시된다 (무료 티어 사용량 포함)
  3. 사용량 데이터가 기간별(일/주/월)로 필터링 가능하다
**Plans**: TBD
**UI hint**: yes

### Phase 49: 설정 UI 정비
**Goal**: 설정 화면의 구조가 분석되고 수정안이 제시된 후 승인을 거쳐 적용되어 사용성이 향상된다
**Depends on**: Phase 46 (Google Drive 설정 항목이 추가된 후 전체 설정 화면 정비)
**Requirements**: SETTINGS-01
**Success Criteria** (what must be TRUE):
  1. 현재 설정 화면의 구조와 정보 배치에 대한 분석 결과가 문서로 제시된다
  2. 수정안이 사용자에게 제시되고 승인을 받은 후에만 적용된다
  3. 적용 후 설정 항목이 논리적으로 그룹화되고 각 섹션이 명확히 구분된다
**Plans**: TBD
**UI hint**: yes

## Previous Phase Details

<details>
<summary>v1.0-v5.0 (Phases 1-38)</summary>

See milestone archives:
- v1.0 (Phases 1-7): `.planning/milestones/v1.0-ROADMAP.md`
- v2.0 (Phases 8-13): `.planning/milestones/v2.0-ROADMAP.md`
- v3.0-v5.0 (Phases 19-38): Previous ROADMAP.md history

</details>

<details>
<summary>v6.0 멀티 엔진 확장 (Phases 39-42) — SHIPPED 2026-04-03</summary>

- [x] Phase 39: STT 엔진 확장 (3/3 plans) — completed 2026-04-03
- [x] Phase 40: 회의록 엔진 확장 (2/2 plans) — completed 2026-04-03
- [x] Phase 41: 설정 UI 확장 (1/1 plan) — completed 2026-04-03
- [x] Phase 42: 버전 번호 포함 APK 빌드 (1/1 plan) — completed 2026-04-03

See: `.planning/milestones/v6.0-ROADMAP.md`

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-7 | v1.0 | 18/18 | Complete | 2026-03-24 |
| 8-13 | v2.0 | 11/13 | Complete | 2026-03-26 |
| 14-18 | v2.1 | 7/7 | Complete | 2026-03-26 |
| 19-23 | v3.0 | 1/1 | Complete | 2026-03-28 |
| 24. 전사 카드 정보 표시 | v3.1 | 1/1 | Complete | 2026-03-28 |
| 25. 전사 이름 관리 | v3.1 | 1/1 | Complete | 2026-03-28 |
| 26. 회의록 제목 및 액션 | v3.1 | 2/2 | Complete | 2026-03-28 |
| 27. URL 음성 다운로드 | v3.1 | 1/1 | Complete | 2026-03-28 |
| 28. 설정 정리 | v3.1 | 1/1 | Complete | 2026-03-29 |
| 29. 전사 카드 UX 개선 | v4.0 | 1/1 | Complete | 2026-03-29 |
| 30. 프롬프트 템플릿 선택 | v4.0 | 1/1 | Complete | 2026-03-29 |
| 31. Gemini 쿼터 관리 | v4.0 | 1/1 | Complete | 2026-03-29 |
| 32. Plaud 공유 링크 수신 | v4.0 | 1/1 | Complete | 2026-03-29 |
| 33. GUI 일관성 개선 | v4.0 | 1/1 | Complete | 2026-03-29 |
| 34. Whisper 전사 진행률 | v4.0 | 2/2 | Complete | 2026-03-30 |
| 35. 회의록 설정 구조 개편 | v4.0 | 3/3 | Complete | 2026-03-30 |
| 36. Minutes 데이터 모델 분리 | v5.0 | 3/3 | Complete | 2026-03-30 |
| 37. 전사-회의록 독립 삭제 | v5.0 | 1/1 | Complete | 2026-03-31 |
| 38. 독립 아키텍처 UI 반영 | v5.0 | 2/2 | Complete | 2026-03-31 |
| 39. STT 엔진 확장 | v6.0 | 3/3 | Complete | 2026-04-03 |
| 40. 회의록 엔진 확장 | v6.0 | 2/2 | Complete | 2026-04-03 |
| 41. 설정 UI 확장 | v6.0 | 1/1 | Complete | 2026-04-03 |
| 42. 버전 번호 포함 APK 빌드 | v6.0 | 1/1 | Complete | 2026-04-03 |
| 43. UX 개선 | v7.0 | 0/1 | Not started | - |
| 44. Groq Whisper 버그 수정 | v7.0 | 0/0 | Not started | - |
| 45. Google Drive 인증 | v7.0 | 0/0 | Not started | - |
| 46. Google Drive 업로드 파이프라인 | v7.0 | 0/0 | Not started | - |
| 47. 회의록 편집 | v7.0 | 0/0 | Not started | - |
| 48. API 사용량 대시보드 | v7.0 | 0/0 | Not started | - |
| 49. 설정 UI 정비 | v7.0 | 0/0 | Not started | - |
