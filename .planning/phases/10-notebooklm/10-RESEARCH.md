# Phase 10: NotebookLM 반자동 연동 - Research

**Researched:** 2026-03-26
**Domain:** Android Intent 공유, Chrome Custom Tabs, NotebookLM MCP 서버 통합
**Confidence:** HIGH

## Summary

Phase 10은 회의록을 NotebookLM에 전달하는 3가지 경로를 구현한다: (1) NotebookLM 앱으로 직접 공유 Intent, (2) Custom Tabs로 NotebookLM 웹 열기, (3) MCP 서버 API 통합 검토. 기존 코드베이스에 이미 ACTION_SEND Intent 패턴이 확립되어 있어 NLMK-01과 NLMK-02는 상대적으로 간단한 구현이다.

**핵심 발견:** NotebookLM 앱의 실제 패키지명은 `com.google.android.apps.labs.language.tailwind`이며, CONTEXT.md에 기재된 `com.google.android.apps.notebooklm`은 잘못된 값이다. MCP 서버는 브라우저 자동화 기반으로 동작하며 HTTP REST API를 제공하지 않으므로, 안드로이드 앱에서 직접 호출하는 것은 불가능하다. NLMK-03은 검토 문서 작성이 주요 산출물이다.

**Primary recommendation:** NotebookLM 패키지명 `com.google.android.apps.labs.language.tailwind`으로 직접 Intent 전송 구현, 미설치 시 Custom Tabs 폴백, MCP 서버는 검토 문서만 작성

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 기존 MinutesDetailScreen의 Share 버튼(ACTION_SEND)은 유지 — NotebookLM 앱이 설치되어 있으면 공유 대상에 자동 표시
- **D-02:** 별도 NotebookLM 전용 버튼을 MinutesDetailScreen TopAppBar에 추가 — NotebookLM 아이콘(또는 텍스트 버튼)
- **D-03:** 전용 버튼 클릭 시 NotebookLM 앱(com.google.android.apps.notebooklm)이 설치되어 있으면 직접 Intent 전송
- **D-04:** NotebookLM 앱 미설치 시 Custom Tabs로 notebooklm.google.com 열기 (폴백)
- **D-05:** 회의록 상세화면에서 NotebookLM 전용 버튼의 폴백으로 Custom Tabs 사용
- **D-06:** 설정화면에 "NotebookLM 열기" 링크 추가 — Custom Tabs로 notebooklm.google.com 열기
- **D-07:** androidx.browser (Custom Tabs) 의존성 추가
- **D-08:** MCP 서버 API를 통한 노트북 생성/소스 추가 실동작 통합 구현
- **D-09:** 검토 문서(MCP-REVIEW.md) 작성 — API 가능성, 제약사항, 앱 내 통합 방안 정리
- **D-10:** 타임박스 초과 시 검토 문서만으로 NLMK-03 충족 (성공 기준이 "검토 문서로 정리")

### Claude's Discretion
- NotebookLM 전용 버튼의 아이콘/디자인 (Material 3 스타일 준수)
- Custom Tabs 색상 테마 (앱 Primary Color 사용 권장)
- MCP 통합 시 에러 처리 및 인증 방식
- 공유 시 회의록 텍스트 형식 (Markdown vs plain text)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NLMK-01 | 사용자가 회의록을 NotebookLM 앱에 공유 Intent로 전달할 수 있다 | NotebookLM 패키지명 확인, Intent ACTION_SEND 패턴, 앱 설치 확인 API |
| NLMK-02 | 사용자가 앱 내에서 Custom Tabs로 NotebookLM 웹을 열 수 있다 | androidx.browser:browser:1.9.0, CustomTabsIntent.Builder 패턴 |
| NLMK-03 | NotebookLM MCP 서버 API를 통한 노트북 생성/소스 추가 기능의 앱 내 구현 가능성을 검토한다 | MCP 서버 아키텍처 분석 — 브라우저 자동화 기반, Android 직접 호출 불가 |
</phase_requirements>

## Standard Stack

### Core (이 Phase에서 새로 추가)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.browser:browser | 1.9.0 | Chrome Custom Tabs | AndroidX 공식 라이브러리, Custom Tabs 지원 |

