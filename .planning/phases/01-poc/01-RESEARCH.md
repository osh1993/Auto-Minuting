# Phase 1: PoC -- 기술 가능성 검증 - Research

**Researched:** 2026-03-24
**Domain:** BLE 녹음기 연동 / 온디바이스 STT / AI 회의록 생성 -- 기술 가능성 검증
**Confidence:** MEDIUM (3개 핵심 의존성 모두 공식 API 불확실성 존재, 그러나 대안 경로 다수 확인)

## Summary

이 Phase의 핵심은 3개 외부 의존성(Plaud 오디오 수집, STT 전사, 회의록 생성)의 기술적 실현 가능성을 검증하는 것이다. 리서치 결과 각 의존성별로 예상보다 나은 상황이 확인되었다.

**Plaud 파일 획득:** Plaud가 2025년 말 공식 Developer Platform과 SDK(Android/iOS)를 출시하여 BLE 연결, 파일 다운로드, 클라우드 전사를 SDK 레벨에서 지원한다. 또한 비공식 Cloud API(api.plaud.ai)도 역공학되어 Python 클라이언트가 존재한다. APK 디컴파일/BLE 역공학 없이도 SDK를 통한 공식 경로가 열려 있다.

**STT 전사:** Galaxy AI 전사 기능은 삼성 내장 앱 전용으로, 서드파티 API가 확인되지 않았다(LOW confidence). 그러나 Google ML Kit GenAI SpeechRecognizer가 한국어(ko-KR)를 Basic/Advanced 모드 모두 지원하며, 파일 입력도 가능하다. Whisper.cpp도 Android에서 동작 확인되었고, S24 Ultra에서 실시간 2배속 전사가 가능하다.

**회의록 생성:** NotebookLM Enterprise API가 2025년 9월 출시(alpha)되었으나 기업 전용이다. 실용적 경로는 Gemini API 직접 호출이며, Firebase AI Logic SDK를 통해 Android에서 바로 사용 가능하다. 이미 설정된 NotebookLM MCP 서버도 PoC 검증 대상이다.

**Primary recommendation:** Plaud SDK를 1차 채택 경로로 전환하고, STT는 ML Kit GenAI SpeechRecognizer + Whisper.cpp 병행 PoC, 회의록은 Gemini API 직접 호출을 1차 경로로 채택한다.

## Project Constraints (from CLAUDE.md)

- **응답 언어:** 한국어
- **코드 주석:** 한국어로 작성
- **커밋 메시지:** 한국어로 작성
- **문서화:** 한국어로 작성
- **변수명/함수명:** 영어 (코드 표준 준수)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** APK 디컴파일을 우선 시도하여 파일 저장 경로, BLE 서비스/특성 UUID, 통신 프로토콜을 분석한다
- **D-02:** Plaud 앱이 로컬에 파일을 저장하는 경우 FileObserver로 감시하는 방식을 1차 채택 경로로 검토한다
- **D-03:** 오디오 포맷은 WAV/MP3로 알려져 있으며, 실제 포맷은 APK 분석 시 확인한다
- **D-04:** 폴백 경로: Plaud 클라우드에서 다운로드한 파일을 입력으로 사용한다
- **D-05:** Galaxy AI 전사 기능의 서드파티 접근 방법을 모든 경로(SpeechRecognizer 온디바이스, 삼성 녹음/노트 앱 Intent, Samsung SDK)에서 조사한다
- **D-06:** 온디바이스(로컬) 처리를 우선시한다 -- 프라이버시가 핵심 가치
- **D-07:** Galaxy AI 접근 불가 시 폴백: OpenAI Whisper 온디바이스 (whisper.cpp 또는 whisper-android)
- **D-08:** 클라우드 STT는 최후의 수단으로만 고려 (Google Cloud STT)
- **D-09:** NotebookLM 연동 방식은 PoC 리서치에서 확인 후 결정 (MCP 서버 릴레이 vs Gemini API 직접 호출)

