# 도메인 함정 (Domain Pitfalls) — v2.0 기능 확장

**도메인:** Android 회의록 자동화 앱 v2.0 기능 추가
**조사일:** 2026-03-25
**대상 기능:** 삼성 녹음기 전사 연동, NotebookLM 통합, Gemini OAuth/API 키 UI, 파일 삭제, Plaud BLE 디버깅
**전체 신뢰도:** MEDIUM-HIGH (코드베이스 직접 분석 기반, 웹 검색 제한으로 일부 항목은 훈련 데이터 기반)

---

## 치명적 함정 (Critical Pitfalls)

아키텍처 변경이나 기능 전체 폐기를 초래하는 실수들.

---

### CP-1: 삼성 녹음기 전사 완료 감지 — 존재하지 않는 API에 의존

**무엇이 잘못되는가:** 삼성 녹음기(Samsung Voice Recorder)에는 전사 완료를 알려주는 공개 API, Intent, Broadcast가 없다. ContentObserver로 MediaStore 변경을 감시하는 접근법을 선택하면, "전사 완료된 파일"을 구분할 수 없어 오탐(false positive)이 대량 발생한다.

**왜 발생하는가:** 삼성 녹음기는 전사 결과를 자체 앱 내부 스토리지(`/data/data/com.sec.android.app.voicenote/`)에 저장한다. Android Scoped Storage 정책으로 서드파티 앱은 이 경로에 접근 불가. ContentObserver는 MediaStore 레벨 변경만 감지하며, 전사 텍스트 파일은 MediaStore에 등록되지 않을 가능성이 높다.

**결과:**
- ContentObserver 기반 자동 감지 구현에 수주간 투자 후 "불가능"이라는 결론
- Accessibility Service 접근은 Play Store 정책 위반 리스크 (접근성 서비스 남용 금지 정책, Google이 2024년부터 강화 적용)
- 자동 감지가 불가능하면 v2.0 핵심 기능 하나가 통째로 사라짐

**예방:**
1. **Phase 1 첫 48시간 내 기술 검증(Spike) 최우선 수행.** 실기기에서 삼성 녹음기 전사 후 다음 명령으로 확인:
   - `adb shell content query --uri content://media/external/file` — MediaStore 변경 여부
   - `adb shell ls -la /sdcard/` — 외부 저장소에 전사 파일 생성 여부
   - ContentObserver 테스트 앱으로 onChange 이벤트 로그 수집
2. **폴백을 기본 경로로 확정.** "공유(Share Intent)로 전사 텍스트 수신" 방식을 1순위 구현 경로로 설정. 자동 감지는 보너스
3. Share Intent 수신 Activity를 `text/plain` MIME 타입으로 등록하면 삼성 녹음기의 "공유" 버튼에서 즉시 동작 가능

**감지 신호:** `adb shell`로 삼성 녹음기 전사 파일 경로를 확인했을 때, 앱 내부 저장소(`/data/data/`)에만 존재하면 자동 감지는 사실상 불가능

**신뢰도:** MEDIUM — 삼성 녹음기 내부 구조는 비공개이므로 실기기 검증 필수

---

### CP-2: NotebookLM에 공식 REST API가 없다는 사실을 무시한 통합 설계

**무엇이 잘못되는가:** NotebookLM에는 공식 REST API가 없다(PROJECT.md에도 기록). 비공식 MCP 서버(`notebooklm-mcp`)가 존재하지만 이것은 브라우저 세션 쿠키 기반이며, Android 앱에서 직접 호출하기에 부적합하다. PC에서 실행되는 MCP 서버에 의존하면, "모바일 독립 실행"이라는 앱 핵심 가치를 잃는다.

**왜 발생하는가:** NotebookLM MCP 서버가 존재하니 "API가 있다"고 착각. 실제로 MCP 서버는 웹 UI를 자동화하는 방식이거나 인증된 브라우저 세션에 의존. Android 앱에서 이 경로를 사용하면 인증 유지, 세션 만료, 헤드리스 브라우저 필요 등 문제가 쏟아진다.

**결과:**
- NotebookLM "직접 연동" 구현 착수 후 인증 벽에 부딪혀 기능 전체 폐기
- 사용자가 PC를 켜놓아야 하는 종속성 발생
- Google이 비공식 접근을 차단하면 기능 즉시 사망