### Supporting (기존 사용 중)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jetpack Compose + Material 3 | BOM 2026.03.00 | UI 컴포넌트 | TopAppBar IconButton 추가 |
| Material Icons Extended | BOM linked | 아이콘 | NotebookLM 버튼 아이콘 |
| Hilt | 2.59.2 | DI | 새 유틸리티 클래스 주입 (필요 시) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom Tabs | WebView | Custom Tabs가 2배 빠른 로딩, 쿠키/세션 공유 |
| 직접 Intent | createChooser | 직접 Intent는 특정 앱 타겟팅, chooser는 범용 공유 |

**Installation (libs.versions.toml 추가):**
```toml
# [versions] 섹션에 추가
browser = "1.9.0"

# [libraries] 섹션에 추가
browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
```

```kotlin
// app/build.gradle.kts dependencies에 추가
implementation(libs.browser)
```

## Architecture Patterns

### 변경 대상 파일 구조
```
app/src/main/java/com/autominuting/
├── presentation/
│   ├── minutes/
│   │   └── MinutesDetailScreen.kt    # NotebookLM 버튼 추가
│   └── settings/
│       └── SettingsScreen.kt         # NotebookLM 링크 추가
├── util/
│   └── NotebookLmHelper.kt          # [NEW] NotebookLM 연동 유틸리티
.planning/phases/10-notebooklm/
└── MCP-REVIEW.md                     # [NEW] MCP 검토 문서
```

### Pattern 1: NotebookLM 앱 설치 확인 + 직접 Intent
**What:** PackageManager를 사용해 NotebookLM 앱 설치 여부를 확인하고, 설치 시 직접 Intent 전송, 미설치 시 Custom Tabs 폴백
**When to use:** NLMK-01 + NLMK-02 구현 시

**중요:** NotebookLM 실제 패키지명은 `com.google.android.apps.labs.language.tailwind`이다.
CONTEXT.md D-03에 기재된 `com.google.android.apps.notebooklm`은 잘못된 패키지명이므로 실제 구현 시 수정 필요.

**Example:**
```kotlin
// Source: Android PackageManager + Intent 공식 문서
object NotebookLmHelper {
    /** NotebookLM 실제 패키지명 (Google Play Store 확인) */
    const val NOTEBOOKLM_PACKAGE = "com.google.android.apps.labs.language.tailwind"
    const val NOTEBOOKLM_WEB_URL = "https://notebooklm.google.com"

    /**
     * NotebookLM 앱 설치 여부를 확인한다.
     */
    fun isNotebookLmInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(NOTEBOOKLM_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 회의록을 NotebookLM에 공유한다.
     * 앱 설치 시 직접 Intent, 미설치 시 Custom Tabs 폴백.
     */
    fun shareToNotebookLm(context: Context, title: String, content: String) {
        if (isNotebookLmInstalled(context)) {
            // 직접 Intent로 NotebookLM 앱에 전송
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                setPackage(NOTEBOOKLM_PACKAGE)
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 앱이 Intent를 처리하지 못하는 경우 Custom Tabs 폴백
                openNotebookLmWeb(context)
            }
        } else {
            openNotebookLmWeb(context)
        }
    }

    /**
     * Custom Tabs로 NotebookLM 웹을 연다.
     */
    fun openNotebookLmWeb(context: Context) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(NOTEBOOKLM_WEB_URL))
    }
}
```

### Pattern 2: Custom Tabs 테마 적용
**What:** 앱의 Primary Color를 Custom Tabs 툴바에 적용
**When to use:** NLMK-02 Custom Tabs 사용 시

**Example:**
```kotlin
// Source: https://developer.chrome.com/docs/android/custom-tabs/guide-get-started
fun openNotebookLmWeb(context: Context) {
    val colorScheme = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(ContextCompat.getColor(context, R.color.primary))
        .build()

    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(colorScheme)
        .build()
    customTabsIntent.launchUrl(context, Uri.parse(NOTEBOOKLM_WEB_URL))
}
```

