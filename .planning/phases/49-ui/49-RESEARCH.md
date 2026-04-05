# Phase 49: 설정 UI 정비 - Research

**Researched:** 2026-04-05
**Domain:** Jetpack Compose Settings UI 재구성
**Confidence:** HIGH

## Summary

Phase 49는 v7.0에서 추가된 기능들(Google Drive, 회의록 편집, API 사용량 대시보드)을 반영하여 설정 화면의 섹션 구조를 논리적으로 재배치하는 작업이다. 현재 SettingsScreen.kt는 1558줄로, 5개 `SettingsSection`으로 구성되어 있다. v7.0 기능이 점진적으로 추가되면서 섹션 배치의 논리적 흐름이 최적이 아닌 상태이다.

현재 섹션 순서는: (1) 회의록 설정, (2) 전사 설정, (3) Gemini 인증, (4) Google 계정 & Drive 이다. 문제점은 Gemini 인증과 Google 계정이 분리되어 있어 사용자가 "인증" 관련 설정을 두 곳에서 찾아야 하는 점, Drive 업로드 폴더 설정이 "Google 계정 & Drive" 섹션 내부에 중첩되어 있어 가시성이 낮은 점이다. Phase 47(회의록 편집)과 Phase 48(API 사용량 대시보드)은 설정 화면에 별도 항목이 필요하지 않다 -- 편집은 상세 화면 내 인라인 기능이고, 대시보드는 별도 탭(DashboardScreen)에 이미 존재한다.

**Primary recommendation:** 섹션을 사용자 멘탈 모델 기준으로 재배치한다: (1) 파이프라인 동작 (자동화/엔진), (2) Google 계정 & 인증 (통합), (3) Google Drive, (4) API 키 관리.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SET7-01 | 설정 화면의 구조와 정보 배치가 분석되고 수정안이 제시된 후 승인을 거쳐 적용된다 | 현재 5개 섹션 구조 완전 분석 완료, 재구성안 아래 Architecture Patterns에 제시 |
</phase_requirements>

## Standard Stack

이 Phase는 기존 코드의 UI 재구성이므로 신규 라이브러리 추가 없음.

### Core (이미 사용 중)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose BOM | 2026.03.00 | UI 프레임워크 | 프로젝트 기존 사용 |
| Material 3 | 1.4.0 | 디자인 시스템 | 프로젝트 기존 사용 |
| Hilt Navigation Compose | BOM 연동 | ViewModel 주입 | 프로젝트 기존 사용 |

**신규 설치 불필요.**

## Architecture Patterns

### 현재 SettingsScreen.kt 구조 분석

```
SettingsScreen (1558줄, 단일 파일)
├── SettingsSection("회의록 설정")          # L182-391
│   ├── 완전 자동 모드 Switch
│   ├── 프롬프트 템플릿 관리 버튼
│   ├── 기본 프롬프트 템플릿 드롭다운
│   ├── 직접 입력 프롬프트 TextField (조건부)
│   └── 회의록 생성 엔진 드롭다운
│
├── SettingsSection("전사 설정")            # L396-624
│   ├── STT 엔진 드롭다운
│   ├── Whisper 모델 관리 (다운로드/삭제)
│   ├── Groq API 키 (조건부)
│   ├── Deepgram API 키 (조건부)
│   ├── CLOVA Speech 설정 (조건부)
│   └── CLOVA Summary 설정 (조건부)
│
├── SettingsSection("Gemini 인증")          # L629-689
│   ├── 인증 모드 RadioButton (API 키/OAuth)
│   ├── OAuth Client ID 입력 (OAuth 모드)
│   ├── Gemini API 권한 부여 버튼 (OAuth+로그인 시)
│   └── API 키 입력 (API 키 모드)
│
├── SettingsSection("Google 계정 & Drive")  # L694-748
│   ├── Google 계정 로그인/로그아웃
│   ├── Google Drive 연결/해제 (로그인 시)
│   └── DriveFolderSection (Drive 연결 시)
│       ├── 자동 업로드 토글
│       ├── 전사 파일 폴더 선택
│       └── 회의록 폴더 선택
│
└── Helper Composables
    ├── GoogleAccountSection         # L762-856
    ├── GoogleDriveSection           # L872-984
    ├── DriveFolderSection           # L986-1259
    ├── OAuthClientIdSection         # L1267-1369
    ├── ApiKeySection                # L1376-1480
    └── ApiKeyInputField             # L1491-1558
```

### 문제점 분석

1. **인증 분산**: Gemini 인증(섹션 3)과 Google 계정(섹션 4)이 분리되어 있지만, OAuth 모드에서는 Google 로그인이 Gemini 인증의 전제 조건이다. 사용자가 두 섹션을 왔다갔다해야 한다.

