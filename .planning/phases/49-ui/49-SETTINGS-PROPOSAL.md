# Phase 49: 설정 화면 재구성 수정안

**작성일:** 2026-04-05
**대상 파일:** `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` (1558줄)
**요구사항:** SET7-01 — 설정 화면의 구조와 정보 배치 분석 및 수정안 제시

---

## 1. 현재 구조 분석

### 1.1 섹션 구조 및 줄 범위

| # | 섹션 이름 | 줄 범위 | 포함 항목 |
|---|----------|---------|----------|
| 1 | 회의록 설정 | L182-391 | 완전 자동 모드 Switch, 프롬프트 템플릿 관리 버튼, 기본 프롬프트 템플릿 드롭다운, 직접 입력 프롬프트 TextField (조건부), 회의록 생성 엔진 드롭다운 |
| 2 | 전사 설정 | L396-624 | STT 엔진 드롭다운, Whisper 모델 관리 (다운로드/삭제), Groq API 키 (조건부), Deepgram API 키 (조건부), CLOVA Speech 설정 (조건부), CLOVA Summary 설정 (조건부) |
| 3 | Gemini 인증 | L629-689 | 인증 모드 RadioButton (API 키/OAuth), OAuth Client ID 입력 (OAuth 모드), Gemini API 권한 부여 버튼 (OAuth+로그인 시), API 키 입력 (API 키 모드) |
| 4 | Google 계정 & Drive | L694-748 | Google 계정 로그인/로그아웃, Google Drive 연결/해제 (로그인 시), DriveFolderSection (Drive 연결 시) — 자동 업로드 토글, 전사/회의록 폴더 선택 |

### 1.2 Helper Composables (섹션 외부)

| Composable | 줄 범위 | 용도 |
|-----------|---------|------|
| `GoogleAccountSection` | L762-856 | Google 계정 로그인/로그아웃 UI (AuthState별 when 분기) |
| `GoogleDriveSection` | L872-984 | Drive 연결/해제 UI (DriveAuthState별 when 분기) |
| `DriveFolderSection` | L986-1259 | Drive 폴더 피커 다이얼로그 + 자동 업로드 토글 + 폴더 선택 UI |
| `OAuthClientIdSection` | L1267-1369 | OAuth Web Client ID 입력/저장/삭제 |
| `ApiKeySection` | L1376-1480 | Gemini API 키 입력/검증/저장/삭제 |
| `ApiKeyInputField` | L1491-1558 | 범용 API 키 입력 재사용 composable (Groq, Deepgram, CLOVA용) |

### 1.3 State 변수 목록 (collectAsStateWithLifecycle 호출)

`SettingsScreen` composable 함수 상단 (L107-126)에서 수집하는 state 변수:

| 변수명 | 출처 (ViewModel Flow) | 사용 섹션 |
|--------|----------------------|----------|
| `templates` | `viewModel.templates` | 섹션 1 (회의록 설정) |
| `defaultTemplateId` | `viewModel.defaultTemplateId` | 섹션 1 |
| `defaultCustomPrompt` | `viewModel.defaultCustomPrompt` | 섹션 1 |
| `sttEngineType` | `viewModel.sttEngineType` | 섹션 2 (전사 설정) |
| `minutesEngineType` | `viewModel.minutesEngineType` | 섹션 1 + 섹션 2 (Deepgram 조건) |
| `hasGroqApiKey` | `viewModel.hasGroqApiKey` | 섹션 2 |
| `hasDeepgramApiKey` | `viewModel.hasDeepgramApiKey` | 섹션 2 |
| `hasClovaInvokeUrl` | `viewModel.hasClovaInvokeUrl` | 섹션 2 |
| `hasClovaSecretKey` | `viewModel.hasClovaSecretKey` | 섹션 2 |
| `hasClovaSummaryClientId` | `viewModel.hasClovaSummaryClientId` | 섹션 2 |
| `hasClovaSummaryClientSecret` | `viewModel.hasClovaSummaryClientSecret` | 섹션 2 |
| `whisperModelState` | `viewModel.whisperModelState` | 섹션 2 |
| `automationMode` | `viewModel.automationMode` | 섹션 1 |
| `authMode` | `viewModel.authMode` | 섹션 3 (Gemini 인증) |
| `authState` | `viewModel.authState` | 섹션 3 + 섹션 4 |
| `driveAuthState` | `viewModel.driveAuthState` | 섹션 4, LaunchedEffect |
| `driveTranscriptFolderId` | `viewModel.driveTranscriptFolderId` | 섹션 4 |
| `driveMinutesFolderId` | `viewModel.driveMinutesFolderId` | 섹션 4 |
| `driveAutoUploadEnabled` | `viewModel.driveAutoUploadEnabled` | 섹션 4 |
| `driveFolderPickerState` | `viewModel.driveFolderPickerState` | 섹션 4 |