### Pattern 3: TopAppBar에 전용 버튼 추가 (기존 패턴 확장)
**What:** MinutesDetailScreen TopAppBar actions에 NotebookLM 아이콘 버튼 추가
**When to use:** D-02 구현 시

**Example:**
```kotlin
// 기존 MinutesDetailScreen.kt의 actions 블록 확장
actions = {
    // 기존 공유 버튼 유지
    if (minutesContent.isNotBlank()) {
        // NotebookLM 전용 버튼
        IconButton(onClick = {
            NotebookLmHelper.shareToNotebookLm(
                context = context,
                title = meeting?.title ?: "회의록",
                content = minutesContent
            )
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,  // 또는 적절한 아이콘
                contentDescription = "NotebookLM으로 보내기"
            )
        }

        // 기존 Share 버튼
        IconButton(onClick = { /* 기존 공유 로직 */ }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "공유"
            )
        }
    }
}
```

### Anti-Patterns to Avoid
- **하드코딩된 잘못된 패키지명:** `com.google.android.apps.notebooklm`을 사용하면 앱을 찾을 수 없다. 반드시 `com.google.android.apps.labs.language.tailwind` 사용
- **Custom Tabs 없이 WebView 사용:** WebView는 별도 프로세스, 쿠키 미공유, 구글 로그인 불가 문제
- **MCP 서버에 HTTP 직접 호출 시도:** MCP 서버는 브라우저 자동화 기반이므로 REST API 엔드포인트가 없다. Android 앱에서 직접 호출 불가

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 인앱 웹 브라우저 | WebView 직접 구현 | CustomTabsIntent | 쿠키 공유, 2x 빠른 로딩, Google 로그인 유지 |
| 앱 설치 확인 | Intent resolve 수동 검사 | PackageManager.getPackageInfo | 표준 API, 예외 기반 분기 |
| MCP 서버 HTTP 래퍼 | REST API 래퍼 구현 | 검토 문서만 작성 | MCP는 브라우저 자동화 기반, HTTP 미지원 |

**Key insight:** 이 Phase의 핵심은 기존 Android 표준 API(Intent, Custom Tabs)를 활용하는 것이며, 새로운 복잡한 라이브러리 도입이 필요 없다. MCP 서버 통합은 기술적 한계로 검토 문서가 주요 산출물이다.

## Common Pitfalls

### Pitfall 1: 잘못된 패키지명 사용
**What goes wrong:** `com.google.android.apps.notebooklm`으로 Intent를 보내면 ActivityNotFoundException 발생
**Why it happens:** NotebookLM은 원래 "Project Tailwind"로 개발되어 패키지명이 `com.google.android.apps.labs.language.tailwind`
**How to avoid:** 상수로 정의하여 일관되게 사용, Google Play Store에서 패키지명 확인
**Warning signs:** "앱을 찾을 수 없습니다" 에러

### Pitfall 2: NotebookLM 앱이 ACTION_SEND를 지원하지 않을 수 있음
**What goes wrong:** 패키지를 지정한 Intent가 앱에서 처리되지 않아 크래시
**Why it happens:** NotebookLM 앱이 text/plain ACTION_SEND Intent filter를 등록하지 않았을 수 있음
**How to avoid:** ActivityNotFoundException을 catch하고 Custom Tabs로 폴백. Google Play Store에서는 NotebookLM이 공유 시트에 나타난다고 언급하므로 지원할 가능성이 높지만 검증 필요
**Warning signs:** try-catch 없이 startActivity 호출

### Pitfall 3: Android 11+ 패키지 가시성 제한
**What goes wrong:** `getPackageInfo()`가 NameNotFoundException을 던지지만 실제로는 앱이 설치되어 있음
**Why it happens:** Android 11(API 30)부터 패키지 가시성 제한. 다른 앱 정보 조회 시 `<queries>` 선언 필요
**How to avoid:** AndroidManifest.xml에 `<queries>` 블록 추가
**Warning signs:** 디바이스에 NotebookLM이 설치되어 있는데도 미설치로 판정

