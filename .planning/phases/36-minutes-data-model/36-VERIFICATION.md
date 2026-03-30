---
phase: 36-minutes-data-model
verified: 2026-03-30T15:30:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 36: Minutes 데이터 모델 분리 Verification Report

**Phase Goal:** Minutes 독립 테이블을 신설하고 Room DB v4->v5 마이그레이션을 통해 전사(Meeting)와 회의록(Minutes)을 1:N 관계로 분리한다. 빌드가 성공해야 한다.
**Verified:** 2026-03-30T15:30:00Z
**Status:** PASSED
**Re-verification:** No — 초기 검증

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Room DB에 Minutes 테이블이 존재하고 meetingId FK로 Meeting과 연결된다 | ✓ VERIFIED | MinutesEntity.kt: `@Entity(tableName="minutes", foreignKeys=[ForeignKey(entity=MeetingEntity::class, ..., onDelete=SET_NULL)])`, schema/5.json 확인 |
| 2 | 하나의 전사에서 회의록을 생성하면 Minutes 테이블에 별도 Row로 저장된다 | ✓ VERIFIED | MinutesGenerationWorker.kt L200: `minutesDao.insert(minutesEntity)` — 회의록 생성 성공 시 새 Row INSERT |
| 3 | 같은 전사에서 회의록을 다시 생성하면 기존 Row 유지 + 새 Row 추가 | ✓ VERIFIED | TranscriptsViewModel.regenerateMinutes() L145-155: 기존 meetingId 재사용 + Worker가 새 MinutesEntity INSERT (copy(id=0) 워크어라운드 제거 확인) |
| 4 | 기존 데이터가 v5 마이그레이션으로 Minutes 테이블에 이관된다 | ✓ VERIFIED | AppDatabase.kt MIGRATION_4_5 L71-76: `INSERT INTO minutes SELECT id, minutesPath, minutesTitle ... FROM meetings WHERE minutesPath IS NOT NULL` |
| 5 | Meeting에서 minutesPath/minutesTitle 제거 + MINUTES_ONLY 워크어라운드 정리 | ✓ VERIFIED | Meeting.kt: minutesPath/minutesTitle 필드 없음. PipelineStatus.kt: MINUTES_ONLY 값 없음 (AUDIO_RECEIVED/TRANSCRIBING/TRANSCRIBED/GENERATING_MINUTES/COMPLETED/FAILED만 존재). MeetingEntity.kt: 두 필드 없음 |
| 6 | assembleDebug BUILD SUCCESSFUL | ✓ VERIFIED | 36-03-SUMMARY.md: "assembleDebug BUILD SUCCESSFUL" 기재. 커밋 7cc6425 "빌드 성공" 메시지 확인 |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | 목적 | Status | Details |
|----------|------|--------|---------|
| `app/src/main/java/com/autominuting/data/local/entity/MinutesEntity.kt` | Minutes Room Entity | ✓ VERIFIED | 68라인, ForeignKey(SET_NULL) + Index, toDomain/fromDomain 매핑 구현 |
| `app/src/main/java/com/autominuting/data/local/dao/MinutesDao.kt` | Minutes CRUD DAO | ✓ VERIFIED | 55라인, 11개 메서드 (Flow 기반 조회 + suspend CRUD) |
| `app/src/main/java/com/autominuting/data/local/AppDatabase.kt` | DB v5 + MIGRATION_4_5 | ✓ VERIFIED | version=5, MinutesEntity 등록, MIGRATION_4_5 5단계 SQL 포함 |
| `app/src/main/java/com/autominuting/domain/model/Meeting.kt` | minutesPath/minutesTitle 제거 | ✓ VERIFIED | 두 필드 없음 확인 |
| `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` | MINUTES_ONLY 제거 | ✓ VERIFIED | MINUTES_ONLY 없음. MIGRATION SQL에서 'MINUTES_ONLY' -> 'COMPLETED' 변환만 참조 |
| `app/src/main/java/com/autominuting/domain/repository/MinutesDataRepository.kt` | 회의록 CRUD 인터페이스 | ✓ VERIFIED | 9개 메서드 인터페이스 |
| `app/src/main/java/com/autominuting/data/repository/MinutesDataRepositoryImpl.kt` | CRUD 구현체 | ✓ VERIFIED | 65라인, MinutesDao 기반 전체 구현 (파일 삭제 포함) |
| `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` | Minutes 테이블 INSERT 기반 워커 | ✓ VERIFIED | minutesDao.insert() 호출 확인 (L200), meetingDao.updatePipelineStatus()로 COMPLETED 설정 |
| `app/schemas/com.autominuting.data.local.AppDatabase/5.json` | DB 스키마 내보내기 | ✓ VERIFIED | minutes 테이블, meetingId FK(SET_NULL), index_minutes_meetingId 모두 반영 |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| MinutesGenerationWorker | MinutesDao | `minutesDao.insert(minutesEntity)` | ✓ WIRED | L200에서 실제 INSERT 호출 |
| MinutesGenerationWorker | MeetingDao | `meetingDao.updatePipelineStatus(...)` | ✓ WIRED | INSERT 후 즉시 COMPLETED 상태 업데이트 |
| MinutesDataRepositoryImpl | MinutesDao | 생성자 주입 + DAO 호출 | ✓ WIRED | 모든 9개 메서드가 minutesDao 위임 |
| AppDatabase MIGRATION_4_5 | meetings -> minutes | INSERT INTO minutes SELECT ... FROM meetings | ✓ WIRED | 데이터 이관 SQL 검증 완료 |
| TranscriptsViewModel.regenerateMinutes | MinutesGenerationWorker | `enqueueMinutesWorker(meetingId)` | ✓ WIRED | 기존 meetingId 재사용, 새 Row 생성 없음 |