### 1.4 조건부 표시 로직 (if/when 분기)

| 위치 | 조건 | 표시 내용 |
|------|------|----------|
| L210-217 | `automationMode == AutomationMode.HYBRID` | 하이브리드 모드 설명 텍스트 |
| L301-329 | `defaultTemplateId == CUSTOM_PROMPT_MODE_ID` | 직접 입력 프롬프트 TextField |
| L524-540 | `sttEngineType == SttEngineType.GROQ` | Groq API 키 입력 |
| L543-559 | `sttEngineType == DEEPGRAM \|\| minutesEngineType == DEEPGRAM` | Deepgram API 키 입력 |
| L562-591 | `sttEngineType == SttEngineType.NAVER_CLOVA` | CLOVA Speech 설정 |
| L594-623 | `minutesEngineType == MinutesEngineType.NAVER_CLOVA` | CLOVA Summary 설정 |
| L666-688 | `authMode == AuthMode.OAUTH` / else | OAuth UI 또는 API 키 UI |
| L671-684 | `authState is AuthState.SignedIn` (OAuth 모드 내부) | Gemini API 권한 부여 버튼 |
| L711-747 | `authState is AuthState.SignedIn` | Drive 연결 + 폴더 설정 전체 |
| L1010 | `driveAuthState !is DriveAuthState.Authorized` | DriveFolderSection early return |

### 1.5 LaunchedEffect / rememberLauncherForActivityResult (이동 불가)

**위치:** L132-165

```kotlin
// L132-155: driveAuthLauncher = rememberLauncherForActivityResult(...)
// L158-165: LaunchedEffect(driveAuthState) { ... driveAuthLauncher.launch(...) }
```

**이동 불가 이유:**
- `rememberLauncherForActivityResult`는 Compose에서 **무조건적(unconditional)** 호출이어야 한다. 조건부 블록 안으로 이동하면 launcher 등록/해제 타이밍이 꼬여 crash 발생 가능
- `LaunchedEffect(driveAuthState)`는 `driveAuthLauncher`를 참조하므로 함께 최상위에 위치해야 함
- **결론: 섹션 레이아웃 변경 시 L132-165 코드는 현재 위치 그대로 유지**

### 1.6 문제점 요약

1. **인증 분산**: Gemini 인증(섹션 3)과 Google 계정(섹션 4)이 분리. OAuth 모드에서는 Google 로그인이 Gemini 인증의 전제 조건인데, 사용자가 두 섹션을 오가야 함
2. **API 키 산재**: Deepgram API 키는 STT와 회의록 엔진 양쪽에서 사용되지만 "전사 설정" 섹션에만 존재
3. **Drive 설정 깊은 중첩**: Drive 폴더 설정이 "Google 계정 & Drive" > SignedIn 조건 > DriveFolderSection 3단계로 중첩. Drive 연결 완료 사용자가 폴더 변경 시 가시성 낮음
4. **섹션명 불일치**: "회의록 설정"에 자동화 모드가, "전사 설정"에 API 키가 혼재. 사용자 멘탈 모델과 불일치

---

## 2. 권장 재구성안 (Option A: 적극적 재구성)

5개 섹션으로 기능 그룹을 논리적으로 재배치한다.

### 2.1 재구성 후 섹션 구조

