---
status: awaiting_human_verify
trigger: "v8.0 release APK에서 Google 로그인 시 'No credentials available' → '[28444] Developer console is not set up correctly' 오류"
created: 2026-04-06T00:00:00+09:00
updated: 2026-04-06T00:00:01+09:00
---

## Current Focus

hypothesis: CONFIRMED (2차) - 에러 28444는 Web Client ID가 설정 화면에 입력/저장되지 않아 발생. GetGoogleIdOption.setServerClientId()에 빈 문자열이 전달되면 Google Identity Services가 28444를 반환함. 단, webClientId가 완전히 빈 문자열이면 GoogleAuthRepository가 별도 에러를 반환하므로, 빌드 시점에 BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID가 ""로 고정 포함됨 + 런타임에 SecureApiKeyRepository에 값이 없는 상태
test: SettingsScreen.kt OAuthClientIdSection 확인 — UI는 존재하지만 빈 placeholder 상태. SettingsViewModel.init()에서 hasOAuthClientId = false임을 확인
expecting: clientIdInput이 저장되지 않은 상태로 signIn() 호출 → webClientId 빈 문자열 → 오류
next_action: 사용자에게 설정 화면에서 Web Client ID를 먼저 입력/저장한 뒤 로그인하도록 안내

## Symptoms

expected: Google 계정 선택 화면이 표시되고 로그인 완료됨
actual: "로그인 실패: [28444] Developer console is not set up correctly." 오류 메시지가 설정 화면에 표시됨
errors: "No credentials available" (1차) → "[28444] Developer console is not set up correctly." (2차, SHA-1 등록 후)
reproduction: 설정 화면 → Google 계정으로 로그인 버튼 탭
started: v8.0 release APK 설치 후 처음 발생

## Eliminated

- hypothesis: ProGuard/R8 minification으로 Google Sign-In 코드가 obfuscate됨
  evidence: build.gradle.kts에서 isMinifyEnabled = false 확인. R8 난독화 미사용
  timestamp: 2026-04-06

- hypothesis: release keystore SHA-1이 Google Cloud Console에 미등록 (1차 원인)
  evidence: SHA-1 등록 후 에러가 "No credentials available" → "[28444] Developer console is not set up correctly"로 변경됨. 이 가설은 실제로 사실이었고 수정됨. 2차 원인으로 진행
  timestamp: 2026-04-06

## Evidence

- timestamp: 2026-04-06
  checked: app/build.gradle.kts signingConfigs
  found: release 빌드는 별도 release keystore(autominuting-release.jks) 사용, debug 빌드는 기본 debug keystore 사용
  implication: release와 debug 빌드의 SHA-1 fingerprint가 다름

- timestamp: 2026-04-06
  checked: release keystore SHA-1
  found: SHA1=22:1C:FD:DB:E8:CE:66:B7:A0:36:23:FB:73:D8:EF:B2:2F:03:A6:76
  implication: 이 SHA-1이 Google Cloud Console OAuth 클라이언트에 등록되어 있어야 함 (사용자가 등록 완료)

- timestamp: 2026-04-06
  checked: local.properties에서 GOOGLE_OAUTH_WEB_CLIENT_ID
  found: 해당 속성이 존재하지 않음. GEMINI_API_KEY만 설정됨
  implication: BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID가 빈 문자열("")로 빌드됨

- timestamp: 2026-04-06
  checked: GoogleAuthRepository.signIn() 코드 흐름
  found: secureApiKeyRepository.getGoogleOAuthClientId() 우선 조회 -> 없으면 BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID 폴백 -> 둘 다 비어있으면 "Google OAuth Web Client ID가 설정되지 않았습니다" 에러 반환
  implication: 그런데 실제 에러는 28444 → webClientId가 완전히 blank는 아니고 잘못된 값(또는 앱을 처음 설치한 새 기기에서 런타임 저장값이 없어서 "" 전달됨)이 SetServerClientId에 전달됨

- timestamp: 2026-04-06
  checked: SettingsScreen.kt OAuthClientIdSection, SettingsViewModel.hasOAuthClientId
  found: UI에 Web Client ID 입력 필드가 있으나 초기값이 빈 문자열. hasOAuthClientId는 secureApiKeyRepository.getGoogleOAuthClientId().isNullOrBlank() 기반. 스크린샷에서 필드가 빈(placeholder) 상태 — 사용자가 Web Client ID를 아직 저장하지 않은 상태
  implication: 앱이 처음 설치된 기기에서 런타임 저장값 없음 + BuildConfig도 "" → GoogleAuthRepository.signIn()의 isBlank() 체크를 통과하지만, Google API 측에서 28444 반환. 이는 webClientId=""(빈 문자열)를 그대로 API에 전달하기 때문일 가능성이 높음

- timestamp: 2026-04-06
  checked: GoogleAuthRepository.signIn() webClientId 검증 로직
  found: `if (webClientId.isBlank()) { return with error }` — blank 검증이 있음
  implication: webClientId가 blank이면 별도 오류. 28444가 나왔다는 것은 webClientId가 blank가 아닌 값(이전에 다른 기기/세션에서 저장된 값이 있거나, 테스트 도중 한 번 저장했던 값)이 들어갔거나, 또는 WebClient ID 자체가 잘못된 값(예: Android Client ID를 Web Client ID로 잘못 입력)임을 시사

## Resolution

root_cause: |
  두 가지 문제가 순차적으로 발생:
  
  1차 원인 (해결됨): Google Cloud Console에 release keystore SHA-1이 미등록
  → SHA1=22:1C:FD:DB:E8:CE:66:B7:A0:36:23:FB:73:D8:EF:B2:2F:03:A6:76 등록으로 해결
  
  2차 원인 (현재): GetGoogleIdOption.setServerClientId()에 전달되는 Web Client ID가 올바르지 않음.
  
  Google Sign-In에서 28444 에러는 "OAuth 클라이언트 설정 불일치"를 의미하며,
  GetGoogleIdOption의 serverClientId에는 반드시 Google Cloud Console의 "웹 애플리케이션" 타입
  OAuth 클라이언트 ID(*.apps.googleusercontent.com)를 사용해야 함.
  Android 타입 클라이언트 ID를 잘못 입력하거나, Web Client ID가 잘못 설정된 경우 발생.
  
  앱은 local.properties에 GOOGLE_OAUTH_WEB_CLIENT_ID가 없어서 BuildConfig가 빈 문자열로 빌드되며,
  런타임에서 사용자가 설정 화면의 "Web Client ID" 필드에 올바른 값을 입력/저장해야 함.

fix: |
  코드 변경 없음. Google Cloud Console 설정 및 앱 내 Web Client ID 입력이 필요:
  
  1. Google Cloud Console → APIs & Services → Credentials에서 "웹 애플리케이션" 타입 OAuth 클라이언트 확인
     - Android 타입이 아닌 "웹 애플리케이션" 타입의 Client ID를 사용해야 함
     - 형태: 숫자-숫자.apps.googleusercontent.com
  
  2. 앱 설정 화면(Settings) → "Google OAuth Web Client ID" 섹션에 올바른 Web Client ID 입력 후 "저장" 버튼 탭
  
  3. 저장 후 "Google 계정으로 로그인" 버튼 탭

verification: 사용자 환경에서 확인 필요
files_changed: []