---

### Data-Flow Trace (Level 4)

| Artifact | 데이터 변수 | 소스 | 실제 데이터 생성 | Status |
|----------|------------|------|----------------|--------|
| MinutesGenerationWorker | `minutesEntity` | Gemini API 응답 + 파일 저장 후 MinutesEntity 생성 | minutesText (Gemini 생성), minutesPath (파일 저장), minutesTitle (첫 줄 추출) | ✓ FLOWING |
| MinutesDataRepositoryImpl.getAllMinutes | `Flow<List<Minutes>>` | `minutesDao.getAllMinutes()` DB 쿼리 | `SELECT * FROM minutes ORDER BY createdAt DESC` | ✓ FLOWING |
| MIGRATION_4_5 | 이관 데이터 | `meetings.minutesPath IS NOT NULL` 행 | INSERT INTO minutes SELECT... FROM meetings (실제 DB 데이터) | ✓ FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED (Android 앱 — 실행 가능한 엔트리포인트 없음, 빌드 성공은 SUMMARY 기재 및 커밋으로 간접 확인)

---

### Requirements Coverage

| Requirement | 담당 Plan | 설명 | Status | Evidence |
|-------------|----------|------|--------|---------|
| DATA-01 | 36-01 | Minutes 독립 테이블 신설, Room DB v5 마이그레이션, 기존 minutesPath 데이터 이관 | ✓ SATISFIED | MinutesEntity + MIGRATION_4_5 (5단계 SQL) + schema/5.json |
| DATA-02 | 36-02 | 하나의 전사에서 여러 회의록 생성 가능 (1:N) | ✓ SATISFIED | MinutesDao.insert() — FK meetingId로 같은 Meeting에 여러 Row. regenerateMinutes()가 새 Row INSERT |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| AppDatabase.kt | 94-99 | `'MINUTES_ONLY'` 문자열 리터럴 참조 | ℹ️ Info | 마이그레이션 SQL에서만 사용 — enum이 제거된 후 기존 DB 데이터를 변환하기 위한 의도적 패턴. 실제 코드 스텁 아님 |

스텁/플레이스홀더 안티패턴: 해당 없음

---

### Human Verification Required

#### 1. 실 기기 마이그레이션 테스트

**Test:** v4 DB가 있는 기기(기존 사용자 환경)에 v5 빌드를 설치하여 앱 실행
**Expected:** Minutes 테이블 생성, 기존 minutesPath가 있던 Meeting의 데이터가 minutes 테이블로 이관, 앱 크래시 없음
**Why human:** SQLite 마이그레이션은 실 기기 실행 없이 코드 검사만으로 검증 불가

#### 2. 회의록 재생성 1:N 동작 확인

**Test:** 완료된 전사에서 회의록 생성 → 같은 전사에서 다시 회의록 생성
**Expected:** Minutes 목록 화면에 같은 meetingId를 가진 2개의 Row가 표시됨
**Why human:** Worker enqueue → Gemini API 호출 → DB INSERT 전체 파이프라인은 실행 환경 필요

---

### Gaps Summary

갭 없음. 6개 성공 기준 모두 충족:

1. **Minutes 테이블 + FK**: MinutesEntity, schema/5.json 모두 meetingId FK(SET_NULL) 포함
2. **1:N INSERT**: MinutesGenerationWorker가 매 생성마다 새 MinutesEntity Row를 INSERT
3. **재생성 Row 유지**: regenerateMinutes()에서 copy(id=0) 워크어라운드 제거, 기존 meetingId 재사용
4. **v5 마이그레이션 데이터 이관**: MIGRATION_4_5 5단계 SQL (생성 → 이관 → 재생성 → 변환 → 교체)
5. **Meeting 정리**: minutesPath/minutesTitle 필드 제거, MINUTES_ONLY enum 제거 확인
6. **빌드 성공**: SUMMARY 기재 + 커밋 7cc6425 "빌드 성공" 메시지

---

_Verified: 2026-03-30T15:30:00Z_
_Verifier: Claude (gsd-verifier)_
