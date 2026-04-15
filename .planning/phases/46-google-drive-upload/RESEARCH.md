# Phase 46: Google Drive 업로드 파이프라인 (DRIVE-02, DRIVE-03, DRIVE-04) - Research

**Researched:** 2026-04-03
**Domain:** Google Drive REST API v3 / WorkManager 파이프라인 확장 / DataStore 폴더 설정
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DRIVE-02 | 전사 파일이 파이프라인 완료 후 설정된 Google Drive 폴더에 자동 업로드 | Multipart upload + DriveUploadWorker 체이닝 패턴 |
| DRIVE-03 | 생성된 회의록 파일이 파이프라인 완료 후 설정된 Google Drive 폴더에 자동 업로드 | MinutesGenerationWorker 완료 후 DriveUploadWorker 독립 enqueue 패턴 |
| DRIVE-04 | 설정 화면에서 전사/회의록 업로드 폴더를 각각 지정 가능 | DataStore stringPreferencesKey 두 개 + SettingsScreen UI 확장 |
</phase_requirements>

---

## Summary

Phase 46은 Phase 45에서 완성된 Drive 인증 레이어(`GoogleAuthRepository.authorizeDrive()`, `DriveAuthState.Authorized`) 위에 실제 파일 업로드 기능을 추가하는 작업이다. 기술적 핵심은 세 가지다.

첫째, **Drive REST API 직접 호출 (OkHttp multipart)**. 전사 텍스트(`.txt`) 및 회의록(`.md`) 파일은 최대 수십 KB 수준이므로 5MB 제한이 없는 multipart upload(`uploadType=multipart`)가 적합하다. `google-api-client-android` + `google-api-services-drive` 의존성 추가 없이 기존 OkHttp 4.12.0으로 구현 가능하며, 이는 Phase 45 리서치의 결론과 일치한다.

둘째, **독립 DriveUploadWorker 설계**. 기존 파이프라인(`TranscriptionTriggerWorker` → `MinutesGenerationWorker`)에 Drive 업로드를 **체이닝으로 직접 추가하지 않는다**. WorkManager 체인에서 한 Worker가 `Result.failure()`를 반환하면 이후 모든 종속 Worker가 FAILED 처리되므로, Drive 업로드 실패가 기존 파이프라인 성공 상태를 오염시킬 수 있다. 대신, 각 Worker 완료 시점에 `DriveUploadWorker`를 별도 `WorkManager.enqueue()`로 독립 실행한다.

셋째, **DataStore 폴더 ID 저장**. DRIVE-04 요구사항은 전사용/회의록용 폴더 ID를 각각 `stringPreferencesKey`로 `UserPreferencesRepository`에 추가하는 것으로 해결한다. 빈 문자열이면 업로드 비활성, `"root"` 또는 실제 폴더 ID면 업로드 활성화로 처리한다.

**Primary recommendation:** OkHttp multipart upload + 독립 DriveUploadWorker + DataStore 폴더 ID 2개 추가. 신규 의존성 불필요.

---

## Project Constraints (from CLAUDE.md)

### 기술 스택 제약
- Kotlin + Android 네이티브 (Min SDK 31, Target SDK 36)
- Jetpack Compose BOM 2026.03.00, Hilt 2.59.2, DataStore 1.2.1
- OkHttp 4.12.0, Retrofit 2.11.0, WorkManager 2.11.1 이미 추가됨
- `google-api-client-android`, `google-api-services-drive` 추가 금지 — 기존 OkHttp로 해결
- GSD 워크플로 없이 직접 파일 수정 금지

### 기존 인증 구조 (Phase 45 완료)
- `GoogleAuthRepository.getDriveAccessToken()` — 메모리 캐시 Drive access token 반환
- `GoogleAuthRepository.driveAuthState: StateFlow<DriveAuthState>` — Authorized/NotAuthorized 상태
- `UserPreferencesRepository.driveAuthorized: Flow<Boolean>` — DataStore 영속화 완료
- `DriveAuthState` sealed interface — Authorized/NotAuthorized/Loading/NeedsConsent/Error

---

## Standard Stack

### Core (추가 불필요 — 이미 프로젝트에 있음)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | Drive REST API HTTP 호출 | multipart/related 바디 직접 구성 가능 |
| kotlinx-coroutines-android | 1.10.1 | Worker 내 suspend 지원 | CoroutineWorker.doWork() |
| WorkManager (work-runtime-ktx) | 2.11.1 | 백그라운드 업로드 스케줄링 | retry, backoff 내장 |
| Hilt (hilt-work + hilt-android) | 2.59.2 + 1.2.0 | Worker DI | @HiltWorker로 @Singleton 주입 |
| DataStore (datastore-preferences) | 1.2.1 | 폴더 ID 설정 저장 | 기존 `UserPreferencesRepository` 확장 |

