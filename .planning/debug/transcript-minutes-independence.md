---
status: fixing
trigger: "전사 삭제 시 회의록도 같이 삭제됨 + 회의록 재작성 시 기존 회의록 덮어씌워짐"
created: 2026-03-30T00:00:00Z
updated: 2026-03-30T00:01:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: 두 버그 모두 근본 원인 확인 및 수정 완료
test: 컴파일 통과 확인
expecting: (완료)
next_action: 사용자 검증 대기

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected:
  bug1: 전사 목록에서 파일을 삭제해도 회의록 탭의 해당 회의록은 그대로 유지되어야 한다. (전사와 회의록은 독립적)
  bug2: 전사 목록에서 특정 파일에 대해 회의록을 다시 작성해도 이전에 작성된 회의록은 보존되어야 한다.

actual:
  bug1: 전사 목록에서 파일을 삭제하면 회의록 탭에 있는 해당 회의록도 함께 삭제된다.
  bug2: 회의록을 재작성하면 이전 회의록이 덮어씌워져 사라진다.

errors: 없음 (기능 버그)

reproduction:
  bug1: 전사 탭에서 전사 파일 삭제 → 회의록 탭 확인 → 해당 회의록 사라짐
  bug2: 전사 탭에서 회의록 재생성 → 회의록 탭 확인 → 이전 회의록 사라짐

started: 알 수 없음

## Eliminated
<!-- APPEND only - prevents re-investigating -->

- hypothesis: Meeting이 별도 Transcript 엔티티와 Minutes 엔티티로 분리되어 있어 각각 독립 삭제 가능하다
  evidence: MeetingEntity 단일 테이블에 transcriptPath, minutesPath 모두 포함. 별도 테이블 없음.
  timestamp: 2026-03-30T00:01:00Z

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-03-30T00:01:00Z
  checked: MeetingEntity.kt
  found: 단일 엔티티에 transcriptPath(21행), minutesPath(22행) 모두 포함
  implication: 전사/회의록이 같은 DB 행에 있어 전체 삭제 시 둘 다 사라짐

- timestamp: 2026-03-30T00:01:00Z
  checked: TranscriptsViewModel.kt deleteTranscript() (85-88행)
  found: meetingRepository.deleteMeeting(id) 호출 — 전체 삭제
  implication: UI 확인 메시지("회의록은 보존됩니다")와 실제 동작 불일치. Bug 1의 직접 원인.

- timestamp: 2026-03-30T00:01:00Z
  checked: MeetingRepositoryImpl.kt deleteMeeting() (52-66행)
  found: audioFilePath, transcriptPath, minutesPath 파일 모두 삭제 후 DB 행 DELETE
  implication: deleteTranscript()를 호출했어야 함 (92-93행: clearTranscriptPath만 초기화)

- timestamp: 2026-03-30T00:01:00Z
  checked: MinutesRepositoryImpl.kt saveMinutesToFile() (101-109행)
  found: File(minutesDir, "${meetingId}.md") — meetingId 기반 고정 파일명
  implication: 재생성 시 항상 같은 경로에 writeText() → 이전 회의록 덮어씀. Bug 2의 직접 원인.

- timestamp: 2026-03-30T00:01:00Z
  checked: MinutesGenerationWorker.kt fallback 경로 (126-133행)
  found: File("minutes/${meetingId}.md") 동일한 고정 파일명
  implication: MinutesRepositoryImpl이 주입되지 않은 경우에도 같은 문제 발생

- timestamp: 2026-03-30T00:01:00Z
  checked: TranscriptsScreen.kt meetingToRegenerate 다이얼로그 (243행)
  found: viewModel.generateMinutes(meeting.id) 호출 — 기존 회의록 삭제 없이 바로 생성 시도
  implication: 파일명이 고정이면 덮어씀, 타임스탬프 변경 후에도 이전 minutesPath 참조가 남아 있으면 부정확

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: |
  Bug 1: TranscriptsViewModel.deleteTranscript()이 meetingRepository.deleteTranscript() 대신
  meetingRepository.deleteMeeting()을 호출하여 Meeting 행 전체(오디오+전사+회의록)가 삭제됨.
  MeetingRepository에는 이미 deleteTranscript()가 올바르게 구현되어 있었으나 ViewModel이 잘못된 메서드를 호출.

  Bug 2: MinutesRepositoryImpl.saveMinutesToFile()이 "${meetingId}.md" 고정 파일명을 사용하여
  재생성 시 이전 회의록 파일을 덮어씀. 또한 재생성 다이얼로그에서 기존 회의록 삭제 없이
  바로 generateMinutes()를 호출하여 이전 파일 참조가 DB에 남는 불일치 발생 가능.

fix: |
  Bug 1: TranscriptsViewModel.deleteTranscript() 내부에서 deleteMeeting(id) → deleteTranscript(id)로 변경.

  Bug 2 (3단계):
  1. MinutesRepositoryImpl.saveMinutesToFile(): "${meetingId}.md" → "${meetingId}_${timestamp}.md"
  2. MinutesGenerationWorker fallback 경로: 동일하게 타임스탬프 포함 파일명 적용
  3. TranscriptsViewModel.regenerateMinutes() 신규 함수 추가:
     deleteMinutesOnly() 호출 후 enqueueMinutesWorker() 실행
  4. TranscriptsScreen 재생성 다이얼로그: generateMinutes() → regenerateMinutes() 교체

verification: 컴파일(./gradlew :app:compileDebugKotlin) 통과. 사용자 기기 검증 대기.

files_changed:
  - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsViewModel.kt
  - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
  - app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt
  - app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt
