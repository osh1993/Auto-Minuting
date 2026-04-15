---
plan: 54-02
phase: 54-home-file-input
status: complete
completed: 2026-04-15
commits:
  - b372abb feat(54-02): DashboardScreen '파일 불러오기' 버튼 + SAF 런처 + 제목 입력 AlertDialog 추가
  - 44b1f4d fix(54-02): SAF URI 파일명 추출을 ContentResolver.DISPLAY_NAME 방식으로 수정
---

## Summary

DashboardScreen의 URL 카드 내부에 "로컬 파일 불러오기" 섹션을 추가했다. SAF GetContent() 런처(audio/* MIME 필터), 제목 입력 AlertDialog, LocalFileState Idle/Processing/Error 3상태 UI를 구현하고, viewModel.processLocalFile()을 호출하여 파이프라인에 진입시킨다. 실기기에서 human-verify 체크포인트 통과(approved).

## Key Files

### Modified
- `app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt`
  - rememberLauncherForActivityResult(GetContent()) — audio/* MIME 필터
  - pendingFileUri / pendingFileTitle state
  - "로컬 파일 불러오기" 섹션 (HorizontalDivider 구분, OutlinedButton + FolderOpen 아이콘)
  - LocalFileState Idle/Processing/Error 분기 UI
  - AlertDialog — 빈 제목 시 확인 버튼 비활성, ContentResolver.DISPLAY_NAME 실제 파일명 추출
  - viewModel.processLocalFile(uri, title) 호출

## Decisions

- D-01: URL 카드 내부 통합 (별도 카드 아닌 기존 카드 하단)
- D-05 수정: ContentResolver.query(OpenableColumns.DISPLAY_NAME)로 실제 파일명 추출 (uri.lastPathSegment는 document ID 반환하여 부적합)

## Self-Check: PASSED

- [x] "파일 불러오기" 문자열 포함
- [x] rememberLauncherForActivityResult 포함
- [x] ActivityResultContracts.GetContent() 포함
- [x] filePickerLauncher.launch("audio/*") 포함
- [x] viewModel.processLocalFile( 호출 포함
- [x] AlertDialog( 포함
- [x] pendingFileUri state 포함
- [x] enabled = pendingFileTitle.isNotBlank() 포함
- [x] Icons.Default.FolderOpen 포함
- [x] HorizontalDivider 포함
- [x] compileDebugKotlin BUILD SUCCESSFUL
- [x] 실기기 human-verify: approved (Success Criteria 1~4 통과)