### Claude's Discretion
- PoC 검증 순서 (3개 의존성 중 어떤 것을 먼저 검증할지)
- 각 의존성별 성공/실패 판단 기준의 구체적 수치
- BLE 스니핑 도구 선택 (nRF Connect 등)
- Whisper 모델 크기 선택 (tiny/base/small)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| POC-01 | Plaud 앱 APK 디컴파일 및 BLE 프로토콜/파일 저장 경로 분석 완료 | Plaud SDK 공식 출시로 APK 디컴파일 대안 확보. 비공식 Cloud API(plaud-api)도 존재. APK 분석은 여전히 유효하지만 우선순위 재조정 가능 |
| POC-02 | Galaxy AI 전사 기능의 서드파티 앱 접근 방법 확인 (API/Intent/Accessibility) | Galaxy AI 전사 API 미확인(LOW). 대안: ML Kit GenAI SpeechRecognizer가 한국어 지원, 파일 입력 가능 |
| POC-03 | Galaxy AI 불가 시 대안 STT 경로 확인 (Whisper 온디바이스 / Google STT) | whisper.cpp Android 빌드 확인(S24 Ultra 2x 실시간), ML Kit GenAI SpeechRecognizer(ko-KR 지원, alpha) |
| POC-04 | NotebookLM Android 연동 방식 확인 (MCP 릴레이 / Gemini API 직접 호출) | NotebookLM Enterprise API alpha(기업 전용). Gemini API는 Firebase AI Logic SDK로 Android 직접 호출 가능. MCP 서버는 PC 필요 |
</phase_requirements>

## Standard Stack (PoC Phase)

PoC 단계에서는 최소한의 도구로 검증한다. 앱 빌드가 아닌 기술 가능성 확인이 목적이다.

### Core (PoC 검증 도구)

| Tool/Library | Version | Purpose | Why Standard |
|-------------|---------|---------|--------------|
| jadx | latest | APK 디컴파일 | Android APK 역공학 사실상 표준 |
| nRF Connect | latest | BLE 서비스/특성 스캔 | BLE 디버깅/분석 사실상 표준 |
| Plaud SDK (Android) | 0.2.8 | Plaud 기기 연결/파일 다운로드 | 공식 SDK, MIT 라이선스 |
| ML Kit GenAI Speech Recognition | 1.0.0-alpha1 | 온디바이스 STT (한국어) | Google 공식, ko-KR 지원, 파일 입력 가능 |
| whisper.cpp | latest | 온디바이스 STT 폴백 | 검증된 온디바이스 Whisper 구현, Android 빌드 확인 |
| Firebase AI Logic SDK | latest | Gemini API 호출 | Google 공식 Android SDK, 무료 티어 제공 |
| NotebookLM MCP Server | (configured) | NotebookLM 자동화 | 프로젝트에 이미 설정됨 |

### Supporting

| Tool | Purpose | When to Use |
|------|---------|-------------|
| adb | APK 추출, 디바이스 디버깅 | Plaud 앱 APK 추출 시 |
| Frida | 런타임 후킹/분석 | BLE 통신 동적 분석 필요 시 |
| plaud-api (Python) | Plaud Cloud API 비공식 클라이언트 | 폴백: 클라우드에서 오디오 다운로드 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Plaud SDK | APK 디컴파일 + BLE 역공학 | SDK가 공식이지만 appKey 필요(신청 필요). 역공학은 즉시 시작 가능하지만 불안정 |
| ML Kit GenAI | Android SpeechRecognizer | SpeechRecognizer는 API 31+에서 온디바이스 지원하지만 파일 입력이 아닌 스트리밍 전용 |
| Gemini API | NotebookLM MCP | MCP는 노트북 관리 기능 포함하지만 별도 PC 서버 필요, 비공식 경로 |
| whisper.cpp | Google Cloud STT | Cloud STT는 정확도 높지만 네트워크 필요, 비용 발생, 프라이버시 이슈 |

## Architecture Patterns

### PoC 프로젝트 구조 (검증용, 경량)

```
poc/
  plaud-analysis/        # Plaud APK 분석 결과, BLE 프로토콜 문서
  plaud-sdk-test/        # Plaud SDK 연동 테스트 Android 프로젝트
  stt-test/              # STT 경로 검증 Android 프로젝트
  minutes-test/          # 회의록 생성 검증 (스크립트 또는 Android)
  results/               # 각 검증 결과 문서
    POC-01-plaud.md
    POC-02-galaxy-ai.md
    POC-03-stt-fallback.md
    POC-04-notebooklm.md
```

### Pattern 1: 독립적 PoC 검증

