# Requirements — v9.0

## Milestone v9.0 Requirements

### Gemini 다중 API 키

- [x] **GEMINI-01**: 사용자가 설정 화면에서 Gemini API 키를 여러 개 추가/삭제할 수 있다
- [x] **GEMINI-02**: 앱이 Gemini STT 또는 회의록 생성 호출 시 등록된 키를 라운드로빈 순서로 순환하여 사용한다
- [x] **GEMINI-03**: 특정 API 키가 오류(권한 오류, 할당량 초과 등)를 반환하면 사용자에게 알림을 표시하고 자동으로 다음 키로 전환한다
- [x] **GEMINI-04**: 등록된 모든 Gemini API 키가 암호화 저장된다

### MP3 파일 합치기

- [x] **MERGE-04**: 사용자가 Share Intent로 여러 MP3 파일을 공유했을 때 앱이 자동으로 하나의 MP3 파일로 합쳐 처리한다 (재인코딩 없이)
- [x] **MERGE-05**: Share Intent로 M4A와 MP3가 혼재된 파일 목록이 전달될 때 앱이 적절히 처리한다 (포맷별 분리 로직)

### 홈 화면 파일 직접 입력

- [ ] **INPUT-01**: 사용자가 홈 화면의 '파일 불러오기' 버튼으로 로컬 음성 파일(M4A/MP3)을 선택할 수 있다
- [ ] **INPUT-02**: 선택한 파일이 기존 STT → 회의록 파이프라인으로 처리된다

### Groq 대용량 파일 자동 분할

- [x] **GROQ-01**: Groq Whisper STT 엔진 선택 시 입력 파일이 25MB를 초과하면 앱이 자동으로 파일을 청크로 분할한다
- [ ] **GROQ-02**: 분할된 각 청크가 순서대로 Groq Whisper API에 전사 요청된다
- [ ] **GROQ-03**: 전사된 청크 결과들이 순서대로 이어붙여져 하나의 전사 텍스트로 출력된다

---

## Future Requirements

- Gemini API 키별 사용량/오류 횟수 통계 표시
- 파일 입력 시 다중 파일 선택 지원 (SAF 다중 선택)
- Groq 분할 청크 크기 사용자 조정 설정

## Out of Scope

- Gemini 키별 모델 개별 지정 — 단일 모델 설정 유지
- MP3 → M4A 자동 변환 — 포맷 변환 없이 포맷별 처리
- 클라우드 파일(Google Drive 등) 직접 입력 — 로컬 파일만 지원

## Traceability

| REQ-ID | Phase | Plan | Status |
|--------|-------|------|--------|
| GEMINI-01 | Phase 51 | — | Pending |
| GEMINI-02 | Phase 52 | — | Pending |
| GEMINI-03 | Phase 52 | — | Pending |
| GEMINI-04 | Phase 51 | — | Pending |
| MERGE-04 | Phase 53 | — | Pending |
| MERGE-05 | Phase 53 | — | Pending |
| INPUT-01 | Phase 54 | — | Pending |
| INPUT-02 | Phase 54 | — | Pending |
| GROQ-01 | Phase 55 | — | Pending |
| GROQ-02 | Phase 55 | — | Pending |
| GROQ-03 | Phase 55 | — | Pending |
