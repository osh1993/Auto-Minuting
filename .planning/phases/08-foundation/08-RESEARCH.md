# Phase 8: 기반 강화 - Research

**Researched:** 2026-03-25
**Domain:** 회의 레코드 삭제, API 키 암호화 저장, Room DB 마이그레이션, GeminiEngine 인증 추상화
**Confidence:** HIGH

## Summary

Phase 8은 네 가지 독립적인 기능 영역을 다룬다: (1) 회의 레코드 삭제 시 DB + 연관 파일 정합성 보장, (2) Gemini API 키를 설정 UI에서 입력/암호화 저장, (3) GeminiEngine이 사용자 API 키를 우선 사용하도록 인증 추상화, (4) Room DB v1->v2 마이그레이션(source 필드 추가). 각 영역은 기존 코드베이스의 확립된 패턴(DataStore, Hilt DI, Compose UI)을 따르므로 신규 아키텍처 도입 없이 구현 가능하다.

API 키 암호화 저장에 대해 CONTEXT.md에서 EncryptedSharedPreferences를 결정했으나, 해당 라이브러리가 2025년 4월 공식 deprecated 되었다. 단, `security-crypto:1.0.0` stable 버전은 여전히 동작하며 단순 API 키 1개 저장 용도로는 충분하다. Tink + Proto DataStore로의 전면 전환은 이 Phase 범위를 초과하므로, EncryptedSharedPreferences `1.0.0` stable을 사용하되 deprecated 경고를 suppressAnnotation으로 처리하는 실용적 접근을 권장한다.

**Primary recommendation:** 기존 패턴을 최대한 활용하여 4개 기능을 순차적으로 구현. 삭제는 Repository 계층에서 파일+DB 원자적 처리, API 키는 EncryptedSharedPreferences 별도 저장(기존 DataStore와 분리), Room migration은 수동 Migration 객체 사용.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 회의 목록에서 카드 길게 누르기(long press)로 삭제 트리거
- **D-02:** 삭제 전 확인 대화상자(AlertDialog) 표시 -- "이 회의록을 삭제할까요?" 확인 후 삭제
- **D-03:** 삭제 시 DB 레코드 + audioFilePath + transcriptPath + minutesPath 연관 파일 모두 삭제
- **D-04:** 기존 SettingsScreen에 "Gemini API" 섹션 추가 (별도 화면 아님)
- **D-05:** OutlinedTextField + 마스킹 토글(눈 아이콘) + 저장 버튼 구성
- **D-06:** 저장 시 Gemini API 테스트 호출로 유효성 검증. 성공 시 저장, 실패 시 에러 표시
- **D-07:** API 키는 Security Crypto(EncryptedSharedPreferences)로 암호화 저장
- **D-08:** Phase 8에서는 단순 구조: DataStore에 저장된 API 키 우선 사용, 없으면 BuildConfig.GEMINI_API_KEY 폴백
- **D-09:** GeminiEngine이 API 키를 외부에서 주입받도록 변경 (생성자 또는 파라미터). Phase 12에서 OAuth 추가 시 interface 도입
- **D-10:** BuildConfig 하드코딩은 폴백으로만 유지, 사용자 설정 API 키가 우선
- **D-11:** AppDatabase version 1->2 마이그레이션 작성. exportSchema=true로 변경
- **D-12:** MeetingEntity에 source 필드 추가 (Phase 9 삼성 공유 수신 대비). 기본값 "PLAUD_BLE"

