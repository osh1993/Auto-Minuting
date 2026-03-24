# Phase 2: 앱 기반 구조 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

이후 모든 파이프라인 구현이 올라갈 수 있는 Clean Architecture 앱 뼈대를 구축한다. 4개 빈 화면(대시보드/전사목록/회의록목록/설정), Room DB 스키마, Hilt DI 그래프, WorkManager 초기화가 포함된다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion

사용자가 모든 기술적 세부사항을 Claude 재량에 위임함. 다음 항목들은 리서치 및 플래닝 단계에서 Claude가 결정:

- **화면 구성:** 4개 화면(대시보드/전사목록/회의록목록/설정) 네비게이션 구조 (Bottom Navigation Bar 또는 Navigation Drawer)
- **DB 스키마 설계:** MeetingEntity, TranscriptEntity, MinutesEntity, PipelineStatus 상태 머신 설계
- **모듈 구조:** Clean Architecture 레이어 분리 방식 (기능별 모듈 vs 레이어별 모듈 vs 하이브리드)
- **패키지 구조:** 패키지 네이밍, 디렉토리 구조
- **테마/스타일:** Material 3 기본 테마, 컬러 스키마
- **WorkManager 구성:** 파이프라인 Worker 체이닝 패턴

### Phase 1 결정 반영 (Locked)

- **D-01:** Plaud SDK 연동 모듈을 위한 인터페이스 정의 필요 (Phase 3에서 구현)
- **D-02:** Whisper 온디바이스 전사 모듈을 위한 인터페이스 정의 필요 (Phase 4에서 구현)
- **D-03:** Gemini API 회의록 생성 모듈을 위한 인터페이스 정의 필요 (Phase 5에서 구현)
- **D-04:** 온디바이스 처리 우선 원칙 — 네트워크 의존 최소화 설계

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프로젝트 컨텍스트
- `.planning/PROJECT.md` — 프로젝트 비전, 제약조건, PoC 결정사항 (Key Decisions 참조)
- `.planning/REQUIREMENTS.md` — APP-01~04 요구사항 상세

### PoC 결과 (아키텍처 결정에 영향)
- `poc/results/DECISION-SUMMARY.md` — 3개 의존성 최종 결정, 채택/폴백 경로

### 리서치
- `.planning/research/STACK.md` — 기술 스택 권장사항 (Kotlin 2.3, Compose BOM, Room, Hilt 등)
- `.planning/research/ARCHITECTURE.md` — 시스템 아키텍처, 컴포넌트 구조

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `poc/minutes-test/gemini-test.py` — Gemini API 호출 패턴 참조 가능 (Python이지만 API 구조 참고)
- `poc/minutes-test/sample-transcript.txt` — 전사 샘플 데이터, DB 시딩에 활용 가능

### Established Patterns
- 없음 (첫 앱 코드 작성)

### Integration Points
- Phase 3: Plaud SDK 연동 → 오디오 수집 인터페이스
- Phase 4: Whisper 전사 → STT 인터페이스
- Phase 5: Gemini API → 회의록 생성 인터페이스
- Phase 6: WorkManager → 파이프라인 체이닝

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

*Phase: 02-app-base*
*Context gathered: 2026-03-24*
