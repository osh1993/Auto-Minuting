---
phase: 52-gemini-roundrobin
plan: "01"
subsystem: data/security, data/minutes, data/stt, service, worker
tags: [gemini, round-robin, api-key-rotation, error-handling, notification]
dependency_graph:
  requires:
    - "51-01: SecureApiKeyRepository getAllGeminiApiKeyValues() 다중 키 저장 구조"
  provides:
    - "GeminiKeyRotator: 라운드로빈 순환 + 오류 전환 전담 컴포넌트"
    - "GeminiAllKeysFailedException: 전체 키 소진 예외"
    - "PipelineNotificationHelper: notifyApiKeyError + notifyAllKeysExhausted"
  affects:
    - "GeminiEngine: generate() 라운드로빈 루프로 교체"
    - "GeminiSttEngine: transcribe() 429/403 키 전환 분기 추가"
    - "MinutesGenerationWorker: GeminiAllKeysFailedException 처리 추가"
    - "TranscriptionTriggerWorker: GeminiAllKeysFailedException 처리 추가"
tech_stack:
  added:
    - "MockK 1.13.17 (testImplementation)"
  patterns:
    - "라운드로빈 인덱스 String으로 EncryptedSharedPreferences 저장 (OEM 호환성)"
    - "testOptions.isReturnDefaultValues=true — JUnit 단위 테스트 android.util.Log 목킹 불필요"
key_files:
  created:
    - "app/src/main/java/com/autominuting/data/security/GeminiKeyRotator.kt"
    - "app/src/test/java/com/autominuting/data/security/GeminiKeyRotatorTest.kt"
  modified:
    - "app/src/main/java/com/autominuting/data/security/SecureApiKeyRepository.kt"
    - "app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt"
    - "app/src/main/java/com/autominuting/data/stt/GeminiSttEngine.kt"
    - "app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt"
    - "app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt"
    - "app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt"
    - "app/build.gradle.kts"
    - "gradle/libs.versions.toml"
decisions:
  - "MockK 의존성 추가 (Rule 3: PLAN이 명시적으로 MockK 요구) — build.gradle.kts testImplementation"
  - "testOptions.isReturnDefaultValues=true 추가 — android.util.Log JUnit 호환성 해결"
  - "GeminiSttEngine getApiKey() private 메서드 제거 — 라운드로빈 도입으로 불필요"
metrics:
  duration: "11분"
  completed_date: "2026-04-15"
  tasks_completed: 2
  tasks_total: 3
  files_created: 2
  files_modified: 8
---

# Phase 52 Plan 01: Gemini 라운드로빈 + 오류 자동 전환 Summary

**One-liner:** GeminiKeyRotator @Singleton으로 다중 키 라운드로빈 순환 + 429/403 자동 전환 + GeminiAllKeysFailedException Worker 알림 파이프라인 구현

## 완료된 작업

### Task 1: SecureApiKeyRepository 인덱스 저장 확장 + GeminiKeyRotator 신규 생성 + 유닛 테스트
**커밋:** ca704c4

- `SecureApiKeyRepository`에 `getRoundRobinIndex()` / `saveRoundRobinIndex()` 메서드 추가 (String 저장 — OEM 호환성)
- `GeminiKeyRotator @Singleton` 클래스 신규 생성 — `getCurrentKey()`, `getCurrentIndex()`, `rotateOnError()`, `advance()`
- `GeminiAllKeysFailedException(message, triedKeyCount)` 예외 클래스 신규 생성 (GeminiKeyRotator.kt 하단)
- `GeminiKeyRotatorTest` 5개 유닛 테스트 작성 및 GREEN 확인

**편차 (Rule 3: blocking issues):**
- MockK 1.13.17 의존성 추가 — PLAN이 MockK 요구, 기존 build.gradle.kts에 미포함
- `testOptions.isReturnDefaultValues=true` 추가 — `android.util.Log` JUnit 단위 테스트 호환성

### Task 2: GeminiEngine + GeminiSttEngine 라운드로빈 통합 + PipelineNotificationHelper 키 오류 알림 추가
**커밋:** bebbeae

- `GeminiEngine.generate()`: `keyRotator.getCurrentIndex()` + `while(triedCount < keys.size)` 라운드로빈 루프 구현
- `GeminiEngine.isAvailable()`: `getAllGeminiApiKeyValues().isNotEmpty()` 기준으로 변경
- `GeminiSttEngine.transcribe()`: `response.code == 429 || 403` 분기 + 키 전환 루프 추가
- `GeminiSttEngine.isAvailable()`: `getAllGeminiApiKeyValues().isNotEmpty()` 기준으로 변경
- `GeminiSttEngine.getApiKey()` private 메서드 제거 (불필요)
- `PipelineNotificationHelper.notifyApiKeyError()` + `notifyAllKeysExhausted()` 메서드 추가
- `MinutesGenerationWorker`: `GeminiAllKeysFailedException` catch → `notifyAllKeysExhausted()` 호출
- `TranscriptionTriggerWorker`: `GeminiAllKeysFailedException` catch → `notifyAllKeysExhausted()` 호출

## 검증 결과

- GeminiKeyRotatorTest 5개 테스트: **PASS**
- assembleDebug: **BUILD SUCCESSFUL**

## 대기 중인 작업

### Task 3: 라운드로빈 통합 결과 검증 (체크포인트)
사용자가 아래 단계를 직접 확인해야 합니다:
1. `./gradlew :app:testDebugUnitTest --tests "com.autominuting.data.security.GeminiKeyRotatorTest"` → 5개 PASS
2. `./gradlew assembleDebug` → BUILD SUCCESSFUL
3. (선택) 2개 이상 Gemini API 키 등록 기기에서 로그캣 "라운드로빈 전진" 확인
4. (선택) 유효하지 않은 키를 첫 번째로 등록 후 회의록 생성 → 알림 + 두 번째 키로 전환 확인

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] MockK 의존성 누락**
- **Found during:** Task 1
- **Issue:** PLAN이 MockK 사용을 명시했으나 build.gradle.kts에 MockK 의존성 없음
- **Fix:** libs.versions.toml에 mockk 1.13.17 추가, build.gradle.kts에 testImplementation(libs.mockk) 추가
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts

**2. [Rule 3 - Blocking] android.util.Log JUnit 단위 테스트 RuntimeException**
- **Found during:** Task 1 테스트 실행
- **Issue:** JUnit 단위 테스트에서 android.util.Log 호출 시 "Method not mocked" RuntimeException 발생
- **Fix:** build.gradle.kts testOptions.unitTests.isReturnDefaultValues = true 추가
- **Files modified:** app/build.gradle.kts

## Self-Check: PASSED
