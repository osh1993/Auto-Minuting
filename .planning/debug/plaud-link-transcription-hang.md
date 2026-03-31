---
status: awaiting_human_verify
trigger: "Plaud 링크 공유로 음성 파일 다운로드 후 전사하는 과정에서 전사가 끝나지 않고 오랫동안 전사중 상태로 멈춰 있음"
created: 2026-03-30T00:00:00+09:00
updated: 2026-03-30T00:00:00+09:00
---

## Current Focus

hypothesis: TranscriptionTriggerWorker가 Foreground Worker가 아니어서 WorkManager 10분 제한에 걸려 kill됨. kill 시 TRANSCRIBING 상태가 복구되지 않아 영구 hang 상태가 됨.
test: Worker 코드에서 setForeground/getForegroundInfo 호출 유무 확인 + 전사 소요 시간 추정
expecting: setForeground 미호출 확인, Whisper small 모델로 긴 오디오 전사 시 10분 초과 가능성 확인
next_action: fix 적용 - Worker를 Long-running(Foreground) Worker로 전환 + 비정상 종료 시 상태 복구 로직 추가

## Symptoms

expected: Plaud 링크 공유 -> 음성 다운로드 -> 전사 완료 -> 회의록 생성
actual: 음성 다운로드 후 전사중(TRANSCRIBING) 상태에서 멈춰 진행되지 않음
errors: 없음 (명시적 오류 없이 멈춤)
reproduction: Plaud 앱에서 링크 공유 -> Auto Minuting 앱으로 Intent 수신 -> 다운로드 -> 전사 시작 -> 무한 대기
started: 현재 발생 중

## Eliminated

- hypothesis: JNI nativeTranscribe 콜백 미호출로 인한 hang
  evidence: JNI 코드(whisper_jni.cpp)는 whisper_full() 완료 후 정상적으로 결과를 반환함. 콜백 문제가 아니라 Worker 자체가 kill되는 문제.
  timestamp: 2026-03-30

- hypothesis: Gemini STT API 타임아웃으로 인한 hang
  evidence: GeminiSttEngine은 OkHttp 타임아웃(5분)과 3회 재시도가 있어 최대 15분 소요 가능하지만, 결국 Result를 반환함. 문제는 Worker가 그 전에 kill되는 것.
  timestamp: 2026-03-30

## Evidence

- timestamp: 2026-03-30
  checked: TranscriptionTriggerWorker 코드
  found: CoroutineWorker 상속, setForeground() 미호출, getForegroundInfo() 미구현. WorkManager 기본 10분 실행 제한 적용됨.
  implication: 긴 오디오 전사 시 10분 초과하면 WorkManager가 Worker를 kill함.

- timestamp: 2026-03-30
  checked: TranscriptionTriggerWorker 상태 전이 로직
  found: doWork() 시작 시 TRANSCRIBING으로 설정 후, 성공/실패 시에만 상태 업데이트. Worker가 비정상 종료(kill)되면 TRANSCRIBING 상태 그대로 남음.
  implication: kill 후 상태 복구 메커니즘이 없어 영구 hang 발생.

- timestamp: 2026-03-30
  checked: whisper_jni.cpp - whisper_full() 호출 구조
  found: Whisper small-q5_1 모델로 전체 오디오를 단일 호출로 처리. 30분 회의 오디오는 모바일에서 10분 이상 소요 가능.
  implication: Whisper 엔진 선택 시 거의 확실히 10분 제한에 걸림.

- timestamp: 2026-03-30
  checked: GeminiSttEngine 타임아웃 설정
  found: readTimeout=5분, writeTimeout=5분, 최대 3회 재시도(20초 간격). 최악의 경우 ~15분 소요 가능.
  implication: Gemini 엔진도 대용량 오디오에서 10분 제한에 걸릴 수 있음.

- timestamp: 2026-03-30
  checked: ShareReceiverActivity.downloadAndStartPipeline()
  found: Plaud 링크에서 다운로드한 오디오를 TranscriptionTriggerWorker로 enqueue. Worker 설정에 BackoffPolicy 없음(기본값 사용).
  implication: Worker 재시작 시에도 동일하게 10분 제한에 걸려 무한 retry+kill 루프 가능.

## Resolution

root_cause: TranscriptionTriggerWorker가 일반 CoroutineWorker로 실행되어 WorkManager 10분 실행 제한에 걸림. Whisper small 모델로 긴 오디오(30분+) 전사 시 10분 이상 소요되므로 Worker가 kill됨. kill 시 DB 상태가 TRANSCRIBING에서 복구되지 않아 영구 hang 발생.
fix: (1) Worker를 Long-running(Foreground) Worker로 전환하여 10분 제한 해제. (2) 앱 시작 시 TRANSCRIBING 상태로 남아있는 엔트리를 FAILED로 복구하는 안전장치 추가.
verification: assembleDebug 빌드 성공 확인. 사용자 실환경 검증 대기 중.
files_changed:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt
  - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt
  - app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt
  - app/src/main/java/com/autominuting/AutoMinutingApplication.kt
