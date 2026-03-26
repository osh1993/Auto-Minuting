# Codebase Concerns

**Analysis Date:** 2026-03-26

---

## Tech Debt

**Whisper JNI 네이티브 라이브러리 미구현:**
- Issue: `WhisperEngine`이 `nativeTranscribe()` JNI 메서드를 선언하고 있으나 실제 `libwhisper.so`가 빌드에 포함되어 있지 않음. `isNativeLoaded = false`로 항상 스텁 모드로 동작
- Files: `app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt`
- Impact: 1차 전사 경로가 항상 실패하므로 모든 전사는 `MlKitEngine` 폴백으로만 처리됨. `MlKitEngine` 자체도 파일 기반 전사를 공식 지원하지 않는 `android.speech.extra.AUDIO_SOURCE` 비공개 Extra에 의존
- Fix approach: whisper.cpp Android 빌드(CMakeLists.txt + JNI 브리지 구현)를 추가하거나, 삼성 Galaxy AI STT API로 교체

**MlKitEngine의 비공개 Extra 의존:**
- Issue: `android.speech.extra.AUDIO_SOURCE` Extra는 Android 공식 API가 아님. 파일 기반 전사가 지원되지 않는 기기(포함 일부 삼성 갤럭시)에서 `onError()`로 빠짐
- Files: `app/src/main/java/com/autominuting/data/stt/MlKitEngine.kt` (line 124)
- Impact: Whisper도 없고 SpeechRecognizer 파일 입력도 실패하면 전사 파이프라인 전체가 `FAILED` 상태로 종료됨
- Fix approach: Samsung Galaxy AI `createOnDeviceSpeechRecognizer` + EXTRA_AUDIO_SOURCE 지원 여부를 기기별로 사전 검증하는 로직 추가, 또는 Galaxy AI SDK 전용 엔진 구현

**`AudioRepositoryImpl.startAudioCollection()`의 빈 구현:**
- Issue: `startAudioCollection()`이 저장 공간 확인 후 아무것도 emit하지 않는 빈 `channelFlow`를 반환
- Files: `app/src/main/java/com/autominuting/data/repository/AudioRepositoryImpl.kt` (lines 38–52)
- Impact: `AudioRepository` 인터페이스 계약을 충족하지 않음. 이 메서드를 호출하는 코드가 있을 경우 데드락 또는 무한 대기 가능
- Fix approach: 삼성 공유 경로에 맞게 구현을 단순화하거나 인터페이스에서 제거

**`TestWorker`가 프로덕션 코드에 잔류:**
- Issue: WorkManager 초기화 검증용 `TestWorker`가 여전히 `worker/` 패키지에 존재하며 제거되지 않음
- Files: `app/src/main/java/com/autominuting/worker/TestWorker.kt`
- Impact: 빌드 크기 증가, 잠재적으로 잘못된 Worker 등록 혼동 가능
- Fix approach: 파일 삭제

**`AudioFileManager`의 지원 확장자 미스매치:**
- Issue: `SUPPORTED_EXTENSIONS = listOf("mp3", "wav")`로 제한되어 있으나, `ShareReceiverActivity`는 `m4a`, `ogg`, `flac`도 수신하고 저장함. `validateAudioFile()`로 검증 시 `m4a` 파일은 무효로 처리됨
- Files:
  - `app/src/main/java/com/autominuting/data/audio/AudioFileManager.kt` (line 29)
  - `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` (lines 237–243)
- Impact: 삼성 녹음앱의 기본 포맷인 `m4a`가 `validateAudioFile()` 검증을 통과하지 못함
- Fix approach: `SUPPORTED_EXTENSIONS`에 `m4a`, `ogg`, `flac` 추가

**WorkManager 체이닝에서 `as?` 타입 캐스팅에 의존:**
- Issue: `TranscriptionTriggerWorker`와 `MinutesGenerationWorker` 모두 `saveTranscriptToFile` / `saveMinutesToFile` 호출을 위해 `repository as? ConcreteImpl` 패턴을 사용. 구현체가 바뀌면 폴백 경로(직접 파일 저장)로 조용히 전환됨
- Files:
  - `app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt` (line 94)
  - `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` (line 116)
- Impact: 인터페이스 기반 의존성 주입의 이점을 무력화함. 리포지토리 교체 시 파일 저장 경로가 달라져 데이터 불일치 가능
- Fix approach: `saveTranscriptToFile` / `saveMinutesToFile` 메서드를 도메인 인터페이스(`TranscriptionRepository`, `MinutesRepository`)에 추가