**예방:**
1. **NotebookLM 연동은 "공유" 방식으로 한정.** Android Share Intent 또는 Custom Tabs로 NotebookLM 앱/웹에 전사 텍스트를 전달하는 것이 현실적 상한
2. **Gemini API 직접 호출(현재 `GeminiEngine`)이 핵심 경로.** NotebookLM은 선택적 보조 기능으로 위치 확정
3. 구현 범위: "앱에서 NotebookLM 웹 열기 + 전사 텍스트 클립보드 복사" 또는 "공유 Intent로 텍스트 전달" 수준의 반자동화
4. **Custom Tabs 사용 시 주의:** Chrome에서 로그인된 Google 계정과 앱 OAuth 계정이 다를 수 있으므로 UX에서 안내 필요

**감지 신호:** "NotebookLM API endpoint"로 검색 시 공식 문서가 나오지 않으면 직접 통합 포기

**신뢰도:** HIGH — PROJECT.md에 "NotebookLM API: No official REST API" 기록 확인

---

### CP-3: Gemini API 키를 BuildConfig에 하드코딩한 채 OAuth 전환 시 기존 파이프라인 파괴

**무엇이 잘못되는가:** 현재 `GeminiEngine`(코드 확인 완료)은 `BuildConfig.GEMINI_API_KEY`를 직접 읽어 `GenerativeModel`을 생성한다. OAuth 인증을 추가할 때 이 코드를 직접 수정하면, API 키 모드와 OAuth 모드를 동시에 지원할 수 없다. 또한 **Google AI Client SDK(`com.google.ai.client.generativeai`)는 API 키 인증만 지원**하며 OAuth Bearer 토큰을 처리하는 코드 경로가 없을 가능성이 높다.

**왜 발생하는가:** `GeminiEngine`이 인증 방식을 하드코딩(`BuildConfig.GEMINI_API_KEY`)하고 있어서, 인증 제공자를 추상화하지 않으면 두 방식 공존이 불가능. OAuth 토큰은 만료되는데, 백그라운드 `WorkManager` 작업 중 토큰 갱신 실패를 처리하는 로직이 없다.

**결과:**
- `GenerativeModel(apiKey = oauthToken)` 시도 시 401 Unauthorized — SDK가 API 키와 OAuth를 다르게 처리
- API 키 사용자와 OAuth 사용자를 동시에 지원 불가
- OAuth 토큰 만료 시 `MinutesGenerationWorker` 전체 중단, WorkManager 무한 재시도

**예방:**
1. **OAuth 경로는 처음부터 Retrofit REST API 직접 호출로 설계.** SDK는 API 키 경로에만 사용:
   ```kotlin
   interface GeminiAuthProvider {
       suspend fun getApiKey(): String?        // API 키 경로 → SDK 사용
       suspend fun getAuthToken(): String?     // OAuth 경로 → REST API 사용
       fun authMode(): AuthMode               // API_KEY | OAUTH
   }
   ```
2. **GeminiEngine을 인증 제공자에 의존하도록 리팩터링** 후에 OAuth 구현 착수
3. **순서: API 키 설정 UI 먼저** (DataStore에 저장) → `BuildConfig` 의존 제거 → 그 후 OAuth 추가
4. **초기 프로토타입에서 SDK에 OAuth 토큰 전달 테스트** — 실패 시 REST API 경로 확정

**감지 신호:** `GenerativeModel` 생성자에 OAuth 토큰을 전달하는 방법이 없으면 REST API 래퍼 필요

**신뢰도:** HIGH (인증 추상화 필요성) / MEDIUM (SDK OAuth 미지원 — 실제 테스트 필요)

---

### CP-4: Room DB 삭제와 파일시스템 삭제의 불일치 (Orphaned Files / Dangling References)

**무엇이 잘못되는가:** 현재 `MeetingDao.delete(id)`는 DB 레코드만 삭제한다(코드 확인 완료). `audioFilePath`, `transcriptPath`, `minutesPath`에 연결된 실제 파일은 파일시스템에 그대로 남는다. 반대로 파일만 삭제하고 DB를 업데이트하지 않으면, UI에서 "파일 없음" 크래시가 발생한다.

**왜 발생하는가:** `MeetingRepositoryImpl.deleteMeeting()`이 `meetingDao.delete(id)`만 호출하고 파일 삭제 로직이 전혀 없다(코드 확인 완료). Room 트랜잭션과 파일시스템 작업은 원자적(atomic)이 아니므로, 한쪽만 성공할 수 있다.

**결과:**
- 삭제 기능을 사용할수록 수백 MB의 오디오 파일이 저장소에 계속 누적
- DB 삭제 성공 → 파일 삭제 실패 → 저장소 누수
- 파일 먼저 삭제 → DB 삭제 실패 → UI에서 존재하지 않는 파일 접근 → 크래시

