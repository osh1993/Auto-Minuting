# Auto Minuting 빠른 시작 가이드

v9.0 — 5분 안에 첫 회의록 만들기

---

## Step 1 — 앱 설치

1. [GitHub Releases](https://github.com/osh1993/Auto-Minuting/releases)에서 `AutoMinuting-v9.0-release.apk` 다운로드
2. `설정` → `생체 인식 및 보안` → `출처를 알 수 없는 앱 설치` → APK 전달 앱 → **허용**
3. 파일 관리자에서 APK 탭 → **설치**

> 이전 버전이 설치된 경우 먼저 삭제 후 설치하세요 (debug↔release 서명 차이로 덮어쓰기 불가)

---

## Step 2 — STT 엔진 선택 및 설정

`설정` 탭 → "STT 엔진" 에서 선택 후 해당 API 키 입력

| 엔진 | 특징 | 필요한 것 |
|------|------|-----------|
| **Groq Whisper** (추천) | 빠름, 무료 | Groq API 키 (25MB 제한) |
| **Gemini Flash** | 한국어 우수 | Google 계정 또는 API 키 |
| **Whisper 온디바이스** | 오프라인, 무제한 | 500MB 모델 다운로드 |
| Deepgram Nova-3 | 고정밀 | Deepgram API 키 |
| Naver CLOVA | 한국어 특화 | Naver API 키 |

### Groq API 키 발급

1. [console.groq.com](https://console.groq.com) → API Keys → Create
2. `설정` → "Groq API 키" 입력 → 저장

### Gemini (Google 계정 OAuth)

1. `설정` → 인증 방식 "Google 계정 (OAuth)" 선택
2. "Google OAuth Web Client ID" 입력 ([설정 방법](#google-계정-로그인-오류-시)) → 저장
3. "Google 계정으로 로그인" 탭

### Gemini (API 키)

1. [aistudio.google.com](https://aistudio.google.com) → "Get API key" → 키 복사
2. `설정` → 인증 방식 "API 키" 선택 → "Gemini API 키" 입력 → 저장

---

## Step 3 — 파일 가져오기

### 음성 파일 (단일)

- **삼성 녹음앱**: 파일 하나 선택 → 공유 → **Auto Minuting**
- **URL 입력**: `대시보드` → URL 필드에 음성 파일 주소 붙여넣기
- **Plaud 링크**: 카카오톡 등에서 web.plaud.ai 링크 공유 → **Auto Minuting**

### 음성 파일 (여러 개 동시)

- 삼성 녹음앱에서 **여러 파일 다중 선택** → 공유 → **Auto Minuting**
- 자동으로 하나의 파일로 합쳐진 후 전사 시작 (M4A/MP3/혼재 모두 지원, 첫 번째 파일명 사용)

### 로컬 파일 직접 선택 (v9.0 신기능)

- `대시보드` → "로컬 파일 불러오기" → **파일 불러오기** 버튼 → M4A/MP3 선택 → 제목 입력 → **확인**

### 전사 텍스트 (STT 없이 바로 회의록)

- `.txt` 파일 공유 → **Auto Minuting**
- 메모·브라우저에서 텍스트 선택 후 공유 → **Auto Minuting**

---

## Step 4 — 전사 확인

- 하단 탭 `전사` → 진행 중인 파일 확인 (배지로 상태 표시)
- 완료되면 알림이 옵니다 (알림 권한 필요: `설정 → 앱 → Auto Minuting → 알림 → 허용`)

---

## Step 5 — 회의록 생성

### 자동 모드

`설정` → "자동 회의록 생성" ON → 전사 완료 즉시 자동 생성

### 수동 모드

전사 카드의 **⋮ 메뉴** → **회의록 작성** → 프롬프트 선택 → 생성

---

## Step 6 — 결과 확인

하단 탭 `회의록` → 카드 탭 → Markdown 회의록 전문 확인

- **편집**: 카드 ⋮ 메뉴 → **편집**
- **공유**: 카드 ⋮ 메뉴 → **공유**
- **Drive 저장**: `설정` → Google Drive 연결 → Drive 자동 업로드 ON

---

## 핵심 팁

| 상황 | 해결 |
|------|------|
| 전사가 너무 느림 | 설정 → Groq 또는 Gemini STT로 변경 |
| 인터넷 없이 전사 | Whisper 모델 다운로드 후 온디바이스 전사 |
| 여러 녹음 파일 한 번에 | 삼성 녹음앱에서 다중 선택 공유 (M4A/MP3 모두 지원) |
| 기기 저장 파일 바로 전사 | 대시보드 → 파일 불러오기 (v9.0) |
| Groq 25MB 초과 파일 | v9.0부터 자동 청크 분할 처리 (별도 조작 불필요) |
| 같은 녹음으로 다른 형식 | ⋮ 메뉴 → "추가 생성" (기존 보존) |
| API 쿼터 초과 | 대시보드 쿼터 위젯 확인, 내일 자동 초기화 |
| 회의록 Drive 자동 저장 | 설정 → Google Drive 연결 → 자동 업로드 ON |

---

## Google 계정 로그인 오류 시

### "No credentials available"

→ Google Cloud Console에 Release APK SHA-1 미등록. [MANUAL.md 7장](MANUAL.md#google-계정-로그인-실패-no-credentials-available) 참조

### "[28444] Developer console is not set up correctly"

→ Web Client ID 미설정 또는 잘못된 값. 아래 절차 진행:

1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. **웹 애플리케이션** 타입 OAuth 클라이언트의 Client ID 복사
   - 없으면: Create Credentials → OAuth client ID → **Web application** 으로 생성
3. 앱 `설정` → "Google OAuth Web Client ID" 입력 → **저장**
4. "Google 계정으로 로그인" 재시도

---

*v9.0 — 상세 내용은 [MANUAL.md](MANUAL.md) 참조*
