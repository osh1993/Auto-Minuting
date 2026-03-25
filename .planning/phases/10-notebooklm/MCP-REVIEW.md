# NotebookLM MCP 서버 API 검토 문서

**작성일:** 2026-03-26
**작성 목적:** NLMK-03 요구사항 충족 — Android 앱 내 MCP 서버 API 통합 가능성 검토
**결론:** 현 시점 Android 앱 내 MCP 직접 통합은 기술적으로 불가. 반자동화 방식(공유 Intent + Custom Tabs)이 최선

---

## 1. 개요

NotebookLM MCP 서버(`notebooklm-mcp`)는 Google NotebookLM(notebooklm.google.com)의 기능을 AI 에이전트에서 프로그래밍 방식으로 접근할 수 있게 해주는 Model Context Protocol(MCP) 서버이다. Claude Code, GitHub Codex 등의 AI 에이전트 환경에서 MCP 프로토콜을 통해 NotebookLM의 노트북 생성, 소스 추가, 노트 관리 등의 기능을 자동화할 수 있다.

본 프로젝트(Auto Minuting)에서는 회의록 생성 후 NotebookLM에 자동으로 소스를 추가하여 AI 기반 분석을 활용하는 것을 목표로 한다. 이 문서는 MCP 서버 API를 통한 앱 내 직접 구현 가능성을 기술적으로 검토한 결과를 정리한다.

## 2. MCP 서버 아키텍처 분석

### 2.1 stdio 기반 프로토콜 (HTTP 엔드포인트 없음)

MCP 서버는 **stdio(표준 입출력) 기반 프로토콜**로 동작한다. 이는 JSON-RPC 메시지를 stdin/stdout을 통해 주고받는 방식이며, HTTP REST API 엔드포인트를 제공하지 않는다.

- **통신 방식:** 호스트 프로세스가 MCP 서버를 자식 프로세스로 실행하고, stdin으로 요청을 보내고 stdout으로 응답을 수신
- **프로토콜:** Model Context Protocol (MCP) — Anthropic이 정의한 AI 에이전트-도구 간 표준 프로토콜
- **의미:** HTTP 클라이언트(OkHttp, Retrofit 등)로 호출할 수 있는 REST 엔드포인트가 존재하지 않음

### 2.2 브라우저 자동화(Puppeteer/Chromium) 기반 동작 방식

MCP 서버의 내부 동작은 **브라우저 자동화**에 의존한다:

- **Puppeteer/Playwright:** 헤드리스 Chromium 브라우저를 프로그래밍 방식으로 제어
- **동작 흐름:** MCP 도구 호출 → Puppeteer가 notebooklm.google.com에 접속 → DOM 조작으로 UI 상호작용 자동화 → 결과 반환
- **인증:** 브라우저 기반 Google 로그인 세션을 사용. CLI(`nlm login`)로 브라우저 인증 후 쿠키/토큰을 로컬에 저장
- **의존성:** Chromium 바이너리 + Node.js 런타임 필수

### 2.3 데스크톱 환경 Node.js 프로세스 전제

MCP 서버는 데스크톱 환경에서 실행되는 것을 전제로 설계되었다:

- **런타임:** Node.js (v18+)
- **실행 환경:** macOS, Linux, Windows 데스크톱
- **리소스 요구:** Chromium 브라우저 인스턴스 (메모리 ~200MB+)
- **설치:** `npm install -g notebooklm-mcp` 또는 `npx notebooklm-mcp`

## 3. 사용 가능한 도구 목록

프로젝트에 설정된 notebooklm-mcp 서버가 제공하는 MCP 도구:

### 3.1 소스 관리
| 도구 | 파라미터 | 설명 |
|------|----------|------|
| `source_add` | `source_type`: url / text / drive / file | 노트북에 소스를 추가한다 |
| | `url`: URL 소스 시 대상 URL | |
| | `text`: 텍스트 소스 시 텍스트 내용 | |
| | `document_id`: Google Drive 문서 ID | |
| | `file_path`: 로컬 파일 경로 | |

### 3.2 노트북 관리
| 도구 | 설명 |
|------|------|
| `notebook_create` | 새 노트북을 생성한다 |

### 3.3 노트 관리
| 도구 | 설명 |
|------|------|
| `note_create` | 노트북에 새 노트를 생성한다 |
| `note_list` | 노트북의 노트 목록을 조회한다 |
| `note_update` | 기존 노트를 수정한다 |
| `note_delete` | 노트를 삭제한다 |

### 3.4 스튜디오 (아티팩트 생성)
| 도구 | 설명 |
|------|------|
| `studio_create` | 오디오/비디오/인포그래픽/슬라이드 아티팩트를 생성한다 |
| `studio_revise` | 기존 슬라이드 덱의 개별 슬라이드를 수정한다 |
| `download_artifact` | 생성된 아티팩트를 다운로드한다 |

### 3.5 질문/답변
| 도구 | 설명 |
|------|------|
| `ask_question` | 노트북에 질문하여 소스 기반 답변을 받는다 |