### Pitfall 4: Custom Tabs 미지원 브라우저
**What goes wrong:** Custom Tabs 지원 브라우저가 없으면 기본 브라우저로 열림
**Why it happens:** 일부 기기에서 Chrome이 설치되지 않았거나 비활성화
**How to avoid:** CustomTabsIntent는 자동으로 기본 브라우저 폴백하므로 별도 처리 불필요. 삼성 갤럭시 기기는 삼성 인터넷 브라우저가 Custom Tabs를 지원
**Warning signs:** 특별한 처리 필요 없음 (자동 폴백)

## Code Examples

### AndroidManifest.xml queries 블록 (Android 11+ 패키지 가시성)
```xml
<!-- Source: https://developer.android.com/training/package-visibility -->
<manifest>
    <queries>
        <!-- NotebookLM 앱 설치 확인용 -->
        <package android:name="com.google.android.apps.labs.language.tailwind" />
    </queries>

    <!-- ... -->
</manifest>
```

### SettingsScreen에 NotebookLM 링크 추가
```kotlin
// 기존 SettingsScreen.kt 패턴을 따르는 NotebookLM 섹션
// --- NotebookLM 섹션 ---
Spacer(modifier = Modifier.height(24.dp))

Text(
    text = "NotebookLM",
    style = MaterialTheme.typography.titleMedium
)
Text(
    text = "NotebookLM에서 AI 기반 회의록 분석을 활용할 수 있습니다",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

Spacer(modifier = Modifier.height(8.dp))

OutlinedButton(
    onClick = { NotebookLmHelper.openNotebookLmWeb(context) }
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.MenuBook,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("NotebookLM 열기")
}
```

### MCP 검토 문서 핵심 내용 (MCP-REVIEW.md 참고용)
```markdown
## MCP 서버 분석 결과

### 아키텍처
- NotebookLM MCP 서버(notebooklm-mcp)는 브라우저 자동화(Puppeteer/Playwright) 기반
- Claude Code, Codex 등 AI 에이전트의 MCP 프로토콜(stdio)을 통해 동작
- HTTP REST API 엔드포인트 미제공

### 사용 가능한 도구 (MCP 프로토콜 경유)
- source_add: 소스 추가 (URL, 텍스트, 파일, Google Drive)
- notebook_create: 노트북 생성 (add_notebook으로도 가능)
- note_create / note_list / note_update / note_delete: 노트 관리
- ask_question: 노트북에 질문
- studio_create: 오디오/비디오 아티팩트 생성

### Android 앱 통합 가능성
- **불가:** MCP 서버는 데스크톱 브라우저 자동화 기반 — Android에서 직접 호출 불가
- **대안 1:** 별도 백엔드 서버를 구축하여 MCP 서버를 프록시 → 과도한 복잡성
- **대안 2:** NotebookLM Enterprise API (discoveryengine.googleapis.com) → 엔터프라이즈 전용
- **현재 권장:** 공유 Intent + Custom Tabs로 반자동화 유지
```

## MCP 서버 상세 분석

### 현재 환경의 MCP 서버 도구 (시스템 컨텍스트에서 확인)
프로젝트에 이미 `notebooklm-mcp` 서버가 설정되어 있으며, 다음 도구를 제공한다:
- `source_add(source_type, url/text/file_path)` — 소스 추가
- `studio_create(artifact_type)` — 아티팩트 생성
- `studio_revise` — 슬라이드 수정
- `download_artifact` — 아티팩트 다운로드
- `note_create / note_list / note_update / note_delete` — 노트 CRUD

### 핵심 제약: Android 앱에서 MCP 직접 호출 불가
1. MCP 서버는 **stdio 기반** 프로토콜 — HTTP 엔드포인트 없음
2. 내부적으로 **브라우저 자동화**(Puppeteer/Chromium)를 사용하여 notebooklm.google.com과 상호작용
3. 데스크톱 환경에서 Node.js 프로세스로 실행 — Android 런타임과 호환 불가
4. 인증은 브라우저 기반 Google 로그인 — Android 앱의 OAuth와 별개

