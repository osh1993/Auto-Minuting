---
phase: 47-minutes-edit
plan: 47-01
subsystem: presentation/minutes
tags: [minutes-edit, viewmodel, compose, navigation, room]
dependency_graph:
  requires: []
  provides: [MinutesEditViewModel, MinutesEditScreen, MinutesEdit-route]
  affects: [MinutesDetailScreen, AppNavigation, Screen, MinutesDao, MinutesDataRepository]
tech_stack:
  added: []
  patterns: [TranscriptEdit-pattern, HiltViewModel, StateFlow, SharedFlow, Dispatchers.IO]
key_files:
  created:
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesEditViewModel.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesEditScreen.kt
  modified:
    - app/src/main/java/com/autominuting/data/local/dao/MinutesDao.kt
    - app/src/main/java/com/autominuting/domain/repository/MinutesDataRepository.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesDataRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/Screen.kt
    - app/src/main/java/com/autominuting/presentation/navigation/AppNavigation.kt
decisions:
  - TranscriptEdit 패턴을 그대로 복사하여 Minutes용으로 변환함 (일관성 유지)
  - saveMinutes()에서 파일 I/O를 Dispatchers.IO에서 실행하여 ANR 방지
  - Edit 버튼을 minutesContent.isNotBlank() 조건 안에서만 표시하여 빈 회의록 편집 방지
metrics:
  duration: ~15분
  completed_date: 2026-04-03
  tasks: 3
  files_changed: 8
---

# Phase 47 Plan 01: 회의록 편집 기능 구현 Summary

**한 줄 요약:** TranscriptEdit 패턴을 Minutes에 적용한 인앱 회의록 편집 기능 — DAO updateUpdatedAt, MinutesEditViewModel(StateFlow/IO), MinutesEditScreen(OutlinedTextField/AlertDialog/Snackbar), Navigation 연결 완성

## 구현 내용

### Task 1: 데이터 계층 확장 (커밋: ec9b405)

| 파일 | 변경 내용 |
|------|-----------|
| MinutesDao.kt | `updateUpdatedAt(id, updatedAt)` 쿼리 추가 |
| MinutesDataRepository.kt | `updateMinutesUpdatedAt(id, updatedAt)` 인터페이스 선언 추가 |
| MinutesDataRepositoryImpl.kt | `updateMinutesUpdatedAt` 구현 추가 |

### Task 2: MinutesEditViewModel (커밋: 28022c9)

| 멤버 | 역할 |
|------|------|
| `minutesId: Long` | SavedStateHandle["minutesId"] 추출 |
| `minutes: StateFlow<Minutes?>` | 회의록 정보 구독 |
| `editText: StateFlow<String>` | 편집 중 텍스트 |
| `originalText: String` | 변경 감지용 원본 텍스트 |
| `isSaving: StateFlow<Boolean>` | 저장 중 상태 |
| `saveSuccess: SharedFlow<Boolean>` | 저장 성공/실패 이벤트 |
| `hasChanges()` | 편집 여부 확인 |
| `updateText(newText)` | 텍스트 업데이트 |
| `saveMinutes()` | 파일 쓰기 + DB updatedAt 갱신 |

### Task 3: UI + Navigation 연결 (커밋: e58688d)

**MinutesEditScreen.kt (신규)**
- OutlinedTextField: weight(1f), fillMaxWidth, padding(16.dp)
- TopAppBar: 뒤로가기(hasChanges 확인) + Save 아이콘 / CircularProgressIndicator
- AlertDialog: "변경 사항을 저장하시겠습니까?" / 저장 / 저장하지 않음
- LaunchedEffect(Unit): saveSuccess.collect → Snackbar

**MinutesDetailScreen.kt (수정)**
- `onEditClick: () -> Unit = {}` 파라미터 추가
- `minutesContent.isNotBlank()` 블록 최상단에 Edit IconButton 삽입

**Screen.kt (수정)**
- `MinutesEdit` data object 추가 (route: "minutes/{minutesId}/edit")

**AppNavigation.kt (수정)**
- MinutesDetail composable에 `onEditClick` 전달
- MinutesEdit composable 신규 등록

## 빌드 결과

```
./gradlew assembleDebug → BUILD SUCCESSFUL in 42s
```

경고: `Icons.Filled.List` deprecated (기존 코드, 이번 변경과 무관)

## Deviations from Plan

없음 — 계획대로 정확히 실행됨.

## Known Stubs

없음 — 모든 파일 읽기/쓰기 및 DB 연동이 실제로 구현됨.

## Self-Check: PASSED

- MinutesEditViewModel.kt: FOUND
- MinutesEditScreen.kt: FOUND
- MinutesDao.updateUpdatedAt: FOUND (line 83)
- MinutesDataRepository.updateMinutesUpdatedAt: FOUND (line 41)
- Screen.MinutesEdit: FOUND (line 64)
- AppNavigation MinutesEditScreen: FOUND (line 117)
- 커밋 ec9b405: FOUND
- 커밋 28022c9: FOUND
- 커밋 e58688d: FOUND
