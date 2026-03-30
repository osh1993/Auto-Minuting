# Phase 36: Minutes 데이터 모델 분리 - Research

**Researched:** 2026-03-30
**Domain:** Room DB 마이그레이션 + 데이터 모델 리팩토링
**Confidence:** HIGH

## Summary

현재 Meeting 테이블에 `minutesPath`와 `minutesTitle` 컬럼이 직접 포함되어 1:1 관계로 강제되어 있다. Phase 36은 Minutes 독립 테이블을 신설하고, Room DB v4 -> v5 마이그레이션으로 기존 데이터를 이관한 뒤, Meeting 테이블에서 해당 컬럼을 제거하는 작업이다.

현재 `regenerateMinutes()`는 **새 Meeting Row를 복제**하여 회의록을 생성하는 워크어라운드를 사용 중이며, `MINUTES_ONLY` PipelineStatus는 전사 삭제 시 회의록을 보존하기 위한 또 다른 워크어라운드이다. 이 두 가지 모두 Minutes 테이블 분리로 자연스럽게 해소된다.

**Primary recommendation:** Minutes 테이블(minutes)을 `meetingId` FK로 신설하고, MIGRATION_4_5에서 기존 minutesPath/minutesTitle 데이터를 이관한 후 SQLite의 테이블 재생성 패턴으로 Meeting 컬럼을 제거한다. UI 변경은 Phase 38로 미루되, Phase 36에서는 기존 UI가 깨지지 않도록 Repository 계층에서 호환 레이어를 유지한다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Minutes 독립 테이블이 신설되어 회의록이 Meeting과 별도 Row로 저장된다 (Room DB v5 마이그레이션) | MinutesEntity 스키마 설계 + MIGRATION_4_5 SQL 완성 |
| DATA-02 | 하나의 전사(Meeting)에서 여러 회의록(Minutes)을 생성할 수 있다 (1:N 관계) | meetingId FK + MinutesDao 설계 + MinutesGenerationWorker 변경 |
</phase_requirements>

## 현재 데이터 모델 분석

### Meeting 테이블 (v4) 스키마

```sql
CREATE TABLE meetings (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    recordedAt INTEGER NOT NULL,
    audioFilePath TEXT NOT NULL,
    transcriptPath TEXT,
    minutesPath TEXT,          -- 제거 대상
    pipelineStatus TEXT NOT NULL,
    errorMessage TEXT,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    source TEXT NOT NULL,
    minutesTitle TEXT           -- 제거 대상
)
```

### minutesPath/minutesTitle 사용처 전수 조사

**쓰기 (Write) 지점:**

| 위치 | 동작 | 변경 필요 |
|------|------|----------|
| `MeetingDao.updateMinutes()` | minutesPath + minutesTitle + status 업데이트 | Minutes 테이블에 INSERT로 변경 |
| `MeetingDao.updateMinutesTitle()` | minutesTitle만 업데이트 | MinutesDao.updateTitle()로 이동 |
| `MeetingDao.clearMinutesPath()` | minutesPath/minutesTitle를 NULL로 + TRANSCRIBED 복원 | MinutesDao.delete()로 변경 |
| `MeetingDao.markMinutesOnly()` | audioFilePath='', transcriptPath=NULL, MINUTES_ONLY 설정 | 제거 가능 (Minutes 테이블이 독립) |
| `MinutesGenerationWorker.doWork()` | meetingDao.updateMinutes() 호출 | minutesDao.insert()로 변경 |
| `TranscriptsViewModel.regenerateMinutes()` | 새 Meeting Row 복제 후 Worker enqueue | 기존 Meeting Row 유지 + Minutes Row 추가로 변경 |

**읽기 (Read) 지점:**