```
SettingsScreen (재구성 후)
├── SettingsSection("파이프라인")              # 핵심 동작 설정
│   ├── 완전 자동 모드 Switch               (현재 L184-217)
│   ├── STT 엔진 드롭다운                    (현재 L397-470)
│   ├── 회의록 생성 엔진 드롭다운             (현재 L333-389)
│   ├── 프롬프트 템플릿 관리 버튼             (현재 L222-227)
│   └── 기본 프롬프트 템플릿 + 직접 입력      (현재 L231-329)
│
├── SettingsSection("Google 계정")            # 인증 통합
│   ├── Google 계정 로그인/로그아웃           (현재 L704-708 → GoogleAccountSection)
│   ├── Gemini 인증 모드 (API 키/OAuth)       (현재 L630-661)
│   ├── OAuth Client ID (OAuth 모드 시)       (현재 L668 → OAuthClientIdSection)
│   ├── Gemini API 권한 부여 (OAuth+SignedIn) (현재 L671-684)
│   └── Gemini API 키 (API 키 모드 시)        (현재 L687 → ApiKeySection)
│
├── SettingsSection("Google Drive")           # Drive 독립 섹션
│   ├── Drive 연결/해제                      (현재 L713-719 → GoogleDriveSection)
│   ├── 자동 업로드 토글                     (현재 L1180-1194)
│   ├── 전사 파일 폴더 선택                  (현재 L1198-1222)
│   └── 회의록 폴더 선택                     (현재 L1227-1257)
│   # 조건부 가드: if (authState is AuthState.SignedIn) 전체 감싸기
│
├── SettingsSection("API 키")                 # 엔진별 API 키 분리
│   ├── Groq API 키 (sttEngineType == GROQ)  (현재 L524-540)
│   ├── Deepgram API 키 (STT/회의록 DEEPGRAM)(현재 L543-559)
│   ├── CLOVA Speech 설정 (STT NAVER_CLOVA)  (현재 L562-591)
│   └── CLOVA Summary 설정 (회의록 NAVER_CLOVA)(현재 L594-623)
│   # 엔진 미선택 시 안내 텍스트 표시
│
└── SettingsSection("모델 관리")              # 대용량 리소스
    └── Whisper 모델 다운로드/삭제            (현재 L473-519)
```

### 2.2 이동해야 할 코드 블록

| 이동 블록 | 현재 위치 | 이동 목적지 | 비고 |
|----------|----------|------------|------|
| 완전 자동 모드 Switch | L184-217 (섹션 1) | 파이프라인 섹션 상단 | 위치 유지 (이미 최상단) |
| STT 엔진 드롭다운 | L397-470 (섹션 2) | 파이프라인 섹션 (자동모드 아래) | 섹션 2에서 이동 |
| 회의록 생성 엔진 드롭다운 | L333-389 (섹션 1) | 파이프라인 섹션 (STT 아래) | 섹션 1 내 순서 변경 |
| 프롬프트 템플릿 관리 | L222-329 (섹션 1) | 파이프라인 섹션 (엔진 아래) | 위치 유지 |
| Google 계정 로그인/로그아웃 | L704-708 (섹션 4) | Google 계정 섹션 상단 | 섹션 4에서 이동 |
| Gemini 인증 모드 전체 | L629-689 (섹션 3) | Google 계정 섹션 (로그인 아래) | 섹션 3 해체 |
| Drive 연결/해제 + 폴더 | L713-747 (섹션 4) | Google Drive 섹션 | 독립 섹션으로 승격 |
| API 키 입력들 (Groq/Deepgram/CLOVA) | L524-623 (섹션 2) | API 키 섹션 | 섹션 2에서 분리 |
| Whisper 모델 관리 | L473-519 (섹션 2) | 모델 관리 섹션 | 섹션 2에서 분리 |

### 2.3 조건부 가드 변경 사항

**Google Drive 섹션 독립 시:**
현재 Drive UI는 `SettingsSection("Google 계정 & Drive")` 내부의 `if (authState is AuthState.SignedIn)` 블록 안에 있다. 독립 섹션으로 분리하면 이 조건부 가드를 Drive 섹션 전체에 적용해야 한다:

```kotlin
// 변경 전: 섹션 4 내부에 중첩
SettingsSection(title = "Google 계정 & Drive") {
    GoogleAccountSection(...)
    if (authState is AuthState.SignedIn) {
        GoogleDriveSection(...)
        DriveFolderSection(...)
    }
}

// 변경 후: Drive 독립 섹션 + 외부 가드
SettingsSection(title = "Google 계정") {
    GoogleAccountSection(...)
    // Gemini 인증 UI 이동...
}

if (authState is AuthState.SignedIn) {
    Spacer(modifier = Modifier.height(24.dp))
    SettingsSection(title = "Google Drive") {
        GoogleDriveSection(...)
        if (driveAuthState is DriveAuthState.Authorized) {
            DriveFolderSection(...)  // 기존 early return 로직 유지
        }
    }
}
```