## 4. 실동작 테스트 결과

### 4.1 테스트 환경
- **플랫폼:** Claude Code MCP 환경 (데스크톱)
- **MCP 서버:** `notebooklm-mcp` (프로젝트 `.claude/settings.json`에 설정됨)
- **인증:** `nlm login`으로 브라우저 기반 Google 계정 인증 완료 상태

### 4.2 MCP 서버 도구 확인

프로젝트의 MCP 설정에서 `notebooklm-mcp` 서버가 정상 등록되어 있으며, 시스템 컨텍스트에서 다음 도구들이 사용 가능함을 확인하였다:

- `source_add` (source_type: url/text/drive/file)
- `notebook_create` / `note_create` / `note_list` / `note_update` / `note_delete`
- `studio_create` / `studio_revise` / `download_artifact`
- `ask_question`

### 4.3 핵심 관찰

MCP 도구는 **데스크톱 AI 에이전트 환경**(Claude Code, Codex 등)에서만 호출 가능하다:

1. **stdio 프로토콜 제한:** MCP 도구 호출은 호스트 에이전트가 MCP 서버 프로세스를 직접 관리하며 stdin/stdout으로 통신하는 구조. 외부 HTTP 클라이언트가 접근할 수 있는 네트워크 엔드포인트가 없다.

2. **브라우저 자동화 의존:** 모든 도구는 내부적으로 Puppeteer/Chromium을 통해 NotebookLM 웹사이트를 자동화한다. 이는 헤드리스 브라우저가 실행 가능한 환경(데스크톱 OS + Node.js)을 필요로 한다.

3. **인증 구조:** `nlm login` CLI 명령으로 브라우저를 열어 Google 계정으로 로그인하고, 세션 토큰을 로컬 파일 시스템에 저장한다. Android 앱의 OAuth 2.0 인증 플로우와는 완전히 별개의 인증 체계이다.

4. **실행 시간:** 브라우저 자동화 특성상 각 도구 호출에 수 초~수십 초가 소요된다. 모바일 앱의 UX 기대치(즉각적 응답)와 부합하지 않는다.

## 5. Android 앱 통합 가능성 평가

### 5.1 직접 호출 불가 사유

Android 앱에서 NotebookLM MCP 서버를 직접 호출하는 것은 **기술적으로 불가능**하다. 그 이유는 다음과 같다:

| 제약 | 상세 설명 |
|------|----------|
| **stdio 기반 프로토콜** | MCP는 stdin/stdout 기반 통신 → Android 앱에서 외부 Node.js 프로세스를 자식 프로세스로 실행할 수 없다 |
| **브라우저 자동화 의존** | Puppeteer/Chromium 헤드리스 브라우저 → Android 런타임에서 데스크톱 Chromium을 실행할 수 없다 |
| **Node.js 런타임 필요** | MCP 서버는 Node.js 기반 → Android에는 Node.js 런타임이 없다 |
| **인증 비호환** | 브라우저 기반 Google 로그인 세션 → Android 앱의 Google Sign-In OAuth와 별개 체계 |
| **리소스 요구** | Chromium 인스턴스 메모리 200MB+ → 모바일 환경에서 비현실적 |

### 5.2 대안 1: 별도 백엔드 서버로 MCP 프록시

**구조:** Android 앱 → HTTP API → 백엔드 서버 → MCP 서버 → NotebookLM

**장점:**
- Android 앱에서 REST API로 호출 가능
- 기존 MCP 도구의 전체 기능 활용 가능

**단점:**
- 별도 서버 인프라 구축/유지 필요 (클라우드 비용 발생)
- MCP 서버의 브라우저 자동화 특성상 서버에도 Chromium 설치 필요
- Google 로그인 세션 관리 복잡성 (서버 측 인증 유지)
- 단일 사용자 앱에 과도한 아키텍처 복잡성
- MCP 서버 업데이트 시 서버 측 대응 필요

**평가:** 복잡성 과도, 유지보수 부담이 프로젝트 규모에 비해 지나침. **비추천.**

### 5.3 대안 2: NotebookLM Enterprise API (discoveryengine.googleapis.com)

**구조:** Android 앱 → REST API → Google Cloud discoveryengine.googleapis.com

**사용 가능한 엔드포인트:**
- `notebooks.create` — 노트북 생성
- `notebooks.sources.uploadFile` — 파일 업로드
- `notebooks.sources.create` — 소스 추가

**장점:**
- 표준 REST API로 Android 앱에서 직접 호출 가능
- Google Cloud 인증(OAuth 2.0) 사용
- 안정적인 공식 API

**단점:**
- **엔터프라이즈 전용:** Google Workspace Enterprise 구독 필요
- 개인 NotebookLM(notebooklm.google.com) 계정에서는 사용 불가
- 비용: 엔터프라이즈 라이선스 비용 발생
- 본 프로젝트의 대상 사용자(개인)에게 적용 불가

**평가:** 기술적으로는 가능하나, 엔터프라이즈 전용이므로 개인 사용자 대상 앱에 적용 불가. **현 시점 적용 불가.**