| 위치 | 동작 | 변경 필요 |
|------|------|----------|
| `MinutesViewModel.meetings` | `meeting.minutesPath != null` 필터 | Minutes 테이블 JOIN 또는 별도 쿼리로 변경 |
| `MinutesViewModel.shareMinutes()` | `meeting.minutesPath` 파일 읽기 | Minutes 엔티티에서 path 읽기 |
| `MinutesViewModel.getMinutesContent()` | minutesPath로 파일 내용 읽기 | Minutes 엔티티에서 path 읽기 |
| `MinutesViewModel.updateMinutesTitle()` | meetingRepository.updateMinutesTitle() | minutesRepository.updateTitle()로 변경 |
| `MinutesDetailViewModel` | `meeting.minutesPath` 감시 | Minutes 엔티티 감시로 변경 |
| `MinutesDetailScreen` | `currentMeeting.minutesPath == null` 체크 | Minutes 엔티티 null 체크로 변경 |
| `MinutesScreen` | `meeting.minutesPath != null` 표시, minutesTitle 표시 | Minutes 엔티티 기반으로 변경 |
| `TranscriptsScreen` | `meeting.minutesPath != null` 배지 | Meeting에 minutesCount 가상 필드 또는 쿼리 |
| `MeetingRepositoryImpl.deleteMeeting()` | entity.minutesPath 파일 삭제 | Minutes 테이블에서 관련 파일 경로 조회 후 삭제 |
| `MeetingRepositoryImpl.deleteMinutesOnly()` | entity.minutesPath 파일 삭제 + clearMinutesPath | Minutes Row 삭제로 변경 |
| `MeetingRepositoryImpl.deleteTranscript()` | entity.minutesPath != null 체크 -> markMinutesOnly | Minutes 존재 여부 체크 (별도 쿼리) |

### MINUTES_ONLY 워크어라운드 분석

**현재 동작:** 전사를 삭제하면 회의록이 있는 경우 `markMinutesOnly()`로 Meeting Row를 보존하되 audioFilePath/transcriptPath를 비운다.

**사용 위치:**
1. `MeetingDao.markMinutesOnly()` - SQL 쿼리
2. `MeetingRepositoryImpl.deleteTranscript()` - 호출부
3. `MeetingRepositoryImpl.archiveAsMinutesOnly()` - 호출부
4. `MeetingRepository.deleteTranscript()` - 인터페이스
5. `MeetingRepository.archiveAsMinutesOnly()` - 인터페이스
6. `MinutesScreen` - MINUTES_ONLY 상태 배지 표시
7. `TranscriptsViewModel` - TRANSCRIPT_VISIBLE_STATUSES에서 MINUTES_ONLY 제외 (전사 목록에 안 보임)
8. `PipelineStatus.MINUTES_ONLY` - enum 값

**Phase 36 처리:** Minutes 테이블이 독립되면 MINUTES_ONLY 상태가 불필요해진다. 전사를 삭제하면 Meeting Row를 삭제하고, Minutes Row는 meetingId가 NULL이 되어도 (또는 orphaned) 독립 존재한다. 단, Phase 37에서 독립 삭제 로직을 구현하므로, Phase 36에서는 MINUTES_ONLY를 **사용하지 않되 enum에서 제거하지 않는** 전략이 안전하다.

### regenerateMinutes() 워크어라운드 분석

**현재 동작:** `TranscriptsViewModel.regenerateMinutes()`가 원본 Meeting을 `copy(id = 0, minutesPath = null, minutesTitle = null)`로 복제하여 새 Meeting Row를 생성하고, 그 Row에 대해 Worker를 enqueue한다. 결과적으로 동일 전사에서 여러 회의록을 만들지만, Meeting이 중복된다.

**Phase 36 변경:** regenerateMinutes()는 기존 Meeting Row를 유지하고, MinutesGenerationWorker가 완료 시 새 Minutes Row를 INSERT한다. 새 Meeting Row를 생성하지 않는다.

## Minutes 테이블 설계

### MinutesEntity 스키마

```kotlin
@Entity(
    tableName = "minutes",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.SET_NULL  // Meeting 삭제 시 Minutes 보존
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class MinutesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meetingId: Long?,       // FK -> meetings.id, NULL 허용 (Meeting 삭제 후 고아 Minutes)
    val minutesPath: String,    // 회의록 파일 경로 (NOT NULL - 회의록이 있어야 Row 존재)
    val minutesTitle: String?,  // 자동 추출 제목
    val templateId: Long?,      // 생성에 사용된 프롬프트 템플릿 ID (추적용)
    val createdAt: Long,        // 생성 시각 (epoch millis)
    val updatedAt: Long         // 수정 시각 (epoch millis)
)
```

### SQL CREATE

```sql
CREATE TABLE minutes (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    meetingId INTEGER,
    minutesPath TEXT NOT NULL,
    minutesTitle TEXT,
    templateId INTEGER,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    FOREIGN KEY (meetingId) REFERENCES meetings(id) ON DELETE SET NULL
)
CREATE INDEX index_minutes_meetingId ON minutes(meetingId)
```