### Claude's Discretion
- Room migration 전략 (addMigration vs fallbackToDestructiveMigration) -- 데이터 보존 필수이므로 addMigration 권장
- API 키 암호화 저장 방식의 구체적 구현 (EncryptedSharedPreferences vs DataStore + Cipher)
- 삭제 확인 대화상자의 정확한 문구

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FILE-01 | 사용자가 회의 레코드를 삭제하면 DB 레코드와 연관 파일(오디오, 전사, 회의록)이 함께 정리된다 | MeetingRepositoryImpl.deleteMeeting() 확장, File.delete() + DAO.delete() 조합, combinedClickable long-press + AlertDialog UI 패턴 |
| AUTH-01 | 사용자가 설정 화면에서 Gemini API 키를 입력/변경할 수 있고, 암호화되어 저장된다 | EncryptedSharedPreferences 1.0.0 + SecureApiKeyRepository, SettingsScreen 섹션 추가, GeminiEngine API 키 주입 패턴 |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **응답 언어**: 한국어
- **코드 주석**: 한국어
- **커밋 메시지**: 한국어
- **문서화**: 한국어
- **변수명/함수명**: 영어
- **플랫폼**: Android 네이티브 (Kotlin)
- **DI**: Hilt
- **UI**: Jetpack Compose + Material 3
- **DB**: Room 2.8.4
- **설정 저장**: DataStore
- **빌드**: KSP (kapt 사용 금지)
- **GSD 워크플로우**: 직접 편집 금지, GSD 명령어 통해 작업

## Standard Stack

이 Phase에 새로 추가되는 의존성:

### New Dependencies
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.security:security-crypto | 1.0.0 | API 키 암호화 저장 | EncryptedSharedPreferences 제공. deprecated이지만 1.0.0 stable은 동작. 단순 API 키 1개 저장에 충분 |

### Existing (No Changes)
| Library | Version | Purpose |
|---------|---------|---------|
| Room | 2.8.4 | DB + Migration |
| DataStore | 1.2.1 | 사용자 설정 (회의록 형식, 자동화 모드) |
| Compose BOM | 2026.03.00 | UI |
| Material 3 | (BOM) | AlertDialog, OutlinedTextField, Icon |
| Hilt | 2.59.2 | DI |
| generativeai | 0.9.0 | Gemini API 테스트 호출 |

**Installation (libs.versions.toml에 추가):**
```toml
[versions]
securityCrypto = "1.0.0"

[libraries]
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.security.crypto)
```

**EncryptedSharedPreferences Deprecation 참고:**
- 2025년 4월 alpha07에서 deprecated 선언됨
- 권장 대체: Tink + Proto DataStore (복잡도 높음, Phase 범위 초과)
- 실용적 결정: `1.0.0` stable 사용 + `@Suppress("DEPRECATION")` 처리
- Phase 12 OAuth 작업 시 Tink 마이그레이션 검토 가능

## Architecture Patterns

### 기존 프로젝트 구조 (변경 없음)
```
app/src/main/java/com/autominuting/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # version 1->2, exportSchema=true
│   │   ├── dao/MeetingDao.kt       # 변경 없음
│   │   ├── entity/MeetingEntity.kt  # source 필드 추가
│   │   └── converter/Converters.kt  # 변경 없음
│   ├── minutes/
│   │   └── GeminiEngine.kt          # API 키 주입받도록 변경
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt  # 변경 없음
│   ├── repository/
│   │   └── MeetingRepositoryImpl.kt      # 파일 삭제 로직 추가
│   └── security/                     # 신규 패키지
│       └── SecureApiKeyRepository.kt # EncryptedSharedPreferences 래핑
├── di/
│   ├── DatabaseModule.kt            # Migration 객체 추가
│   └── SecurityModule.kt            # 신규: EncryptedSharedPreferences 제공
├── domain/
│   └── repository/
│       └── MeetingRepository.kt     # 변경 없음 (deleteMeeting 이미 존재)
└── presentation/
    ├── minutes/
    │   ├── MinutesScreen.kt          # long-press + 삭제 대화상자 추가
    │   └── MinutesViewModel.kt       # deleteMeeting 액션 추가
    └── settings/
        ├── SettingsScreen.kt         # Gemini API 키 섹션 추가
        └── SettingsViewModel.kt      # API 키 저장/검증 로직 추가
```