**What:** 각 의존성을 독립된 미니 프로젝트로 검증한다.
**When to use:** 3개 의존성이 서로 독립적이므로 병렬 검증 가능.
**Why:** 한 의존성 검증 실패가 다른 검증을 블로킹하지 않는다. 각 결과를 독립적으로 문서화할 수 있다.

### Pattern 2: 타임박스 + Go/No-Go 판단

**What:** 각 검증 경로에 타임박스를 설정하고, 시간 내 성공/실패를 판단한 후 다음 경로로 이동한다.
**When to use:** APK 분석, BLE 역공학 등 불확실한 리서치 작업.
**Why:** 무한정 삽질 방지. 실패 시 즉시 폴백 경로로 전환.

### Anti-Patterns to Avoid

- **모든 경로를 동시에 깊게 파기:** 1차 경로부터 순차 검증 후, 실패 시에만 다음 경로로 이동
- **PoC에서 앱 아키텍처 구현하기:** 이 Phase는 기술 가능성만 확인. Clean Architecture, DI 등은 Phase 2
- **실제 기기 없이 이론적 검증만 하기:** Galaxy AI, BLE 등은 실제 기기에서만 확인 가능

## Research Findings by Dependency

### 의존성 1: Plaud 오디오 파일 획득

#### 경로 A: Plaud 공식 SDK (신규 발견 -- HIGH IMPACT)

**Confidence: MEDIUM** (SDK 존재 확인, 실제 appKey 획득 필요)

Plaud가 2025년 말 Developer Platform을 출시했다. 핵심 내용:

| 항목 | 상세 |
|------|------|
| SDK 플랫폼 | Android (API 21+), iOS (13.0+), ReactNative |
| 라이선스 | MIT |
| 핵심 기능 | BLE 스캔/바인딩/연결, 녹음 제어, 파일 다운로드, 클라우드 전사 |
| 인증 | appKey 필요 (support@plaud.ai에 신청) |
| 오디오 포맷 | MP3, WAV 지원 |
| 최신 버전 | v0.2.8 (2025년 12월) |
| GitHub | https://github.com/Plaud-AI/plaud-sdk |

**SDK 핵심 API:**
- `Sdk.initSdk(context, appKey, bleAgentListener, ...)` -- 초기화
- `agent.scanBle(isStart)` -- BLE 스캔 시작/중지
- `agent.connectionBLE(device, bindToken, ...)` -- BLE 연결
- `agent.syncFileStart(sessionId, start, end, ...)` -- 파일 다운로드
- `agent.getRecSessions(sessionId, ...)` -- 녹음 세션 목록

**의존성:** Guava 28.2, Retrofit 2.9.0, OkHttp 4.10.0

**리스크:** appKey 신청이 기업 대상일 수 있음(개인 개발자 접근 가능 여부 불확실). SDK가 아직 v0.2.8로 초기 단계.

#### 경로 B: APK 디컴파일 + 파일 시스템 감시 (CONTEXT.md D-01, D-02)

**Confidence: MEDIUM**

APK 디컴파일(jadx)로 다음을 확인할 수 있다:
- Plaud 앱의 로컬 파일 저장 경로
- BLE GATT 서비스/특성 UUID
- 파일 포맷 및 인코딩
- 클라우드 API 엔드포인트

**주의사항:**
- Android Scoped Storage(API 29+)로 인해 다른 앱의 내부 저장소 접근 불가
- Plaud가 앱별 외부 저장소 사용 시 접근 제한
- MediaStore에 등록하는 경우에만 표준 API로 접근 가능

#### 경로 C: Plaud Cloud API (폴백 -- D-04)

**Confidence: HIGH** (역공학된 API가 동작 확인됨)

비공식 Python 클라이언트 2개가 존재하며, 동작이 확인되었다:
- `arbuzmell/plaud-api`: 녹음 목록, 다운로드, 전사본 조회
- `leonardsellem/plaud-sync-for-obsidian`: JWT 토큰(~10개월 유효), S3 presigned URL로 다운로드

**API 구조:**
- 엔드포인트: `https://api.plaud.ai` (리전별 변형 존재: `api-euc1.plaud.ai`)
- 인증: JWT Bearer 토큰 (브라우저 localStorage에서 추출)
- 다운로드: S3 presigned URL 반환
- 포맷: MP3/WAV 내보내기 가능

#### 권장 검증 순서

