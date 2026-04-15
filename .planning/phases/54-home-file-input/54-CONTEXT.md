---
phase: 54
name: 홈 화면 파일 직접 입력
created: 2026-04-15
requirements: INPUT-01, INPUT-02
---

# Phase 54: 홈 화면 파일 직접 입력 - Context

**Gathered:** 2026-04-15
**Status:** Ready for planning

<domain>
## Phase Boundary

DashboardScreen('대시보드' 탭)에 '파일 불러오기' 버튼을 추가하여, 사용자가 로컬 음성 파일(M4A/MP3)을 SAF 파일 피커로 선택하면 기존 STT → 회의록 파이프라인이 자동 실행된다.

- **범위 내**: DashboardScreen UI 추가, DashboardViewModel 처리 로직, SAF ActivityResult 등록, Meeting insert, TranscriptionTriggerWorker enqueue
- **범위 외**: 다중 파일 선택(REQUIREMENTS.md Future), 클라우드 파일 직접 입력(Out of Scope), ShareReceiverActivity 수정

</domain>

<decisions>
## Implementation Decisions

### 버튼 위치 & 레이아웃
- **D-01:** 기존 'URL에서 음성 다운로드' 카드(secondaryContainer) 안에 '파일 불러오기' 버튼을 추가한다. 별도 카드 생성 없음.
- **D-02:** 버튼 배치: URL 입력 필드 + '다운로드 시작' 버튼 아래에 `OutlinedButton("파일 불러오기")` 추가. `HorizontalDivider` 또는 `Spacer`로 시각적 구분.
- **D-03:** 아이콘: `Icons.Default.FolderOpen` 또는 `Icons.Default.AttachFile` (planner 재량)

### Meeting 제목 결정
- **D-04:** 파일 선택 후 제목 입력 다이얼로그(AlertDialog 또는 BottomSheet)를 표시한다.
- **D-05:** 다이얼로그 기본값: 선택한 파일명(확장자 제외). 예: `recording_20240415.m4a` → 기본값 `recording_20240415`.
- **D-06:** 사용자가 제목을 수정하거나 그대로 확인하면 파이프라인이 시작된다. 취소 시 아무 동작 없음.
- **D-07:** 입력 필드가 비어있으면 '확인' 버튼 비활성화.

### 파일 처리 로직 진입점
- **D-08:** `DashboardViewModel`에 `processLocalFile(uri: Uri, title: String)` 메서드를 직접 구현한다. `ShareReceiverActivity.processSharedAudio()` 패턴을 참조하여 동일 로직 적용.
- **D-09:** Meeting.source 값: `"LOCAL_FILE"` (기존 "SAMSUNG_SHARE", "PLAUD_BLE"와 동일 패턴)
- **D-10:** SAF 파일 피커는 `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` 로 등록. MIME type: `"audio/*"` (M4A/MP3 모두 포함).
- **D-11:** 파일 처리 상태는 기존 `DownloadState` sealed interface를 재사용하거나 별도 `LocalFileState`로 분리 (planner 재량). 핵심은 기존 파이프라인 배너가 정상 표시되어야 함.
- **D-12:** Content URI → 앱 내부 저장소 복사는 `ShareReceiverActivity`와 동일 방식 (ContentResolver.openInputStream → File.copyTo).

### Claude's Discretion
- 제목 입력 UI: AlertDialog vs BottomSheet — 기존 코드베이스 패턴 참고 (ManualMinutesDialog가 AlertDialog 방식이므로 동일 패턴 권장)
- DownloadState 재사용 vs LocalFileState 신규 sealed class — 코드 복잡도와 가독성 기준으로 결정
- 버튼 아이콘 선택

</decisions>

<specifics>
## Specific Ideas

- URL 카드 안에 통합: "로컬 파일 입력"과 "URL 다운로드" 두 가지 입력 방식이 한 카드에서 제공되어 화면 스크롤 길이를 줄임.
- 제목 입력 다이얼로그: 기존 `ManualMinutesDialog` 구현 방식(AlertDialog 내 OutlinedTextField) 참조.
- 파이프라인 시작 후: 기존 파이프라인 배너(`activePipeline`)가 자동으로 상태를 표시하므로 별도 성공 UI 불필요.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 핵심 수정 대상
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt` — UI 수정 대상 (URL 카드에 버튼 추가, 제목 입력 다이얼로그)
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt` — processLocalFile() 추가 대상
- `app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt` — DashboardScreen 호출부 (변경 없을 수 있으나 확인 필요)

### 참조 패턴 (로직 복사 소스)
- `app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt` — processSharedAudio() 로직 패턴 (Meeting insert + TranscriptionTriggerWorker enqueue)
- `app/src/main/java/com/autominuting/presentation/transcripts/TranscriptsScreen.kt` — ManualMinutesDialog 사용 패턴 (제목 입력 다이얼로그 참조)

### 도메인 모델
- `app/src/main/java/com/autominuting/domain/model/Meeting.kt` — source 필드 확인 ("LOCAL_FILE" 추가)
- `app/src/main/java/com/autominuting/domain/repository/MeetingRepository.kt` — insertMeeting() 시그니처
- `.planning/REQUIREMENTS.md` — INPUT-01, INPUT-02 요구사항

</canonical_refs>

<deferred>
## Deferred Ideas

- **다중 파일 선택**: REQUIREMENTS.md Future 항목 ("파일 입력 시 다중 파일 선택 지원") — 이번 Phase는 단일 파일만.
- **클라우드 파일 직접 입력**: Out of Scope — 로컬 파일만 지원.

</deferred>

---

*Phase: 54-home-file-input*
*Context gathered: 2026-04-15 via /gsd:discuss-phase*