### Pattern 1: 파일 + DB 삭제 정합성 (Repository 계층)
**What:** deleteMeeting()에서 먼저 Entity를 조회하여 파일 경로를 얻고, 파일 삭제 후 DB 레코드 삭제
**When to use:** 연관 리소스가 있는 레코드 삭제 시
**Example:**
```kotlin
// MeetingRepositoryImpl.kt
override suspend fun deleteMeeting(id: Long) {
    // 1. 파일 경로 조회 (삭제 전에 Entity에서 가져와야 함)
    val entity = meetingDao.getMeetingByIdOnce(id) ?: return

    // 2. 연관 파일 삭제 (실패해도 DB 삭제 진행)
    listOfNotNull(
        entity.audioFilePath,
        entity.transcriptPath,
        entity.minutesPath
    ).forEach { path ->
        try { File(path).delete() } catch (_: Exception) { }
    }

    // 3. DB 레코드 삭제
    meetingDao.delete(id)
}
```

**주의:** MeetingDao에 `getMeetingByIdOnce(id): MeetingEntity?` (suspend, non-Flow) 쿼리가 필요함. 기존 `getMeetingById(id)`는 Flow를 반환하므로 삭제 로직에서 사용하기 부적절.

### Pattern 2: EncryptedSharedPreferences 래핑 Repository
**What:** API 키를 암호화 저장/조회하는 전용 Repository
**When to use:** 민감한 데이터를 기존 DataStore와 분리하여 암호화 저장 시
**Example:**
```kotlin
// SecureApiKeyRepository.kt
@Singleton
class SecureApiKeyRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "secure_api_keys",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 저장된 Gemini API 키를 반환한다. 없으면 null. */
    fun getGeminiApiKey(): String? =
        encryptedPrefs.getString(KEY_GEMINI_API, null)

    /** Gemini API 키를 암호화하여 저장한다. */
    fun saveGeminiApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_GEMINI_API, apiKey).apply()
    }

    /** Gemini API 키를 삭제한다. */
    fun clearGeminiApiKey() {
        encryptedPrefs.edit().remove(KEY_GEMINI_API).apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
```

### Pattern 3: GeminiEngine API 키 주입
**What:** GeminiEngine 생성자에 API 키 제공자를 주입하여 BuildConfig 하드코딩 제거
**When to use:** 외부 서비스 키가 동적으로 변경 가능할 때
**Example:**
```kotlin
// GeminiEngine.kt -- 변경 후
@Singleton
class GeminiEngine @Inject constructor(
    private val secureApiKeyRepository: SecureApiKeyRepository
) {
    suspend fun generate(
        transcriptText: String,
        format: MinutesFormat = MinutesFormat.STRUCTURED
    ): Result<String> {
        // 사용자 설정 키 우선, 없으면 BuildConfig 폴백
        val apiKey = secureApiKeyRepository.getGeminiApiKey()
            ?: BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API 키가 설정되지 않았습니다"))
        }

        val model = GenerativeModel(modelName = MODEL_NAME, apiKey = apiKey)
        // ... 이하 동일
    }
}
```

### Pattern 4: Room Manual Migration (v1 -> v2)
**What:** ALTER TABLE로 source 컬럼 추가
**When to use:** 기존 데이터 보존이 필수인 스키마 변경
**Example:**
```kotlin
// AppDatabase.kt 내부 또는 별도 파일
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN source TEXT NOT NULL DEFAULT 'PLAUD_BLE'")
    }
}
```

```kotlin
// DatabaseModule.kt
@Provides
@Singleton
fun provideDatabase(
    @ApplicationContext context: Context
): AppDatabase = Room.databaseBuilder(
    context,
    AppDatabase::class.java,
    "auto_minuting.db"
).addMigrations(MIGRATION_1_2)
 .build()
```

### Pattern 5: Long-Press 삭제 + AlertDialog (Compose)
**What:** combinedClickable로 long-press 감지, AlertDialog로 확인 후 삭제
**When to use:** 목록 아이템 삭제 UX
**Example:**
```kotlin
// MinutesScreen.kt
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinutesMeetingCard(
    meeting: Meeting,
    onClick: () -> Unit,
    onDeleteRequest: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onDeleteRequest(meeting.id) }
            )
    ) { /* ... */ }
}

// 삭제 확인 대화상자
@Composable
fun DeleteConfirmationDialog(
    meetingTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회의록 삭제") },
        text = { Text("\"$meetingTitle\" 회의록을 삭제할까요?\n관련된 오디오, 전사, 회의록 파일이 모두 삭제됩니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
```

