# Feature Landscape

**Domain:** 자동 회의록 생성 Android 앱 (v2.0 기능 확장)
**Researched:** 2026-03-25
**Focus:** v2.0 신규 기능에 대한 기대 동작, 복잡도, 의존성 분석

## Table Stakes

v2.0에서 사용자가 당연히 기대하는 기능. 없으면 앱이 미완성으로 느껴진다.

| Feature | Why Expected | Complexity | Depends On | Notes |
|---------|--------------|------------|------------|-------|
| 전사파일/회의록 삭제 | 기본 CRUD. 목록만 있고 삭제가 없으면 불편 | Low | 기존 MeetingDao.delete() 존재 | DB 삭제 + 파일시스템 삭제 동시 수행 필요 |
| Gemini API 키 설정 UI | 현재 BuildConfig 하드코딩. 사용자가 자체 키 필수 | Low | 기존 DataStore 인프라 | 설정 화면에 입력 필드 + 검증 로직 |
| 삼성 녹음앱 공유 수신 | "공유" 버튼은 모든 Android 사용자가 익숙한 UX | Med | AndroidManifest intent-filter 추가 | 텍스트/파일 공유 intent 수신 처리 |

## Differentiators

제품을 차별화하는 기능. 기대하지 않지만, 있으면 가치가 크다.

| Feature | Value Proposition | Complexity | Depends On | Notes |
|---------|-------------------|------------|------------|-------|
| 삼성 녹음기 전사 완료 자동 감지 | 진정한 "원클릭" -- 공유 버튼도 누를 필요 없음 | High | ContentObserver 또는 NotificationListener | 기술적 불확실성 높음, 폴백 필수 |
| NotebookLM 연동 | 전사 텍스트를 NotebookLM에 자동 등록하여 AI 분석 활용 | High | NotebookLM MCP 서버 (환경에 존재) | MCP 서버 접근 가능하나 모바일-MCP 통신 설계 필요 |
| Gemini OAuth 인증 | API 키 없이 Google 계정으로 로그인. UX 대폭 개선 | Med-High | Google Sign-In + OAuth 스코프 | generativelanguage OAuth 스코프 지원 여부 확인 필요 |

## Anti-Features

명시적으로 구현하지 않아야 하는 기능.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Accessibility Service 기반 자동 감지 | Google Play 심사에서 거부 사유. 접근성 서비스는 장애인 보조 목적만 허용 | ContentObserver + Share Intent 폴백 조합 |
| NotebookLM 웹 스크래핑 | 불안정, TOS 위반 가능, 인증 복잡 | MCP 서버 활용 또는 Share Intent로 브라우저 전달 |
| Plaud 앱 파일시스템 직접 접근 | Scoped Storage(API 30+)로 다른 앱 파일 접근 차단 | Plaud SDK BLE 사용 (이미 구현) |
| 자체 STT 대체 (삼성 녹음기 경로) | 삼성 온보드 AI가 이미 최고 품질의 한국어 전사 제공. 중복 작업 | 삼성 전사 결과를 수신하여 바로 회의록 생성 파이프라인에 투입 |
| 다건 일괄 삭제 | v2.0 사용 규모에서 과도, 구현 복잡도 증가 | 단건 삭제로 시작, 필요 시 v2.1에서 추가 |

## 각 Feature 상세 분석

### 1. 삼성 녹음기 전사 완료 자동 감지

**기대 동작:** 삼성 녹음 앱에서 AI 전사가 완료되면, Auto Minuting이 자동으로 전사 텍스트를 감지하여 회의록 생성 파이프라인을 시작한다.

**기술 접근법 (우선순위순):**

**A. ContentObserver on MediaStore (권장 1차)**
- `ContentResolver.registerContentObserver()`로 MediaStore 변경 감시
- 삼성 녹음 앱 전사 파일 예상 경로: `Samsung/Voice Recorder/` 하위
- 전사 파일 형식: `.txt` 또는 `.srt`
- onChange() 콜백에서 파일 경로 필터링하여 삼성 녹음 앱 전사 파일만 선별
- **한계:** 삼성 녹음 앱이 전사 파일을 MediaStore에 노출하는지 미확인. Scoped Storage 하에서 다른 앱의 app-specific 저장소는 접근 불가
- **Confidence:** LOW -- 실기기 검증 필수