### FK onDelete 전략: SET_NULL

- Meeting 삭제 시 Minutes Row는 보존되되 `meetingId = NULL`이 된다
- Phase 37의 "전사 삭제해도 회의록 보존" 요구사항과 정확히 일치
- MinutesScreen에서 고아 Minutes도 표시 가능 (출처 전사 없음으로 표기)

### Minutes 도메인 모델

```kotlin
data class Minutes(
    val id: Long = 0,
    val meetingId: Long?,
    val minutesPath: String,
    val minutesTitle: String?,
    val templateId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

## Room DB 마이그레이션 전략: v4 -> v5

### 마이그레이션 SQL (MIGRATION_4_5)

SQLite는 `ALTER TABLE DROP COLUMN`을 지원하지 않는다 (SQLite 3.35.0+에서 지원하나 Android minSdk 31은 SQLite 3.32.2). 따라서 **테이블 재생성 패턴**을 사용한다.

**Confidence: HIGH** - Android Room 공식 문서에서 권장하는 패턴.

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1단계: minutes 테이블 생성
        db.execSQL("""
            CREATE TABLE minutes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                meetingId INTEGER,
                minutesPath TEXT NOT NULL,
                minutesTitle TEXT,
                templateId INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY (meetingId) REFERENCES meetings(id) ON DELETE SET NULL
            )
        """)
        db.execSQL("CREATE INDEX index_minutes_meetingId ON minutes(meetingId)")

        // 2단계: 기존 minutesPath 데이터를 minutes 테이블로 이관
        db.execSQL("""
            INSERT INTO minutes (meetingId, minutesPath, minutesTitle, templateId, createdAt, updatedAt)
            SELECT id, minutesPath, minutesTitle, NULL, updatedAt, updatedAt
            FROM meetings
            WHERE minutesPath IS NOT NULL
        """)

        // 3단계: meetings 테이블 재생성 (minutesPath, minutesTitle 제거)
        db.execSQL("""
            CREATE TABLE meetings_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                recordedAt INTEGER NOT NULL,
                audioFilePath TEXT NOT NULL,
                transcriptPath TEXT,
                pipelineStatus TEXT NOT NULL,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                source TEXT NOT NULL
            )
        """)

        // 4단계: 기존 데이터 복사 (MINUTES_ONLY -> COMPLETED 상태 변환)
        db.execSQL("""
            INSERT INTO meetings_new (id, title, recordedAt, audioFilePath, transcriptPath,
                                      pipelineStatus, errorMessage, createdAt, updatedAt, source)
            SELECT id, title, recordedAt, audioFilePath, transcriptPath,
                   CASE WHEN pipelineStatus = 'MINUTES_ONLY' THEN 'COMPLETED' ELSE pipelineStatus END,
                   errorMessage, createdAt, updatedAt, source
            FROM meetings
        """)

        // 5단계: 기존 테이블 제거 및 새 테이블로 교체
        db.execSQL("DROP TABLE meetings")
        db.execSQL("ALTER TABLE meetings_new RENAME TO meetings")
    }
}
```

### MINUTES_ONLY 상태 데이터 처리

기존 MINUTES_ONLY 상태인 Meeting Row들은:
- `minutesPath`가 있으므로 2단계에서 minutes 테이블로 이관됨
- `audioFilePath = ''`, `transcriptPath = NULL` 상태
- 4단계에서 `pipelineStatus`를 `COMPLETED`로 변환

**주의:** MINUTES_ONLY Meeting Row에서 `audioFilePath = ''`인 경우가 있다. 마이그레이션 후에도 이 데이터는 그대로 유지된다. Phase 37에서 전사 삭제 시 Meeting Row 자체를 삭제하도록 변경하므로, 마이그레이션에서 이 Row들을 삭제할 필요는 없다. 다만 `pipelineStatus`를 `COMPLETED`로 바꿔야 enum에서 MINUTES_ONLY 제거 시 안전하다.

## 의존성 변경 목록 (Phase 36 범위)

### 반드시 변경해야 하는 파일 (데이터 레이어)