### 추가 금지 라이브러리
| Library | 이유 |
|---------|------|
| `google-api-client-android` | OkHttp로 충분, 불필요한 JAR 추가 (~3MB) |
| `google-api-services-drive` | REST 직접 호출로 대체. DriveScopes 상수는 문자열로 직접 선언 |
| `google-http-client-android` | 위와 동일 — 기존 OkHttp 대체 불필요 |

**신규 `build.gradle.kts` 변경:** 없음 (의존성 추가 불필요)

---

## Architecture Patterns

### 권장 구조

```
data/drive/
└── DriveUploadRepository.kt   ← OkHttp multipart 업로드 로직 캡슐화

worker/
├── TranscriptionTriggerWorker.kt  ← 완료 후 DriveUploadWorker 독립 enqueue (DRIVE-02)
├── MinutesGenerationWorker.kt     ← 완료 후 DriveUploadWorker 독립 enqueue (DRIVE-03)
└── DriveUploadWorker.kt           ← @HiltWorker, 독립 실행, 재시도 3회 후 포기

di/
└── RepositoryModule.kt        ← DriveUploadRepository 바인딩 추가

data/preferences/
└── UserPreferencesRepository.kt   ← DRIVE-04: 폴더 ID 키 2개 추가

presentation/settings/
├── SettingsScreen.kt          ← Drive 폴더 설정 UI 섹션 추가 (DRIVE-04)
└── SettingsViewModel.kt       ← 폴더 ID 저장/조회 로직 추가
```

### Pattern 1: OkHttp Multipart Upload (Drive REST API)

**What:** `multipart/related` 포맷으로 메타데이터(JSON) + 파일 바이너리를 단일 요청에 전송한다.
**When to use:** 파일 크기 5MB 이하 (전사 `.txt`/회의록 `.md` 파일은 수 KB ~ 수십 KB 수준).
**Source:** https://developers.google.com/drive/api/guides/manage-uploads#multipart

```kotlin
// Source: https://developers.google.com/drive/api/guides/manage-uploads
// DriveUploadRepository.kt 핵심 구현 패턴

private val UPLOAD_URL =
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

suspend fun uploadFile(
    accessToken: String,
    fileName: String,
    mimeType: String,       // "text/plain" 또는 "text/markdown"
    fileContent: ByteArray,
    parentFolderId: String  // "root" 또는 실제 폴더 ID
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val boundary = "auto_minuting_boundary_${System.currentTimeMillis()}"

        // 메타데이터 JSON 파트
        val metadataJson = """{"name":"$fileName","parents":["$parentFolderId"]}"""
        val metadataPart = metadataJson.toByteArray(Charsets.UTF_8)

        // multipart/related 바디 직접 구성
        val body = buildMultipartBody(boundary, metadataPart, mimeType, fileContent)

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "multipart/related; boundary=$boundary")
            .post(body.toRequestBody())
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val fileId = parseFileId(response.body?.string())
            Result.success(fileId ?: "")
        } else if (response.code == 401) {
            Result.failure(UnauthorizedException("Drive access token 만료 — 재인증 필요"))
        } else {
            Result.failure(IOException("Drive 업로드 실패: HTTP ${response.code}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// multipart/related 바이트 배열 직접 구성
private fun buildMultipartBody(
    boundary: String,
    metadataBytes: ByteArray,
    mimeType: String,
    fileBytes: ByteArray
): ByteArray {
    val nl = "\r\n"
    return buildList<ByteArray> {
        add("--$boundary$nl".toByteArray())
        add("Content-Type: application/json; charset=UTF-8$nl$nl".toByteArray())
        add(metadataBytes)
        add("$nl--$boundary$nl".toByteArray())
        add("Content-Type: $mimeType$nl$nl".toByteArray())
        add(fileBytes)
        add("$nl--$boundary--$nl".toByteArray())
    }.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
}
```

**MIME 타입 결정:**
- 전사 파일(`.txt`): `"text/plain"` — Google Docs로 변환 없이 원본 파일로 저장됨
- 회의록 파일(`.md`): `"text/plain"` 또는 `"text/markdown"` — Drive는 두 타입 모두 원본 형식 유지

### Pattern 2: 독립 DriveUploadWorker 설계

**What:** 기존 파이프라인 체인에 포함하지 않고, 파이프라인 완료 시점에 별도 `enqueue()`로 독립 실행한다.
**Why:** WorkManager 체인에서 `Result.failure()` 전파를 방지하기 위함. Drive 업로드 실패가 `TRANSCRIBED`/`COMPLETED` 상태를 오염시키지 않아야 한다.