**B. NotificationListenerService (대안 2차)**
- 삼성 녹음 앱이 "전사 완료" 알림을 보내는 경우, 알림 내용 파싱하여 트리거
- 사용자가 설정 > 알림 접근 권한을 명시적으로 허용해야 함
- 알림에서 파일 경로나 텍스트를 추출하기 어려울 수 있음
- **한계:** 삼성 녹음 앱의 알림 여부/형식 미확인
- **Confidence:** LOW

**C. Accessibility Service (구현 금지 -- Anti-Feature)**
- Google Play 정책 위반 고위험. 절대 사용하지 않는다

**결론:** ContentObserver로 가능성을 탐색하되, Share Intent 방식을 확실한 폴백으로 먼저 구현한다. 자동 감지는 실기기 조사 결과에 따라 v2.0 포함 여부를 결정한다.

---

### 2. 삼성 녹음앱 공유로 전사 텍스트 수신

**기대 동작:** 사용자가 삼성 녹음 앱에서 전사 텍스트 선택 > "공유" > Auto Minuting 선택 > 회의록 생성 파이프라인 자동 시작.

**구현 방식:**
```xml
<!-- AndroidManifest.xml 추가 -->
<activity android:name=".ShareReceiverActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/*" />
    </intent-filter>
</activity>
```

**핵심 설계 결정:**
- 수신 시 확인 UI 표시 여부: 자동화 모드(FULL_AUTO)면 즉시 파이프라인, HYBRID면 확인 UI
- Meeting 엔티티에 `source` 필드 추가 권장 (PLAUD_BLE / SAMSUNG_SHARE / MANUAL)
- 수신 텍스트가 전사 텍스트인지 검증: 최소 길이, 빈 텍스트 거부
- STT 단계를 건너뛰고 바로 Gemini 회의록 생성 워커 호출

**삼성 녹음 앱의 공유 동작 (추정):**
- 전사 텍스트 공유: `text/plain` MIME, `Intent.EXTRA_TEXT`에 텍스트 포함
- 녹음 파일 공유: `audio/*` MIME, URI 전달 (이 경우 앱에서 STT 파이프라인 진입)
- **Confidence:** MEDIUM -- 삼성 녹음 앱의 일반적 동작이나 버전별 차이 가능

---

### 3. NotebookLM 연동

**기대 동작:** 전사 텍스트 또는 회의록을 NotebookLM 노트북에 소스로 자동 등록.

**접근법 분석:**

**A. NotebookLM MCP 서버 경유 (기술적으로 가장 강력)**
- 현재 환경에 `notebooklm-mcp` 서버 확인됨
- 지원 도구: `source_add(text=...)`, `note_create`, `note_list`, `note_update`, `note_delete`
- **문제:** MCP 서버는 로컬 PC/CLI에서 실행. Android 앱에서 직접 MCP 프로토콜 호출 불가
- **해결 방안:** HTTP 프록시 서버를 PC에 띄워 앱 > HTTP > MCP 서버 경유
  - PC가 같은 네트워크에 켜져 있어야 함
  - 설정에서 MCP 프록시 URL 입력
- **사용 시나리오:** 홈/오피스에서 자동 연동. 외부에서는 수동 공유 폴백

**B. Share Intent > 브라우저 (가장 안정적)**
- 앱에서 전사 텍스트를 클립보드에 복사 + NotebookLM 웹 URL을 Custom Tab/브라우저에서 열기
- 사용자가 수동으로 "소스 추가" > 붙여넣기
- 자동화 수준은 낮지만 100% 동작 보장

**C. NotebookLM 공식 API (미존재)**
- 2026년 3월 기준, NotebookLM에 공식 REST API 없음
- 웹 전용 도구이며 Android 앱 미존재
- **Confidence:** MEDIUM

