# Phase 11: 삼성 자동 감지 스파이크 - Research

**Researched:** 2026-03-26
**Domain:** Android 파일 시스템 모니터링 (ContentObserver / FileObserver / Scoped Storage)
**Confidence:** MEDIUM (실기기 검증 필수 — 삼성 녹음앱 내부 동작은 문서화되지 않음)

## Summary

이 Phase는 삼성 녹음앱(com.sec.android.app.voicenote)이 전사를 완료했을 때 이를 자동으로 감지할 수 있는지를 실기기에서 검증하는 48시간 타임박스 스파이크이다. 핵심 질문은 "삼성 녹음앱이 전사 결과를 파일 시스템에 저장할 때, 우리 앱이 이를 감지할 수 있는가?"이다.

조사 결과, 삼성 녹음앱의 전사 텍스트는 앱 내부 데이터베이스에 저장되며 별도의 텍스트 파일로 공개 저장소에 자동 저장되지 않는다. 사용자가 명시적으로 "공유 > 텍스트 파일"을 선택해야만 외부로 전사 텍스트가 나간다. 이는 자동 감지가 **No-Go일 가능성이 높음**을 시사하지만, 오디오 파일 자체(m4a)가 `Recordings/Voice Recorder/` 경로에 MediaStore 등록될 가능성이 있어, "새 녹음 감지 → 사용자에게 전사 공유 프롬프트"라는 대안 경로도 검증해야 한다.

**Primary recommendation:** ContentObserver로 MediaStore.Audio 변경 감지를 먼저 시도하고, 삼성 녹음앱이 전사 텍스트를 별도 파일로 저장하는지 실기기에서 확인한다. Go/No-Go 판정은 "전사 텍스트 자동 감지"와 "오디오 파일 감지 후 수동 전사 트리거"를 구분하여 내린다.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SREC-02 | 삼성 녹음앱 전사 완료 시 자동 감지 가능성을 실기기에서 검증한다 (ContentObserver/FileObserver 스파이크, 48시간 타임박스) | ContentObserver/FileObserver 기술 스택, Scoped Storage 제약, 삼성 녹음앱 파일 저장 경로 조사 결과, Go/No-Go 판정 기준 |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- 플랫폼: Android 네이티브 (Kotlin)
- Min SDK: API 31 (Android 12)
- Target SDK: API 36
- Accessibility Service 활용은 Out of Scope (Google Play Store 정책 위반 리스크)
- 기본 응답/주석/커밋/문서: 한국어
- 변수명/함수명: 영어

## Standard Stack

이 스파이크는 기존 프로젝트 스택 + Android Platform API만 사용한다. 추가 라이브러리 불필요.

### Core (Platform APIs)

| Technology | API Level | Purpose | Why Standard |
|------------|-----------|---------|--------------|
| ContentObserver | API 1+ | MediaStore 변경 감지 | Scoped Storage 환경에서 유일한 타 앱 파일 감지 수단 |
| ContentResolver | API 1+ | MediaStore 쿼리 | ContentObserver와 함께 사용하여 변경 내용 조회 |
| MediaStore.Audio | API 1+ | 오디오 파일 감지 | 삼성 녹음앱 m4a 파일이 등록되는 테이블 |
| MediaStore.Files | API 11+ | 모든 파일 감지 | 텍스트 파일 감지 시도용 |
| FileObserver | API 1+ | 파일 시스템 변경 감지 (inotify) | 보조 검증용 — Scoped Storage 제한으로 실효성 낮음 |
| Foreground Service | API 26+ | 백그라운드 모니터링 | ContentObserver를 지속 실행하려면 필요 |

### Supporting (기존 프로젝트 스택)

| Technology | Purpose | When to Use |
|------------|---------|-------------|
| Hilt | DI | Observer/Service 주입 |
| Room | 감지 결과 기록 | 스파이크 결과 로깅 (선택) |
| WorkManager | 주기적 폴링 대안 | ContentObserver 실패 시 폴링 방식 검증 |
| Coroutines/Flow | 비동기 처리 | Observer 콜백 → Flow 변환 |

## Architecture Patterns

### 스파이크 프로젝트 구조