**`@Suppress("DEPRECATION")` 사용 — MasterKeys API:**
- Issue: `SecureApiKeyRepository`에서 deprecated `MasterKeys.getOrCreate()` API를 사용하고 있음
- Files: `app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt` (line 27)
- Impact: API 수준에 따라 Lint 경고. 향후 AGP 버전 업그레이드 시 컴파일 경고가 오류로 격상될 가능성
- Fix approach: `MasterKey.Builder` API로 마이그레이션

---

## Known Bugs

**`MeetingEntity.toDomain()`의 잠재적 크래시 — `PipelineStatus.valueOf()`:**
- Symptoms: DB에 저장된 `pipelineStatus` 문자열이 `PipelineStatus` enum에 없는 값이면 `IllegalArgumentException` 발생하며 앱 크래시
- Files: `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` (line 43)
- Trigger: DB 마이그레이션 후 enum 값 이름이 변경된 경우. 과거 커밋(`fix: AUDIO_SAVED → AUDIO_RECEIVED enum 수정`)은 이 문제가 실제로 발생했음을 보여줌
- Workaround: 없음 (현재 enum 값 이름 변경 금지로 회피 중)

**`UserPreferencesRepository`의 `MinutesFormat.valueOf()` / `AutomationMode.valueOf()` 크래시:**
- Symptoms: DataStore에 저장된 값이 enum에 없으면 Flow 구독 시 `IllegalArgumentException` 발생
- Files: `app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt` (lines 47, 53, 59)
- Trigger: 앱 업데이트로 enum 이름이 변경된 경우
- Workaround: 없음. `runCatching` 또는 `orElse` 패턴 필요

**`PipelineNotificationHelper.notifyComplete()`에서 전체 회의록 텍스트를 Intent Extra에 삽입:**
- Symptoms: 회의록이 길면 `TransactionTooLargeException`(1MB 제한) 발생 가능
- Files: `app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt` (line 83)
- Trigger: 긴 회의록(대략 500KB 이상 텍스트) 생성 후 공유 버튼 사용
- Workaround: 없음. `minutesText` 대신 `meetingId`만 PendingIntent에 전달하고, 수신 측에서 DB/파일로 읽도록 변경 필요

**`ShareReceiverActivity`의 음성 공유 시 기본 title이 하드코딩:**
- Symptoms: 공유된 음성 파일의 회의명이 항상 "음성 공유 회의"로 저장됨
- Files: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` (line 260)
- Trigger: 모든 오디오 공유 수신
- Workaround: 없음. 파일명 또는 `EXTRA_SUBJECT`에서 제목 추출 로직 추가 필요

---

## Security Considerations

**BuildConfig에 API 키 임베드 가능성:**
- Risk: `local.properties`에 `GEMINI_API_KEY`가 없으면 빈 문자열로 빌드되지만, 개발자가 직접 `buildConfigField`에 값을 입력하면 APK 내 `BuildConfig.class`에 평문으로 노출됨
- Files: `app/build.gradle.kts` (lines 23–30), `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` (line 116)
- Current mitigation: `SecureApiKeyRepository`로 런타임 입력 저장을 권장. `BuildConfig` 폴백은 개발 편의용
- Recommendations: 프로덕션 빌드에서 `BuildConfig.GEMINI_API_KEY`가 빈 문자열인지 CI에서 검증. `GeminiEngine.isAvailable()`이 `BuildConfig` 폴백을 사용하지 않도록 수정 고려

**OAuth Access Token 메모리 캐시:**
- Risk: `cachedAccessToken`이 `@Volatile` 필드로 메모리에만 보관됨. 앱 프로세스가 살아있는 동안 힙 덤프 등으로 노출 가능
- Files: `app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt` (line 62)
- Current mitigation: 디스크에 저장하지 않는 것이 오히려 보안상 유리
- Recommendations: Token 만료 처리가 없음 — 토큰 갱신 로직 추가 필요

**`ShareReceiverActivity`가 `*/*` MIME 타입을 수락:**
- Risk: Manifest에 `<data android:mimeType="*/*" />`로 등록되어 있어 임의의 파일이 앱으로 공유될 수 있음
- Files: `app/src/main/AndroidManifest.xml` (line 66)
- Current mitigation: 코드에서 `mimeType?.startsWith("audio/")` 체크로 오디오 파일만 처리
- Recommendations: Manifest의 MIME 타입을 `audio/*`와 `text/*`로 제한하여 공격 표면 축소

**BroadcastReceiver에서 Intent Extra를 검증하지 않음:**
- Risk: `PipelineActionReceiver`에서 `meetingId`, `transcriptPath`, `minutesText`를 신뢰함. `android:exported="false"`이므로 외부 앱에서는 발송 불가하나, 앱 내 다른 컴포넌트에서 잘못된 값이 전달될 수 있음
- Files: `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt`
- Current mitigation: `android:exported="false"` 설정
- Recommendations: `meetingId > 0` 검증 추가