**예방:**
1. **삭제 순서: DB 먼저, 파일 나중.** DB 삭제가 실패하면 롤백 가능하므로 더 안전:
   ```kotlin
   suspend fun deleteMeeting(id: Long) {
       val meeting = meetingDao.getMeetingByIdOnce(id) ?: return
       meetingDao.delete(id)  // 1단계: DB 삭제
       // 2단계: 파일 삭제 (실패해도 DB는 정리됨 — 고아 파일은 주기적 정리)
       listOfNotNull(meeting.audioFilePath, meeting.transcriptPath, meeting.minutesPath)
           .forEach { path -> File(path).delete() }
   }
   ```
2. **주기적 고아 파일 정리** WorkManager 작업 추가 (DB에 없는 파일 스캔 후 삭제)
3. **파일 읽기 시 null-safe 처리:** 파일이 없으면 "파일이 삭제됨" UI 표시 (크래시 방지)
4. `MeetingDao`에 `getMeetingByIdOnce(): MeetingEntity?` (suspend, non-Flow) 함수 추가 필요

**감지 신호:** 앱 저장소 크기가 사용 기간에 비례하여 계속 증가하면 고아 파일 누수

**신뢰도:** HIGH — `MeetingRepositoryImpl.kt`, `MeetingDao.kt` 코드 직접 확인. 파일 삭제 로직 완전 부재

---

## 중간 수준 함정 (Moderate Pitfalls)

기능 지연이나 사용자 경험 저하를 유발하는 실수들.

---

### MP-1: Share Intent 수신을 기존 MainActivity에 합치면 딥링크 충돌

**무엇이 잘못되는가:** 삼성 녹음기에서 "공유"로 전사 텍스트를 보내면 `ACTION_SEND` Intent가 발생한다. 이것을 기존 `MainActivity`의 `intent-filter`에 추가하면, 앱이 이미 실행 중일 때 Activity 재생성이나 `onNewIntent` 처리 누락으로 데이터가 유실된다.

**왜 발생하는가:** 현재 `MainActivity`는 `MAIN/LAUNCHER` intent-filter만 가지고 있다(AndroidManifest.xml 확인). 여기에 `ACTION_SEND`를 추가하면, 런치 모드 설정에 따라 새 인스턴스가 생성되거나 기존 인스턴스의 `onNewIntent`가 호출되는데, Compose Navigation 상태가 꼬일 수 있다.

**예방:**
1. **전용 `ShareReceiverActivity` 생성** (투명 Activity 패턴 — theme: `@android:style/Theme.Translucent.NoTitleBar`)
2. 수신한 텍스트를 즉시 Repository/WorkManager에 전달 후 Activity 즉시 종료 (`finish()`)
3. AndroidManifest에 MIME 타입 등록 — `text/plain` 필수, `text/*`도 추가 권장
4. **실기기에서 삼성 녹음기가 실제로 보내는 MIME 타입과 Extra 키를 확인** (`EXTRA_TEXT` vs `EXTRA_STREAM`)
5. **수신한 텍스트가 전사 텍스트인지 확인 불가** — `ACTION_SEND`는 모든 앱에서 전송 가능하므로 "이 텍스트로 회의록을 생성하시겠습니까?" 확인 화면 필수

**신뢰도:** HIGH — Android Share Intent 표준 패턴, 현재 AndroidManifest.xml 분석 기반

---

### MP-2: Gemini OAuth에서 Credential Manager vs 구형 GoogleSignIn 혼용

**무엇이 잘못되는가:** Android에서 Google OAuth 구현 시 Credential Manager(2024년 이후 권장)와 `GoogleSignInClient`(deprecated)가 혼재한다. 잘못된 라이브러리를 선택하면 Android 14+ 기기에서 로그인 실패가 발생하거나, deprecated 라이브러리 제거 시 재작성이 필요하다.

**왜 발생하는가:** Google의 인증 라이브러리 전환이 진행 중이며, 검색 결과의 대부분이 구형 `GoogleSignIn` 방식을 설명. 또한 Credential Manager는 Play Services 버전에 의존하여 구형 기기에서 실패할 수 있다.