| 파일 | 변경 내용 |
|------|----------|
| `MinutesEntity.kt` (신규) | Minutes 테이블 Entity 클래스 |
| `Minutes.kt` (신규, domain) | Minutes 도메인 모델 |
| `MinutesDao.kt` (신규) | Minutes CRUD DAO |
| `AppDatabase.kt` | entities에 MinutesEntity 추가, version=5, MIGRATION_4_5 |
| `DatabaseModule.kt` | addMigrations에 MIGRATION_4_5 추가, minutesDao() 제공 |
| `MeetingEntity.kt` | minutesPath, minutesTitle 필드 제거 |
| `MeetingDao.kt` | updateMinutes(), updateMinutesTitle(), clearMinutesPath(), markMinutesOnly() 제거 |
| `Meeting.kt` (domain) | minutesPath, minutesTitle 필드 제거 |
| `MeetingRepository.kt` | deleteMinutesOnly(), archiveAsMinutesOnly(), updateMinutesTitle() 제거/이동 |
| `MeetingRepositoryImpl.kt` | 위 메서드 구현 제거, deleteMeeting()에서 minutes 파일 삭제 로직 변경 |
| `PipelineStatus.kt` | MINUTES_ONLY 값 제거 (마이그레이션에서 COMPLETED로 변환 후) |

### 반드시 변경해야 하는 파일 (Worker)

| 파일 | 변경 내용 |
|------|----------|
| `MinutesGenerationWorker.kt` | meetingDao.updateMinutes() -> minutesDao.insert() + Meeting status만 업데이트 |

### 반드시 변경해야 하는 파일 (Presentation - 최소한)

| 파일 | 변경 내용 |
|------|----------|
| `MinutesViewModel.kt` | Meeting.minutesPath 대신 Minutes 테이블 쿼리, deleteMinutesOnly -> minutesDao 기반 |
| `MinutesDetailViewModel.kt` | Meeting.minutesPath 대신 Minutes 엔티티 감시 |
| `MinutesScreen.kt` | Meeting.minutesPath/minutesTitle 참조 -> Minutes 엔티티 참조 |
| `MinutesDetailScreen.kt` | Meeting.minutesPath null 체크 -> Minutes 엔티티 |
| `TranscriptsViewModel.kt` | regenerateMinutes() 워크어라운드 제거 (새 Meeting Row 생성 중단) |
| `TranscriptsScreen.kt` | minutesPath 배지 -> minutes count 기반 |

### Phase 36에서 변경하지 않는 파일 (Phase 37-38 범위)

- 독립 삭제 로직 상세 구현 (Phase 37)
- MinutesScreen 카드 디자인 변경 (Phase 38)
- 전사 카드 회의록 수 badge (Phase 38)

## MinutesDao 설계

```kotlin
@Dao
interface MinutesDao {
    /** 모든 회의록을 생성 시각 역순으로 조회한다. */
    @Query("SELECT * FROM minutes ORDER BY createdAt DESC")
    fun getAllMinutes(): Flow<List<MinutesEntity>>

    /** 특정 Meeting에 연결된 회의록 목록을 조회한다. */
    @Query("SELECT * FROM minutes WHERE meetingId = :meetingId ORDER BY createdAt DESC")
    fun getMinutesByMeetingId(meetingId: Long): Flow<List<MinutesEntity>>

    /** 특정 회의록을 ID로 조회한다. */
    @Query("SELECT * FROM minutes WHERE id = :id")
    fun getMinutesById(id: Long): Flow<MinutesEntity?>

    /** 특정 회의록을 ID로 일회성 조회한다. */
    @Query("SELECT * FROM minutes WHERE id = :id")
    suspend fun getMinutesByIdOnce(id: Long): MinutesEntity?

    /** 특정 Meeting에 연결된 회의록 수를 조회한다. */
    @Query("SELECT COUNT(*) FROM minutes WHERE meetingId = :meetingId")
    fun getMinutesCountByMeetingId(meetingId: Long): Flow<Int>

    /** 회의록을 삽입하고 ID를 반환한다. */
    @Insert
    suspend fun insert(entity: MinutesEntity): Long

    /** 회의록 제목을 업데이트한다. */
    @Query("UPDATE minutes SET minutesTitle = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    /** 회의록을 삭제한다. */
    @Query("DELETE FROM minutes WHERE id = :id")
    suspend fun delete(id: Long)

    /** 특정 Meeting의 모든 회의록을 삭제한다. */
    @Query("DELETE FROM minutes WHERE meetingId = :meetingId")
    suspend fun deleteByMeetingId(meetingId: Long)
}
```

## 최소 충격 전략 (Phase 36 범위 제한)