```kotlin
// Source: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work
// TranscriptionTriggerWorker.doWork() 성공 후 — 기존 MinutesGenerationWorker 체이닝 이후에 추가

// 전사 완료 후 Drive 업로드 enqueue (DRIVE-02)
val driveWorkRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
    .setInputData(workDataOf(
        DriveUploadWorker.KEY_FILE_PATH to transcriptPath,
        DriveUploadWorker.KEY_FILE_TYPE to DriveUploadWorker.TYPE_TRANSCRIPT,
        DriveUploadWorker.KEY_MEETING_ID to meetingId
    ))
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .build()
WorkManager.getInstance(applicationContext).enqueue(driveWorkRequest)
```

### Pattern 3: DriveUploadWorker 구현 스켈레톤

**What:** `@HiltWorker` + `@Singleton GoogleAuthRepository` 주입으로 access token 접근.
**Key:** `runAttemptCount >= MAX_ATTEMPTS`이면 `Result.failure()`로 재시도 중단.

```kotlin
// DriveUploadWorker.kt 핵심 구조
@HiltWorker
class DriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleAuthRepository: GoogleAuthRepository,   // @Singleton 직접 주입
    private val driveUploadRepository: DriveUploadRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 재시도 횟수 제한
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.w(TAG, "Drive 업로드 최대 재시도 초과: 포기")
            return Result.failure()
        }

        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileType = inputData.getString(KEY_FILE_TYPE) ?: return Result.failure()

        // Drive 인증 상태 확인
        val accessToken = googleAuthRepository.getDriveAccessToken()
        if (accessToken.isNullOrBlank()) {
            Log.w(TAG, "Drive access token 없음 — 업로드 건너뜀")
            // 토큰 없음은 재시도해도 해결 안 됨 → 즉시 failure (사용자 재인증 필요)
            return Result.failure(
                workDataOf(KEY_ERROR to "Drive access token 없음. 설정에서 Drive 연동 필요.")
            )
        }

        // 폴더 ID 조회
        val folderId = when (fileType) {
            TYPE_TRANSCRIPT -> userPreferencesRepository.getDriveTranscriptFolderIdOnce()
            TYPE_MINUTES    -> userPreferencesRepository.getDriveMinutesFolderIdOnce()
            else -> ""
        }
        if (folderId.isBlank()) {
            Log.d(TAG, "Drive 폴더 미설정 — 업로드 건너뜀 (type=$fileType)")
            return Result.success()  // 설정 안 된 것은 오류 아님
        }

        val file = java.io.File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "업로드 대상 파일 없음: $filePath")
            return Result.failure()
        }

        val mimeType = if (fileType == TYPE_MINUTES) "text/plain" else "text/plain"
        val uploadResult = driveUploadRepository.uploadFile(
            accessToken = accessToken,
            fileName = file.name,
            mimeType = mimeType,
            fileContent = file.readBytes(),
            parentFolderId = folderId
        )

        return if (uploadResult.isSuccess) {
            Log.d(TAG, "Drive 업로드 성공: ${file.name} → folderId=$folderId")
            Result.success()
        } else {
            val error = uploadResult.exceptionOrNull()
            if (error is UnauthorizedException) {
                // 401 — 재시도해도 소용없음, 즉시 포기
                Result.failure(workDataOf(KEY_ERROR to "Drive 토큰 만료 — 재인증 필요"))
            } else {
                // 네트워크 오류 등 — 재시도
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_FILE_PATH = "driveFilePath"
        const val KEY_FILE_TYPE = "driveFileType"
        const val KEY_MEETING_ID = "meetingId"
        const val KEY_ERROR = "driveError"
        const val TYPE_TRANSCRIPT = "transcript"
        const val TYPE_MINUTES = "minutes"
        private const val MAX_ATTEMPTS = 3
        private const val TAG = "DriveUploadWorker"
    }
}
```

### Pattern 4: DataStore 폴더 ID 저장 (DRIVE-04)

**What:** `UserPreferencesRepository`에 폴더 ID 키 2개 추가.
**Convention:** 빈 문자열(`""`) = 업로드 비활성. `"root"` = Drive 루트. 실제 폴더 ID 문자열 = 해당 폴더.