2. **엔진별 API 키가 "전사 설정"에 혼재**: STT 엔진 선택은 논리적으로 맞지만, Deepgram API 키는 STT와 회의록 엔진 양쪽에서 사용되므로 "전사 설정"에만 있으면 혼동된다.

3. **Drive 설정 중첩**: Drive 업로드 폴더/자동 업로드 토글이 "Google 계정 & Drive" 내부에 깊이 중첩되어, Drive 연결이 완료된 사용자가 폴더 설정을 찾기 어렵다.

4. **v7.0 기능 반영 누락**: Phase 47(편집)과 Phase 48(대시보드)은 설정 항목이 필요 없지만, SET7-01 요구사항은 "v7.0 신규 기능을 포함하여 논리적으로 재구성"이므로 전체 흐름의 논리성을 검증해야 한다.

### 권장 재구성안

```
SettingsScreen (재구성 후)
├── SettingsSection("파이프라인")            # 핵심 동작 설정을 최상단에
│   ├── 완전 자동 모드 Switch
│   ├── STT 엔진 드롭다운
│   ├── 회의록 생성 엔진 드롭다운
│   └── 프롬프트 템플릿 (관리 버튼 + 기본 템플릿 선택)
│
├── SettingsSection("Google 계정")           # 인증을 하나로 통합
│   ├── Google 계정 로그인/로그아웃
│   ├── Gemini 인증 모드 (API 키/OAuth)
│   ├── OAuth Client ID (OAuth 모드 시)
│   └── Gemini API 키 (API 키 모드 시)
│
├── SettingsSection("Google Drive")          # Drive를 독립 섹션으로 승격
│   ├── Drive 연결/해제
│   ├── 자동 업로드 토글
│   ├── 전사 파일 폴더 선택
│   └── 회의록 폴더 선택
│
├── SettingsSection("API 키")               # 엔진별 API 키를 별도 섹션으로
│   ├── Groq API 키 (GROQ 선택 시)
│   ├── Deepgram API 키 (DEEPGRAM STT/회의록 시)
│   ├── CLOVA Speech 설정 (NAVER_CLOVA STT 시)
│   └── CLOVA Summary 설정 (NAVER_CLOVA 회의록 시)
│
└── SettingsSection("모델 관리")             # 대용량 리소스 관리
    └── Whisper 모델 다운로드/삭제
```

### 대안: 최소 변경안 (보수적)

기존 섹션 순서를 유지하되, 이름과 소규모 재배치만 수행:
- "회의록 설정" + "전사 설정" 합병 -> "엔진 설정"
- "Gemini 인증" + "Google 계정 & Drive" 합병 -> "Google 계정 & 인증"
- API 키 입력 필드를 "엔진 설정" 하위로 유지

### Anti-Patterns to Avoid

- **단일 파일 1500줄 이상 유지**: 재구성 시 가능하면 섹션별 composable을 별도 파일로 extract하는 것이 유지보수에 유리하지만, SET7-01 스코프는 "구조 재배치"이지 "파일 분리"가 아니다. 파일 분리는 별도 리팩토링으로 진행.
- **ViewModel 변경 최소화**: 이 Phase는 UI 레이아웃 재배치이므로 ViewModel의 StateFlow나 함수 시그니처 변경은 불필요. 기존 ViewModel API를 그대로 사용.
- **기능 변경 혼입 금지**: 섹션 재배치 과정에서 기능 추가/변경을 섞지 않는다. 순수 레이아웃 변경만.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 섹션 헤더 스타일 | 새 헤더 composable | 기존 `SettingsSection` composable (L79-92) | 이미 재사용 가능한 패턴이 존재 |
| 조건부 섹션 표시 | 복잡한 state machine | Compose의 `if/when` 분기 | 현재 패턴이 이미 잘 동작 |

## Common Pitfalls

### Pitfall 1: 섹션 이동 시 state 수집 누락
**What goes wrong:** `SettingsSection` 블록을 이동할 때 해당 블록 내부에서 사용하는 `collectAsStateWithLifecycle()` 호출이 누락되거나, 조건부 표시 로직의 의존성이 깨진다.
**Why it happens:** 대규모 composable 코드 이동 시 참조 변수의 선언 위치를 놓친다.
**How to avoid:** 이동할 섹션이 참조하는 모든 state 변수를 먼저 식별하고, 이동 후 컴파일 에러로 검증한다.
**Warning signs:** 컴파일 에러 "Unresolved reference", 런타임 시 특정 섹션 내용이 표시 안됨.

