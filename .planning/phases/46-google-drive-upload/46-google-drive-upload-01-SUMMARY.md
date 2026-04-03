---
phase: 46-google-drive-upload
plan: "01"
subsystem: data-drive
tags: [drive, okhttp, datastore, repository, hilt]
dependency_graph:
  requires: []
  provides: [DriveUploadRepository, UnauthorizedException, DRIVE_TRANSCRIPT_FOLDER_KEY, DRIVE_MINUTES_FOLDER_KEY]
  affects: [UserPreferencesRepository, RepositoryModule]
tech_stack:
  added: []
  patterns: [OkHttp multipart/related, Hilt @Singleton @Inject constructor, DataStore stringPreferencesKey]
key_files:
  created:
    - app/src/main/java/com/autominuting/data/drive/DriveUploadRepository.kt
  modified:
    - app/src/main/java/com/autominuting/data/preferences/UserPreferencesRepository.kt
decisions:
  - "DriveUploadRepository는 @Singleton @Inject constructor이므로 RepositoryModule에 별도 바인딩 불필요 — Hilt 자동 처리"
  - "multipart/related 바디는 ByteArray fold 패턴으로 구성"
metrics:
  duration: 5m
  completed: 2026-04-03
  tasks_completed: 2
  files_changed: 2
---

# Phase 46 Plan 01: Drive 업로드 인프라 레이어 Summary

**한 줄 요약:** OkHttp multipart/related Drive REST API 클라이언트 + DataStore 폴더 ID 키 2개로 Drive 업로드 인프라 완성

## 완료된 작업

### Task 1: DriveUploadRepository 생성
- `DriveUploadRepository.kt` 신규 생성 (`data/drive/` 패키지)
- `UnauthorizedException` 같은 파일에 선언
- `uploadFile()`: `withContext(Dispatchers.IO)` + OkHttp multipart/related 요청
- `buildMultipartBody()`: ByteArray fold 패턴으로 메타데이터 + 파일 파트 구성
- `parseFileId()`: `org.json.JSONObject.optString("id")` — 추가 의존성 없음
- 401 → `UnauthorizedException`, 그 외 → `IOException`, 예외 → `Result.failure(e)`

### Task 2: UserPreferencesRepository 확장
- `DRIVE_TRANSCRIPT_FOLDER_KEY` / `DRIVE_MINUTES_FOLDER_KEY` companion object에 추가
- `driveTranscriptFolderId` / `driveMinutesFolderId` Flow 프로퍼티 추가 (기본값 `""`)
- `setDriveTranscriptFolderId()` / `setDriveMinutesFolderId()` suspend 저장 메서드 추가
- `getDriveTranscriptFolderIdOnce()` / `getDriveMinutesFolderIdOnce()` suspend 즉시 조회 메서드 추가
- RepositoryModule 수정 없음 — `@Singleton @Inject constructor`로 Hilt 자동 바인딩 확인

## Deviations from Plan

None - plan executed exactly as written.

단, Task 2 플랜에서 언급한 RepositoryModule companion object 패턴은 DriveUploadRepository가 `@Singleton @Inject constructor`를 가지므로 불필요함을 확인하고 건너뜀.

## 빌드 결과

`./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL

## Self-Check: PASSED

- DriveUploadRepository.kt: FOUND
- UserPreferencesRepository.kt DRIVE_TRANSCRIPT_FOLDER_KEY: FOUND
- UserPreferencesRepository.kt getDriveTranscriptFolderIdOnce: FOUND
- 커밋 3c38014: FOUND