```kotlin
// UserPreferencesRepository.kt에 추가

// DRIVE-04: 전사 파일 Drive 업로드 폴더 ID
val DRIVE_TRANSCRIPT_FOLDER_KEY = stringPreferencesKey("drive_transcript_folder_id")

// DRIVE-04: 회의록 Drive 업로드 폴더 ID
val DRIVE_MINUTES_FOLDER_KEY = stringPreferencesKey("drive_minutes_folder_id")

/** 전사 Drive 폴더 ID를 관찰한다. 빈 문자열 = 업로드 비활성. */
val driveTranscriptFolderId: Flow<String> = dataStore.data.map { prefs ->
    prefs[DRIVE_TRANSCRIPT_FOLDER_KEY] ?: ""
}

/** 회의록 Drive 폴더 ID를 관찰한다. 빈 문자열 = 업로드 비활성. */
val driveMinutesFolderId: Flow<String> = dataStore.data.map { prefs ->
    prefs[DRIVE_MINUTES_FOLDER_KEY] ?: ""
}

suspend fun setDriveTranscriptFolderId(folderId: String) {
    dataStore.edit { prefs -> prefs[DRIVE_TRANSCRIPT_FOLDER_KEY] = folderId }
}

suspend fun setDriveMinutesFolderId(folderId: String) {
    dataStore.edit { prefs -> prefs[DRIVE_MINUTES_FOLDER_KEY] = folderId }
}

// Worker에서 즉시 조회용
suspend fun getDriveTranscriptFolderIdOnce(): String =
    dataStore.data.first()[DRIVE_TRANSCRIPT_FOLDER_KEY] ?: ""

suspend fun getDriveMinutesFolderIdOnce(): String =
    dataStore.data.first()[DRIVE_MINUTES_FOLDER_KEY] ?: ""
```

### Pattern 5: SettingsScreen Drive 폴더 설정 UI (DRIVE-04)

**What:** 기존 `GoogleAccountSection` 아래에 Drive 폴더 ID 입력 필드 2개 추가.
**Constraint:** 폴더 ID 획득은 앱 내 Drive 파일 선택 UI 없이 "Google Drive URL에서 폴더 ID 복사" 방식으로 구현 (v1). Drive 파일 선택기(`ACTION_OPEN_DOCUMENT`)는 `drive.file` 스코프로 접근 불가 — 별도 `drive` 스코프 필요하므로 Phase 46 범위 외.

```kotlin
// SettingsScreen 내 Drive 폴더 설정 섹션 (신규 추가)
@Composable
private fun DriveFolderSection(
    driveAuthState: DriveAuthState,
    transcriptFolderId: String,
    minutesFolderId: String,
    onTranscriptFolderIdChange: (String) -> Unit,
    onMinutesFolderIdChange: (String) -> Unit
) {
    // DriveAuthState.Authorized일 때만 표시
    if (driveAuthState !is DriveAuthState.Authorized) return

    SettingsSection(title = "Google Drive 자동 업로드 폴더") {
        Text(
            text = "폴더 ID: Google Drive에서 폴더 열기 → URL의 마지막 경로 복사",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = transcriptFolderId,
            onValueChange = onTranscriptFolderIdChange,
            label = { Text("전사 파일 폴더 ID (비워두면 업로드 안 함)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("예: 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = minutesFolderId,
            onValueChange = onMinutesFolderIdChange,
            label = { Text("회의록 폴더 ID (비워두면 업로드 안 함)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("예: 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms") }
        )
    }
}
```

### Anti-Patterns to Avoid

- **Drive 업로드를 기존 파이프라인 체인에 `.then()` 으로 추가하지 않는다:** 체인 실패가 전파되어 `TRANSCRIBED`/`COMPLETED` 상태가 `FAILED`로 덮어씌워진다.
- **`google-api-services-drive` 라이브러리 사용 금지:** `DriveScopes.DRIVE_FILE` 상수를 얻기 위해 추가하는 경우가 있으나, 문자열 상수로 직접 선언하면 충분하다.
- **Worker에서 Activity Context로 `authorize()` 재호출 금지:** Worker는 ApplicationContext만 갖는다. Drive 토큰이 만료된 경우(401) Worker는 즉시 `Result.failure()`로 포기하고, UI에서 재인증을 유도한다.
- **폴더 ID 검증 없이 저장하지 않는다:** 폴더 ID는 입력값 그대로 DataStore에 저장하되, 업로드 시 Drive API 400/404 응답을 통해 유효하지 않은 ID임을 감지한다.
- **resumable upload를 사용하지 않는다:** 전사/회의록 파일은 수 KB ~ 수십 KB. 5MB 미만이므로 multipart가 충분하다. resumable은 불필요한 복잡성을 추가한다.
- **Drive 업로드 실패를 `Result.retry()` 무한 루프로 빠트리지 않는다:** `runAttemptCount >= MAX_ATTEMPTS`(3회) 체크로 최대 재시도 횟수를 명시적으로 제한한다. WorkManager의 backoff policy는 재시도 횟수를 제한하지 않는다.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP multipart body 구성 | 직접 String 연결로 바디 구성 | OkHttp `RequestBody` + 직접 바이트 배열 조합 | Content-Type boundary 처리, 멀티바이트 파일 데이터 처리 필요 |
| OAuth token 갱신 | Worker 내 authorize() 재호출 | UI 레이어에서 DriveAuthState 관찰 후 재인증 유도 | Worker에는 Activity Context가 없어 authorize() 실행 불가 |
| 업로드 재시도 로직 | 직접 루프 + sleep | WorkManager BackoffPolicy.EXPONENTIAL + runAttemptCount | 앱 종료 후 재시작 시에도 재시도 보장 |
| 폴더 선택 UI | 직접 Drive 파일 목록 API 조회 | 폴더 ID 수동 입력 텍스트필드 (v1) | `drive.file` 스코프로는 Drive 탐색 불가. `drive` 스코프는 심사 필요 |