### Pattern 6: API 키 입력 UI (마스킹 토글)
**What:** OutlinedTextField + 비밀번호 마스킹 토글 + 저장/검증 버튼
**Example:**
```kotlin
// SettingsScreen.kt 내 Gemini API 섹션
var apiKeyInput by remember { mutableStateOf("") }
var isKeyVisible by remember { mutableStateOf(false) }

OutlinedTextField(
    value = apiKeyInput,
    onValueChange = { apiKeyInput = it },
    label = { Text("Gemini API 키") },
    visualTransformation = if (isKeyVisible)
        VisualTransformation.None
    else
        PasswordVisualTransformation(),
    trailingIcon = {
        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
            Icon(
                imageVector = if (isKeyVisible)
                    Icons.Default.VisibilityOff
                else
                    Icons.Default.Visibility,
                contentDescription = if (isKeyVisible) "숨기기" else "보기"
            )
        }
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
)
```

### Anti-Patterns to Avoid
- **DataStore에 API 키 평문 저장:** DataStore는 암호화되지 않으므로 민감 데이터 저장 금지. 반드시 EncryptedSharedPreferences 사용
- **파일 삭제 후 DB 삭제 트랜잭션 의존:** Room Transaction 안에서 파일 I/O를 하면 안 됨. 파일 삭제가 실패해도 DB 삭제는 진행해야 함 (고아 파일 > 고아 레코드)
- **Flow 기반 getMeetingById로 삭제 전 조회:** Flow는 비동기 스트림이므로 삭제 전 일회성 조회에는 suspend 함수 사용
- **fallbackToDestructiveMigration 사용:** 기존 사용자 데이터 손실. 반드시 수동 Migration 사용

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| API 키 암호화 | 자체 AES/Cipher 구현 | EncryptedSharedPreferences | KeyStore 통합, 키 관리, IV 처리를 자동화 |
| 비밀번호 마스킹 | 커스텀 TextTransformation | PasswordVisualTransformation | Compose 내장, 접근성 지원 |
| DB 스키마 버전 관리 | 수동 SQLite 파일 조작 | Room Migration | 컴파일 타임 검증, 자동 버전 추적 |
| 삭제 확인 UI | 커스텀 오버레이 | Material3 AlertDialog | 시스템 테마 통합, 접근성 자동 지원 |

**Key insight:** 이 Phase의 모든 기능은 Android/Jetpack에서 표준 솔루션이 존재하며, 커스텀 구현은 보안 취약점이나 호환성 문제를 야기한다.

## Common Pitfalls

### Pitfall 1: EncryptedSharedPreferences 초기화 실패 (특정 OEM)
**What goes wrong:** Samsung/Xiaomi 일부 기기에서 KeyStore 초기화 실패 시 앱 크래시
**Why it happens:** Android KeyStore 구현이 OEM마다 다름. 키셋 손상(keyset corruption) 발생 가능
**How to avoid:** try-catch로 감싸고, 실패 시 일반 SharedPreferences 폴백 + 사용자에게 경고 표시. 또는 키를 삭제하고 재생성
**Warning signs:** `InvalidProtocolBufferException`, `GeneralSecurityException` 크래시 로그

### Pitfall 2: Room Migration에서 NOT NULL 컬럼 추가 시 기본값 누락
**What goes wrong:** `ALTER TABLE ADD COLUMN source TEXT NOT NULL` 실행 시 기본값 없으면 SQLite 에러
**Why it happens:** SQLite ALTER TABLE은 NOT NULL 컬럼 추가 시 DEFAULT 절 필수
**How to avoid:** 반드시 `DEFAULT 'PLAUD_BLE'` 포함. Entity의 기본값과 Migration SQL의 DEFAULT가 일치해야 함
**Warning signs:** 앱 업데이트 후 즉시 크래시, `Room cannot verify the data integrity` 에러