**권장:** 단기적으로 Share/클립보드 방식(B), 중기적으로 MCP 프록시(A). 두 경로 모두 설정 화면에서 선택 가능하게 구성.

---

### 4. 전사파일/회의록 삭제

**기대 동작:** 회의 목록에서 항목 삭제 시, DB 레코드 + 로컬 파일(오디오/전사/회의록) 모두 제거.

**현재 상태 (이미 구현된 것):**
- `MeetingDao.delete(id)` -- DB 레코드 삭제
- `MeetingRepository.deleteMeeting(id)` -- Repository 계층
- `MeetingEntity`에 `audioFilePath`, `transcriptPath`, `minutesPath` 경로 보유

**구현 필요 사항:**
- UI: 삭제 버튼 (swipe-to-delete 또는 long-press 메뉴)
- 삭제 확인 AlertDialog (실수 방지)
- Repository에서 파일 삭제 로직 추가 (DB 삭제 전에 파일 경로 조회, 파일 삭제 후 DB 삭제)
- 삭제 실패 시 에러 처리 (파일 없음 = 정상 처리)

**설계 결정:**
- 즉시 삭제 권장 (소프트 삭제는 개인용 앱에서 과도)
- 삭제 순서: 파일 경로 조회 > 파일 삭제 > DB 삭제 (트랜잭션 아님, 파일 삭제 실패해도 DB 삭제 진행)

---

### 5. Gemini API 키 설정 UI

**기대 동작:** 설정 화면에서 API 키를 입력/수정/테스트. 런타임 키가 BuildConfig 키보다 우선.

**현재 상태:**
- `BuildConfig.GEMINI_API_KEY`로 하드코딩 (`GeminiEngine.kt` 확인)
- `UserPreferencesRepository`에 DataStore 인프라 확립
- `SettingsViewModel`에 설정 관리 패턴 존재

**구현 계획:**
1. `UserPreferencesRepository`에 `GEMINI_API_KEY` 키 추가 (stringPreferencesKey)
2. `GeminiEngine`에 DataStore 키 우선 > BuildConfig 폴백 로직
3. 설정 화면에 OutlinedTextField (비밀번호 마스킹 `visualTransformation`)
4. "API 키 테스트" 버튼: 간단한 generateContent 호출로 키 유효성 검증
5. 키 미설정 시 대시보드에 경고 배너 표시

**보안:** DataStore 평문 저장. 개인용 앱이므로 디바이스 암호화가 1차 보호선. EncryptedDataStore는 복잡도 대비 가치 낮음.

---

### 6. Gemini OAuth 인증

**기대 동작:** Google 계정 로그인으로 API 키 없이 Gemini API 사용.

**기술 분석:**
- **Google AI Client SDK (generativeai):** `GenerativeModel(modelName, apiKey)` -- API 키만 지원, OAuth 미지원. Confidence: MEDIUM (코드 분석 기반)
- **Gemini REST API 직접 호출:** `generativelanguage.googleapis.com` 엔드포인트에 OAuth Bearer 토큰 가능성. OAuth 스코프 `https://www.googleapis.com/auth/generative-language` 미확인. Confidence: LOW
- **Vertex AI 경유:** OAuth/SA 인증 지원하나 Google Cloud 프로젝트 필요. 개인용 앱에 과도. Confidence: MEDIUM

**구현 경로 (OAuth 가능 판명 시):**
1. Credential Manager API 또는 `play-services-auth`
2. Google Sign-In > accessToken 획득
3. Retrofit + OkHttp Interceptor에 Bearer 토큰 주입
4. Gemini REST API 직접 호출 (SDK 대체)
5. Refresh Token으로 자동 갱신

**결론:** API 키 설정 UI를 먼저 구현. OAuth는 Gemini REST API 스코프 지원 확인 후 결정. 확인 불가 시 v2.1로 이연.

---

### 7. Plaud SDK BLE 연결 디버깅

**기대 동작:** 실제 Plaud 녹음기와 BLE 연결 > GATT 서비스 탐색 > 오디오 파일 전송 수신.

**현재 상태:** PlaudSdkManager, AudioCollectionService, 대시보드 BLE 토글 UI 모두 구현 완료. 실기기 테스트 미완료.