**API 키 섹션:**
현재 각 API 키는 엔진 선택 조건으로 가드되어 있다. 이 조건을 그대로 유지하되, 모든 엔진 미선택 시 빈 섹션 방지:

```kotlin
// 하나라도 표시할 항목이 있을 때만 섹션 렌더링
val showApiKeySection = sttEngineType == SttEngineType.GROQ ||
    sttEngineType == SttEngineType.DEEPGRAM || minutesEngineType == MinutesEngineType.DEEPGRAM ||
    sttEngineType == SttEngineType.NAVER_CLOVA || minutesEngineType == MinutesEngineType.NAVER_CLOVA

if (showApiKeySection) {
    Spacer(modifier = Modifier.height(24.dp))
    SettingsSection(title = "API 키") {
        // 기존 조건부 API 키 입력 블록들 이동
    }
}
```

### 2.4 변경되지 않는 코드

| 코드 영역 | 줄 범위 | 이유 |
|----------|---------|------|
| `driveAuthLauncher` + `LaunchedEffect` | L132-165 | Compose lifecycle 제약 — 무조건적 호출 필수 |
| State 수집 (collectAsStateWithLifecycle) | L107-126 | 모든 state는 함수 상단에서 수집, 위치 변경 불필요 |
| Scaffold + TopAppBar | L167-180 | 화면 프레임 불변 |
| Helper Composables 본체 | L762-1558 | 내부 로직 변경 없음, 호출 위치만 변경 |

---

## 3. 보수적 대안 (Option B: 최소 변경)

기존 4개 섹션 구조를 유지하되, 이름 변경과 소규모 병합만 수행한다.

### 3.1 재구성 후 섹션 구조

```
SettingsScreen (보수적 재구성)
├── SettingsSection("엔진 설정")              # 기존 "회의록 설정" + "전사 설정" 합병
│   ├── 완전 자동 모드 Switch               (현재 위치 유지)
│   ├── STT 엔진 드롭다운                    (이동: 섹션 2 → 섹션 1)
│   ├── 회의록 생성 엔진 드롭다운             (현재 위치 유지)
│   ├── 프롬프트 템플릿 관리 + 기본 템플릿    (현재 위치 유지)
│   ├── 직접 입력 프롬프트 (조건부)           (현재 위치 유지)
│   ├── Whisper 모델 관리                    (이동: 섹션 2 → 섹션 1)
│   └── 엔진별 API 키 입력 (조건부)          (이동: 섹션 2 → 섹션 1)
│
├── SettingsSection("Google 계정 & 인증")     # 기존 "Gemini 인증" + "Google 계정 & Drive" 합병
│   ├── Google 계정 로그인/로그아웃           (이동: 섹션 4 → 섹션 2)
│   ├── Gemini 인증 모드 (API 키/OAuth)       (현재 섹션 3 내용)
│   ├── OAuth Client ID (OAuth 모드 시)       (현재 위치 유지)
│   ├── Gemini API 키 (API 키 모드 시)        (현재 위치 유지)
│   ├── Drive 연결/해제                      (이동: 섹션 4 → 섹션 2)
│   └── DriveFolderSection (Drive 연결 시)    (이동: 섹션 4 → 섹션 2)
│
└── (섹션 3, 4 제거 — 위의 2개 섹션으로 통합)
```

### 3.2 이동 블록

| 이동 블록 | 현재 위치 | 이동 목적지 | 비고 |
|----------|----------|------------|------|
| STT 엔진 드롭다운 | L397-470 (섹션 2) | 엔진 설정 섹션 (자동모드 다음) | 회의록 엔진 드롭다운 앞으로 |
| Whisper 모델 관리 | L473-519 (섹션 2) | 엔진 설정 섹션 (STT 엔진 아래) | STT 관련이므로 STT 직후 |
| API 키 입력들 | L524-623 (섹션 2) | 엔진 설정 섹션 하단 | 엔진 선택 후 키 입력 |
| Google 계정 로그인 | L704-708 (섹션 4) | Google 계정 & 인증 섹션 상단 | Gemini 인증보다 먼저 |
| Drive 연결 + 폴더 | L713-747 (섹션 4) | Google 계정 & 인증 섹션 하단 | 인증 후 Drive 순서 |