**예방:**
1. **Credential Manager + Google Identity Services** 사용 (2025년 이후 권장 방식)
2. `com.google.android.libraries.identity.googleid` 의존성 사용
3. Gemini API OAuth 스코프: `https://www.googleapis.com/auth/generative-language`
4. **Play Services 버전 부족 시** "Google Play Services 업데이트 필요" 안내 메시지 표시
5. OAuth 토큰 갱신 실패 시 API 키로 폴백하는 전략 (양쪽 다 설정된 경우)

**신뢰도:** MEDIUM — Credential Manager는 확인된 권장 방식이나, Gemini API 스코프는 실제 테스트 필요

---

### MP-3: Plaud SDK BLE 디버깅 시 에뮬레이터에서 시간 낭비

**무엇이 잘못되는가:** Android 에뮬레이터는 BLE를 지원하지 않는다. 현재 `NiceBuildSdkWrapper`가 스텁으로 항상 `onError(-1)`을 반환하는 구조이므로(코드 확인 완료), 실제 Plaud 녹음기 + 실 Android 기기가 없으면 BLE 디버깅이 물리적으로 불가능하다.

**예방:**
1. **BLE 디버깅 Phase를 마지막에 배치** (PROJECT.md의 v2.0 계획과 일치)
2. **nRF Connect 앱으로 Plaud BLE 서비스/특성(UUID)을 먼저 스캔** 후 SDK 호출 전 연결 가능성 확인
3. **SDK 콜백 로깅을 최대한 상세하게:** `PlaudBleListener`의 모든 콜백에 `Log.d` 추가
4. **Android 12+(API 31) 런타임 퍼미션:** `BLUETOOTH_CONNECT` 미승인 시 무음 실패 — Manifest에 선언은 되어 있으나(확인 완료) 런타임 요청 로직 확인 필요
5. **BLE GATT 133 에러:** Android BLE의 고질적 문제. 재연결 시 최소 2초 딜레이 필요, `BluetoothGatt.close()` 반드시 호출 후 재생성

**감지 신호:** `scanBle`이 에러 코드를 반환하는데 원인 불명 → 대부분 퍼미션 미승인 또는 Bluetooth 어댑터 비활성화

**신뢰도:** HIGH — Android BLE 공통 경험 + `PlaudSdkManager.kt` 코드 직접 확인

---

### MP-4: API 키를 DataStore에 평문 저장 + EncryptedSharedPreferences 초기화 실패

**무엇이 잘못되는가:** DataStore Preferences는 암호화되지 않은 파일이다. Gemini API 키를 그대로 저장하면 루팅된 기기나 adb backup으로 키가 노출된다. 그러나 EncryptedSharedPreferences로 전환하면 일부 기기/OS 버전에서 Android Keystore 접근 실패로 앱이 크래시한다.

**예방:**
1. **EncryptedSharedPreferences 사용** (`androidx.security:security-crypto`)을 기본으로 시도
2. **try-catch로 초기화 실패 시 일반 DataStore로 폴백** — 보안은 떨어지지만 기능은 동작:
   ```kotlin
   val securePrefs = try {
       EncryptedSharedPreferences.create(...)
   } catch (e: Exception) {
       Log.w(TAG, "암호화 저장소 초기화 실패, 일반 저장소 사용")
       context.getSharedPreferences("gemini_prefs", MODE_PRIVATE)
   }
   ```
3. API 키 입력 UI에서 `visualTransformation = PasswordVisualTransformation()` (Compose)으로 마스킹
4. **Crashlytics/로그에서 `KeyStoreException` 모니터링** — 문제 기기 파악

**신뢰도:** HIGH — Android 보안 표준 가이드 + EncryptedSharedPreferences 불안정성은 공지된 문제

---

### MP-5: Room 스키마 마이그레이션 누락 — v2.0 기능 추가 시 기존 사용자 앱 크래시

**무엇이 잘못되는가:** v2.0에서 소스 타입(`sourceType`), 소프트 삭제(`isDeleted`) 등 컬럼 추가 시, Room DB 버전을 올리지 않으면 `IllegalStateException: Room cannot verify the data integrity`로 앱이 즉시 크래시한다.

**왜 발생하는가:** 현재 `AppDatabase`가 `version = 1`, `exportSchema = false`로 설정되어 있다(코드 확인 완료). 스키마 JSON이 생성되지 않아 마이그레이션 SQL을 자동으로 검증할 수 없는 상태.

