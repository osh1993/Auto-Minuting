# Phase 1: PoC — 기술 가능성 검증 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

3개 핵심 외부 의존성(Plaud 오디오 수집, Galaxy AI STT 전사, NotebookLM 회의록 생성)의 기술적 실현 가능성을 검증하고, 각 의존성별 채택 경로와 폴백 경로를 확정한다. 실제 앱 구현은 Phase 2 이후.

</domain>

<decisions>
## Implementation Decisions

### Plaud 파일 획득 전략
- **D-01:** APK 디컴파일을 우선 시도하여 파일 저장 경로, BLE 서비스/특성 UUID, 통신 프로토콜을 분석한다
- **D-02:** Plaud 앱이 로컬에 파일을 저장하는 경우 FileObserver로 감시하는 방식을 1차 채택 경로로 검토한다
- **D-03:** 오디오 포맷은 WAV/MP3로 알려져 있으며, 실제 포맷은 APK 분석 시 확인한다
- **D-04:** 폴백 경로: Plaud 클라우드에서 다운로드한 파일을 입력으로 사용한다

### STT 전사 경로
- **D-05:** Galaxy AI 전사 기능의 서드파티 접근 방법을 모든 경로(SpeechRecognizer 온디바이스, 삼성 녹음/노트 앱 Intent, Samsung SDK)에서 조사한다
- **D-06:** 온디바이스(로컬) 처리를 우선시한다 — 프라이버시가 핵심 가치
- **D-07:** Galaxy AI 접근 불가 시 폴백: OpenAI Whisper 온디바이스 (whisper.cpp 또는 whisper-android)
- **D-08:** 클라우드 STT는 최후의 수단으로만 고려 (Google Cloud STT)

### NotebookLM 연동 방식
- **D-09:** NotebookLM 연동 방식은 PoC 리서치에서 확인 후 결정 (MCP 서버 릴레이 vs Gemini API 직접 호출)

### Claude's Discretion
- PoC 검증 순서 (3개 의존성 중 어떤 것을 먼저 검증할지)
- 각 의존성별 성공/실패 판단 기준의 구체적 수치
- BLE 스니핑 도구 선택 (nRF Connect 등)
- Whisper 모델 크기 선택 (tiny/base/small)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프로젝트 컨텍스트
- `.planning/PROJECT.md` — 프로젝트 전체 비전, 제약조건, Key Decisions
- `.planning/REQUIREMENTS.md` — POC-01~04 요구사항 상세

### 리서치 결과
- `.planning/research/STACK.md` — 기술 스택 권장사항, Galaxy AI/NotebookLM 분석
- `.planning/research/ARCHITECTURE.md` — 시스템 구조, BLE 통신/파이프라인 설계
- `.planning/research/PITFALLS.md` — 13개 도메인 함정, 특히 Critical 4개

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 없음 (그린필드 프로젝트)

### Established Patterns
- 없음 (첫 번째 페이즈)

### Integration Points
- 없음 (PoC 단계에서는 독립적 실험)

</code_context>

<specifics>
## Specific Ideas

- Plaud 앱 APK 디컴파일이 첫 번째 조사 대상 — jadx 등의 도구 활용
- Galaxy AI는 삼성 갤럭시 스마트폰 내장 기능이므로 실제 기기에서만 테스트 가능
- Whisper 온디바이스가 폴백이므로 whisper.cpp의 Android 빌드도 함께 조사
- NotebookLM MCP 서버가 프로젝트에 이미 설정되어 있으므로 이를 활용할 수 있는지 확인

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-poc*
*Context gathered: 2026-03-24*