### Phase 36의 원칙

1. **데이터 모델 분리**: Minutes 테이블 신설 + 마이그레이션이 핵심
2. **기존 기능 유지**: 회의록 생성, 조회, 삭제, 공유가 모두 동작해야 함
3. **UI 최소 변경**: MinutesScreen은 데이터 소스만 변경 (Minutes 테이블), 카드 디자인은 Phase 38
4. **MINUTES_ONLY 제거**: PipelineStatus에서 제거하고, 마이그레이션에서 COMPLETED로 변환
5. **regenerateMinutes 정리**: 새 Meeting Row 생성 대신 기존 Meeting에 Minutes Row 추가

### Phase 36에서 하는 것

- MinutesEntity, MinutesDao, Minutes 도메인 모델 신규 생성
- Room DB v4 -> v5 마이그레이션 (데이터 이관 + 컬럼 제거)
- MeetingEntity/Meeting에서 minutesPath/minutesTitle 제거
- MeetingDao에서 minutes 관련 쿼리 제거
- MinutesGenerationWorker가 Minutes 테이블에 INSERT하도록 변경
- MinutesViewModel/MinutesDetailViewModel이 Minutes 테이블을 사용하도록 변경
- MinutesScreen/MinutesDetailScreen의 데이터 소스 변경 (기존 디자인 유지)
- TranscriptsViewModel.regenerateMinutes() 워크어라운드 제거
- TranscriptsScreen의 minutesPath 배지를 minutes count 기반으로 변경
- MINUTES_ONLY PipelineStatus 제거

### Phase 36에서 하지 않는 것

- 전사 삭제 시 Meeting Row 삭제 로직 변경 (Phase 37)
- 회의록 삭제 시 Meeting 상태 미변경 로직 (Phase 37)
- MinutesScreen 카드에 출처 전사 이름 표기 (Phase 38)
- 전사 카드 회의록 수 badge (Phase 38)

## Common Pitfalls

### Pitfall 1: SQLite DROP COLUMN 지원 범위
**What goes wrong:** `ALTER TABLE DROP COLUMN` 사용 시 minSdk 31(SQLite 3.32.2)에서 크래시
**Why it happens:** DROP COLUMN은 SQLite 3.35.0+에서만 지원, API 31은 3.35.0 미만
**How to avoid:** 테이블 재생성 패턴 사용 (CREATE new -> INSERT INTO new SELECT -> DROP old -> RENAME)
**Confidence:** HIGH - Android 공식 문서 확인

### Pitfall 2: ForeignKey onDelete 설정 누락
**What goes wrong:** Meeting 삭제 시 Minutes Row도 함께 삭제되거나, FK 제약 위반 에러
**Why it happens:** onDelete 기본값은 NO_ACTION으로, Meeting 삭제 시 FK 제약 위반
**How to avoid:** `onDelete = ForeignKey.SET_NULL` 명시적 설정 + meetingId를 nullable로 선언
**Confidence:** HIGH

### Pitfall 3: 마이그레이션 데이터 이관 누락
**What goes wrong:** 기존 minutesPath가 있는 Meeting의 회의록이 마이그레이션 후 사라짐
**Why it happens:** INSERT INTO minutes SELECT 쿼리에서 WHERE 조건 누락
**How to avoid:** `WHERE minutesPath IS NOT NULL` 조건 + 마이그레이션 테스트 작성
**Confidence:** HIGH

### Pitfall 4: MINUTES_ONLY 상태 잔류
**What goes wrong:** 마이그레이션 후에도 DB에 MINUTES_ONLY 문자열이 남아있어 enum 파싱 실패
**Why it happens:** 마이그레이션에서 상태 변환을 빼먹음
**How to avoid:** 마이그레이션 SQL에서 CASE WHEN으로 MINUTES_ONLY -> COMPLETED 변환
**Confidence:** HIGH

### Pitfall 5: regenerateMinutes() 중복 Meeting Row 잔류
**What goes wrong:** 이전 regenerateMinutes()로 생성된 중복 Meeting Row가 마이그레이션 후에도 남아있음
**Why it happens:** 과거에 같은 transcriptPath를 공유하는 Meeting Row가 여러 개 존재할 수 있음
**How to avoid:** 이는 기존 데이터의 특성이므로 마이그레이션에서 별도 정리하지 않음. 각 Row의 minutesPath는 고유하므로 minutes 테이블에 각각 이관되어 정상 동작함. 사용자에게 중복 표시는 Phase 38에서 UI 수준에서 처리
**Confidence:** MEDIUM

