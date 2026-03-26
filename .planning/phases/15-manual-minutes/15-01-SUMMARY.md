---
phase: 15-manual-minutes
plan: 01
subsystem: database, ui
tags: [room, dao, repository, hilt, jetpack-compose, prompt-template]

requires:
  - phase: 08-foundation
    provides: Room DB v2, DatabaseModule, RepositoryModule, Hilt DI 패턴
provides:
  - PromptTemplate Room 테이블 (prompt_templates, DB v3)
  - PromptTemplateDao (CRUD + isBuiltIn 보호)
  - PromptTemplateRepository (getAll, insert, update, delete, ensureDefaultTemplates)
  - PromptTemplateScreen (관리 UI)
  - 기본 제공 프롬프트 3종 자동 생성 (구조화/요약/액션아이템)
affects: [15-02-PLAN, minutes-generation]

tech-stack:
  added: []
  patterns: [Room Entity-Domain 분리 패턴, isBuiltIn SQL 보호 삭제]

key-files:
  created:
    - app/src/main/java/com/autominuting/domain/model/PromptTemplate.kt
    - app/src/main/java/com/autominuting/data/local/entity/PromptTemplateEntity.kt
    - app/src/main/java/com/autominuting/data/local/dao/PromptTemplateDao.kt
    - app/src/main/java/com/autominuting/domain/repository/PromptTemplateRepository.kt
    - app/src/main/java/com/autominuting/data/repository/PromptTemplateRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/templates/PromptTemplateViewModel.kt
    - app/src/main/java/com/autominuting/presentation/templates/PromptTemplateScreen.kt
  modified:
    - app/src/main/java/com/autominuting/data/local/AppDatabase.kt
    - app/src/main/java/com/autominuting/di/DatabaseModule.kt
    - app/src/main/java/com/autominuting/di/RepositoryModule.kt
    - app/src/main/java/com/autominuting/presentation/navigation/Screen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt
    - app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt

key-decisions:
  - "isBuiltIn 템플릿은 DAO SQL WHERE 조건으로 삭제 차단 (isBuiltIn = 0 조건)"
  - "기본 템플릿 3종은 dao.count() == 0일 때만 자동 생성 (중복 방지)"

patterns-established:
  - "isBuiltIn 패턴: SQL WHERE 조건으로 삭제 보호, UI에서 이름 readOnly"

requirements-completed: [MINS-02]

duration: 5min
completed: 2026-03-26
---

# Phase 15 Plan 01: 프롬프트 템플릿 데이터 모델 + 관리 UI Summary

**Room prompt_templates 테이블(v3 마이그레이션) + 기본 3종 자동 생성 + 템플릿 추가/편집/삭제 관리 화면**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-26T07:50:04Z
- **Completed:** 2026-03-26T07:55:25Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- PromptTemplate Room 데이터 레이어 구축 (Entity, DAO, Repository, DI, DB v2->v3 마이그레이션)
- 기본 제공 프롬프트 3종 (구조화/요약/액션아이템) 자동 생성 로직
- 프롬프트 템플릿 관리 화면 (목록, 추가/편집 다이얼로그, 삭제 확인)
- 설정 화면에서 "프롬프트 템플릿 관리" 버튼으로 네비게이션 연결

## Task Commits

Each task was committed atomically:

1. **Task 1: PromptTemplate 데이터 레이어** - `5ae457c` (feat)
2. **Task 2: 프롬프트 템플릿 관리 화면 + 네비게이션 연결** - `9dad87a` (feat)

## Files Created/Modified
- `domain/model/PromptTemplate.kt` - 도메인 모델 (id, name, promptText, isBuiltIn, timestamps)
- `data/local/entity/PromptTemplateEntity.kt` - Room Entity + toDomain/fromDomain 변환
- `data/local/dao/PromptTemplateDao.kt` - CRUD DAO (isBuiltIn 삭제 보호)
- `domain/repository/PromptTemplateRepository.kt` - Repository 인터페이스
- `data/repository/PromptTemplateRepositoryImpl.kt` - 구현체 (ensureDefaultTemplates 포함)
- `data/local/AppDatabase.kt` - v3 마이그레이션, PromptTemplateEntity 추가
- `di/DatabaseModule.kt` - PromptTemplateDao 제공, MIGRATION_2_3 등록
- `di/RepositoryModule.kt` - PromptTemplateRepository 바인딩
- `presentation/templates/PromptTemplateViewModel.kt` - 목록 조회/추가/편집/삭제
- `presentation/templates/PromptTemplateScreen.kt` - 관리 UI (다이얼로그 포함)
- `presentation/navigation/Screen.kt` - PromptTemplates 경로 추가
- `presentation/navigation/AppNavigation.kt` - composable 등록
- `presentation/settings/SettingsScreen.kt` - 프롬프트 템플릿 관리 버튼 추가

## Decisions Made
- isBuiltIn 템플릿은 DAO SQL WHERE 조건으로 삭제 차단 (앱 레벨 보호보다 안전)
- 기본 템플릿 3종은 dao.count() == 0일 때만 자동 생성 (최초 1회)
- combinedClickable로 탭=편집, 롱프레스=삭제 UX 구현

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- 15-02 Plan에서 이 템플릿을 회의록 생성 파이프라인에 연결하여 사용자가 선택한 프롬프트로 회의록을 생성할 수 있다
- PromptTemplateRepository.getAll()로 템플릿 목록 조회 가능

---
*Phase: 15-manual-minutes*
*Completed: 2026-03-26*

## Self-Check: PASSED
