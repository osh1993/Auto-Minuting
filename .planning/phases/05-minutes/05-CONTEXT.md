# Phase 5: 회의록 생성 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

전사된 텍스트를 Gemini API(1차) 또는 NotebookLM MCP(2차)로 처리하여 구조화된 회의록을 생성하고 로컬에 저장한다. 사용자가 기존 노트 또는 새 노트를 선택하여 소스를 등록할 수 있다.

</domain>

<decisions>
## Implementation Decisions

### Phase 1 결정 반영 (Locked)

- **D-01:** Gemini API 직접 호출(Firebase AI Logic SDK)을 1차 회의록 생성 경로로 채택
- **D-02:** NotebookLM MCP 서버를 2차 폴백으로 유지 (PC 의존, 비공식)
- **D-03:** 대안 파이프라인: Gemini API 오디오 직접 입력으로 STT 단계 생략 가능 — 이 Phase에서 품질 비교 후 결정
- **D-04:** 온디바이스 처리 우선 원칙 (회의록 생성은 클라우드 API 필요하므로 예외)

### Phase 2-4 코드 연동 (Locked)

- **D-05:** `MinutesRepository` 인터페이스가 domain 레이어에 정의됨 — 구현체 작성 대상
- **D-06:** `MeetingEntity`에 `minutesPath` 필드와 `PipelineStatus.MINUTES_GENERATED` 상태 정의됨
- **D-07:** 전사 텍스트가 `transcriptPath` 파일로 저장됨 — 이를 읽어서 Gemini API에 전달
- **D-08:** Clean Architecture + Hilt DI + 이중 경로 폴백 패턴 확립됨

### Claude's Discretion

사용자가 모든 기술적 세부사항을 Claude에 위임:

- **Gemini API 프롬프트:** 구조화된 회의록/요약/커스텀 형식별 프롬프트 설계
- **NotebookLM 연동:** MCP 서버를 통한 노트 선택/생성, 소스 등록 방식
- **회의록 저장 형식:** Markdown, 플레인텍스트, 또는 JSON 구조화 저장
- **오디오 직접 입력 비교:** Gemini API에 오디오 직접 전달 vs 전사 텍스트 전달 품질 비교 로직
- **에러 처리:** API 키 미설정, 네트워크 오류, 토큰 한도 초과 등

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프로젝트 컨텍스트
- `.planning/PROJECT.md` — Key Decisions (Gemini API 채택, NotebookLM 폴백)
- `.planning/REQUIREMENTS.md` — MIN-01~04 요구사항

### PoC 결과
- `poc/results/POC-04-notebooklm.md` — Gemini API + NotebookLM 평가 결과
- `poc/minutes-test/gemini-test.py` — Gemini API 테스트 스크립트 (Python 참조)
- `poc/minutes-test/gemini-api-test.md` — API 검증 결과
- `poc/minutes-test/sample-transcript.txt` — 한국어 회의 전사 샘플

### 기존 코드 (Phase 2-4)
- `app/src/main/java/com/autominuting/domain/repository/MinutesRepository.kt` — 회의록 생성 인터페이스
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — DB Entity (minutesPath)
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` — DAO
- `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` — 상태 머신

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MinutesRepository` 인터페이스: 구현 대상
- `TranscriptionRepositoryImpl`: 이중 경로 폴백 패턴 — 회의록 생성에도 동일 패턴 적용
- `AudioRepositoryImpl`: 이중 경로 패턴 참조
- `poc/minutes-test/gemini-test.py`: Gemini API 호출 패턴 (Python → Kotlin 변환)
- `poc/minutes-test/sample-transcript.txt`: 50줄 3화자 한국어 전사 샘플

### Established Patterns
- Clean Architecture: domain 인터페이스 → data 구현체 → di 바인딩
- 이중 경로 폴백: 1차 실패 → 2차 자동 전환
- WorkManager: @HiltWorker + inputData
- 파일 기반 저장: transcriptPath 패턴 → minutesPath에 동일 적용

### Integration Points
- 전사 완료 (TRANSCRIBED) → 회의록 생성 Worker 트리거
- 회의록 생성 완료 → `MeetingDao.updateMinutes()` + `PipelineStatus.MINUTES_GENERATED`
- MinutesScreen (현재 빈 화면) → 회의록 뷰어로 확장 (Phase 7)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. 사용자가 모든 구현 세부사항을 Claude에 위임.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-minutes*
*Context gathered: 2026-03-24*
