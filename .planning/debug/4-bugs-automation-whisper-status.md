---
status: awaiting_human_verify
trigger: "4개 버그 조사 및 수정: 완전자동모드, Whisper 속도, 긴 파일 전사 실패, 카드 상태 동기화"
created: 2026-03-30T00:00:00Z
updated: 2026-03-30T00:00:00Z
symptoms_prefilled: true
---

## Current Focus

hypothesis: 4개 버그 근본 원인 모두 특정 완료. 수정 적용 중
test: N/A
expecting: N/A
next_action: 4개 파일 수정 적용

## Symptoms

expected:
- Bug 1: HYBRID 모드에서는 전사 완료 후 회의록 자동 생성 안 함
- Bug 2: Whisper 전사가 합리적인 시간 내 완료
- Bug 3: 긴 음성 파일도 Whisper 전사 성공
- Bug 4: 각 TranscriptItem 카드가 자신의 상태만 독립 표시

actual:
- Bug 1: HYBRID 모드임에도 전사 완료 후 회의록 자동 생성됨
- Bug 2: 전사 너무 오래 걸림
- Bug 3: 긴 음성파일 전사 실패
- Bug 4: 한 항목이 "회의록 완료"가 되면 다른 항목도 동일 상태 표시

errors: 없음 (기능적 버그)

reproduction:
- Bug 1: HYBRID 모드 설정 후 전사 실행
- Bug 2: Whisper 엔진으로 음성파일 전사 시작
- Bug 3: 긴 음성파일로 Whisper 전사 시도
- Bug 4: 두 개 이상 전사 항목 있을 때 한 항목 회의록 완료 시 확인

started: 알 수 없음

## Eliminated

(없음)

## Evidence

- timestamp: 2026-03-30T00:10:00Z
  checked: TranscriptionTriggerWorker.kt 56-57행
  found: KEY_AUTOMATION_MODE 없으면 AutomationMode.FULL_AUTO.name으로 기본값 설정됨
  implication: automationMode를 Worker inputData에 전달하지 않으면 항상 FULL_AUTO로 동작

- timestamp: 2026-03-30T00:10:00Z
  checked: DashboardViewModel.kt 390-398행 (URL 다운로드 파이프라인)
  found: TranscriptionTriggerWorker enqueue 시 KEY_AUTOMATION_MODE 미전달
  implication: Bug 1 원인 - URL 다운로드 경로에서는 항상 FULL_AUTO로 처리됨

- timestamp: 2026-03-30T00:10:00Z
  checked: TranscriptsViewModel.kt 188-196행 (retranscribe)
  found: TranscriptionTriggerWorker enqueue 시 KEY_AUTOMATION_MODE 미전달
  implication: 재전사 시에도 항상 FULL_AUTO로 처리됨 (Bug 1 추가 경로)

- timestamp: 2026-03-30T00:10:00Z
  checked: whisper_jni.cpp 116행
  found: params.n_threads = 4 하드코딩
  implication: Bug 2 원인 - 모바일 최적 스레드 수는 2개. 4개로 설정 시 스레드 컨텍스트 전환 오버헤드로 느려짐

- timestamp: 2026-03-30T00:10:00Z
  checked: whisper_jni.cpp 64-110행 (WAV 파일 읽기)
  found: pcmf32 벡터로 전체 오디오를 한번에 메모리에 로드. AudioConverter.kt도 ByteArrayOutputStream으로 전체 PCM을 메모리에 로드
  implication: Bug 3 원인 - 긴 파일(예: 60분 = 16000*60*2*4 bytes ≈ 115MB float 배열) OOM 발생

- timestamp: 2026-03-30T00:10:00Z
  checked: TranscriptsViewModel.kt 184-185행 (retranscribe)
  found: MeetingRepository.updatePipelineStatus(meetingId, PipelineStatus.TRANSCRIBING) 호출 시 minutesPath를 null로 초기화하지 않음
  implication: 재전사 후 이전 minutesPath 값이 DB에 남아있음

- timestamp: 2026-03-30T00:10:00Z
  checked: TranscriptsScreen.kt 529행 (MinutesStatusBadge)
  found: meeting.minutesPath != null이면 "회의록 완료" 표시. pipelineStatus 무관.
  implication: Bug 4 원인 - 재전사 후 minutesPath가 초기화되지 않으면 TRANSCRIBING/TRANSCRIBED 상태에서도 "회의록 완료" 배지 표시됨

## Resolution

root_cause: |
  Bug 1: TranscriptionTriggerWorker를 enqueue하는 3곳(DashboardViewModel URL 다운로드,
  TranscriptsViewModel 재전사, ShareReceiverActivity 음성 공유/Plaud 공유)에서
  KEY_AUTOMATION_MODE를 inputData에 전달하지 않아 Worker 기본값인 FULL_AUTO로 처리됨.

  Bug 2: whisper_jni.cpp에서 n_threads=4 하드코딩. 모바일에서는 스레드 2개가 최적이며
  4개 이상은 컨텍스트 전환 오버헤드로 오히려 느려짐.

  Bug 3: whisper_jni.cpp에서 전체 오디오를 pcmf32 단일 벡터로 메모리에 로드 후
  whisper_full을 한 번에 호출. 긴 파일(60분 = ~115MB float32)은 OOM 발생.

  Bug 4: TranscriptsViewModel.retranscribe에서 재전사 시작 시 이전 minutesPath를
  DB에서 초기화하지 않아, 재전사 중에도 meeting.minutesPath != null이 참이므로
  MinutesStatusBadge가 "회의록 완료"를 계속 표시함.

fix: |
  Bug 1: DashboardViewModel.downloadDirectUrl, TranscriptsViewModel.retranscribe,
  ShareReceiverActivity 2곳에서 TranscriptionTriggerWorker enqueue 시
  KEY_AUTOMATION_MODE를 automationMode.value.name / getAutomationModeOnce().name으로 전달.

  Bug 2: whisper_jni.cpp params.n_threads = 4 → 2로 변경.

  Bug 3: whisper_jni.cpp에서 단일 whisper_full 호출을 30초(WHISPER_SAMPLE_RATE×30)
  청크 루프로 교체. 각 청크마다 whisper_full 호출 후 결과를 full_text에 누적.
  청크 기반 진행률 콜백도 동시 적용.

  Bug 4: TranscriptsViewModel.retranscribe에서 meeting.minutesPath != null이면
  TRANSCRIBING 상태 설정 전에 meetingRepository.deleteMinutesOnly(meetingId) 호출하여
  minutesPath를 null로 초기화하고 회의록 파일 삭제.

verification: 수동 검증 필요
files_changed:
  - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
  - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
  - app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt
  - app/src/main/cpp/whisper_jni.cpp