**디버깅 체크리스트:**
- Plaud SDK appKey 설정 및 초기화 확인
- BLE 스캔 > 페어링 > GATT 연결 각 단계 로그
- Android 14+ 런타임 권한 (BLUETOOTH_CONNECT, BLUETOOTH_SCAN) 요청 플로우
- ForegroundService TYPE_CONNECTED_DEVICE 정상 동작
- 파일 전송 콜백 및 저장 경로 확인

---

## Feature Dependencies (시각적 정리)

```
[Phase 1: 기반 기능 -- 독립적, 낮은 위험]
  전사파일/회의록 삭제 (독립, Low)
  Gemini API 키 설정 UI (독립, Low)
  삼성 녹음앱 공유 수신 (독립, Med)
      |
      v
[Phase 2: 고급 연동 -- 탐색적, 높은 불확실성]
  삼성 전사 자동 감지 (공유 수신이 폴백)
  Gemini OAuth 인증 (API 키 UI가 기본 경로)
  NotebookLM 연동 (인증 인프라 + 통신 경로 필요)
      |
      v
[Phase 3: 실동작 검증 -- 실기기 필수]
  Plaud BLE 디버깅 (독립, 마지막 수행)
```

## MVP Recommendation (v2.0)

**반드시 포함 (Table Stakes):**
1. **전사파일/회의록 삭제** -- 기본 CRUD 완성, Low 복잡도
2. **Gemini API 키 설정 UI** -- 하드코딩 해소, Low 복잡도
3. **삼성 녹음앱 공유 수신** -- 새로운 입력 경로, Medium 복잡도

**탐색 후 결정 (Differentiators):**
4. **삼성 전사 자동 감지** -- 실기기 ContentObserver 조사 후 구현 여부 결정
5. **NotebookLM 연동** -- MCP 프록시 실현 가능성 판단 후 범위 결정
6. **Gemini OAuth** -- REST API OAuth 스코프 확인 후 구현 (불가 시 v2.1)

**마지막 수행:**
7. **Plaud BLE 디버깅** -- 다른 기능에 영향 없으므로 최후에 실기기 테스트

**이연 권장:** Gemini OAuth (불확실성 높음), NotebookLM 완전 자동화 (인프라 의존성)

## Confidence Assessment

| Feature | Confidence | Reason |
|---------|------------|--------|
| 전사파일/회의록 삭제 | HIGH | 기존 DAO/Repository 완비, 표준 Android 패턴 |
| Gemini API 키 설정 UI | HIGH | DataStore 인프라 존재, 기존 설정 패턴 확장 |
| 삼성 녹음앱 공유 수신 | MEDIUM | Share Intent는 표준이나 삼성 녹음 앱의 정확한 공유 형식 실기기 확인 필요 |
| 삼성 전사 자동 감지 | LOW | ContentObserver 가능 여부 전혀 미확인, 실기기 탐색 필수 |
| NotebookLM 연동 | LOW | 공식 API 부재, MCP 서버-모바일 브릿지 미검증 |
| Gemini OAuth | LOW | Gemini REST API의 OAuth 스코프 지원 여부 미확인 |
| Plaud BLE 디버깅 | MEDIUM | 코드 존재하나 실기기 동작 예측 불가 |

## Sources

- Android ContentObserver API: https://developer.android.com/reference/android/database/ContentObserver -- HIGH
- Android Share Intent 수신: https://developer.android.com/training/sharing/receive -- HIGH
- Google AI Client SDK: 프로젝트 내 GeminiEngine.kt 코드 분석 -- HIGH
- NotebookLM MCP 서버: 현재 환경 MCP 도구 목록 확인 -- MEDIUM
- MeetingDao/Repository: 프로젝트 내 코드 분석 -- HIGH
- UserPreferencesRepository: 프로젝트 내 코드 분석 -- HIGH
- Samsung Voice Recorder 전사 동작: 공식 문서 부재, 추론 기반 -- LOW
- Gemini OAuth 스코프: 공식 문서 접근 실패 -- LOW
