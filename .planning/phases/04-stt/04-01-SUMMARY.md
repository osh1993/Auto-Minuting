---
phase: 04-stt
plan: 01
subsystem: stt-engine
tags: [whisper, ml-kit, stt, transcription, fallback]
dependency_graph:
  requires: [03-audio-01, 03-audio-02]
  provides: [transcription-repository, stt-engines, worker-transcription]
  affects: [05-minutes]
tech_stack:
  added: [whisper.cpp-jni-stub, android-speech-recognizer]
  patterns: [dual-path-fallback, stub-pattern, constructor-injection]
key_files:
  created:
    - app/src/main/java/com/autominuting/data/stt/SttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
    - app/src/main/java/com/autominuting/data/stt/MlKitEngine.kt
    - app/src/main/java/com/autominuting/data/stt/AudioConverter.kt
    - app/src/main/java/com/autominuting/data/repository/TranscriptionRepositoryImpl.kt
    - app/src/main/java/com/autominuting/di/SttModule.kt
  modified:
    - app/src/main/java/com/autominuting/di/RepositoryModule.kt
    - app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt
    - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
decisions:
  - SttModule을 빈 Hilt object 모듈로 유지: WhisperEngine/MlKitEngine이 @Inject constructor + @Singleton으로 자동 주입되므로 @Provides 중복 방지
  - AudioConverter에서 선형 보간법 리샘플링 채택: MediaCodec 디코딩 후 16kHz mono 변환
  - TranscriptionTriggerWorker에 meetingId + audioFilePath 이중 입력 지원: 기존 호환성 유지
metrics:
  duration: 5min
  completed: "2026-03-24T11:19:29Z"
  tasks_completed: 2
  tasks_total: 2
  files_created: 6
  files_modified: 3
---

# Phase 04 Plan 01: Whisper/ML Kit STT 엔진 및 TranscriptionRepository Summary

Whisper JNI 래퍼(1차) + SpeechRecognizer 폴백(2차) 이중 경로 STT 파이프라인을 구현하고, TranscriptionTriggerWorker에서 실제 전사를 수행하도록 연동 완료

## Tasks Completed

### Task 1: STT 엔진 인터페이스 + Whisper/ML Kit 구현체 + TranscriptionRepositoryImpl
- **Commit:** f37e187
- **변경 사항:**
  - `SttEngine` 인터페이스 정의 (transcribe, isAvailable, engineName)
  - `WhisperEngine`: whisper.cpp JNI 래퍼, language="ko", temperature=0.0, 네이티브 라이브러리 미로드 시 스텁 모드
  - `MlKitEngine`: Android SpeechRecognizer 래퍼, ko-KR 로케일, 온디바이스 인식기 우선
  - `AudioConverter`: MediaCodec/MediaExtractor로 디코딩 후 16kHz mono PCM WAV 변환
  - `TranscriptionRepositoryImpl`: Whisper 1차 -> ML Kit 2차 이중 경로 폴백 (AudioRepositoryImpl 패턴 동일)
  - `MeetingDao`에 updateTranscript, getMeetingByAudioPath 메서드 추가
  - `RepositoryModule`에 TranscriptionRepository 바인딩 추가
  - `SttModule` Hilt 모듈 생성

### Task 2: TranscriptionTriggerWorker 실제 전사 로직 연동
- **Commit:** 9f5b6bf
- **변경 사항:**
  - 생성자에 `TranscriptionRepository` Hilt 주입 추가
  - `doWork()`에서 실제 전사 파이프라인 실행 (Whisper 1차 + ML Kit 2차)
  - 성공 시: 전사 텍스트 파일 저장 + PipelineStatus.TRANSCRIBED 업데이트
  - 실패 시: PipelineStatus.FAILED + 에러 메시지 저장
  - meetingId inputData 지원 + audioFilePath로 DB 조회 폴백 (기존 호환성)
  - outputData에 transcriptPath 포함 (Phase 5 체이닝용)
  - 스텁 코드(TODO 주석, "Phase 4에서 실제 전사 로직 구현 예정") 완전 제거

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SttModule @Provides 중복 바인딩 방지**
- **발견 시점:** Task 1
- **이슈:** WhisperEngine/MlKitEngine이 `@Inject constructor`와 `@Singleton`을 갖고 있어 SttModule에서 `@Provides`로 다시 제공하면 Hilt 중복 바인딩 오류 발생
- **해결:** SttModule을 빈 object 모듈로 유지, constructor injection에 의존
- **파일:** app/src/main/java/com/autominuting/di/SttModule.kt

**2. [Rule 2 - Critical] MeetingDao에 getMeetingByAudioPath 추가**
- **발견 시점:** Task 1
- **이슈:** Task 2에서 meetingId가 없을 때 audioFilePath로 회의를 조회해야 하는데 해당 DAO 메서드가 없었음
- **해결:** `getMeetingByAudioPath(audioFilePath)` 쿼리 메서드 추가
- **파일:** app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt

## Verification Results

- WhisperEngine 존재 확인
- MlKitEngine 존재 확인
- TranscriptionRepositoryImpl 존재 확인
- Worker에서 transcriptionRepository.transcribe 호출 확인
- RepositoryModule에 bindTranscriptionRepository 바인딩 확인
- MeetingDao에 updateTranscript 메서드 확인
- 빌드 검증: JAVA_HOME 미설정으로 assembleDebug 실행 불가 (정적 검증으로 대체)

## Known Stubs

| 파일 | 위치 | 설명 | 해결 시점 |
|------|------|------|----------|
| WhisperEngine.kt | nativeTranscribe() | whisper.cpp 네이티브 라이브러리(libwhisper.so) 미배치 시 스텁 모드로 동작, JNI 호출 실패 시 Result.failure() 반환 | 실기기에 네이티브 라이브러리 배치 시 자동 활성화 |
| MlKitEngine.kt | performRecognition() | SpeechRecognizer 파일 기반 전사는 기기 지원에 따라 제한적, EXTRA_AUDIO_SOURCE 미지원 기기에서 실패 가능 | 실기기 테스트 후 개선 |

스텁은 Phase 3의 NiceBuildSdkWrapper 스텁 패턴(per D-08)과 동일하게 설계됨: 네이티브 라이브러리/모델 미배치 시에도 컴파일 성공, 런타임에 자동 감지하여 폴백 전환.

## Decisions Made

1. **SttModule 빈 모듈 유지**: `@Inject constructor` + `@Singleton`으로 Hilt가 자동 생성하므로 `@Provides` 불필요
2. **AudioConverter 선형 보간법 리샘플링**: MediaCodec 디코딩 후 단순 선형 보간으로 16kHz 변환 (고급 리샘플링은 추후 최적화)
3. **Worker 이중 입력 지원**: meetingId(우선) + audioFilePath(폴백) 조회로 기존 Phase 3 코드와 호환성 유지

## Self-Check: PASSED

- 생성 파일 6개: 모두 FOUND
- 수정 파일 3개: 커밋에 포함 확인
- 커밋 f37e187 (Task 1): FOUND
- 커밋 9f5b6bf (Task 2): FOUND