### 3.3 조건부 가드 변경

Option B에서는 Drive 관련 조건부 가드가 병합된 섹션 내부에 그대로 유지되므로, 가드 로직 변경이 최소화된다:

```kotlin
SettingsSection(title = "Google 계정 & 인증") {
    GoogleAccountSection(...)      // 로그인/로그아웃
    Spacer(...)
    // Gemini 인증 모드 (기존 섹션 3 내용 그대로)
    ...
    if (authState is AuthState.SignedIn) {
        // Drive 관련 (기존 섹션 4의 if 블록 그대로)
        GoogleDriveSection(...)
        DriveFolderSection(...)
    }
}
```

---

## 4. 비교표

| 기준 | Option A (적극적 재구성) | Option B (보수적 변경) |
|------|------------------------|---------------------|
| **섹션 수** | 5개 (파이프라인, Google 계정, Google Drive, API 키, 모델 관리) | 2개 (엔진 설정, Google 계정 & 인증) |
| **이동할 코드 블록 수** | ~8개 블록 | ~5개 블록 |
| **변경 줄 수 (추정)** | ~200줄 이동/재배치 | ~120줄 이동/재배치 |
| **새 조건부 가드** | 필요 (Drive 섹션 독립, API 키 섹션 빈 여부 체크) | 최소 (기존 가드 재활용) |
| **위험도** | 중간 — 조건부 가드 누락 시 UI 오류 가능 | 낮음 — 기존 구조에서 순서만 변경 |
| **사용자 경험 개선도** | 높음 — 기능별 명확한 그룹, 인증 통합, Drive 독립 | 중간 — 인증 통합은 되나 섹션 규모가 커짐 |
| **인증 분산 해소** | 완전 해소 (Google 계정 섹션에 통합) | 완전 해소 (Google 계정 & 인증에 통합) |
| **Drive 가시성 개선** | 높음 (독립 섹션) | 낮음 (여전히 인증 섹션 하위에 중첩) |
| **API 키 가시성** | 높음 (별도 섹션) | 낮음 (엔진 설정 섹션이 매우 길어짐) |

### 권장 사항

**Option A (적극적 재구성)를 권장한다.**

이유:
1. **사용자 멘탈 모델 일치**: "무엇을 할 것인가"(파이프라인) → "누구로 할 것인가"(Google 계정) → "어디에 저장할 것인가"(Drive) → "어떤 키가 필요한가"(API 키) 순서가 자연스럽다
2. **Drive 독립 섹션**: v7.0에서 Drive 연동이 핵심 기능으로 추가되었으므로, 별도 섹션으로 승격하는 것이 기능 중요도를 반영
3. **긴 섹션 방지**: Option B의 "엔진 설정"은 자동모드 + STT + 회의록 + 프롬프트 + Whisper + API 키로 매우 길어져 스크롤 부담
4. **위험도 관리 가능**: 조건부 가드 변경이 필요하지만, 컴파일 에러로 즉시 검증 가능

---

## 5. 스코프 외 사항

- **ViewModel 변경 불필요**: 모든 StateFlow와 함수 시그니처는 그대로 유지. UI 레이아웃 재배치만 수행
- **파일 분리 스코프 외**: 1558줄 단일 파일 문제는 인지하지만, SET7-01 요구사항은 "섹션 구조 재배치"이므로 파일 분리는 향후 별도 리팩토링 과제로 남긴다
- **기능 추가/변경 없음**: 순수 레이아웃 변경만 수행. 새 기능이나 동작 변경을 섞지 않는다
- **Helper Composables 내부 로직 불변**: `GoogleAccountSection`, `GoogleDriveSection`, `DriveFolderSection`, `OAuthClientIdSection`, `ApiKeySection`, `ApiKeyInputField`의 내부 구현은 변경하지 않는다. 호출 위치만 변경
