# Roadmap: Auto Minuting

## Milestones

- ✅ **v1.0 Auto Minuting MVP** — Phases 1-7 (shipped 2026-03-24)
- ✅ **v2.0 실동작 파이프라인 + 기능 확장** — Phases 8-13 (shipped 2026-03-26)
- ✅ **v2.1 안정화** — Phases 14-18 (shipped 2026-03-26)
- **v3.0 기능 확장 및 UX 개선** — Phases 19-23

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

**잔여 작업:**
- [ ] whisper.cpp NDK 빌드 (libwhisper.so + JNI 브릿지)

### Phase 21: 회의록 목록 액션 메뉴 (삭제, 공유)
### Phase 22: 설정 메뉴 및 테스트 도구 이동

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