---

## Performance Bottlenecks

**`AudioConverter.decodeAudioToPcm()`의 전체 파일 메모리 로드:**
- Problem: 디코딩된 PCM 데이터 전체를 `mutableListOf<ByteArray>`에 누적한 뒤 `fold`로 합침. 60분 회의 오디오(~160MB WAV)의 경우 메모리 부족(OOM) 가능
- Files: `app/src/main/java/com/autominuting/data/stt/AudioConverter.kt` (lines 126–168)
- Cause: 스트리밍 대신 일괄 처리 방식 사용
- Improvement path: `OutputStream`으로 직접 청크 단위 출력 파이프라이닝 구현

**`resampleToTarget()`의 단순 선형 보간:**
- Problem: 정밀도가 낮은 선형 보간법을 사용. 고주파수 음성에서 aliasing 발생 가능
- Files: `app/src/main/java/com/autominuting/data/stt/AudioConverter.kt` (lines 178–224)
- Cause: 구현 단순화
- Improvement path: 사인카 보간 또는 Android `AudioRecord`/`AudioTrack` 리샘플링 활용

**`GeminiEngine`이 매 호출마다 `GenerativeModel` 인스턴스 생성:**
- Problem: `generate()` 호출마다 `GenerativeModel(modelName, apiKey)`를 새로 생성
- Files: `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` (lines 127–130)
- Cause: API 키 변경 대응을 위한 lazy 초기화 회피
- Improvement path: `cachedApiKey`를 추적하고 키가 변경된 경우에만 모델 재생성

---

## Fragile Areas

**`MeetingEntity.pipelineStatus`의 문자열 저장 방식:**
- Files:
  - `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt`
  - `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` (lines 74–79)
- Why fragile: `PipelineStatus` enum의 이름(`name`)을 DB에 직접 저장. enum 이름 변경 시 기존 DB 데이터가 역직렬화 불가로 크래시 발생 (과거에 실제 크래시 발생: `fix: AUDIO_SAVED → AUDIO_RECEIVED`)
- Safe modification: enum 이름 변경 금지. 이름 변경이 필요할 경우 Room 마이그레이션 + `updateAllPipelineStatus()` SQL 쿼리 작성 필수
- Test coverage: 없음

**`BearerTokenInterceptor`의 `runBlocking` 사용:**
- Files: `app/src/main/java/com/autominuting/data/minutes/BearerTokenInterceptor.kt` (line 22)
- Why fragile: OkHttp 인터셉터는 IO 스레드에서 실행되므로 `runBlocking`으로 suspend 함수 호출은 기술적으로 허용되나, 메인 스레드에서 호출될 경우 ANR 발생 가능
- Safe modification: `GoogleAuthRepository.getAccessToken()`이 항상 동기 반환이므로 현재는 안전하나, 향후 토큰 갱신 로직을 suspend로 추가할 경우 `runBlocking` 제거 필수
- Test coverage: 없음