1. **1차: Plaud SDK appKey 신청** -- 공식 경로가 가장 안정적
2. **2차: APK 디컴파일** -- SDK 대기 중 병행 진행. 파일 저장 경로 확인
3. **3차: Cloud API 폴백** -- JWT 토큰 추출하여 오디오 다운로드 테스트

### 의존성 2: STT 전사

#### 경로 A: Galaxy AI 전사 (D-05)

**Confidence: LOW** (서드파티 API 존재 미확인)

삼성 Galaxy AI Transcript Assist는 Samsung Voice Recorder 앱에 내장되어 있다. 서드파티 접근 경로:

| 경로 | 가능성 | 상세 |
|------|--------|------|
| Samsung SDK/API | LOW | 공개 SDK 미발견. Samsung Developer 포털에 전사 API 없음 |
| SpeechRecognizer API | LOW-MEDIUM | Samsung 기기에서 기본 엔진이 Galaxy AI일 수 있으나 확인 필요 |
| Samsung 녹음앱 Intent | LOW | 전사 기능을 트리거하는 공개 Intent 미확인 |
| Accessibility Service | MEDIUM | 기술적으로 가능하나 유지보수 부담 극심, Play Store 정책 위반 |

**결론:** Galaxy AI 서드파티 접근은 거의 불가능할 가능성이 높다.

#### 경로 B: Google ML Kit GenAI SpeechRecognizer (신규 발견)

**Confidence: MEDIUM** (API alpha 단계, 한국어 지원 확인)

| 항목 | 상세 |
|------|------|
| 버전 | 1.0.0-alpha1 |
| 한국어 | ko-KR 지원 (Basic/Advanced 모드 모두) |
| 입력 | 마이크 + 오디오 파일 |
| 최소 API | 26 (Android 8.0) |
| 모드 | Basic(전통 온디바이스), Advanced(GenAI, Pixel 10 전용) |
| 모델 | 다운로드 필요 (크기 미공개) |
| 제한 | Unlocked bootloader 미지원, AICore 앱 초기화 필요 |

**핵심 장점:** 온디바이스, 파일 입력 지원, Google 공식 API, 한국어 지원
**리스크:** alpha 단계, Advanced 모드는 Pixel 10 전용(Samsung 미지원), Basic 모드 한국어 품질 미검증

#### 경로 C: Whisper 온디바이스 (D-07)

**Confidence: MEDIUM-HIGH** (Android 빌드 확인, 한국어 지원)

| 항목 | 상세 |
|------|------|
| 구현 | whisper.cpp (C/C++), Android 빌드 지원 |
| 한국어 | 지원 (medium-resource 언어, CER 기준 측정) |
| 성능 | S24 Ultra에서 distil-small.en 모델 2x 실시간 속도 |
| 모델 크기 | tiny(~75MB), base(~150MB), small(~500MB) |
| 예제 앱 | whisper.android(Kotlin), whisper.android.java 존재 |
| 주의 | 한국어는 hallucination/반복 현상 보고됨 (Whisper v3) |

