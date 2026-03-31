# Phase 19: 수동 회의록 생성 및 음성 공유 처리 - Context

**Gathered:** 2026-03-27
**Status:** Ready for planning

<domain>
## Phase Boundary

전사된 텍스트 파일에서 수동으로 회의록 생성을 트리거하는 기능과,
삼성 녹음앱에서 음성 파일이 공유될 때 저장 및 STT 전사 처리를 구현한다.

</domain>

<decisions>
## Implementation Decisions

### 수동 회의록 생성
- 전사 목록에서 전사 완료(TRANSCRIBED) 또는 실패(FAILED) 상태의 항목을 선택하면 "회의록 작성" 액션을 제공
- 회의록 작성 시 MinutesGenerationWorker를 수동으로 enqueue
- 이미 회의록이 있는 경우(COMPLETED) 재생성 여부 확인 다이얼로그 표시

### 음성 파일 공유 수신
- 삼성 녹음앱에서 audio/* MIME 타입으로 공유 시 ShareReceiverActivity에서 처리
- 음성 파일을 앱 내부 저장소(filesDir/audio/)에 복사
- MeetingEntity 생성 (source=SAMSUNG_SHARE, status=AUDIO_RECEIVED)
- TranscriptionTriggerWorker로 STT 전사 시작

### Claude's Discretion
- Worker 에러 핸들링 및 재시도 전략
- 음성 파일 포맷별 확장자 매핑 세부사항
- Toast/Snackbar 메시지 문구

</decisions>

<specifics>
## Specific Ideas

- 기존 ShareReceiverActivity의 processSharedAudio() 메서드가 이미 음성 처리 로직을 포함하고 있음
- TranscriptionTriggerWorker가 이미 구현되어 있음
- MinutesGenerationWorker가 이미 구현되어 있음
- 전사 목록(TranscriptsScreen)에서 클릭 시 편집 화면으로 이동하는 로직이 이미 있음

</specifics>

<deferred>
## Deferred Ideas

- 전사 목록 액션 메뉴 (삭제, 전사, 공유) → Phase 20
- 회의록 목록 액션 메뉴 (삭제, 공유) → Phase 21
- 설정 메뉴 이동 → Phase 22

</deferred>

---

*Phase: 19-수동-회의록-생성-및-음성-공유-처리*
*Context gathered: 2026-03-27 via conversation*