**Key insight:** `drive.file` 스코프는 앱이 생성한 파일만 접근 가능하다. 즉, Drive 폴더를 앱 내에서 탐색/선택하는 것은 이 스코프로 불가능하다. Phase 46 범위에서는 사용자가 Drive URL에서 폴더 ID를 직접 복사·입력하는 방식이 현실적이고 안전하다.

---

## Common Pitfalls

### Pitfall 1: Drive 업로드 실패가 파이프라인 상태를 FAILED로 만드는 문제
**What goes wrong:** `DriveUploadWorker`를 `.then()`으로 체이닝하면 업로드 실패 시 `MinutesGenerationWorker`의 `COMPLETED` 상태가 `FAILED`로 바뀐다.
**Why it happens:** WorkManager 체인은 `Result.failure()` 전파 방지 메커니즘이 없다.
**How to avoid:** 독립 `WorkManager.enqueue()`로 DriveUploadWorker를 실행한다. 체인과 완전히 분리된 독립 작업으로 취급한다.
**Warning signs:** 회의록 생성은 성공했는데 파이프라인 상태가 FAILED로 표시됨

### Pitfall 2: Worker 내 Drive 토큰 만료 — 무한 retry
**What goes wrong:** 401 응답에 `Result.retry()`를 반환하면 재시도가 반복되지만 토큰이 갱신되지 않아 계속 실패한다.
**Why it happens:** WorkManager Worker에는 Activity Context가 없어 `authorize()` 재호출 불가.
**How to avoid:** 401 응답 시 즉시 `Result.failure()`로 종료하고, `KEY_ERROR`에 "Drive 토큰 만료" 메시지를 담아 UI에서 `DriveAuthState` 관찰 후 재인증 유도.
**Warning signs:** DriveUploadWorker가 3-4시간 후에도 계속 ENQUEUED 상태로 남음

### Pitfall 3: multipart/related boundary 누락 또는 잘못된 형식
**What goes wrong:** Drive API가 400 Bad Request를 반환한다.
**Why it happens:** `Content-Type: multipart/related; boundary=XXX` 헤더에서 `boundary=`와 바디의 `--XXX` 값이 불일치하거나, 최종 boundary `--XXX--`가 누락됨.
**How to avoid:** boundary 문자열을 변수로 선언하여 헤더와 바디 모두 동일한 변수를 참조한다. 최종 경계선은 `--boundary--` (trailing `--` 필수).
**Warning signs:** HTTP 400, `"Invalid multipart/related request"` 에러

### Pitfall 4: 폴더 ID로 "root" vs 빈 문자열 구분
**What goes wrong:** `parents: [""]`로 업로드하면 Drive API 오류 발생.
**Why it happens:** 빈 문자열은 유효한 폴더 ID가 아니다.
**How to avoid:** 빈 문자열 = 업로드 건너뜀(early return). `"root"` = Drive 루트 폴더. 실제 폴더 ID = 해당 폴더. Worker에서 `folderId.isBlank()` 체크 후 `Result.success()` 반환.
**Warning signs:** HTTP 404 `"File not found: "` 에러

### Pitfall 5: runAttemptCount 미확인으로 무한 재시도
**What goes wrong:** WorkManager BackoffPolicy는 재시도 횟수를 제한하지 않는다. `Result.retry()`만 반환하면 무기한 재시도된다.
**Why it happens:** `setBackoffCriteria()`는 대기 시간만 제어하며 횟수는 제어하지 않는다.
**How to avoid:** `doWork()` 진입 시 `if (runAttemptCount >= MAX_ATTEMPTS) return Result.failure()` 추가.
**Warning signs:** WorkManager DB에 ENQUEUED 상태로 오래된 작업이 계속 쌓임