**Whisper 모델 크기 권장 (Claude's Discretion):**
- PoC 검증: `small` 모델 사용 -- 한국어 정확도와 속도의 균형
- `tiny`/`base`는 한국어 정확도가 부족할 가능성 높음
- `medium`/`large`는 모바일 메모리/속도 제약

#### 권장 검증 순서 (Claude's Discretion)

1. **1차: ML Kit GenAI SpeechRecognizer** -- Google 공식, 한국어 파일 입력 가능
2. **2차: Android SpeechRecognizer** -- Samsung 기기에서 온디바이스 모드 테스트
3. **3차: whisper.cpp** -- small 모델로 한국어 전사 품질 테스트
4. **최후: Galaxy AI 접근 경로 조사** -- SpeechRecognizer 엔진 확인, Intent 조사

#### 성공/실패 판단 기준 (Claude's Discretion)

| STT 경로 | 성공 기준 | 실패 기준 |
|----------|----------|----------|
| ML Kit GenAI | 한국어 오디오 파일 전사 가능, 내용 이해 가능 수준 | 한국어 모델 다운로드 불가 또는 Samsung 기기 미지원 |
| Whisper | 1시간 회의 녹음을 10분 이내 전사, 내용 70%+ 이해 가능 | OOM 또는 30분+ 소요 |
| Galaxy AI | 서드파티 앱에서 프로그래밍적 호출 가능 | API/Intent/SDK 미존재 확인 |

### 의존성 3: 회의록 생성

#### 경로 A: NotebookLM MCP 서버 (D-09)

**Confidence: MEDIUM** (프로젝트에 설정 확인, 안정성 미검증)

이미 프로젝트에 notebooklm-mcp 서버가 설정되어 있다. 사용 가능한 도구:
- `source_add(source_type, ...)` -- 소스 추가 (text, url, file, drive)
- `note_create` / `note_update` / `note_list` / `note_delete` -- 노트 관리

**제약:**
- PC에서 MCP 서버 실행 필요 (모바일 독립 실행 불가)
- Google 계정 인증 필요 (`nlm login`)
- 비공식 경로 -- Google UI 변경 시 깨질 수 있음

#### 경로 B: NotebookLM Enterprise API

**Confidence: LOW** (alpha, 기업 전용)

2025년 9월 출시. 엔드포인트: `discoveryengine.googleapis.com`. 노트북 CRUD + 소스 추가 + 오디오 오버뷰 생성 지원. 그러나 Cloud NotebookLM User IAM 역할, Google Cloud 프로젝트 필요 -- 개인 개발자 접근 사실상 불가.

#### 경로 C: Gemini API 직접 호출 (권장)

**Confidence: HIGH** (공식 API, Android SDK 존재, 무료 티어)

| 항목 | 상세 |
|------|------|
| SDK | Firebase AI Logic SDK (`firebase-ai`) |
| 모델 | Gemini 2.5 Flash (빠르고 저비용) |
| 오디오 입력 | 최대 9.5시간 오디오 직접 처리 가능 |
| 텍스트 입력 | 전사 텍스트 + 프롬프트로 회의록 생성 |
| 가격 | 무료 티어 제공 (Firebase) |
| Android 연동 | Kotlin suspend function, Coroutine 자연스러운 통합 |

**핵심 장점:** 공식 API, 안정적, 모바일 직접 호출, 오디오 직접 입력도 가능(STT+회의록 한 번에), 무료 티어

#### 권장 검증 순서

1. **1차: Gemini API 직접 호출** -- 전사 텍스트로 회의록 생성 테스트
2. **2차: NotebookLM MCP 서버** -- source_add + 노트 생성으로 자동 회의록 테스트
3. **3차: Gemini API 오디오 직접 입력** -- STT 건너뛰고 오디오에서 바로 회의록 (대안 파이프라인)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BLE 기기 연결/파일 전송 | 자체 BLE GATT 프로토콜 역공학 | Plaud SDK | 공식 SDK가 BLE 통신 전체를 추상화, 프로토콜 변경에도 SDK 업데이트로 대응 |
| 오디오 파일 STT 전사 | 자체 STT 모델 훈련 | ML Kit GenAI / whisper.cpp | 사전 훈련된 모델이 한국어 지원, 온디바이스 실행 가능 |
| 회의록 생성 프롬프트 엔진 | 자체 NLP 파이프라인 | Gemini API | LLM이 전사 텍스트에서 구조화된 회의록 생성에 최적화 |
| JWT 토큰 관리 | 자체 인증 시스템 | 플랫폼별 표준 (AccountManager, credential store) | 보안 취약점 방지 |

**Key insight:** PoC 단계에서는 어떤 것도 직접 만들지 않는다. 기존 도구/API/SDK의 동작 여부만 확인한다.

## Common Pitfalls

### Pitfall 1: Plaud SDK appKey 획득 지연

**What goes wrong:** Plaud SDK appKey를 신청했지만 응답이 없거나 기업 전용이라 거절된다. SDK 경로가 블로킹된다.
**Why it happens:** Plaud Developer Platform이 기업 대상(B2B)으로 설계되어 있으며, 개인 개발자/사이드 프로젝트에 appKey를 발급하지 않을 수 있다.
**How to avoid:** appKey 신청과 동시에 APK 디컴파일/Cloud API 폴백을 병행 진행. SDK 의존도를 고정하지 않는다.
**Warning signs:** 신청 후 1주일 이상 응답 없음, 또는 "enterprise plan" 언급.

### Pitfall 2: ML Kit GenAI SpeechRecognizer가 Samsung 기기에서 미지원

**What goes wrong:** ML Kit GenAI API는 Android AICore에 의존하며, Samsung 기기에서 AICore가 설치/초기화되지 않아 모델 다운로드 불가.
**Why it happens:** Google의 on-device GenAI 기능은 Pixel 우선 배포 경향. Samsung은 자체 AI 스택을 운영.
**How to avoid:** PoC에서 실제 Samsung Galaxy 기기에서 `checkStatus()` 결과를 먼저 확인. UNAVAILABLE이면 즉시 whisper.cpp로 전환.
**Warning signs:** `checkStatus()` 반환값이 UNAVAILABLE, 또는 AICore 앱 설치 프롬프트 미표시.

### Pitfall 3: Scoped Storage로 Plaud 앱 파일 접근 불가

**What goes wrong:** Plaud 앱이 앱별 내부 저장소에 파일을 저장하여 다른 앱에서 접근 불가.
**Why it happens:** Android 10+ Scoped Storage 정책. 앱별 저장소는 해당 앱만 접근 가능.
**How to avoid:** APK 분석 시 저장 경로가 내부 저장소인지 확인. 내부 저장소라면 FileObserver 방식 불가 -> SDK 또는 Cloud API로 전환.
**Warning signs:** jadx 분석에서 `context.filesDir` 또는 `context.getExternalFilesDir()` 사용 확인.

### Pitfall 4: PoC에서 앱 아키텍처를 과도하게 구현

**What goes wrong:** PoC 검증 코드에 Clean Architecture, DI, Room DB 등을 적용하여 검증 속도가 느려진다.
**Why it happens:** 개발자 습관. "나중에 다시 만들기 싫어서" 처음부터 잘 만들려는 경향.
**How to avoid:** PoC는 throwaway 코드. 단일 Activity에서 버튼 하나로 기능 확인. Phase 2에서 제대로 구현.
**Warning signs:** PoC 프로젝트에 모듈 분리, DI 설정, 데이터 레이어 추상화가 등장.

### Pitfall 5: Whisper 한국어 hallucination

**What goes wrong:** Whisper가 한국어 음성에서 실제 없는 내용을 반복 생성하거나, 무음 구간에서 텍스트를 만들어낸다.
**Why it happens:** Whisper v3에서 한국어/일본어 등에서 hallucination 빈도가 높아진 것이 보고됨.
**How to avoid:** VAD(Voice Activity Detection) 전처리 적용, temperature 파라미터 조정, 무음 구간 감지 후 제거.
**Warning signs:** 전사 결과에 반복 문구, 존재하지 않는 대화, 전사 길이가 오디오 길이에 비해 비정상적.

## Code Examples

### Plaud SDK 초기화 및 BLE 연결 (Android)

```java
// Source: https://github.com/Plaud-AI/plaud-sdk/blob/main/docs/sdk-integration-guide.md
// SDK 초기화
Sdk.initSdk(context, "plaud-YOUR_APP_KEY", bleAgentListener, hostName, null);

// BLE 스캔 시작
agent.scanBle(true);

// 기기 연결
agent.connectionBLE(device, bindToken, devToken, userName,
    connectTimeout, handshakeTimeout);

// 파일 다운로드
agent.syncFileStart(sessionId, start, end, callback);
```

### ML Kit GenAI SpeechRecognizer (Kotlin)

```kotlin
// Source: https://developers.google.com/ml-kit/genai/speech-recognition/android
// 의존성: implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")

val options = SpeechRecognizerOptions.builder()
    .setLocale(Locale.KOREAN)  // ko-KR
    .build()

val speechRecognizer = SpeechRecognizer.getClient(options)

// 모델 상태 확인
val status = speechRecognizer.checkStatus()
when (status) {
    AVAILABLE -> { /* 바로 사용 가능 */ }
    DOWNLOADABLE -> { speechRecognizer.download() }
    UNAVAILABLE -> { /* 폴백: whisper.cpp */ }
}

// 파일 입력으로 전사
val request = SpeechRecognitionRequest.builder()
    .setAudioSource(AudioSource.fromFileDescriptor(fd))
    .build()

speechRecognizer.startRecognition(request).collect { response ->
    // 전사 결과 처리
}
```

### Gemini API 회의록 생성 (Kotlin)

```kotlin
// Source: https://firebase.google.com/docs/ai-logic/generate-text
// 의존성: implementation("com.google.firebase:firebase-ai")

val model = Firebase.ai(backend = GenerativeBackend.googleAI())
    .generativeModel("gemini-2.5-flash")

val prompt = """
다음 회의 전사 텍스트를 바탕으로 회의록을 작성해주세요.

## 형식
1. 회의 개요 (날짜, 참석자)
2. 주요 안건 및 논의 내용
3. 결정 사항
4. 액션 아이템 (담당자, 기한)

## 전사 텍스트
$transcriptText
""".trimIndent()

val response = model.generateContent(prompt)
val minutesText = response.text
```

### Plaud Cloud API 폴백 (Python 테스트)

```python
# Source: https://github.com/arbuzmell/plaud-api
# pip install plaud-api
from plaud import PlaudClient

client = PlaudClient(token="YOUR_JWT_TOKEN")

# 녹음 목록
recordings = client.recordings.list()

# 오디오 다운로드 URL 획득
audio_url = client.recordings.get_audio_url(file_id)

# 전사본 조회
transcript = client.transcription.get(file_id)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Plaud BLE 역공학 | Plaud 공식 SDK | 2025년 말 | BLE 역공학 불필요, SDK로 공식 연동 가능 |
| Galaxy AI 전사 의존 | ML Kit GenAI SpeechRecognizer | 2025-2026 | 온디바이스 STT의 새로운 공식 경로 |
| NotebookLM 비공식 자동화만 | Enterprise API (alpha) + Gemini API | 2025년 9월 | Gemini API 직접 호출이 더 안정적 경로 |
| Android SpeechRecognizer (스트리밍만) | ML Kit GenAI (파일 입력 지원) | 2025-2026 | 녹음 파일 배치 전사 가능 |

**Deprecated/outdated:**
- Galaxy AI 전사 API 의존 전략: 서드파티 접근 거의 불가 확인, 대안 우선
- NotebookLM MCP만 의존하는 전략: Gemini API가 더 안정적이고 모바일 독립 실행 가능

## Open Questions

1. **Plaud SDK appKey 개인 개발자 발급 가능 여부**
   - What we know: SDK 존재 확인, appKey 신청 필요 (support@plaud.ai)
   - What's unclear: 개인/사이드 프로젝트에 발급하는지, 비용이 있는지
   - Recommendation: 즉시 신청. 동시에 APK 분석 병행

2. **ML Kit GenAI SpeechRecognizer Samsung Galaxy 기기 호환성**
   - What we know: API 존재, ko-KR 지원, alpha 단계
   - What's unclear: Samsung 기기에서 AICore 앱/모델 사용 가능 여부
   - Recommendation: 실제 Galaxy 기기에서 `checkStatus()` 호출 테스트

3. **Whisper small 모델 한국어 회의 음성 품질**
   - What we know: 한국어 지원, S24에서 동작, hallucination 보고 있음
   - What's unclear: 실제 회의 환경(다중 화자, 잡음) 한국어 정확도
   - Recommendation: 실제 회의 녹음 샘플로 전사 테스트

4. **Gemini API 무료 티어 한도**
   - What we know: Firebase AI Logic에 무료 티어 존재
   - What's unclear: 분당/일당 요청 한도, 입력 토큰 제한
   - Recommendation: PoC에서는 충분할 것으로 예상. 공식 문서 확인 필요

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| jadx | POC-01 APK 분석 | 확인 필요 | - | 온라인 디컴파일러 (javadecompilers.com) |
| nRF Connect 앱 | POC-01 BLE 분석 | Galaxy 기기에서 설치 | - | - |
| Samsung Galaxy 기기 | POC-02 Galaxy AI 테스트 | 사용자 보유 (프로젝트 전제) | - | - |
| Android Studio | 테스트 앱 빌드 | 확인 필요 | - | - |
| Firebase 프로젝트 | POC-04 Gemini API | 생성 필요 | - | Google AI Studio API 키 |
| Python 3 | Cloud API 폴백 테스트 | 확인 필요 | - | - |

**Missing dependencies with no fallback:**
- Samsung Galaxy 기기 (Galaxy AI 테스트 필수, 프로젝트 전제 조건)

**Missing dependencies with fallback:**
- jadx: 온라인 디컴파일러로 대체 가능
- Firebase: Google AI Studio에서 직접 API 키 발급 가능

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | 없음 (PoC 단계, 코드 테스트 아닌 기술 가능성 검증) |
| Config file | 해당 없음 |
| Quick run command | 각 PoC 프로젝트별 수동 실행 |
| Full suite command | 해당 없음 |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| POC-01 | Plaud 오디오 파일 획득 방법 1개+ 확인 | manual-only | 실제 기기에서 SDK/APK 분석 실행 | -- Wave 0 |
| POC-02 | Galaxy AI 서드파티 접근 가능 여부 | manual-only | Samsung 기기에서 API 호출 테스트 | -- Wave 0 |
| POC-03 | 대안 STT 경로 1개+ 확인 | manual-only | ML Kit/Whisper 테스트 앱 실행 | -- Wave 0 |
| POC-04 | 회의록 생성 방법 1개+ 확인 | manual-only | Gemini API/MCP 서버 호출 테스트 | -- Wave 0 |

**Justification for manual-only:** PoC Phase는 기술 가능성을 검증하는 리서치 단계이다. 자동화된 테스트가 아닌, 실제 기기/API에서의 동작 확인이 검증 방법이다. 각 결과는 문서(Go/No-Go 판단)로 기록된다.

### Sampling Rate

- **Per task commit:** 각 의존성별 검증 결과 문서 작성
- **Per wave merge:** 전체 3개 의존성 검증 완료 확인
- **Phase gate:** POC-01~04 모두 최소 1개 경로에서 성공 확인

### Wave 0 Gaps

- [ ] `poc/plaud-sdk-test/` -- Plaud SDK 연동 테스트 Android 프로젝트 (또는 APK 분석 환경)
- [ ] `poc/stt-test/` -- ML Kit GenAI / Whisper 테스트 Android 프로젝트
- [ ] `poc/minutes-test/` -- Gemini API 테스트 코드
- [ ] `poc/results/` -- 검증 결과 문서 디렉토리

## Sources

### Primary (HIGH confidence)
- [Plaud SDK GitHub](https://github.com/Plaud-AI/plaud-sdk) -- 공식 SDK, BLE 연결/파일 다운로드 API 확인
- [Plaud SDK Integration Guide](https://github.com/Plaud-AI/plaud-sdk/blob/main/docs/sdk-integration-guide.md) -- Android 연동 상세
- [ML Kit GenAI Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android) -- 한국어 지원, 파일 입력 확인
- [Firebase AI Logic SDK](https://firebase.google.com/docs/ai-logic/get-started) -- Gemini API Android 연동
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp) -- Android 빌드 지원 확인
- [NotebookLM Enterprise API](https://docs.cloud.google.com/gemini/enterprise/notebooklm-enterprise/docs/api-notebooks) -- Enterprise API alpha 상태

### Secondary (MEDIUM confidence)
- [plaud-api (Python)](https://github.com/arbuzmell/plaud-api) -- 비공식 Cloud API 클라이언트, 동작 확인
- [plaud-sync-for-obsidian](https://github.com/leonardsellem/plaud-sync-for-obsidian) -- JWT 인증, S3 presigned URL 방식 확인
- [Samsung Galaxy AI Transcript Assist](https://www.sammobile.com/samsung/galaxy-ai/transcript-assist/) -- 기능 설명, 서드파티 API 미존재 확인
- [Plaud Developer Platform PR](https://www.prnewswire.com/news-releases/plaud-launches-developer-platform-to-unlock-the-missing-half-of-conversational-intelligence-in-person-interactions-302576832.html) -- 플랫폼 출시 확인

### Tertiary (LOW confidence)
- Galaxy AI 전사 서드파티 API 존재 여부 -- 공식 문서에서 확인 실패, 미존재로 추정
- ML Kit GenAI Samsung 기기 호환성 -- 실기기 테스트 필요
- Whisper 한국어 회의 음성 정확도 -- 실제 데이터 테스트 필요

## Metadata

**Confidence breakdown:**
- Plaud 파일 획득: MEDIUM -- SDK 존재 확인하나 appKey 획득 필요, Cloud API 폴백은 HIGH
- STT 전사: MEDIUM -- ML Kit ko-KR 지원 확인, Samsung 호환성 미검증. Whisper 동작 확인
- 회의록 생성: HIGH -- Gemini API 공식, Firebase SDK 존재, 무료 티어
- 전체 PoC 성공 가능성: MEDIUM-HIGH -- 각 의존성에 최소 2개 이상 경로 존재

**Research date:** 2026-03-24
**Valid until:** 2026-04-07 (2주 -- 빠르게 변하는 API/SDK 영역)