### Pitfall 6: Room auto-migration 대신 수동 마이그레이션 필요
**What goes wrong:** Room auto-migration은 컬럼 삭제+테이블 생성+데이터 이관을 한 번에 처리 불가
**Why it happens:** Auto-migration은 단순 스키마 변경만 지원, 데이터 변환 로직 불가
**How to avoid:** 수동 Migration 객체 작성 필수
**Confidence:** HIGH

## Architecture Patterns

### 변경 후 프로젝트 구조

```
data/local/
  entity/
    MeetingEntity.kt    # minutesPath, minutesTitle 제거됨
    MinutesEntity.kt    # 신규
    PromptTemplateEntity.kt
  dao/
    MeetingDao.kt       # minutes 관련 쿼리 제거됨
    MinutesDao.kt       # 신규
    PromptTemplateDao.kt
  AppDatabase.kt        # version=5, entities에 MinutesEntity 추가

domain/model/
  Meeting.kt            # minutesPath, minutesTitle 제거됨
  Minutes.kt            # 신규 도메인 모델
  PipelineStatus.kt     # MINUTES_ONLY 제거됨

domain/repository/
  MeetingRepository.kt  # minutes 관련 메서드 제거/이동
  MinutesRepository.kt  # 기존 (generateMinutes 전용), 변경 없음

data/repository/
  MeetingRepositoryImpl.kt  # minutes 관련 로직 제거
```

### Meeting-Minutes 관계

```
meetings (1) ----< (N) minutes
   id                    meetingId (FK, nullable)
                         onDelete = SET_NULL
```

### Meeting PipelineStatus 변경

Minutes 테이블 분리 후 Meeting의 pipelineStatus는 **전사 파이프라인 상태만** 추적한다:
- AUDIO_RECEIVED -> TRANSCRIBING -> TRANSCRIBED -> COMPLETED (전사 완료)
- GENERATING_MINUTES와 COMPLETED는 유지하되, 의미가 변한다:
  - **GENERATING_MINUTES**: 회의록 생성 중 (Minutes Row가 아직 없음)
  - **COMPLETED**: 최소 1개 이상의 Minutes Row 존재

실제로 Phase 36에서는 PipelineStatus 의미 변경을 최소화하고 기존 플로우를 유지한다. MinutesGenerationWorker 완료 시 Meeting 상태를 COMPLETED로 업데이트하는 것은 기존과 동일하되, minutesPath 대신 Minutes Row를 INSERT한다.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 테이블 재생성 SQL | Room auto-migration 시도 | 수동 Migration 객체 | 데이터 이관+컬럼 삭제+상태 변환은 auto-migration 불가 |
| FK 관계 관리 | 수동 ID 참조 무결성 | Room @ForeignKey | Room이 컴파일 타임에 FK 검증 |
| 마이그레이션 테스트 | 수동 DB 생성 | Room MigrationTestHelper | v4 스키마에서 v5로 마이그레이션 자동 검증 |

## Code Examples