### Pitfall 6: `drive.file` 스코프로 Drive 탐색 불가
**What goes wrong:** Drive API `files.list()`를 `drive.file` 스코프로 호출하면 앱이 생성한 파일만 반환된다. 기존 폴더를 선택하는 탐색 UI를 구현할 수 없다.
**Why it happens:** `drive.file`은 Non-sensitive 스코프로, 앱이 생성하거나 사용자가 선택한 파일만 접근 가능하다.
**How to avoid:** Phase 46에서는 폴더 ID를 텍스트 입력으로 처리한다. Drive 폴더 선택 UI가 필요하면 `drive.readonly` 스코프 추가 + Google Play Store 심사 필요 (별도 Phase).
**Warning signs:** `files.list()` 응답이 비어 있음, 기존 폴더 목록 조회 불가

---

## Code Examples

### OkHttp multipart/related 요청 전체 예제

```kotlin
// Source: https://developers.google.com/drive/api/guides/manage-uploads#multipart
// DriveUploadRepository.kt

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

suspend fun uploadFile(
    accessToken: String,
    fileName: String,
    mimeType: String,
    fileContent: ByteArray,
    parentFolderId: String
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val boundary = "auto_minuting_${System.currentTimeMillis()}"
        val CRLF = "\r\n"

        // Part 1: 메타데이터 JSON
        val metadataJson = buildString {
            append("""{"name":"$fileName"""")
            if (parentFolderId.isNotBlank()) {
                append(""","parents":["$parentFolderId"]""")
            }
            append("}")
        }

        // multipart/related 바디 바이트 조립
        val bodyBytes = buildList<ByteArray> {
            add("--$boundary$CRLF".toByteArray())
            add("Content-Type: application/json; charset=UTF-8$CRLF$CRLF".toByteArray())
            add(metadataJson.toByteArray(Charsets.UTF_8))
            add("$CRLF--$boundary$CRLF".toByteArray())
            add("Content-Type: $mimeType$CRLF$CRLF".toByteArray())
            add(fileContent)
            add("$CRLF--$boundary--$CRLF".toByteArray())
        }.fold(ByteArray(0)) { acc, b -> acc + b }

        val requestBody = bodyBytes.toRequestBody(
            "multipart/related; boundary=$boundary".toMediaType()
        )

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = response.body?.string() ?: ""
            // {"kind":"drive#file","id":"FILE_ID","name":"...","mimeType":"..."}
            val fileId = JSONObject(json).optString("id", "")
            Result.success(fileId)
        } else {
            when (response.code) {
                401 -> Result.failure(UnauthorizedException("Drive access token 만료"))
                404 -> Result.failure(IOException("Drive 폴더 ID가 유효하지 않음: $parentFolderId"))
                else -> Result.failure(IOException("Drive 업로드 실패: HTTP ${response.code}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Worker에서 @Singleton GoogleAuthRepository 주입

```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
// 기존 프로젝트의 @HiltWorker 패턴과 동일 (TranscriptionTriggerWorker 참조)

@HiltWorker
class DriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleAuthRepository: GoogleAuthRepository,       // @Singleton 직접 주입 가능
    private val driveUploadRepository: DriveUploadRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) { ... }
```

### DataStore 폴더 ID 즉시 조회 패턴 (Worker 내 사용)

```kotlin
// Source: 기존 UserPreferencesRepository 패턴 (getAutomationModeOnce() 동일 패턴)
suspend fun getDriveTranscriptFolderIdOnce(): String =
    dataStore.data.first()[DRIVE_TRANSCRIPT_FOLDER_KEY] ?: ""
```

### WorkManager 독립 enqueue (파이프라인 오염 방지)

```kotlin
// TranscriptionTriggerWorker.doWork() 성공 블록 내 — 기존 MinutesGenerationWorker enqueue 이후
// Result.success() 반환 직전에 추가

