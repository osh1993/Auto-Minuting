---
status: fixing
trigger: "전사 목록 삭제 안됨 + Whisper 전사 오류 (이전 세션 변경으로 인한 리그레션)"
created: 2026-03-30T00:00:00Z
updated: 2026-03-30T00:01:00Z
symptoms_prefilled: true
---

## Current Focus

hypothesis: [BUG1 확인] deleteTranscript()가 DB row를 삭제하지 않고 transcriptPath=NULL + 상태=AUDIO_RECEIVED로 되돌림. AUDIO_RECEIVED는 TRANSCRIPT_VISIBLE_STATUSES에 포함되므로 목록에서 사라지지 않음. [BUG2 확인] whisper_jni.cpp에서 청크 루프가 동일한 g_ctx(global whisper_context)에 여러 번 whisper_full()을 호출함. whisper_full()은 호출마다 컨텍스트의 내부 상태(past KV cache)를 축적하므로, 두 번째 청크부터는 이전 청크 잔존 상태가 간섭하여 오류/왜곡 발생 가능. no_context=true로 설정되어 있으나, 이것은 텍스트 컨텍스트(prompt prefix)만 비활성화하고 내부 audio 처리 상태는 별도임. 또한 전체 오디오를 이미 메모리에 pcmf32로 로드하므로 청크 분할 자체가 불필요함.
test: 코드 직접 읽기로 확인 완료
expecting: 두 버그 모두 수정 후 정상 동작
next_action: 두 파일 수정 적용

## Symptoms

### Bug 1: 전사 목록 삭제 안됨
expected: 전사 목록에서 삭제 메뉴 선택 → 해당 항목이 목록에서 제거됨
actual: 삭제를 시도해도 항목이 목록에서 사라지지 않음. 아무 동작이 없거나 오류 발생.
errors: 미확인
reproduction: 전사 목록에서 항목 삭제 메뉴 선택
started: 이전 디버그 세션에서 deleteMeeting() → deleteTranscript()로 변경 이후

### Bug 2: Whisper 전사 오류
expected: Whisper 엔진으로 음성 파일 전사 정상 완료
actual: 전사 중 오류 발생
errors: 정확한 오류 메시지 미확인
reproduction: Whisper 엔진으로 전사 시작
started: 이전 디버그 세션에서 whisper_jni.cpp를 30초 청크 루프 방식으로 수정 이후

## Eliminated

(없음)

## Evidence

- timestamp: 2026-03-30T00:01:00Z
  checked: TranscriptsViewModel.kt — deleteTranscript() 호출부
  found: meetingRepository.deleteTranscript(id) 호출 정상. ViewModel 코드에는 문제 없음.
  implication: 버그는 Repository 구현체 또는 DAO 레벨에 있음

- timestamp: 2026-03-30T00:01:00Z
  checked: MeetingRepository.kt — 인터페이스
  found: deleteTranscript(id: Long) 인터페이스 존재. suspend fun으로 선언되어 있음.
  implication: 인터페이스 자체는 정상

- timestamp: 2026-03-30T00:01:00Z
  checked: MeetingRepositoryImpl.kt — deleteTranscript() 구현
  found: 전사 파일만 삭제(File.delete()) 후 meetingDao.clearTranscriptPath() 호출. DB row를 삭제하지 않고 transcriptPath=NULL, 상태=AUDIO_RECEIVED로 업데이트함.
  implication: AUDIO_RECEIVED가 TRANSCRIPT_VISIBLE_STATUSES에 포함되어 있으므로 항목이 목록에서 사라지지 않음. 이것이 Bug 1의 근본 원인.

- timestamp: 2026-03-30T00:01:00Z
  checked: MeetingDao.kt — TRANSCRIPT_VISIBLE_STATUSES (TranscriptsViewModel.kt)
  found: AUDIO_RECEIVED, TRANSCRIBING, TRANSCRIBED, GENERATING_MINUTES, COMPLETED, FAILED 모두 가시 목록에 포함됨
  implication: clearTranscriptPath 이후 상태가 AUDIO_RECEIVED가 되어도 목록에 계속 표시됨

- timestamp: 2026-03-30T00:01:00Z
  checked: whisper_jni.cpp — 청크 루프 구조
  found: pcmf32 전체를 메모리에 로드한 뒤 30초 단위로 나눠 동일 g_ctx에 whisper_full()을 반복 호출함. no_context=true는 텍스트 prefix만 비활성화하고 내부 audio 처리 상태는 청크 간에 공유됨. 또한 진행률 콜백이 params에 등록되지 않고 직접 호출되는 방식이었음.
  implication: 두 번째 청크부터 첫 청크의 잔존 내부 상태가 간섭하여 오류 또는 왜곡 발생. 이것이 Bug 2의 근본 원인.

## Eliminated

- hypothesis: deleteTranscript()가 인터페이스에 없거나 빈 구현일 것
  evidence: 인터페이스와 구현체 모두 존재하며 코드가 실행됨. 다만 구현 로직이 잘못됨.
  timestamp: 2026-03-30T00:01:00Z

- hypothesis: DAO의 clearTranscriptPath()가 존재하지 않을 것
  evidence: MeetingDao.kt에 clearTranscriptPath() 정상 정의되어 있음
  timestamp: 2026-03-30T00:01:00Z

## Resolution

root_cause: |
  [Bug 1] deleteTranscript()가 DB row를 삭제하지 않고 transcriptPath=NULL + 상태=AUDIO_RECEIVED로만 업데이트함.
  AUDIO_RECEIVED는 TRANSCRIPT_VISIBLE_STATUSES에 포함되어 있어 항목이 목록에서 사라지지 않음.
  수정: DB row 자체를 meetingDao.delete(id)로 삭제하고, 오디오/전사 파일도 함께 삭제. 회의록 파일(.md)은 보존.

  [Bug 2] whisper_jni.cpp의 청크 루프가 동일 g_ctx에 whisper_full()을 반복 호출하여
  내부 상태 축적으로 두 번째 청크부터 오류/왜곡 발생. 또한 전체 오디오를 이미 메모리에 로드한
  상태에서 청크 분할은 불필요함.
  수정: 청크 루프 제거, 단일 whisper_full() 호출로 복원. 진행률 콜백을 params에 정상 등록.

fix: |
  [Bug 1] MeetingRepositoryImpl.kt — deleteTranscript():
    - clearTranscriptPath() 대신 meetingDao.delete(id) 호출
    - 오디오 파일 + 전사 파일 삭제 (회의록 파일 보존)

  [Bug 2] whisper_jni.cpp:
    - 청크 루프(for chunk_idx) 제거
    - whisper_full(g_ctx, params, pcmf32.data(), total_samples) 단일 호출
    - 진행률 콜백을 params.progress_callback에 정상 등록

verification: 수정 코드 검토 완료. 인간 검증 대기 중.
files_changed:
  - app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt
  - app/src/main/cpp/whisper_jni.cpp