```
app/src/main/java/com/autominuting/
├── spike/                          # 스파이크 전용 패키지 (임시)
│   ├── SamsungRecorderObserver.kt  # ContentObserver 구현
│   ├── SamsungFileObserver.kt      # FileObserver 구현 (보조)
│   ├── SpikeLogActivity.kt         # 감지 결과 실시간 표시 UI
│   └── SpikeService.kt             # Foreground Service (Observer 유지)
```

### Pattern 1: ContentObserver for MediaStore 변경 감지

**What:** ContentResolver.registerContentObserver로 MediaStore URI 변경을 감시
**When to use:** 타 앱이 파일을 공유 저장소에 저장하고 MediaStore에 등록할 때
**Example:**

```kotlin
// ContentObserver를 MediaStore.Audio에 등록
class SamsungRecorderObserver(
    handler: Handler,
    private val contentResolver: ContentResolver
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // uri가 null일 수 있음 — 전체 테이블 변경 알림
        Log.d("SpikeObserver", "MediaStore 변경 감지: uri=$uri, selfChange=$selfChange")

        // 최근 추가된 파일 쿼리
        queryRecentFiles()
    }

    private fun queryRecentFiles() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.OWNER_PACKAGE_NAME  // API 29+
        )

        val selection = "${MediaStore.Audio.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(
            ((System.currentTimeMillis() / 1000) - 60).toString()  // 최근 1분
        )

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val path = cursor.getString(2)
                val owner = cursor.getString(4)
                Log.d("SpikeObserver", "새 오디오: name=$name, path=$path, owner=$owner")
            }
        }
    }
}

// 등록
val observer = SamsungRecorderObserver(Handler(Looper.getMainLooper()), contentResolver)
contentResolver.registerContentObserver(
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    true,  // notifyForDescendants
    observer
)
```

### Pattern 2: FileObserver (보조 검증)

**What:** inotify 기반 파일 시스템 변경 감시
**When to use:** 알려진 디렉토리 경로를 직접 감시할 때
**Limitation:** Scoped Storage에서 타 앱의 private directory 접근 불가

```kotlin
// 삼성 녹음앱 공개 저장 경로 감시 시도
val voiceRecorderPath = Environment.getExternalStorageDirectory()
    .resolve("Recordings/Voice Recorder")

val observer = object : FileObserver(voiceRecorderPath, CREATE or CLOSE_WRITE or MOVED_TO) {
    override fun onEvent(event: Int, path: String?) {
        Log.d("SpikeFileObs", "이벤트=$event, 경로=$path")
    }
}
observer.startWatching()
```

### Pattern 3: NotificationListenerService (대안 접근)

**What:** 삼성 녹음앱이 전사 완료 알림을 발행하면 감지
**When to use:** ContentObserver가 No-Go일 때 대안 검증
**Limitation:** 사용자가 설정에서 알림 접근 권한 명시적 허용 필요

```kotlin
class SpikeNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.sec.android.app.voicenote") {
            val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
            Log.d("SpikeNotif", "삼성 녹음앱 알림: $text")
        }
    }
}
```

### Anti-Patterns to Avoid

- **AccessibilityService 사용 금지:** REQUIREMENTS.md Out of Scope에 명시. Google Play 2026년 1월 정책 강화로 UI 자동화 목적 사용 시 앱 정지/계정 종료 위험
- **MANAGE_EXTERNAL_STORAGE 무조건 요청 금지:** 파일 관리자/백업/안티바이러스/문서 관리 앱만 허용됨. 회의록 앱은 해당 카테고리에 속하지 않아 Play Store 거부 확실
- **폴링 루프로 파일 존재 확인 금지:** 배터리 소모, 정확도 낮음

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MediaStore 변경 감지 | 직접 폴링 | ContentObserver | 시스템 API가 정확하고 효율적 |
| 파일 시스템 감시 | 주기적 디렉토리 스캔 | FileObserver (inotify) | OS 레벨 이벤트 드리븐 |
| 백그라운드 지속 실행 | Thread + while loop | Foreground Service + WorkManager | 시스템 최적화, 배터리 관리 |

## Common Pitfalls

### Pitfall 1: 삼성 녹음앱 전사 텍스트가 파일로 저장되지 않을 수 있음

**What goes wrong:** 전사 텍스트가 앱 내부 DB에만 저장되어 외부에서 감지 불가
**Why it happens:** 삼성 녹음앱은 전사 결과를 앱 내부에 유지하고, 사용자가 "공유"를 선택해야만 외부로 전달
**How to avoid:** 스파이크 첫 단계에서 전사 전후 MediaStore 상태를 비교하여 실제로 새 파일이 등록되는지 확인
**Warning signs:** ContentObserver에 전사 완료 시점 이벤트가 오지 않음

### Pitfall 2: ContentObserver onChange에서 URI가 null

**What goes wrong:** onChange(selfChange, uri)에서 uri가 null로 전달되어 어떤 파일이 변경됐는지 알 수 없음
**Why it happens:** 일부 ContentProvider는 batch 변경 시 개별 URI를 제공하지 않음
**How to avoid:** uri가 null일 때 전체 테이블을 쿼리하여 마지막 확인 시점 이후 추가된 파일을 조회. DATE_ADDED 기반 delta 쿼리
**Warning signs:** 로그에 uri=null이 빈번하게 기록됨

### Pitfall 3: OWNER_PACKAGE_NAME 미지원 또는 null

**What goes wrong:** MediaStore.Audio.Media.OWNER_PACKAGE_NAME이 null이거나 삼성 녹음앱 패키지명을 반환하지 않음
**Why it happens:** OWNER_PACKAGE_NAME은 API 29+에서 사용 가능하나, 모든 파일에 대해 정확히 반환되지 않을 수 있음
**How to avoid:** RELATIVE_PATH(예: "Recordings/Voice Recorder/")와 DISPLAY_NAME(파일 확장자, 명명 패턴)을 조합하여 삼성 녹음앱 파일 식별
**Warning signs:** OWNER_PACKAGE_NAME 쿼리 결과가 일관성 없음

### Pitfall 4: Foreground Service 없이 Observer 등록

**What goes wrong:** 앱이 백그라운드로 전환되면 프로세스가 종료되어 Observer도 해제됨
**Why it happens:** Android 시스템이 백그라운드 프로세스를 메모리 회수 대상으로 삼음
**How to avoid:** Foreground Service(foregroundServiceType="dataSync" 또는 "specialUse")에서 Observer 등록. 또는 WorkManager 주기적 작업으로 폴링
**Warning signs:** 앱을 백그라운드로 두고 삼성 녹음앱에서 전사하면 감지 안 됨

### Pitfall 5: Scoped Storage에서 FileObserver 제한

**What goes wrong:** FileObserver가 `/storage/emulated/0/Recordings/Voice Recorder/` 경로를 감시하지 못하거나 이벤트를 받지 못함
**Why it happens:** Android 11+에서 Scoped Storage가 강제되면 타 앱의 파일에 대한 inotify 접근이 제한될 수 있음
**How to avoid:** FileObserver는 보조 수단으로만 사용. ContentObserver를 주력으로 사용
**Warning signs:** FileObserver.startWatching() 호출 후 이벤트 미수신

## Code Examples

### 스파이크 검증 시나리오별 테스트 코드

```kotlin
// 시나리오 1: MediaStore.Audio에서 삼성 녹음앱 파일 감지
fun testMediaStoreAudioDetection(contentResolver: ContentResolver) {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.MIME_TYPE
    )

    // API 29+ 에서 OWNER_PACKAGE_NAME 추가 가능
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // OWNER_PACKAGE_NAME 포함하여 쿼리
    }

    // "Recordings/Voice Recorder/" 경로 필터링
    val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Voice Recorder%")

    contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, selectionArgs,
        "${MediaStore.Audio.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        Log.d("Spike", "Voice Recorder 오디오 파일 ${cursor.count}개 발견")
        while (cursor.moveToNext()) {
            val name = cursor.getString(1)
            val path = cursor.getString(2)
            Log.d("Spike", "  - $name (경로: $path)")
        }
    }
}

// 시나리오 2: MediaStore.Files에서 텍스트 파일 감지 시도
fun testMediaStoreFilesDetection(contentResolver: ContentResolver) {
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.RELATIVE_PATH,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED
    )

    val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND " +
        "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("text/plain", "%Voice Recorder%")

    contentResolver.query(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
        projection, selection, selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    )?.use { cursor ->
        Log.d("Spike", "Voice Recorder 텍스트 파일 ${cursor.count}개 발견")
    }
}
```