**`ShareReceiverActivity`의 encoding 자동 감지 휴리스틱:**
- Files: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` (lines 337–345)
- Why fragile: BOM 없는 파일의 경우 2번째 또는 1번째 바이트가 `0x00`인지로 UTF-16을 추정. 짧은 ASCII 파일에서 오탐 가능
- Safe modification: BOM이 없는 경우 UTF-8 기본값으로만 처리하고, 디코딩 실패 시 UTF-16 재시도 패턴 적용
- Test coverage: 없음

**동일한 `PIPELINE_NOTIFICATION_ID`를 모든 알림에 재사용:**
- Files: `app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt` (line 24)
- Why fragile: 파이프라인이 동시에 두 건 이상 진행될 경우 알림이 덮어쓰여짐. 현재는 단일 파이프라인만 지원되지만 향후 다중 공유 수신 시나리오에서 문제 발생
- Safe modification: `meetingId`를 알림 ID로 사용

---

## Scaling Limits

**단일 파이프라인 병렬 처리 미지원:**
- Current capacity: 하나의 파이프라인만 처리. `DashboardViewModel.activePipeline`이 `firstOrNull()`로 하나만 추적
- Limit: 여러 회의 파일을 연속으로 공유하면 파이프라인 상태 추적이 불가능
- Scaling path: WorkManager `UniqueWork` 또는 `meetingId` 기반 독립 체이닝으로 다중 파이프라인 지원

**음성 파일 무제한 누적:**
- Current capacity: `filesDir/audio/` 내 파일이 자동 정리되지 않음
- Limit: 기기 저장 공간 부족
- Scaling path: `MeetingRepositoryImpl.deleteMeeting()`은 파일을 삭제하나, 사용자가 수동 삭제해야 함. 보존 정책(예: 30일) 자동화 필요

---

## Dependencies at Risk

**`whisper.cpp` JNI 미통합:**
- Risk: 네이티브 라이브러리 없이 코드 구조만 존재. `WhisperEngine`을 실제로 동작시키려면 C++ 빌드 인프라가 필요
- Impact: 전사 1차 경로 완전 비작동
- Migration plan: whisper.cpp Android 공식 예제 참조 (`android/java`) 또는 온디바이스 Gemini Nano STT로 대체 검토

**`EncryptedSharedPreferences` Deprecated API:**
- Risk: `MasterKeys.getOrCreate()` 및 `EncryptedSharedPreferences.create()` 구 서명 deprecated
- Impact: 향후 `security-crypto` 라이브러리 업데이트 시 컴파일 오류 가능
- Migration plan: `MasterKey.Builder().setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()` + `EncryptedSharedPreferences.create(context, fileName, masterKey, ...)` 최신 서명으로 교체

**`play.services.auth`(Identity SDK) + `credentials` 중복:**
- Risk: `GoogleAuthRepository`가 `Identity.getAuthorizationClient()`(구형 `play.services.auth`) 와 `CredentialManager`(신형 `credentials`) API를 혼용
- Impact: Google이 `play.services.auth`를 단계적으로 제거하는 경우 `authorize()` 메서드 동작 중단
- Migration plan: `Identity.getAuthorizationClient` 호출을 `CredentialManager`의 `GetCredentialRequest`로 통합

---

## Missing Critical Features

**STT 파이프라인 실질적 미작동:**
- Problem: Whisper 네이티브 라이브러리 없음 + MlKitEngine의 파일 기반 전사 비공식 지원 → 실제 기기에서 전사 성공률 미확인
- Blocks: 전체 자동화 파이프라인 E2E 검증 불가

**OAuth Token 갱신 로직 없음:**
- Problem: `cachedAccessToken`은 앱 세션 동안만 유효. 앱 재시작 또는 토큰 만료 시 `authorize()` 재호출 필요하나 자동화되지 않음
- Files: `app/src/main/java/com/autominuting/data/auth/GoogleAuthRepository.kt` (line 62)
- Blocks: OAuth 모드에서 백그라운드 Worker가 토큰 만료 후 Gemini API를 호출하면 `401` 오류로 조용히 실패

**NotebookLM 실제 통합 미구현:**
- Problem: `NotebookLmHelper`는 앱이 설치된 경우 `ACTION_SEND` Intent, 미설치 시 웹 열기만 수행. 자동 노트북 생성/소스 추가는 없음
- Files: `app/src/main/java/com/autominuting/util/NotebookLmHelper.kt`
- Blocks: CLAUDE.md의 핵심 가치인 "원클릭 파이프라인"에서 NotebookLM 자동 등록 단계가 누락

---

## Test Coverage Gaps

**테스트 파일 전무:**
- What's not tested: 전체 코드베이스에 `*.test.kt` 또는 `*.spec.kt` 파일이 존재하지 않음
- Files: `app/src/test/`, `app/src/androidTest/` 디렉토리 미존재
- Risk: 파이프라인 상태 전환 로직, DB 마이그레이션, 전사 폴백 체인, encoding 감지 휴리스틱 등 모두 회귀 위험
- Priority: High

**`PipelineStatus` enum 직렬화 회귀:**
- What's not tested: DB 마이그레이션 후 enum name 변경 시 크래시 재현
- Files: `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt`
- Risk: 과거 실제 크래시가 발생했으나 재발 방지 테스트 없음
- Priority: High

**`AudioConverter` 오디오 변환 정확성:**
- What's not tested: 다양한 샘플레이트(44.1kHz, 48kHz) 및 채널 수(스테레오)에서 변환 결과의 정확성
- Files: `app/src/main/java/com/autominuting/data/stt/AudioConverter.kt`
- Risk: 잘못된 리샘플링은 STT 품질 저하 또는 Whisper crash로 이어짐
- Priority: Medium

**`ShareReceiverActivity` encoding 감지:**
- What's not tested: UTF-16 LE/BE BOM, UTF-8 BOM, BOM 없는 UTF-16 파일에 대한 감지 정확성
- Files: `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt`
- Risk: 삼성 녹음앱이 UTF-16 LE로 저장한다는 전제에서만 정상 동작
- Priority: Medium

---

*Concerns audit: 2026-03-26*
