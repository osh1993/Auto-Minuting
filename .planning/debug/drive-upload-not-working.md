---
status: investigating
trigger: "Google Drive 수동/자동 업로드가 동작하지 않음"
created: 2026-04-05T00:00:00
updated: 2026-04-05T00:00:00
---

## Current Focus

hypothesis: Drive access token이 만료된 상태로 저장되어 Worker 실행 시 401 응답 → Result.failure() 반환
test: GoogleAuthRepository.getDriveAccessToken() 토큰 갱신 메커니즘 존재 여부 확인
expecting: refresh token 로직이 없으면 가설 확인
next_action: 코드 분석 완료 — 근본 원인 정리

## Symptoms

expected: 수동 업로드 시 Drive에 파일 업로드됨. 자동 업로드 활성화 시 전사/회의록 완료 후 자동으로 Drive 업로드됨.
actual: 수동, 자동 업로드 모두 동작하지 않음. Drive에 파일이 생성되지 않음.
errors: 로그 수집 불가 (adb 환경 없음)
reproduction: 앱 설치 후 Drive 권한 부여, 수동 업로드 버튼 클릭 → Drive에 파일 없음
started: Phase 46에서 구현, UAT에서 미동작 확인

## Eliminated

- hypothesis: Hilt DI OkHttpClient 충돌 (Drive용 vs OAuth용)
  evidence: AuthModule은 @Named("oauth")로 한정, RepositoryModule은 비한정. Hilt가 올바르게 구분한다.
  timestamp: 2026-04-05

- hypothesis: HiltWorkerFactory 미설정
  evidence: AutoMinutingApplication이 Configuration.Provider 구현 + HiltWorkerFactory 주입 확인됨
  timestamp: 2026-04-05

- hypothesis: WorkManager enqueue 로직 누락
  evidence: TranscriptsViewModel.uploadTranscriptToDrive(), MinutesViewModel.uploadMinutesToDrive(), TranscriptionTriggerWorker, MinutesGenerationWorker 모두 DriveUploadWorker를 올바르게 enqueue하고 있음
  timestamp: 2026-04-05

## Evidence

- timestamp: 2026-04-05
  checked: GoogleAuthRepository.getDriveAccessToken() 토큰 갱신 로직
  found: 토큰 갱신(refresh) 메커니즘이 전혀 없음. authorizeDrive() 호출 시 단 1회 access token 획득 후 EncryptedSharedPreferences에 저장. OAuth access token은 일반적으로 1시간 후 만료됨.
  implication: 인증 후 1시간 이상 경과하면 모든 Drive 업로드가 401로 실패한다.

- timestamp: 2026-04-05
  checked: DriveUploadWorker의 401 처리 로직 (line 99-103)
  found: UnauthorizedException → Result.failure() 반환. 재시도 없이 즉시 실패로 처리됨.
  implication: 만료된 토큰으로 업로드 시도 → 401 → failure → Worker 종료. 사용자에게 오류 UI 피드백도 없음.

- timestamp: 2026-04-05
  checked: DriveUploadRepository.uploadFile() Bearer 토큰 전달
  found: Request.Builder().addHeader("Authorization", "Bearer $accessToken") — 토큰이 null이 아닌 한 올바르게 전달됨
  implication: 토큰 전달 자체는 정상. 문제는 토큰의 유효성.

- timestamp: 2026-04-05
  checked: 폴더 ID 설정 UI (DriveFolderSection)
  found: 사용자가 수동으로 Google Drive 폴더 URL에서 ID를 복사하여 텍스트 필드에 입력하는 방식. 빈 문자열이면 업로드 건너뜀.
  implication: 폴더 ID 미설정 또는 잘못된 ID 입력 시 업로드가 조용히 건너뛰어질 수 있음. 그러나 "root"를 기본값으로 사용하지 않아, 사용자가 폴더 ID를 입력하지 않으면 아예 동작하지 않음.

- timestamp: 2026-04-05
  checked: DriveUploadWorker 폴더 ID 빈 문자열 처리 (line 71-74)
  found: folderId.isBlank() → Result.success() 조기 반환 (로그만 남기고 업로드 건너뜀)
  implication: 폴더 ID 미설정 시 Worker는 "성공"으로 처리되지만 실제 업로드는 수행되지 않음. 사용자는 이 사실을 알 수 없음.

## Resolution

root_cause: |
  **1차 원인 (필수 수정): OAuth access token 만료 후 갱신 메커니즘 부재**
  - Google OAuth access token은 약 1시간 후 만료됨
  - authorizeDrive()에서 단 1회 token 획득 후 저장만 하고 refresh 로직 없음
  - Worker 실행 시점에 token이 거의 확실히 만료된 상태
  - 만료 → Drive API 401 → UnauthorizedException → Result.failure() → 업로드 중단
  
  **2차 원인 (가능성): 폴더 ID 미설정**
  - 사용자가 수동으로 Drive 폴더 URL에서 ID를 복사하여 입력해야 함
  - 폴더 ID가 비어있으면 Worker는 Result.success()로 조기 반환 (silent skip)
  - 사용자가 폴더 ID를 입력하지 않았을 가능성 있음

fix: |
  (수정 전 방향 제안)
  
  1. AuthorizationClient를 Worker 내에서 재호출하여 토큰을 갱신하는 로직 추가
     - 단, AuthorizationClient는 Activity 컨텍스트가 필요하므로 Worker에서 직접 사용 불가
     - 대안: GoogleCredential/GoogleAccountCredential의 refreshToken 활용, 또는
       GoogleSignInAccount.getServerAuthCode()로 서버사이드 refresh token 획득 후 
       Worker에서 token endpoint 호출하여 새 access token 취득
  
  2. 혹은 DriveUploadWorker에서 업로드 직전에 AuthorizationClient.authorize() 가능한
     방법으로 전환 (Identity API의 authorize()는 이전에 승인된 스코프는 Activity 없이도
     토큰을 반환할 수 있음 — 재확인 필요)
  
  3. 폴더 ID 기본값을 "root"로 변경하여, 미설정 시에도 Drive 루트에 업로드되도록 함

verification: 코드 정적 분석 완료. 실기기 검증 필요.
files_changed: []