// DRIVE-02: 전사 파일 Drive 업로드 (드라이브 인증 상태 무관하게 enqueue — Worker 내에서 확인)
val driveTranscriptUpload = OneTimeWorkRequestBuilder<DriveUploadWorker>()
    .setInputData(workDataOf(
        DriveUploadWorker.KEY_FILE_PATH to transcriptPath,
        DriveUploadWorker.KEY_FILE_TYPE to DriveUploadWorker.TYPE_TRANSCRIPT,
        DriveUploadWorker.KEY_MEETING_ID to meetingId
    ))
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .build()
WorkManager.getInstance(applicationContext).enqueue(driveTranscriptUpload)
// ↑ 체인에 포함하지 않음: 실패해도 파이프라인 상태에 영향 없음
```

---

## Drive 업로드 방식 결정 Matrix

| 기준 | Multipart | Resumable |
|------|-----------|-----------|
| 파일 크기 | 5MB 이하 권장 | 5MB 초과 권장 |
| 전사 파일 (`.txt`) | 수 KB ~ 수십 KB ← **여기 해당** | 해당 없음 |
| 회의록 파일 (`.md`) | 수 KB ~ 수십 KB ← **여기 해당** | 해당 없음 |
| 구현 복잡도 | 단일 HTTP 요청 | 2단계 요청 + 세션 URI 관리 |
| 재시도 시 | 전체 재전송 | 중단 지점부터 재개 |
| **결론** | **사용** | 불필요 |

**결론:** 전사/회의록 파일은 5MB를 초과할 일이 없으므로 **multipart upload만 구현**한다.

---

## 라이브러리 비교: google-api-services-drive vs OkHttp 직접 호출

| 항목 | google-api-services-drive | OkHttp 직접 REST |
|------|--------------------------|-----------------|
| 신규 의존성 | `google-api-client-android` + `google-api-services-drive` + `google-http-client-android` (~5MB+ JAR) | 없음 (기존 OkHttp 4.12.0 사용) |
| API 스타일 | Drive.Builder().build().files().create(metadata, content).execute() | Request.Builder().url(...).post(body).build() |
| 인증 연동 | GoogleAccountCredential (deprecated 흐름과 연관) | Authorization: Bearer {token} 헤더 직접 |
| multipart 처리 | 라이브러리가 자동 처리 | 직접 바이트 배열 조립 (50줄 내외) |
| 유지보수 | google-api-services-drive 버전 관리 추가 필요 | 없음 |
| 이 프로젝트 적합성 | Phase 45 리서치에서 추가 금지 결정 | 기존 코드베이스 일관성 유지 |
| **결론** | **금지** | **사용** |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Drive Android API + GoogleAccountCredential | Drive REST + Authorization Bearer header | Feb 2023 (Drive Android API shutdown) | google-api-services-drive 직접 사용 지양 |
| drive.files().create() Java SDK 호출 | OkHttp multipart POST 직접 구성 | 2023+ | 의존성 없이 REST 호출 |
| GoogleSignInClient.requestScopes(Drive.SCOPE_FILE) | Identity.getAuthorizationClient (Phase 45 구현 완료) | 2022-2023 deprecated | 이미 처리됨 |

**Deprecated/outdated:**
- `GoogleDriveApi` (play-services-drive): 2023년 2월 완전 종료
- `GoogleAccountCredential` + `Drive.Builder()`: deprecated, REST 직접 호출로 대체
- `uploadType=resumable` for small files: 오버엔지니어링, multipart로 충분

---

## Open Questions

1. **DriveUploadWorker를 언제 enqueue하는가 — Drive 인증 상태 선확인 vs Worker 내 확인**
   - What we know: Worker enqueue 전에 `googleAuthRepository.driveAuthState.value`를 체크하면 불필요한 Worker 생성을 막을 수 있다. 그러나 Worker 내에서 확인해도 동작한다.
   - What's unclear: 인증 미완료 상태에서 불필요한 WorkManager DB 항목이 쌓이는 것이 문제가 되는지.
   - Recommendation: Worker 내에서 확인 후 `Result.success()` early return으로 처리. 코드 간결성 우선.

2. **Drive access token 만료 후 자동 갱신 — Phase 46 범위 여부**
   - What we know: access token 유효기간 ~1시간. `GoogleAuthRepository.authorizeDrive(activity)` 재호출로 갱신 가능하나 Activity 필요.
   - What's unclear: Phase 46 범위에서 토큰 만료 시 자동 갱신 여부 (UI 알림 vs 자동 처리).
   - Recommendation: Phase 46에서는 만료 시 Drive 업로드를 `Result.failure()` + 알림으로 처리한다. 자동 갱신은 UI에서 사용자에게 재인증 유도 (Drive 연동 버튼 재탭). 토큰 자동 갱신은 별도 Phase 고려.

3. **`text/markdown` MIME 타입 공식 지원 여부**
   - What we know: Drive는 `text/plain`으로 업로드된 파일을 원본 형식으로 저장한다.
   - What's unclear: `text/markdown`이 Drive에서 공식 지원되는지, 아니면 `text/plain`으로 처리되는지.
   - Recommendation: 회의록(`.md`) 파일은 `"text/plain"` MIME 타입으로 업로드한다. 파일명에 `.md` 확장자를 포함하면 Drive가 올바른 파일 형식으로 인식한다 (공식 문서 명시).

---

## Environment Availability

Step 2.6: SKIPPED — Phase 46은 순수 코드/설정 변경으로 외부 서비스 의존성 없음.
Google Drive REST API는 HTTPS 네트워크 접속만 필요하며, 테스트 환경에서 access token만 유효하면 됨.

---

## Validation Architecture

> `workflow.nyquist_validation` 설정 미확인 — 기본값 활성으로 처리.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + MockK (기존 프로젝트 설정) |
| Config file | 없음 — Wave 0에서 확인 필요 |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.DriveUploadRepositoryTest"` |
| Full suite command | `./gradlew :app:testDebugUnitTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DRIVE-02 | 전사 파일 업로드 — Drive API 성공 케이스 | unit (MockK로 OkHttp mock) | `./gradlew :app:testDebugUnitTest --tests "*.DriveUploadRepositoryTest"` | 없음 — Wave 0 생성 |
| DRIVE-02 | 전사 Worker — 폴더 ID 빈 문자열 시 건너뜀 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DriveUploadWorkerTest"` | 없음 — Wave 0 생성 |
| DRIVE-03 | 회의록 파일 업로드 — Drive API 성공 케이스 | unit | 위와 동일 테스트 클래스 | 없음 — Wave 0 생성 |
| DRIVE-03 | 401 응답 시 Result.failure() 즉시 반환 | unit | 위와 동일 | 없음 — Wave 0 생성 |
| DRIVE-04 | 폴더 ID DataStore 저장/조회 | unit | `./gradlew :app:testDebugUnitTest --tests "*.UserPreferencesRepositoryTest"` | 없음 — Wave 0 생성 |