### NotebookLM Enterprise API (공식 REST API)
- Google Cloud `discoveryengine.googleapis.com` 엔드포인트 존재
- `notebooks.create`, `notebooks.sources.uploadFile` 등 REST 메서드 제공
- **제한:** NotebookLM Enterprise 고객 전용 (Google Workspace Enterprise 구독 필요)
- 개인 NotebookLM (notebooklm.google.com)에는 적용 불가

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| NotebookLM 웹 전용 | Android/iOS 앱 출시 | 2025-05 (I/O) | 공유 Intent로 소스 추가 가능 |
| 공식 API 없음 | Enterprise API 존재 | 2025 | 엔터프라이즈 전용, 개인용은 MCP 우회 |
| MCP 별도 패키지 | notebooklm-mcp 통합 패키지 | 2026-01 | CLI + MCP 서버 통합 |

**Deprecated/outdated:**
- `com.google.android.apps.notebooklm` — 이 패키지명은 존재하지 않음. 실제: `com.google.android.apps.labs.language.tailwind`

## Open Questions

1. **NotebookLM 앱의 ACTION_SEND Intent filter 지원 여부**
   - What we know: Google은 "공유 시트에서 소스를 추가할 수 있다"고 공식 발표. Play Store 설명에도 공유 기능 언급
   - What's unclear: text/plain 타입의 ACTION_SEND를 처리하는지, 아니면 URL만 처리하는지 불확실
   - Recommendation: 구현 후 실기기 테스트로 확인. ActivityNotFoundException catch + Custom Tabs 폴백으로 안전하게 처리

2. **공유 시 텍스트 형식**
   - What we know: 회의록은 현재 Markdown으로 저장됨
   - What's unclear: NotebookLM이 Markdown 텍스트를 소스로 제대로 인식하는지
   - Recommendation: plain text로 전송 (Markdown 렌더링 마크업 제거는 불필요 — NotebookLM이 텍스트 자체를 분석). Claude's Discretion으로 구현자가 판단

## Project Constraints (from CLAUDE.md)

- **언어:** 한국어 응답, 한국어 주석, 한국어 커밋 메시지, 한국어 문서
- **코드:** 변수명/함수명 영어
- **플랫폼:** Android 네이티브 (Kotlin)
- **빌드:** AGP 9.1.0, Kotlin 2.3.20, JDK 17
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **워크플로우:** GSD 워크플로우를 통해서만 코드 변경

## Sources

### Primary (HIGH confidence)
- [Google Play Store NotebookLM](https://play.google.com/store/apps/details?id=com.google.android.apps.labs.language.tailwind) — 패키지명 확인
- [Chrome Custom Tabs 공식 가이드](https://developer.chrome.com/docs/android/custom-tabs/guide-get-started) — Custom Tabs 구현
- [androidx.browser 릴리즈](https://developer.android.com/jetpack/androidx/releases/browser) — 최신 버전 1.9.0 확인
- [Android 패키지 가시성](https://developer.android.com/training/package-visibility) — queries 블록 필요

### Secondary (MEDIUM confidence)
- [NotebookLM MCP (GitHub)](https://github.com/PleasePrompto/notebooklm-mcp) — MCP 서버 아키텍처 확인
- [NotebookLM Enterprise API (Google Cloud)](https://docs.cloud.google.com/gemini/enterprise/notebooklm-enterprise/docs/api-notebooks-sources) — Enterprise API 존재 확인
- [9to5Google NotebookLM 앱 발표](https://9to5google.com/2025/05/19/notebooklm-app-launch/) — 앱 공유 기능 지원 확인

### Tertiary (LOW confidence)
- NotebookLM 앱의 ACTION_SEND intent filter 세부 사항 — 공식 문서 미확인, 실기기 테스트 필요

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — androidx.browser는 안정적 AndroidX 라이브러리, 버전 확인 완료
- Architecture: HIGH — 기존 코드 패턴(TopAppBar IconButton, ACTION_SEND Intent) 확인, 확장만 필요
- Pitfalls: HIGH — Android 패키지 가시성, 패키지명 오류 등 주요 위험 요인 식별 완료
- MCP 통합: HIGH — 기술적 한계 명확히 확인 (브라우저 자동화 기반, Android 직접 호출 불가)

**Research date:** 2026-03-26
**Valid until:** 2026-04-26 (안정적 API, 패키지명은 변경 가능성 있으나 드묾)
