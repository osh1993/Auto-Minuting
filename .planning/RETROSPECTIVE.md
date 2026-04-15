# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v9.0 — 다중 Gemini 계정 + 파일 입력 확장 + Groq 대용량 처리

**Shipped:** 2026-04-15
**Phases:** 5 (51–55) | **Plans:** 8 | **LOC:** 16,804 Kotlin

### What Was Built
- Gemini API 키 다중 등록/라운드로빈 순환 + 오류 시 자동 다음 키 전환 (암호화 저장)
- Share Intent MP3 파일 합치기 (재인코딩 없음) + M4A+MP3 혼재 포맷별 분리 처리
- 홈 화면 SAF '파일 불러오기' 버튼 → STT → 회의록 파이프라인 직접 진입
- Groq 25MB 초과 파일 MediaCodec 스트리밍 청크 분할(600초) → 순차 전사 → 이어붙이기
- AudioChunker OOM 버그 수정 — 전체 PCM 메모리 로드 → 스트리밍 디코딩 전환 (537MB → 19MB/청크)

### What Worked
- 실기기 logcat 즉시 진단 — OOM 스택트레이스로 근본 원인을 정확히 포착
- Phase 55 실행 중 OOM 버그를 발견하고 디버그 세션으로 즉시 수정, 사이클 낭비 없이 처리
- AudioChunker의 `splitPcmForTest()` 순수 함수 분리 — Android 없이 JVM 단위 테스트 가능, 알고리즘 정확성 보장

### What Was Inefficient
- Wave 에이전트 스폰 후 사용량 한도 초과 — 중단 후 재시작 필요 (30분 대기)
- 플랜 주석의 1800초 → 3청크 계산이 틀려서 에이전트가 알고리즘 실제 동작(4청크)으로 수정 — 플랜 작성 시 수식 검증 필요

### Patterns Established
- 대용량 파일 처리: 전체 로드 금지 — 항상 스트리밍/청크 단위로 처리 (`AudioChunker` 패턴)
- OOM 디버그: `adb logcat --pid=<앱PID>` + `grep -E "(Error|OOM|chunk)"` 조합으로 신속 진단

### Key Lessons
1. **청크 분할은 스트리밍이 기본** — "메모리에서 자른다" 방식은 파일 크기에 비례해 힙을 소모. MediaCodec 출력을 직접 스트리밍하면 최대 메모리 사용량이 청크 1개 크기로 고정됨
2. **플랜 수식은 코드로 검증** — 시간·바이트 계산을 주석에만 남기면 틀리기 쉬움. `splitPcmForTest()` 같은 순수 함수 + 단위 테스트로 경계값을 명시적으로 검증

### Cost Observations
- Model mix: 주로 opus (executor/debugger)
- Notable: 65MB 실파일로 즉시 회귀 테스트 가능한 환경이 OOM 수정 검증을 빠르게 해줌

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Key Change |
|-----------|--------|------------|
| v9.0 | 5 | 실기기 logcat을 통한 즉시 회귀 테스트 루프 확립 |

### Top Lessons (Verified Across Milestones)

1. 실기기 로그 확인을 실행 직후 루틴화 — 계획 단계에서 못 잡은 런타임 버그를 조기 발견
2. 순수 함수 + 단위 테스트 분리 — Android 의존성 없이 핵심 알고리즘 검증 가능
