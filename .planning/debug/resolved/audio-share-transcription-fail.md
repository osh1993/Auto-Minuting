---
status: awaiting_human_verify
trigger: "공유받은 음성 파일의 전사와 회의록 작성 관련 2가지 버그"
created: 2026-03-27T00:00:00
updated: 2026-03-27T00:00:00
---

## Current Focus

hypothesis: 3가지 근본 원인 확인됨 — 수정 적용 중
test: 코드 수정 후 로직 검증
expecting: 모든 3가지 버그가 해결됨
next_action: 수정 적용

## Symptoms

expected: |
  1. 공유받은 음성("음성 공유 회의")에는 "회의록 작성" 버튼이 보이면 안 된다 (전사가 안 된 상태이므로)
  2. 공유받은 음성이 자동으로 전사되어야 한다 (TranscriptionTriggerWorker가 enqueue되어야 함)
  3. 재전사 기능도 동작해야 한다
actual: |
  1. 공유받은 음성 카드에 "회의록 작성" 버튼이 표시됨
  2. 공유받은 음성이 전사되지 않고 "실패" 상태로 남아있거나 "전사 중"에서 멈춰있음
  3. 재전사도 동작하지 않음
errors: 스크린샷에서 "음성 공유 회의" 카드들이 "실패" 상태로 표시되고 "회의록 작성" 버튼도 보임
reproduction: 다른 앱에서 음성 파일을 Auto Minuting으로 공유하면 발생
started: Phase 19 이후 음성 공유 수신이 추가된 후부터

## Eliminated

## Evidence

- timestamp: 2026-03-27T00:01
  checked: TranscriptsScreen.kt 270-271행 — "회의록 작성" 버튼 표시 조건
  found: TRANSCRIBED와 FAILED 모두에서 버튼 표시. FAILED 상태에서 transcriptPath가 null일 수 있음 (전사 실패 시)
  implication: transcriptPath 없는 FAILED 회의에서도 "회의록 작성" 버튼이 보임

- timestamp: 2026-03-27T00:02
  checked: TranscriptsViewModel.kt 176-182행 — TRANSCRIPT_VISIBLE_STATUSES
  found: AUDIO_RECEIVED가 포함되지 않음. 공유 음성은 AUDIO_RECEIVED로 시작하므로 목록에 안 보임
  implication: 공유 음성이 전사 중 상태가 되기 전까지 사용자에게 보이지 않음

- timestamp: 2026-03-27T00:03
  checked: TranscriptsViewModel.kt retranscribe() 113행 — deleteTranscript 호출
  found: deleteTranscript가 pipelineStatus를 AUDIO_RECEIVED로 되돌림 (MeetingDao 78행). 이후 Worker enqueue하지만 AUDIO_RECEIVED는 VISIBLE_STATUSES에 없음
  implication: 재전사 시 일시적으로 목록에서 사라짐. Worker가 TRANSCRIBING으로 변경하면 다시 보이지만, 그 사이 혼란 발생

- timestamp: 2026-03-27T00:04
  checked: ShareReceiverActivity.kt processSharedAudio() 267-291행
  found: Worker enqueue는 정상적으로 수행됨. 문제는 Worker 자체의 전사 실패(Whisper+MLKit 둘다 실패)가 원인
  implication: 전사 자체가 실패하면 FAILED 상태가 되고, transcriptPath=null인 상태로 "회의록 작성" 버튼이 보임

## Resolution

root_cause: |
  3가지 근본 원인:
  1. TranscriptsScreen.kt: "회의록 작성" 버튼이 FAILED 상태에서 무조건 표시됨. transcriptPath가 null인 경우(전사 실패)에도 버튼이 보여 사용자 혼란 유발
  2. TranscriptsViewModel.kt: TRANSCRIPT_VISIBLE_STATUSES에 AUDIO_RECEIVED가 없어서 공유 음성이 전사 시작 전까지 목록에 보이지 않음
  3. TranscriptsViewModel.kt retranscribe(): deleteTranscript가 상태를 AUDIO_RECEIVED로 되돌린 후 Worker enqueue하는데, 상태를 즉시 TRANSCRIBING으로 설정하지 않아 일시적으로 목록에서 사라짐

fix: |
  1. TranscriptsScreen.kt: FAILED 상태에서 "회의록 작성" 버튼을 transcriptPath != null 조건 추가
  2. TranscriptsViewModel.kt: TRANSCRIPT_VISIBLE_STATUSES에 AUDIO_RECEIVED 추가
  3. TranscriptsViewModel.kt retranscribe(): deleteTranscript 후 즉시 pipelineStatus를 TRANSCRIBING으로 업데이트
verification: 빌드 성공 (경고 0). 로직 검증 완료. 실기기 테스트 필요.
files_changed:
  - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
  - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