### Wave 0 Gaps
- [ ] `app/src/test/.../DriveUploadRepositoryTest.kt` — multipart body 구성, 성공/401/404 응답 케이스
- [ ] `app/src/test/.../DriveUploadWorkerTest.kt` — 폴더 ID 빈 문자열/토큰 없음/성공/retry 케이스
- [ ] `app/src/test/.../UserPreferencesRepositoryTest.kt` — Drive 폴더 ID 키 저장/조회 케이스 (기존 파일에 추가 가능)

---

## Sources

### Primary (HIGH confidence)
- https://developers.google.com/drive/api/guides/manage-uploads — multipart/simple/resumable 업로드 공식 가이드, boundary 형식, parents 필드
- https://developers.google.com/drive/api/reference/rest/v3/files/create — files.create 엔드포인트, uploadType 파라미터
- https://developers.google.com/drive/api/guides/folder — parents 배열로 특정 폴더에 업로드하는 방법
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work — WorkManager backoff 정책, runAttemptCount, MIN_BACKOFF_MILLIS=10초
- https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager — @HiltWorker + @Singleton 주입 공식 문서
- https://developers.google.com/workspace/drive/api/guides/mime-types — MIME 타입 목록, text/plain 원본 저장 동작

### Secondary (MEDIUM confidence)
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work — 체인 실패 전파 메커니즘, 독립 enqueue 방식
- https://developer.android.com/reference/androidx/work/BackoffPolicy — BackoffPolicy 공식 레퍼런스

### Tertiary (LOW confidence)
- WebSearch: "Drive API v3 OkHttp Android Kotlin 2024" — 커뮤니티 패턴 확인 (공식 문서로 교차 검증)
- WebSearch: "WorkManager retry backoff exponential Android 2024" — runAttemptCount 패턴 확인

---

## Metadata

**Confidence breakdown:**
- Drive REST API multipart 업로드: HIGH — 공식 문서에서 HTTP 예제 직접 확인
- OkHttp 구현 패턴: HIGH — 기존 프로젝트 OkHttp 사용 코드 직접 분석, multipart body 구성은 표준 HTTP
- WorkManager 독립 enqueue 패턴: HIGH — 공식 체이닝 문서에서 실패 전파 메커니즘 확인
- DataStore 폴더 ID 저장: HIGH — 기존 `UserPreferencesRepository` 패턴 직접 분석, 동일 방식 확장
- 폴더 ID 입력 UI (텍스트필드 방식): HIGH — `drive.file` 스코프 제한으로 Drive 탐색 불가 확인됨
- token 만료 처리 전략: MEDIUM — Worker에서 Activity 없이 갱신 불가는 확인됨, UI 재인증 유도 패턴은 합리적 추론

**Research date:** 2026-04-03
**Valid until:** 2026-07-03 (90일 — Drive REST API v3는 안정적, WorkManager API 변경 없음)