### 5.4 대안 3: 향후 공식 REST API 출시 대기

Google은 NotebookLM의 기능을 지속적으로 확장하고 있으며, 2025년에 Android/iOS 앱을 출시하고 Enterprise API를 제공한 바 있다. 향후 개인 사용자용 공식 REST API가 출시될 가능성이 있다.

**장점:**
- 공식 API는 안정성과 지원 보장
- OAuth 2.0 기반 인증으로 Android 앱 통합 용이
- 브라우저 자동화 의존 없음

**단점:**
- 출시 시점 불확실 (공식 발표 없음)
- 개인용 API 제공 여부 불확실

**평가:** 장기적으로 가장 바람직한 방향. 출시 시 즉시 통합 검토 필요.

## 6. 현재 권장 방식

현 시점에서 Android 앱과 NotebookLM의 통합은 **반자동화 방식**이 최선이다:

### 6.1 공유 Intent (Phase 10 Plan 01에서 구현)
- NotebookLM 앱이 설치된 경우: `ACTION_SEND` Intent로 회의록 텍스트를 직접 전달
- NotebookLM 앱의 패키지명: `com.google.android.apps.labs.language.tailwind`
- 사용자 조작: 1~2탭으로 소스 추가 가능

### 6.2 Custom Tabs (Phase 10 Plan 01에서 구현)
- NotebookLM 앱 미설치 시: Chrome Custom Tabs로 notebooklm.google.com 열기
- Google 계정 세션 공유로 별도 로그인 불필요
- 사용자가 웹에서 수동으로 소스 추가 필요

### 6.3 조합 흐름
```
회의록 생성 완료
  → NotebookLM 버튼 클릭
    → 앱 설치됨? → 직접 Intent 공유 (1~2탭)
    → 앱 미설치? → Custom Tabs로 웹 열기 (수동 추가)
```

이 방식은 추가 서버 인프라 없이 Android 표준 API만으로 구현 가능하며, 사용자에게 최소한의 수동 조작만 요구한다.

## 7. 향후 전망

### 7.1 공식 REST API 출시 시
Google이 개인 사용자용 NotebookLM REST API를 출시하면:
- Retrofit/OkHttp로 직접 호출 가능
- Google Sign-In OAuth 2.0 인증 통합
- 완전 자동화 파이프라인 구현 가능 (녹음 → 전사 → 회의록 → NotebookLM 자동 등록)

### 7.2 Enterprise API 접근 가능 시
Google Workspace Enterprise 구독이 가능한 환경이라면:
- `discoveryengine.googleapis.com` API 즉시 활용 가능
- 노트북 생성, 소스 업로드, 질문/답변 모두 REST로 구현

### 7.3 MCP 서버 발전
MCP 프로토콜이 HTTP 기반 전송(Streamable HTTP, SSE)을 지원하기 시작했으며, notebooklm-mcp 서버가 이를 채택할 경우:
- 원격 MCP 서버 호출이 가능해질 수 있음
- 단, 브라우저 자동화 의존성은 여전히 남는 구조적 한계

## 8. 결론

| 항목 | 결론 |
|------|------|
| MCP 서버 Android 직접 호출 | **불가** — stdio 프로토콜 + 브라우저 자동화 + Node.js 의존 |
| 백엔드 프록시 구축 | **비추천** — 과도한 복잡성, 개인 프로젝트에 부적합 |
| Enterprise API 사용 | **불가** — 엔터프라이즈 구독 필요, 개인용 계정 미지원 |
| 공식 REST API 대기 | **권장 (장기)** — 출시 시 즉시 재검토 |
| 반자동화 (Intent + Custom Tabs) | **현재 최선** — 추가 인프라 없이 Android 표준 API로 구현 |

**최종 판단:** Android 앱에서 NotebookLM MCP 서버를 직접 통합하는 것은 현 시점에서 기술적으로 불가능하다. MCP 서버는 데스크톱 환경의 브라우저 자동화를 전제로 설계되었으며, Android 런타임과는 근본적으로 호환되지 않는다. 공유 Intent + Custom Tabs를 활용한 반자동화 방식이 현재로서는 가장 실용적인 접근법이며, 향후 Google이 개인 사용자용 REST API를 제공할 때 완전 자동화로 전환할 수 있도록 아키텍처를 설계해 두는 것이 바람직하다.

---

**참고 자료:**
- [notebooklm-mcp GitHub](https://github.com/PleasePrompto/notebooklm-mcp) — MCP 서버 소스 코드
- [NotebookLM Enterprise API](https://docs.cloud.google.com/gemini/enterprise/notebooklm-enterprise/docs/api-notebooks-sources) — 엔터프라이즈 REST API
- [Model Context Protocol 사양](https://modelcontextprotocol.io/) — MCP 프로토콜 표준
- [Google Play Store - NotebookLM](https://play.google.com/store/apps/details?id=com.google.android.apps.labs.language.tailwind) — Android 앱 패키지명 확인