**예방:**
1. **`exportSchema = true`로 변경** 후 스키마 JSON 파일을 버전 관리에 포함
2. 컬럼 추가 시 반드시 `Migration(1, 2)` 작성:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE meetings ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'PLAUD_BLE'")
       }
   }
   ```
3. **`fallbackToDestructiveMigration()` 사용 절대 금지** — 기존 사용자의 회의록 전부 삭제됨
4. 테스트에서 `MigrationTestHelper`로 마이그레이션 검증

**감지 신호:** `AppDatabase`의 `version`이 1인 채로 `MeetingEntity`에 컬럼을 추가하면 기존 사용자의 앱 즉시 크래시

**신뢰도:** HIGH — Room 표준 동작, `AppDatabase.kt` 코드 직접 확인

---

### MP-6: ContentObserver 배터리 소모 + 메모리 누수

**무엇이 잘못되는가:** MediaStore URI에 ContentObserver를 상시 등록하면, 다른 앱의 미디어 작업(사진 촬영, 파일 다운로드 등)에도 `onChange`가 호출되어 불필요한 wake-up과 배터리 소모가 발생한다. 또한 Service 종료 시 해제하지 않으면 메모리 누수.

**예방:**
1. `onChange`에서 빠르게 필터링 (경로/파일명 패턴 매칭) — 불필요한 이벤트는 즉시 return
2. 사용자가 "삼성 녹음기 자동 감지" 토글을 켤 때만 Observer 등록
3. `onDestroy()`에서 반드시 `contentResolver.unregisterContentObserver()` 호출
4. 현재 `AudioCollectionService.onDestroy()`에 코루틴 정리는 있으나(확인 완료) ContentObserver 해제 패턴은 추가 필요

**신뢰도:** HIGH — Android ContentObserver 공식 문서 확인

---

## 경미한 함정 (Minor Pitfalls)

개발 속도를 늦추거나 기술 부채를 쌓는 실수들.

---

### mP-1: Share Intent 수신 시 대용량 전사 텍스트로 ANR

**무엇이 잘못되는가:** `Intent.EXTRA_TEXT`로 수신되는 전사 텍스트가 수만 자(1시간 회의 기준)일 수 있다. UI 스레드에서 바로 DB 저장이나 파일 쓰기를 수행하면 ANR이 발생한다.

**예방:**
- 수신 즉시 코루틴(Dispatchers.IO)으로 파일 저장 후 WorkManager에 파일 경로만 전달
- ShareReceiverActivity에서 UI 렌더링 없이 즉시 처리 후 `finish()`

---

### mP-2: Gemini API 키 설정 UI에서 유효성 검증 누락

**무엇이 잘못되는가:** 사용자가 잘못된 API 키를 입력해도 저장되고, 회의록 생성 시점(WorkManager 백그라운드)에서야 실패가 발견된다.

**예방:**
- 키 저장 전에 간단한 Gemini API 호출(`GET /v1beta/models`)로 유효성 검증
- 검증 실패 시 저장 차단 및 에러 메시지 표시
- 검증 중 로딩 인디케이터 표시

---

### mP-3: 다중 소스(Plaud BLE + 삼성 녹음기 공유)에서 중복 회의 생성

**무엇이 잘못되는가:** 같은 회의를 Plaud로 녹음하고, 삼성 녹음기로도 녹음한 경우, 두 경로에서 파이프라인이 동시에 트리거되어 중복 회의록이 생성된다.

**예방:**
- `MeetingEntity`에 `sourceType` 컬럼 추가 (`PLAUD_BLE`, `SAMSUNG_SHARE`, `MANUAL`)
- 파이프라인 트리거 시 타임스탬프 기반 중복 감지 (동일 시간대 +-5분 이내 회의 존재 여부)
- UI에서 "유사한 시간대의 회의가 이미 있습니다" 알림 표시

---

### mP-4: NotebookLM 연동 범위를 과도하게 잡아 시간 낭비

**무엇이 잘못되는가:** "앱 내에서 NotebookLM을 완전 제어하겠다"는 목표를 잡으면, API가 없는 상태에서 웹뷰 해킹이나 자동화 시도에 빠진다.

**예방:**
- 구현 범위를 명확히 한정: (1) 전사 텍스트를 공유 Intent로 전달, (2) NotebookLM 웹 URL을 Custom Tabs로 열기
- 최대 2일 타임박스. 그 이상 소요 시 "브라우저 열기 + 클립보드 복사" 수준으로 확정

---

### mP-5: Foreground Service 타입 추가 시 AndroidManifest 퍼미션 누락

**무엇이 잘못되는가:** ContentObserver를 별도 Foreground Service로 운영할 경우, Android 14+에서는 `foregroundServiceType`을 적절히 선언하지 않으면 크래시. 현재 `AudioCollectionService`는 `connectedDevice` 타입.

**예방:**
- 새 서비스 추가 시 `foregroundServiceType` 선언 필수 (`dataSync` 또는 `specialUse`)
- Android 14 `FOREGROUND_SERVICE_DATA_SYNC` 퍼미션 Manifest에 추가
- 가능하면 기존 서비스에 합치지 않고 책임별로 분리

---

## Phase별 경고 매트릭스

| Phase 주제 | 예상 함정 | 심각도 | 완화 전략 |
|------------|----------|--------|----------|
| 삼성 녹음기 전사 감지 Spike | CP-1: 자동 감지 불가능 가능성 | **치명적** | Phase 1 첫 48시간 내 실기기 검증 → 불가 시 공유 방식 확정 |
| GeminiEngine 인증 추상화 | CP-3: 파이프라인 파괴, SDK OAuth 미지원 | **치명적** | AuthProvider 인터페이스 선 설계, OAuth는 REST API 경로 |
| 파일 삭제 기능 | CP-4: DB-파일 불일치 | **치명적** | 삭제 UseCase에서 양쪽 처리, 고아 파일 정리 |
| 삼성 녹음기 공유 수신 | MP-1: Intent 충돌, mP-1: ANR | 중간 | 전용 ShareReceiverActivity + 확인 화면 + 비동기 처리 |
| Gemini API 키 UI | MP-4: 보안/호환성, mP-2: 유효성 미검증 | 중간 | EncryptedSharedPreferences(폴백 있음) + 저장 전 검증 |
| Gemini OAuth | MP-2: 라이브러리 선택 | 중간 | Credential Manager 사용, SDK 미지원 시 REST API |
| NotebookLM 연동 | CP-2: API 부재 | **치명적** | 공유 방식으로 한정, 2일 타임박스 |
| Room 스키마 변경 | MP-5: 마이그레이션 누락 | 중간 | exportSchema=true, Migration 작성, 파괴적 마이그레이션 금지 |
| Plaud BLE 디버깅 | MP-3: 에뮬레이터 불가, GATT 133 | 중간 | 마지막 Phase, 실기기 + nRF Connect 필수 |

---

## Phase 순서 권장 (함정 기반)

함정의 심각도와 의존 관계를 고려한 최적 순서:

1. **삼성 녹음기 감지 Spike** — CP-1 즉시 확인. 불가 판정 시 기능 범위 축소. 최소 투자로 최대 리스크 해소
2. **GeminiEngine 인증 추상화 + API 키 설정 UI** — CP-3 사전 방지. BuildConfig 의존 제거. 이후 OAuth의 기반
3. **파일 삭제 기능 + Room 마이그레이션** — CP-4 + MP-5 동시 해결. DB 정합성 확보
4. **삼성 녹음기 공유 수신** — Spike 결과로 확정된 경로 구현. ShareReceiverActivity + 파이프라인 연결
5. **NotebookLM 연동** — CP-2 범위 한정. 공유/Custom Tabs 수준. 2일 타임박스
6. **Gemini OAuth** — MP-2 대응. 인증 추상화가 이미 되어 있으므로 안전하게 추가
7. **Plaud BLE 디버깅** — MP-3. 실기기 필수, 다른 기능과 병행 불가, 마지막 수행

---

## 출처

- 현재 코드베이스 직접 분석: `GeminiEngine.kt`, `MeetingDao.kt`, `MeetingRepositoryImpl.kt`, `PlaudSdkManager.kt`, `AudioCollectionService.kt`, `AndroidManifest.xml`, `AppDatabase.kt`, `MeetingEntity.kt` — HIGH
- Android ContentObserver 공식 문서: https://developer.android.com/reference/android/database/ContentObserver — HIGH
- Android BLE 공식 가이드: https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview — HIGH
- Android Scoped Storage 정책: https://developer.android.com/about/versions/11/privacy/storage — HIGH
- Room 마이그레이션 가이드: https://developer.android.com/training/data-storage/room/migrating-db-versions — HIGH
- Android Credential Manager: https://developer.android.com/identity/sign-in/credential-manager — MEDIUM
- NotebookLM 공식 API 부재: PROJECT.md 기록 확인 — HIGH
- Samsung Voice Recorder 내부 구조: 비공개, 실기기 검증 필요 — LOW
- Google AI Client SDK OAuth 지원: 미확인, 실제 테스트 필요 — LOW
- EncryptedSharedPreferences 안정성: Android 보안 라이브러리 알려진 이슈 — MEDIUM