### Foreground Service에서 Observer 유지

```kotlin
class SpikeService : Service() {
    private lateinit var audioObserver: ContentObserver
    private lateinit var filesObserver: ContentObserver

    override fun onCreate() {
        super.onCreate()
        val handler = Handler(Looper.getMainLooper())

        audioObserver = SamsungRecorderObserver(handler, contentResolver)
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, audioObserver
        )

        filesObserver = SamsungRecorderObserver(handler, contentResolver)
        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, filesObserver
        )
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(audioObserver)
        contentResolver.unregisterContentObserver(filesObserver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

## 삼성 녹음앱 분석 결과

### 파일 저장 경로

| 데이터 유형 | 저장 위치 | 외부 접근 가능 | Confidence |
|-------------|-----------|--------------|------------|
| 오디오 녹음 (m4a) | `Internal Storage/Recordings/Voice Recorder/` | YES (MediaStore 등록) | HIGH |
| 전사 텍스트 | 앱 내부 DB (`/data/data/com.sec.android.app.voicenote/`) | NO (Scoped Storage 차단) | MEDIUM |
| 공유된 텍스트 파일 | 사용자 선택 경로 (공유 Intent) | YES (공유 시점에만) | HIGH |

### 패키지 정보

- 패키지명: `com.sec.android.app.voicenote`
- 녹음 파일 형식: m4a, 3GP, WAV
- 전사 내보내기: 사용자가 "공유 > 텍스트 파일" 선택 시만 가능

### 핵심 발견사항

1. **전사 텍스트는 자동으로 파일 시스템에 저장되지 않는다** — 삼성 녹음앱 내부 DB에만 존재
2. **오디오 파일(m4a)은 공유 저장소에 저장되어 MediaStore에서 감지 가능**
3. **전사 결과를 외부로 내보내려면 사용자가 반드시 "공유" 버튼을 눌러야 함** — Phase 9의 공유 수신 방식이 사실상 유일한 경로

## Go/No-Go 판정 기준

### 판정 시나리오

| 시나리오 | 판정 | 후속 조치 |
|----------|------|-----------|
| A: ContentObserver로 전사 텍스트 파일 자동 감지 성공 | **Go** | Phase에서 본구현 진행 (SREC-F01) |
| B: 오디오 파일만 감지 가능, 전사 텍스트 감지 불가 | **Partial Go** | "새 녹음 감지 → 사용자에게 전사 공유 프롬프트" 알림 제공 |
| C: ContentObserver/FileObserver 모두 실패 | **No-Go** | Phase 9의 수동 공유 방식이 기본 경로로 확정 |
| D: NotificationListenerService로 전사 완료 알림 감지 성공 | **Partial Go** | 알림 감지 → 사용자에게 공유 프롬프트 |

### 48시간 타임박스 일정

| 시간대 | 활동 |
|--------|------|
| 0-4h | 실기기에서 삼성 녹음앱 전사 후 MediaStore/파일시스템 상태 수동 조사 |
| 4-12h | ContentObserver 프로토타입 구현 + 실기기 테스트 |
| 12-18h | FileObserver 보조 검증 + NotificationListenerService 대안 검증 |
| 18-24h | 결과 분석, Go/No-Go 판정, 문서화 |
| 24-48h | (Go/Partial Go 시) 프로토타입 안정화 및 코드 정리 |

## 감지 접근법 비교

| 접근법 | 실현 가능성 | 사용자 경험 | Play Store 리스크 | 우선순위 |
|--------|------------|------------|------------------|---------|
| ContentObserver (MediaStore.Audio) | HIGH | 오디오만 감지 → 프롬프트 | 없음 | 1 |
| ContentObserver (MediaStore.Files) | LOW | 전사 텍스트 감지 가능 시 최고 | 없음 | 2 |
| FileObserver | LOW | Scoped Storage 제한 | 없음 | 3 |
| NotificationListenerService | MEDIUM | 사용자 권한 허용 필요 | 낮음 (권한 고지 필요) | 4 |
| AccessibilityService | - | - | **거부 확실** | 제외 |
| MANAGE_EXTERNAL_STORAGE | - | - | **거부 확실** | 제외 |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| READ_EXTERNAL_STORAGE로 타앱 파일 직접 접근 | Scoped Storage + MediaStore API | Android 10 (2019) → Android 11 강제 (2020) | 타앱 파일 직접 경로 접근 불가 |
| FileObserver로 자유로운 디렉토리 감시 | ContentObserver + MediaStore 조합 | Android 11 (2020) | FileObserver의 타앱 디렉토리 감시 제한 |
| KAPT 사용 | KSP 사용 | 2023+ | 빌드 속도 향상 (이 스파이크에는 무관) |

## Open Questions

1. **삼성 녹음앱이 전사 완료 시 알림을 발행하는가?**
   - What we know: 삼성 녹음앱이 녹음 중 알림을 표시하는 것은 확인됨
   - What's unclear: 전사 완료 시점에 별도 알림이 발행되는지 미확인
   - Recommendation: 실기기에서 NotificationListenerService로 확인

2. **MediaStore.Audio에 OWNER_PACKAGE_NAME이 정확히 반환되는가?**
   - What we know: API 29+에서 사용 가능한 컬럼
   - What's unclear: 삼성 녹음앱 파일에 대해 "com.sec.android.app.voicenote"가 반환되는지
   - Recommendation: 실기기에서 쿼리하여 확인. RELATIVE_PATH 기반 식별을 백업으로 준비

3. **삼성 녹음앱이 전사 텍스트를 내부 DB가 아닌 별도 파일로 저장하는 버전/설정이 있는가?**
   - What we know: 공식 문서에서는 앱 내부에 유지된다고 함
   - What's unclear: One UI 버전이나 설정에 따라 다를 수 있음
   - Recommendation: 실기기에서 전사 전후 파일시스템 diff로 확인

4. **Foreground Service foregroundServiceType으로 무엇을 사용해야 하는가?**
   - What we know: Android 14+에서 foregroundServiceType 필수
   - What's unclear: 파일 모니터링용으로 "dataSync"가 적절한지, "specialUse"가 필요한지
   - Recommendation: 스파이크 단계에서는 "specialUse"로 시작, 본구현 시 정책 확인

## Sources

### Primary (HIGH confidence)
- Android ContentObserver 공식 문서: https://developer.android.com/reference/kotlin/android/database/ContentObserver
- Android FileObserver 공식 문서: https://developer.android.com/reference/kotlin/android/os/FileObserver
- Android MediaStore 공식 문서: https://developer.android.com/reference/android/provider/MediaStore.Files
- Android Scoped Storage 공식 문서: https://developer.android.com/about/versions/11/privacy/storage
- Samsung Voice Recorder 공식 지원: https://www.samsung.com/us/support/answer/ANS10000942/

### Secondary (MEDIUM confidence)
- MANAGE_EXTERNAL_STORAGE Play Store 정책: https://support.google.com/googleplay/android-developer/answer/10467955
- AccessibilityService Play Store 정책: https://support.google.com/googleplay/android-developer/answer/10964491
- NotificationListenerService 공식 문서: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Samsung Voice Recorder 전사 내보내기 가이드: https://gotranscript.com/en/blog/transcribe-audio-samsung-voice-recorder-export-transcript

### Tertiary (LOW confidence)
- 삼성 녹음앱 전사 텍스트 내부 DB 저장 여부: 공식 문서 미확인, 간접 증거(공유 필수)로 추론. 실기기 검증 필요

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Android Platform API는 안정적이고 잘 문서화됨
- Architecture: MEDIUM — ContentObserver 패턴은 표준이나 삼성 녹음앱의 MediaStore 등록 여부는 실기기 확인 필요
- Pitfalls: HIGH — Scoped Storage 제약은 잘 알려진 제한사항
- Go/No-Go 예측: MEDIUM — 전사 텍스트 자동 감지는 No-Go 가능성 높으나, 오디오 파일 감지(Partial Go)는 가능성 있음

**Research date:** 2026-03-26
**Valid until:** 2026-04-25 (Android Platform API는 안정적이므로 30일)