### Pitfall 2: Drive 섹션의 조건부 표시 로직 분리
**What goes wrong:** 현재 Drive 폴더 섹션은 `if (authState is AuthState.SignedIn)` 블록 내부에 중첩되어 있다. Drive를 독립 섹션으로 분리할 때 이 조건을 빠뜨리면 로그인 안 된 상태에서 Drive UI가 노출된다.
**Why it happens:** 중첩된 조건부 로직이 섹션 외부로 이동 시 자연스럽게 해제됨.
**How to avoid:** Drive 섹션 전체를 `if (authState is AuthState.SignedIn && driveAuthState is DriveAuthState.Authorized)` 등으로 적절히 가드한다.
**Warning signs:** 로그인하지 않았는데 Drive 폴더 선택 UI가 표시됨.

### Pitfall 3: LaunchedEffect 위치 이동
**What goes wrong:** `driveAuthLauncher`와 관련된 `LaunchedEffect(driveAuthState)` (L158-165)가 컴포저블 최상위에 있어야 한다. 섹션 재배치 중 이것을 조건부 블록 내부로 이동하면 launcher 등록이 해제될 수 있다.
**Why it happens:** Compose에서 `rememberLauncherForActivityResult`는 조건부로 호출하면 안 된다.
**How to avoid:** L132-165의 launcher + LaunchedEffect 코드는 절대 이동하지 않는다. 섹션 레이아웃 변경만 수행.
**Warning signs:** "Launcher already registered" crash 또는 Drive 동의 화면이 뜨지 않음.

### Pitfall 4: 승인 프로세스 미이행
**What goes wrong:** SET7-01은 명시적으로 "수정안이 제시된 후 승인을 거쳐 적용"을 요구한다. 승인 없이 바로 적용하면 요구사항 미충족.
**Why it happens:** 개발자가 분석과 구현을 한 번에 처리하려 한다.
**How to avoid:** Plan에서 반드시 (1) 현재 상태 분석 + 수정안 제시, (2) 사용자 승인 대기, (3) 승인된 안에 따라 구현의 3단계를 명확히 분리한다.
**Warning signs:** 사용자 확인 없이 UI 변경 커밋.

## Code Examples

### 현재 SettingsSection 재사용 패턴
```kotlin
// 기존 패턴 — 그대로 유지
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Column { content() }
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
}
```

### 섹션 이동 시 조건부 가드 패턴
```kotlin
// Drive 섹션을 독립시킬 때 — 조건부 가드 필수
if (authState is AuthState.SignedIn) {
    Spacer(modifier = Modifier.height(24.dp))
    SettingsSection(title = "Google Drive") {
        GoogleDriveSection(
            driveAuthState = driveAuthState,
            onConnectDrive = { viewModel.authorizeDrive(context as Activity) },
            onRevokeDrive = { viewModel.revokeDriveAuth() }
        )
        if (driveAuthState is DriveAuthState.Authorized) {
            Spacer(modifier = Modifier.height(16.dp))
            DriveFolderSection(/* ... */)
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 단일 긴 Composable | 섹션별 extract + SettingsSection wrapper | 프로젝트 초기부터 | SettingsSection 패턴은 이미 적용됨 |

## Open Questions

1. **섹션 재배치 범위**
   - What we know: SET7-01은 "구조와 정보 배치" 분석 + 수정안 + 승인을 요구
   - What's unclear: 사용자가 보수적 변경(섹션 순서만)을 원하는지 적극적 재구성(섹션 분리/병합)을 원하는지
   - Recommendation: 수정안을 2가지(보수적/적극적) 제시하고 사용자가 선택하도록 한다

2. **파일 분리 여부**
   - What we know: 1558줄 단일 파일이 유지보수에 부담
   - What's unclear: SET7-01 스코프에 파일 분리가 포함되는지
   - Recommendation: SET7-01 스코프 외로 처리. 섹션 재배치만 수행하고 파일 분리는 향후 과제로 남긴다

## Sources

### Primary (HIGH confidence)
- 프로젝트 코드 직접 분석: SettingsScreen.kt (1558줄), SettingsViewModel.kt (657줄), UserPreferencesRepository.kt (309줄)
- REQUIREMENTS.md: SET7-01 정의 확인
- STATE.md: Phase 45-48 완료 상태 및 축적된 결정사항 확인

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 기존 프로젝트 스택 재사용, 신규 라이브러리 없음
- Architecture: HIGH - 현재 코드 전체 분석 완료, 섹션 구조 완전 파악
- Pitfalls: HIGH - Compose 라이프사이클 및 조건부 composable 관련 실제 코드 기반 분석

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (안정된 UI 재구성, 빠른 변화 없음)