### Pitfall 3: exportSchema=true 전환 시 스키마 파일 경로 미설정
**What goes wrong:** Room KSP가 스키마 JSON 파일 생성 위치를 모름
**Why it happens:** exportSchema=true 설정 시 KSP argument로 `room.schemaLocation`을 지정해야 함
**How to avoid:** build.gradle.kts에 추가:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```
**Warning signs:** 빌드 경고 "Schema export directory is not provided to the annotation processor"

### Pitfall 4: 파일 삭제 시 경로가 null인 경우
**What goes wrong:** transcriptPath나 minutesPath가 null일 수 있음 (파이프라인 미완료 상태)
**Why it happens:** MeetingEntity에서 transcriptPath, minutesPath는 nullable
**How to avoid:** listOfNotNull()로 null 필터링 후 삭제 시도

### Pitfall 5: API 키 검증 시 네트워크 타임아웃
**What goes wrong:** 사용자가 저장 버튼을 누르고 오래 기다림
**Why it happens:** Gemini API 호출이 느리거나 네트워크 불안정
**How to avoid:** 타임아웃 설정 (5초), 로딩 인디케이터 표시, 실패해도 "키를 저장하시겠습니까?" 옵션 제공
**Warning signs:** UI가 응답하지 않음, ANR 보고

### Pitfall 6: combinedClickable ExperimentalFoundationApi
**What goes wrong:** `combinedClickable`이 ExperimentalFoundationApi로 표시됨
**Why it happens:** Compose Foundation에서 아직 실험적 API
**How to avoid:** `@OptIn(ExperimentalFoundationApi::class)` 어노테이션 추가

## Code Examples

### MeetingDao에 추가할 suspend 조회 함수
```kotlin
// MeetingDao.kt에 추가
/** 삭제 전 파일 경로 조회용 일회성 조회 */
@Query("SELECT * FROM meetings WHERE id = :id")
suspend fun getMeetingByIdOnce(id: Long): MeetingEntity?
```

### MeetingEntity source 필드 추가
```kotlin
// MeetingEntity.kt -- 변경 후
@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // ... 기존 필드 동일 ...
    /** 데이터 소스 (PLAUD_BLE, SAMSUNG_SHARE 등) */
    val source: String = "PLAUD_BLE"
)
```

### AppDatabase v2 설정
```kotlin
@Database(
    entities = [MeetingEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
}
```

### API 키 검증 (SettingsViewModel)
```kotlin
// SettingsViewModel.kt에 추가
fun validateAndSaveApiKey(apiKey: String) {
    viewModelScope.launch {
        _apiKeyValidationState.value = ApiKeyValidationState.Validating
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
            // 최소 비용 테스트 호출
            val response = model.generateContent("Hello")
            if (response.text != null) {
                secureApiKeyRepository.saveGeminiApiKey(apiKey)
                _apiKeyValidationState.value = ApiKeyValidationState.Success
            } else {
                _apiKeyValidationState.value = ApiKeyValidationState.Error("빈 응답")
            }
        } catch (e: Exception) {
            _apiKeyValidationState.value = ApiKeyValidationState.Error(
                e.message ?: "API 키 검증 실패"
            )
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedSharedPreferences | Tink + Proto DataStore | 2025-04 deprecated | 단순 API 키 저장에는 ESP 1.0.0 여전히 유효. 복잡한 데이터는 Tink 권장 |
| Room KAPT | Room KSP | 2024 | 이미 프로젝트에서 KSP 사용 중 |
| fallbackToDestructiveMigration | AutoMigration / Manual Migration | Room 2.4+ | 데이터 보존 필수. 수동 Migration 사용 |

**Deprecated/outdated:**
- `EncryptedSharedPreferences`: 2025-04 deprecated (alpha07). 1.0.0 stable은 동작하나 신규 개발에는 Tink 권장
- `kapt`: 유지보수 모드. 이미 KSP 사용 중이므로 해당 없음

## Open Questions

1. **EncryptedSharedPreferences OEM 호환성**
   - What we know: Samsung 기기에서 키셋 손상 보고가 있음
   - What's unclear: 대상 기기(Samsung Galaxy)에서의 실제 안정성
   - Recommendation: try-catch 래핑 + 로깅으로 방어적 구현. 실기기 테스트 시 확인

2. **API 키 검증 비용**
   - What we know: Gemini API는 호출당 토큰 비용 발생
   - What's unclear: "Hello" 같은 최소 프롬프트의 과금 여부
   - Recommendation: 가능한 짧은 프롬프트 사용 (models.list API는 SDK에서 지원하지 않으므로 generateContent 사용)

## Discretion Recommendations

### Room Migration 전략: addMigration (수동)
**Recommendation:** `addMigrations(MIGRATION_1_2)` 사용
**Rationale:**
- 데이터 보존 필수 (사용자 기존 회의록 손실 불가)
- 스키마 변경이 단순 (컬럼 1개 추가) -- AutoMigration도 가능하나, exportSchema가 기존에 false였으므로 v1 스키마 JSON이 없어 AutoMigration 불가
- 수동 Migration이 가장 안전하고 명시적

### API 키 암호화: EncryptedSharedPreferences 1.0.0
**Recommendation:** EncryptedSharedPreferences 사용 (CONTEXT.md 결정 D-07 준수)
**Rationale:**
- 단일 API 키 문자열 저장 용도 -- 복잡한 Tink + Proto DataStore는 과잉
- 1.0.0 stable 버전은 프로덕션에서 검증됨
- 기존 DataStore와 별도 저장소로 분리하여 관심사 분리
- deprecated이지만 기능 제거가 아닌 권장 변경이므로 동작에는 문제 없음

### 삭제 확인 대화상자 문구
**Recommendation:**
- 제목: "회의록 삭제"
- 본문: "\"[회의 제목]\" 회의록을 삭제할까요?\n관련된 오디오, 전사, 회의록 파일이 모두 삭제됩니다."
- 확인: "삭제", 취소: "취소"
- 파일이 함께 삭제됨을 명시하여 사용자가 인지하도록 함

## Sources

### Primary (HIGH confidence)
- 기존 코드베이스 직접 분석 -- MeetingEntity, MeetingDao, MeetingRepositoryImpl, GeminiEngine, SettingsScreen, SettingsViewModel, DatabaseModule, UserPreferencesRepository, AppDatabase, DataStoreModule, MinutesScreen, MinutesViewModel, build.gradle.kts, libs.versions.toml
- Android Room Migration 공식 문서 -- https://developer.android.com/training/data-storage/room/migrating-db-versions
- Compose Dialog 공식 문서 -- https://developer.android.com/develop/ui/compose/components/dialog

### Secondary (MEDIUM confidence)
- EncryptedSharedPreferences deprecation -- https://developer.android.com/jetpack/androidx/releases/security
- Tink + DataStore 대안 -- https://github.com/Jaypatelbond/Android-DataStore-Tink-Migration
- Gemini API 키 사용 공식 가이드 -- https://ai.google.dev/gemini-api/docs/api-key

### Tertiary (LOW confidence)
- EncryptedSharedPreferences OEM 키셋 손상 이슈 -- 커뮤니티 보고 (WebSearch, 다수 출처 일치)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- 기존 프로젝트 의존성 + security-crypto 1.0.0 (well-known stable)
- Architecture: HIGH -- 기존 패턴(Repository, ViewModel, DataStore) 확장, 신규 아키텍처 없음
- Pitfalls: HIGH -- Room migration, ESP 이슈 모두 공식 문서 + 커뮤니티에서 잘 문서화됨

**Research date:** 2026-03-25
**Valid until:** 2026-04-25 (stable stack, 변화 가능성 낮음)