### MIGRATION_4_5 전체

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. minutes 테이블 생성
        db.execSQL("""
            CREATE TABLE minutes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                meetingId INTEGER,
                minutesPath TEXT NOT NULL,
                minutesTitle TEXT,
                templateId INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY (meetingId) REFERENCES meetings(id) ON DELETE SET NULL
            )
        """)
        db.execSQL("CREATE INDEX index_minutes_meetingId ON minutes(meetingId)")

        // 2. 기존 데이터 이관
        db.execSQL("""
            INSERT INTO minutes (meetingId, minutesPath, minutesTitle, templateId, createdAt, updatedAt)
            SELECT id, minutesPath, minutesTitle, NULL, updatedAt, updatedAt
            FROM meetings
            WHERE minutesPath IS NOT NULL
        """)

        // 3. meetings 테이블 재생성 (minutesPath, minutesTitle, minutesTitle 제거)
        db.execSQL("""
            CREATE TABLE meetings_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                recordedAt INTEGER NOT NULL,
                audioFilePath TEXT NOT NULL,
                transcriptPath TEXT,
                pipelineStatus TEXT NOT NULL,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                source TEXT NOT NULL
            )
        """)

        // 4. 데이터 복사 (MINUTES_ONLY -> COMPLETED 변환)
        db.execSQL("""
            INSERT INTO meetings_new (id, title, recordedAt, audioFilePath, transcriptPath,
                                      pipelineStatus, errorMessage, createdAt, updatedAt, source)
            SELECT id, title, recordedAt, audioFilePath, transcriptPath,
                   CASE WHEN pipelineStatus = 'MINUTES_ONLY' THEN 'COMPLETED' ELSE pipelineStatus END,
                   errorMessage, createdAt, updatedAt, source
            FROM meetings
        """)

        // 5. 교체
        db.execSQL("DROP TABLE meetings")
        db.execSQL("ALTER TABLE meetings_new RENAME TO meetings")
    }
}
```

### MinutesGenerationWorker 변경 패턴

```kotlin
// 기존: meetingDao.updateMinutes(meetingId, minutesPath, minutesTitle, COMPLETED, now)
// 변경:
val minutesEntity = MinutesEntity(
    meetingId = meetingId,
    minutesPath = minutesPath,
    minutesTitle = minutesTitle,
    templateId = templateId.takeIf { it > 0 },
    createdAt = now,
    updatedAt = now
)
minutesDao.insert(minutesEntity)
meetingDao.updatePipelineStatus(meetingId, PipelineStatus.COMPLETED.name, null, now)
```

### MinutesViewModel 변경 패턴

```kotlin
// 기존: meetingRepository.getMeetings().map { list -> list.filter { it.minutesPath != null } }
// 변경:
val minutes: StateFlow<List<Minutes>> = minutesDao.getAllMinutes()
    .map { list -> list.map { it.toDomain() } }
    .stateIn(...)
```

## Open Questions

1. **regenerateMinutes() 기존 중복 Meeting Row 처리**
   - What we know: 과거 regenerateMinutes()로 생성된 중복 Meeting Row가 존재할 수 있음
   - What's unclear: 얼마나 많은 중복 Row가 있는지, 정리가 필요한지
   - Recommendation: 마이그레이션에서 건드리지 않음. 각 Row에 minutesPath가 있으면 minutes 테이블에 이관되고, 없으면 단순 TRANSCRIBED 상태 Meeting으로 남음. 사용자에게 혼란이 없도록 Phase 38 UI에서 처리

2. **MinutesDetailScreen 라우팅 변경**
   - What we know: 현재 meetingId로 라우팅하여 meeting.minutesPath를 읽음
   - What's unclear: minutesId 기반 라우팅으로 변경할지, meetingId 유지할지
   - Recommendation: Phase 36에서는 minutesId 기반으로 변경. 1:N이므로 meetingId로는 특정 회의록을 지정할 수 없음

## Project Constraints (from CLAUDE.md)

- 기본 응답 언어: 한국어
- 코드 주석: 한국어
- 커밋 메시지: 한국어
- 변수명/함수명: 영어
- Kotlin 2.3.20 + AGP 9.1.0 + Room 2.8.4 + Hilt + Jetpack Compose
- Room schema export 활성화 (app/schemas/ 디렉토리 존재)
- GSD 워크플로우 준수

## Sources

### Primary (HIGH confidence)
- 프로젝트 코드베이스 직접 분석 - AppDatabase.kt, MeetingEntity.kt, MeetingDao.kt, Meeting.kt 등 14개 파일
- Room schema export: app/schemas/com.autominuting.data.local.AppDatabase/4.json - v4 스키마 확인
- Room ForeignKey, Migration 패턴: Android 공식 문서 기반 (training data) + 프로젝트 기존 마이그레이션 패턴 참조

### Secondary (MEDIUM confidence)
- SQLite DROP COLUMN 지원 버전 (3.35.0+): 프로젝트 minSdk 31 = SQLite 3.32.2이므로 테이블 재생성 필수

## Metadata

**Confidence breakdown:**
- 마이그레이션 SQL: HIGH - 기존 프로젝트 패턴(MIGRATION_1_2, 2_3, 3_4) + Room 공식 패턴 일치
- Minutes 스키마 설계: HIGH - 요구사항이 명확하고 표준 1:N FK 관계
- 의존성 분석: HIGH - 전수 grep으로 모든 사용처 파악 완료
- MINUTES_ONLY 처리: HIGH - 마이그레이션에서 COMPLETED로 변환하면 안전

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (안정적 내부 리팩토링, 외부 의존성 변경 없음)
