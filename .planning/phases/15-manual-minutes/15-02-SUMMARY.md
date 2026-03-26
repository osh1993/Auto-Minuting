---
phase: 15-manual-minutes
plan: 02
subsystem: minutes-engine, presentation
tags: [custom-prompt, manual-minutes, dialog, viewmodel, gemini]

requires:
  - phase: 15-manual-minutes
    plan: 01
    provides: PromptTemplate 모델, PromptTemplateRepository
  - phase: 12-google-oauth
    provides: MinutesEngine 인터페이스, GeminiOAuthEngine
provides:
  - MinutesEngine/Repository customPrompt 파라미터 지원
  - ManualMinutesDialog (템플릿 선택 + 직접 입력)
  - TranscriptsScreen 수동 회의록 생성 버튼
  - TranscriptsViewModel 수동 생성 로직
affects: [minutes-generation, transcripts-ui]

tech-stack:
  added: []
  patterns: [커스텀 프롬프트 파라미터 default null 호환 패턴, SharedFlow 이벤트 패턴]

key-files:
  created:
    - app/src/main/java/com/autominuting/presentation/transcripts/ManualMinutesDialog.kt
  modified:
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/GeminiOAuthEngine.kt
    - app/src/main/java/com/autominuting/data/minutes/MinutesEngineSelector.kt
    - app/src/main/java/com/autominuting/domain/repository/MinutesRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
    - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt

key-decisions:
  - "customPrompt: String? = null default로 기존 자동 파이프라인 호환성 유지"
  - "수동 생성은 Worker 없이 ViewModel에서 직접 MinutesRepository 호출"
  - "SharedFlow로 생성 결과 이벤트 전달 (일회성 소비)"

requirements-completed: [MINS-01]

duration: 4min
completed: 2026-03-26
---

# Phase 15 Plan 02: 수동 회의록 생성 플로우 Summary

**전사 완료 항목에서 템플릿 선택/직접 프롬프트 입력으로 Gemini 호출하여 회의록 수동 생성하는 전체 플로우**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-26T07:59:58Z
- **Completed:** 2026-03-26T08:03:56Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- MinutesEngine 인터페이스에 customPrompt 파라미터 추가 (GeminiEngine, GeminiOAuthEngine, MinutesEngineSelector 모두 반영)
- MinutesRepository/Impl에 customPrompt 전파하여 커스텀 프롬프트로 회의록 생성 가능
- ManualMinutesDialog 생성: 탭으로 템플릿 선택/직접 입력 모드 전환, 생성 중 프로그레스 표시
- TranscriptsViewModel에 수동 회의록 생성 로직 추가 (PromptTemplateRepository + MinutesRepository 연동)
- TranscriptsScreen에 NoteAdd 아이콘 버튼 추가: 전사 완료/완료/실패 상태에서 회의록 생성/재생성 가능
- Snackbar로 생성 결과 피드백 제공

## Task Commits

Each task was committed atomically:

1. **Task 1: MinutesEngine/Repository에 커스텀 프롬프트 지원 추가** - `f352e56` (feat)
2. **Task 2: 전사 목록에서 수동 회의록 생성 플로우** - `e501b6e` (feat)

## Files Created/Modified
- `presentation/transcripts/ManualMinutesDialog.kt` - 템플릿 선택 + 직접 입력 다이얼로그 (170+ LOC)
- `data/minutes/MinutesEngine.kt` - customPrompt: String? = null 파라미터 추가
- `data/minutes/GeminiEngine.kt` - 커스텀 프롬프트 분기 로직
- `data/minutes/GeminiOAuthEngine.kt` - 커스텀 프롬프트 분기 로직
- `data/minutes/MinutesEngineSelector.kt` - customPrompt 위임 전달
- `domain/repository/MinutesRepository.kt` - customPrompt 파라미터 추가
- `data/repository/MinutesRepositoryImpl.kt` - customPrompt 전파
- `presentation/transcripts/TranscriptsScreen.kt` - 회의록 생성 버튼 + ManualMinutesDialog 연결
- `presentation/transcripts/TranscriptsViewModel.kt` - 수동 생성 로직 + 결과 이벤트

## Decisions Made
- customPrompt: String? = null default로 기존 자동 파이프라인(MinutesGenerationWorker) 호환성 완전 유지
- 수동 생성은 Worker 대신 ViewModel에서 직접 호출 (즉시 결과 피드백 필요)
- SharedFlow로 일회성 이벤트 전달하여 Snackbar 표시

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## Known Stubs
None - 모든 데이터 소스가 실제 Repository/Engine에 연결됨

## Next Phase Readiness
- 수동 회의록 생성이 기존 회의록 목록(MinutesScreen)에서 자동으로 표시됨 (동일한 MeetingEntity 경로)
- 향후 프롬프트 템플릿 확장 시 ManualMinutesDialog는 자동 반영

## Self-Check: PASSED

---
*Phase: 15-manual-minutes*
*Completed: 2026-03-26*
