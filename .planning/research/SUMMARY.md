# Project Research Summary

**Project:** Auto Minuting v2.0
**Domain:** Android 자동 회의록 생성 앱 — 기능 확장 (v1.0 → v2.0)
**Researched:** 2026-03-25
**Confidence:** MEDIUM

## Executive Summary

Auto Minuting v2.0은 v1.0의 Plaud BLE 단일 파이프라인을 삼성 녹음기 연동, 공유 Intent 수신, NotebookLM 통합으로 확장하는 작업이다. 핵심 스택(Kotlin 2.3.20, Compose BOM 2026.03, Hilt 2.59.2, Room 2.8.4, WorkManager 2.11.1, GeminiEngine)은 이미 검증 완료 상태이며, v2.0에서 추가되는 라이브러리는 Credential Manager(OAuth), Security Crypto(API 키 암호화), Custom Tabs(NotebookLM 웹 연동) 세 가지로 최소화된다. 기존 3-layer Clean Architecture는 그대로 유지하면서 MeetingSource 패턴으로 다중 입력 경로를 통합하는 것이 핵심 설계 방향이다.

가장 큰 리스크는 두 가지다. 첫째, 삼성 녹음기 전사 완료 자동 감지(ContentObserver/FileObserver)는 삼성 앱이 내부 저장소를 사용할 경우 Scoped Storage 정책으로 완전히 불가능해진다. 실기기 검증 전에 이 기능에 개발 자원을 투입하면 수주 낭비가 발생할 수 있으므로, 공유 Intent 수신 방식을 안정적인 기본 경로로 먼저 확보해야 한다. 둘째, NotebookLM 공식 REST API가 존재하지 않아 완전 자동화가 불가능하다. MCP 서버는 PC 로컬 환경 의존성이 생기고 Android 앱에서의 직접 통합은 인증 유지 문제로 복잡도가 폭발한다. Custom Tabs + 클립보드 복사 수준의 반자동화로 범위를 한정하고 2일 타임박스를 적용해야 한다.

권장 개발 순서는 "확실성이 높은 기능부터, 리서치 필요 기능을 뒤로" 원칙을 따른다. 파일 삭제 + API 키 설정 UI(기존 인프라 활용, HIGH confidence) → 공유 Intent 수신(Android 표준 API, HIGH confidence) → NotebookLM 반자동 연동(2일 타임박스) → 삼성 녹음기 자동 감지(실기기 스파이크 선행 필수) → Google OAuth(API 키 UI 이후) → Plaud BLE 실기기 디버깅(항상 마지막)으로 진행한다.

## Key Findings

### Recommended Stack

기존 스택이 이미 안정적으로 구성되어 있으며 v2.0에서 추가되는 라이브러리는 세 가지로 최소화된다. 레거시 Google Sign-In(`play-services-auth`)은 deprecated 상태로 Credential Manager가 유일한 공식 후속 API다. NotebookLM은 공식 REST API가 없으므로 REST 클라이언트를 별도로 추가하지 않는다. 삭제, 공유 수신, ContentObserver는 모두 플랫폼 API로 처리하므로 라이브러리 불필요.

**핵심 신규 기술:**

- `androidx.credentials:credentials:1.5.0`: Gemini OAuth 및 Google 계정 인증 — 레거시 Sign-In 대체 공식 API (HIGH)
- `androidx.security:security-crypto:1.1.0`: API 키 암호화 저장 — Android Keystore 기반 AES, BuildConfig 하드코딩 해소 (HIGH)
- `androidx.browser:browser:1.8.0`: Custom Tabs로 NotebookLM 웹 연동 — WebView보다 보안/성능/UX 우수 (HIGH)
- `ContentObserver` / `FileObserver` (플랫폼 API): 삼성 녹음기 파일 감시 — 실기기 검증 필수 (LOW)
- `Intent.ACTION_SEND` (플랫폼 API): 삼성 녹음앱 공유 수신 — 표준 Android 패턴 (HIGH)

**주의:** Gemini REST API의 OAuth 스코프(`generative-language`)와 Google AI Client SDK의 OAuth 미지원은 실제 테스트 전까지 LOW confidence다. OAuth 구현 시 SDK 대신 Retrofit REST 직접 호출이 필요할 수 있다.

### Expected Features

v2.0 기능은 확실성과 가치 기준으로 명확히 분류된다.

**반드시 포함 (Table Stakes):**

- 전사파일/회의록 삭제 — 기본 CRUD 부재는 사용자가 앱 미완성으로 인지. 기존 DAO 인프라로 Low 복잡도
- Gemini API 키 설정 UI — BuildConfig 하드코딩은 사용자가 자체 키를 사용할 수 없어 앱 동작 자체가 막힘
- 삼성 녹음앱 공유 수신 — Android 표준 UX인 "공유" 버튼으로 새 입력 경로 확보

**탐색 후 결정 (Differentiators):**

- 삼성 녹음기 전사 자동 감지 — 진정한 원클릭 자동화지만 기술적 가능성 미확인. 실기기 스파이크 후 결정
- NotebookLM 연동 — AI 분석 통합 가치가 크지만 공식 API 부재로 반자동화로 한정
- Gemini OAuth 인증 — UX 개선이나 REST API OAuth 스코프 미확인으로 v2.1 이연 검토

**명시적으로 제외 (Anti-Features):**

- Accessibility Service 기반 자동 감지 — Google Play 정책 위반, 장애인 보조 목적만 허용
- NotebookLM 웹 스크래핑 — TOS 위반 가능, 인증 복잡도 폭발
- 다건 일괄 삭제 — v2.0 규모에서 과도, v2.1로 이연

### Architecture Approach

v1.0의 3-layer Clean Architecture(presentation/domain/data)를 완전히 유지하면서, 신규 기능은 기존 패턴에 자연스럽게 통합된다. 핵심 설계 원칙은 "MeetingSource 패턴": 모든 입력 경로(Plaud BLE, 삼성 자동 감지, 공유 Intent)가 동일한 MeetingEntity를 생성하고 동일한 후반부 파이프라인(Gemini 회의록 생성)으로 진입한다. 삼성 녹음기 경로는 이미 전사된 텍스트를 받으므로 STT 단계를 완전히 스킵(`TRANSCRIPT_IMPORTED` 상태 진입)한다. NotebookLM은 필수 경로가 아닌 선택적 후처리로 설계하여 외부 서비스 장애가 핵심 파이프라인을 중단시키지 않는다.

**주요 신규 컴포넌트:**

1. `ShareReceiverActivity` (presentation/share/) — 공유 Intent 전용 투명 Activity, 수신 후 즉시 파이프라인 진입 및 finish()
2. `SamsungRecorderObserver` (data/samsung/) — FileObserver/ContentObserver 구현체, 실기기 검증 후 확정
3. `NotebookLmMcpClient` (data/notebooklm/) — MCP 서버 HTTP 클라이언트, 선택적 후처리
4. `GoogleAuthManager` (data/auth/) — Credential Manager 래퍼

**주요 수정 대상:**

- `GeminiEngine`: BuildConfig 하드코딩 제거, 동적 API 키 주입 (인증 추상화 선행 필수)
- `MeetingRepositoryImpl`: 삭제 시 파일 삭제 로직 추가 (현재 DB만 삭제, 파일 삭제 로직 완전 부재)
- `MeetingEntity`: source 컬럼 추가 + Room DB v1→v2 마이그레이션
- `SettingsScreen/VM`: API 키 입력, Google 로그인, NotebookLM 설정으로 대폭 확장
- `AppDatabase`: exportSchema=true로 변경 (현재 false로 스키마 검증 불가)

### Critical Pitfalls

1. **삼성 녹음기 전사 자동 감지 의존** — 삼성 녹음기 내부 저장소는 Scoped Storage로 접근 불가. Phase 4 착수 전 48시간 내 실기기 adb 검증 필수 (`adb shell content query --uri content://media/external/file`, `adb shell ls -la /sdcard/`). 불가 판정 시 공유 방식이 기본 경로로 확정.

2. **NotebookLM API 없이 완전 자동화 설계** — 공식 REST API 미존재 확인됨. MCP 서버는 PC 로컬 의존. 구현 범위를 "공유 Intent + Custom Tabs" 반자동화로 한정하고 2일 타임박스 적용. 초과 시 기능 축소 확정.

3. **GeminiEngine OAuth 전환 시 기존 파이프라인 파괴** — 현재 SDK는 API 키만 지원. 인증 추상화(`GeminiAuthProvider` 인터페이스) 없이 OAuth를 직접 추가하면 API 키 모드와 OAuth 모드 공존 불가. 순서: API 키 설정 UI → 인증 추상화 → OAuth 추가.

4. **Room DB 삭제와 파일시스템 불일치** — 현재 `MeetingRepositoryImpl.deleteMeeting()`은 DB 레코드만 삭제, 파일 삭제 로직 완전 부재 코드 분석으로 확인. 오디오 파일이 계속 누적된다. 삭제 순서: 경로 조회 → 파일 삭제 → DB 삭제.

5. **Room DB exportSchema=false + 마이그레이션 누락** — 현재 `AppDatabase`가 `version = 1`, `exportSchema = false` 상태 확인. source 컬럼 추가 시 Migration 없으면 기존 사용자 앱 즉시 크래시. exportSchema=true 변경 및 `MIGRATION_1_2` 작성 필수. `fallbackToDestructiveMigration()` 절대 사용 금지.

## Implications for Roadmap

연구 결과를 종합하면 6개 Phase가 적절하다. 확실성이 높은 기능을 앞에 배치하여 조기 가치를 확보하고, 불확실한 기능은 탐색 단계를 거친 후 뒤에 배치한다.

### Phase 1: 기반 강화 (파일 삭제 + API 키 설정 + DB 마이그레이션)

**Rationale:** HIGH confidence 기능으로 즉각적 가치 제공. DB 스키마 변경을 이 시점에 처리하여 이후 Phase에서 마이그레이션 부채가 생기지 않도록 한다. GeminiEngine 인증 추상화도 여기서 선행하여 Phase 5 OAuth 추가가 안전해진다.

**Delivers:** 전사파일/회의록 삭제 기능 완성, Gemini API 키 사용자 설정, Room DB v2 마이그레이션, GeminiEngine 인증 추상화

**Addresses:** Table Stakes 2개 (삭제, API 키), CP-4 (파일 누수), MP-5 (마이그레이션 크래시), CP-3 사전 방지 (인증 추상화)

**Avoids:** CP-4, MP-5 — 조기 해결이 이후 Phase 전체의 안정성을 보장

**Research flag:** 표준 패턴 — 추가 리서치 불필요

### Phase 2: 공유 Intent 수신 (삼성 녹음앱 → 파이프라인)

**Rationale:** Android 표준 Share API 활용으로 HIGH confidence. 삼성 녹음기 자동 감지(Phase 4)를 기다리지 않고 안정적 수동 경로를 먼저 확보한다. Phase 4의 자동 감지가 실패해도 이 경로가 폴백이다.

**Delivers:** 삼성 녹음앱 공유 → 회의록 생성 E2E 동작, ShareReceiverActivity, TRANSCRIPT_IMPORTED 파이프라인 경로, MeetingSource enum

**Uses:** `Intent.ACTION_SEND` (플랫폼 API), 기존 WorkManager 파이프라인

**Implements:** ShareReceiverActivity (presentation/share/), PipelineStatus.TRANSCRIPT_IMPORTED 추가

**Addresses:** Table Stakes 3번째 (공유 수신)

**Avoids:** MP-1 (기존 MainActivity와 Intent 충돌 — 전용 Activity 생성), mP-1 (ANR — 비동기 처리)

**Research flag:** 표준 패턴. 단, 실기기에서 삼성 녹음앱 MIME 타입 및 Extra 키 확인 권장.

### Phase 3: NotebookLM 반자동 연동

**Rationale:** 외부 서비스 연동이므로 핵심 기능 이후에. API 부재를 이미 알고 있으므로 2일 타임박스로 범위를 한정. 초과 시 "Custom Tabs + 클립보드" 수준으로 확정.

**Delivers:** Custom Tabs로 NotebookLM 웹 열기 + 클립보드 복사, 선택적 MCP 프록시 경로 (네트워크 내 PC 의존), SettingsScreen NotebookLM 설정 섹션

**Implements:** `NotebookLmMcpClient`, 선택적 후처리 패턴 (Pattern 2)

**Avoids:** CP-2 (API 없는데 완전 자동화 설계), mP-4 (타임박스 초과로 시간 낭비)

**Research flag:** 2일 타임박스 자체가 리스크 관리. MCP 서버 → Android HTTP 통신 경로 검증 필요.

### Phase 4: 삼성 녹음기 자동 감지 (실기기 스파이크 선행)

**Rationale:** CP-1 함정을 피하기 위해 "48시간 스파이크"를 Phase 첫 작업으로 강제. 스파이크 결과가 불가 판정이면 Phase 전체를 드롭하고 Phase 2 공유 방식이 기본 경로로 확정된다.

**Delivers:** (스파이크 성공 시) FileObserver/ContentObserver 기반 자동 감지, 진정한 원클릭 파이프라인. (실패 시) Phase 드롭, 리소스를 Phase 5-6으로 재배분.

**Implements:** `SamsungRecorderObserver` (data/samsung/), `SamsungRecorderFileParser`

**Avoids:** CP-1 (불가능한 API에 수주 투자), MP-6 (ContentObserver 배터리/메모리 누수)

**Research flag:** 반드시 실기기 스파이크 선행. `adb shell content query --uri content://media/external/file`, `adb shell ls -la /sdcard/Samsung/` 등으로 삼성 녹음기 전사 파일 저장 경로 확인.

### Phase 5: Google 인증 (Credential Manager + Gemini OAuth)

**Rationale:** Phase 1에서 API 키 UI가 이미 완성되어 있으므로 OAuth는 "있으면 더 좋은" 옵션으로 후순위. Gemini REST API의 OAuth 스코프 미확인 리스크로 API 키 UI 완성 후에 시도.

**Delivers:** Google Sign-In (Credential Manager), OAuth 가능 시 API 키 없이 Gemini 사용, NotebookLM Google 계정 연계

**Uses:** `androidx.credentials:credentials:1.5.0`, `com.google.android.libraries.identity.googleid:1.1.1`

**Implements:** `GoogleAuthManager`, `GeminiAuthProvider` 인터페이스 완성, Retrofit OAuth 경로

**Avoids:** CP-3 (SDK OAuth 미지원 — REST API 직접 호출), MP-2 (레거시 GoogleSignIn 혼용)

**Research flag:** Gemini REST API OAuth 스코프 실기기 테스트 필수. 불가 판정 시 v2.1로 이연.

### Phase 6: Plaud BLE 실기기 디버깅

**Rationale:** 에뮬레이터에서 테스트 불가 (BLE 미지원). 다른 모든 기능과 독립적이어서 마지막 배치. 현재 코드는 존재하나 실기기 동작 미검증.

**Delivers:** Plaud 녹음기 BLE 연결 안정화, 오디오 파일 전송 E2E 동작, 엔드투엔드 Plaud → STT → 회의록 파이프라인

**Implements:** `PlaudSdkManager` 수정, `AudioCollectionService` 생명주기 개선

**Avoids:** MP-3 (에뮬레이터 시간 낭비), BLE GATT 133 에러 (재연결 시 2초 딜레이 + BluetoothGatt.close() 필수)

**Research flag:** nRF Connect로 Plaud BLE UUID 스캔 선행 권장. 실기기 + Plaud 하드웨어 필수.

### Phase Ordering Rationale

- Phase 1-2가 먼저인 이유: DB 스키마 변경과 기본 CRUD를 초반에 처리해야 이후 Phase에서 마이그레이션 부채가 생기지 않는다. GeminiEngine 인증 추상화도 Phase 1에서 처리해야 이후 OAuth 추가가 안전하다.
- Phase 2가 Phase 4보다 먼저인 이유: 자동 감지(Phase 4)가 실패해도 공유 수신(Phase 2)이 폴백으로 작동해야 한다. 폴백이 없는 상태에서 자동 감지부터 구현하면 실패 시 입력 경로가 완전히 없어진다.
- Phase 3 위치: 외부 서비스지만 타임박스를 걸어 리스크 관리. 핵심 파이프라인에 영향이 없는 선택적 후처리이므로 Phase 4 이전에 배치.
- Phase 4 후반: 실기기 스파이크 결과에 따라 Phase 전체가 드롭될 수 있어 후반에 배치. 실패해도 앞선 Phase들이 완성한 공유 방식이 폴백.
- Phase 5-6 마지막: OAuth는 API 키 완성 이후 선택적 개선. BLE 디버깅은 항상 마지막으로, 다른 기능과 병행 불가.

### Research Flags

추가 리서치가 필요한 Phase:

- **Phase 4:** 삼성 녹음기 저장 경로와 MediaStore 등록 여부 실기기 adb 검증 필수. 공식 문서 없음. 스파이크 결과에 따라 Phase 드롭 여부 결정.
- **Phase 5:** Gemini REST API OAuth 스코프 지원 여부 실기기 테스트 필수. 불가 시 v2.1 이연.
- **Phase 3:** MCP 서버 HTTP 인터페이스 존재 여부 및 인증 방식 확인 필요.

표준 패턴으로 추가 리서치 불필요:

- **Phase 1:** Room 마이그레이션, DataStore API 키 저장, 파일 삭제는 표준 Android 패턴
- **Phase 2:** Android Share Intent 수신은 공식 문서가 명확한 표준 패턴
- **Phase 6:** BLE 디버깅은 기존 코드 수정이며 새로운 패턴 탐색 불필요

## Confidence Assessment

| 영역 | Confidence | 비고 |
| --- | --- | --- |
| Stack | HIGH | 기존 스택 검증 완료. 신규 3개 라이브러리 공식 문서 확인 |
| Features | MEDIUM | Table Stakes 3개는 HIGH. Differentiators(자동 감지, OAuth)는 LOW — 실기기 의존 |
| Architecture | HIGH | 기존 코드베이스 58개 파일 직접 분석. 수정 대상과 신규 컴포넌트 명확히 식별 |
| Pitfalls | HIGH | 코드베이스 직접 분석 기반. 파일 삭제 로직 부재, exportSchema=false 등 실제 문제 확인 |

**전체 신뢰도:** MEDIUM — 기술 스택과 아키텍처는 HIGH이나, 삼성 녹음기 자동 감지와 Gemini OAuth는 실기기 검증 전까지 LOW 상태를 유지.

### Gaps to Address

- **삼성 녹음기 전사 파일 위치:** 내부 저장소 vs 외부 저장소 vs MediaStore 등록 여부 미확인. Phase 4 착수 전 adb로 반드시 확인. 파이프라인 경로 결정에 직결. 불가 판정 시 Phase 4 드롭.
- **Gemini API OAuth 스코프:** `generative-language` 스코프 지원 여부 미확인. Phase 5 착수 전 Google OAuth Playground에서 테스트. 불가 시 Vertex AI(유료) 경유 또는 v2.1 이연.
- **Google AI Client SDK OAuth 미지원:** 코드 분석 기반 추론. SDK 내부 코드 또는 공식 이슈 확인 필요. 불가 확인 시 Retrofit REST 직접 호출로 구현.
- **삼성 녹음앱 공유 MIME 타입 및 Extra 키:** `text/plain` + `EXTRA_TEXT` 가정이나 버전별 차이 가능. Phase 2 구현 후 실기기 검증.
- **MCP 서버 HTTP 엔드포인트:** `notebooklm-mcp`의 HTTP 인터페이스 존재 여부 및 인증 방식 미확인. Phase 3에서 스파이크 필요.
- **googleid 라이브러리 버전:** v1.1.1은 훈련 데이터 기반. Phase 5 착수 전 Maven Central에서 최신 버전 확인 권장.

## Sources

### Primary (HIGH confidence)

- 프로젝트 코드베이스 직접 분석 (`GeminiEngine.kt`, `MeetingDao.kt`, `MeetingRepositoryImpl.kt`, `PlaudSdkManager.kt`, `AudioCollectionService.kt`, `AndroidManifest.xml`, `AppDatabase.kt`, `MeetingEntity.kt`) — 현재 구현 상태, 누락 로직, DB 스키마
- Android Credential Manager 릴리스: <https://developer.android.com/jetpack/androidx/releases/credentials> — v1.5.0 stable 확인
- Android Security Crypto 릴리스: <https://developer.android.com/jetpack/androidx/releases/security> — v1.1.0 확인
- Android ContentObserver: <https://developer.android.com/reference/android/database/ContentObserver>
- Android Share Intent 수신: <https://developer.android.com/training/sharing/receive>
- Chrome Custom Tabs: <https://developer.android.com/develop/ui/views/layout/webapps/custom-tabs>
- Room 마이그레이션 가이드: <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- Android Scoped Storage: <https://developer.android.com/about/versions/11/privacy/storage>
- Android BLE 가이드: <https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview>

### Secondary (MEDIUM confidence)

- NotebookLM MCP 서버: 프로젝트 내 환경 설정 확인 — MCP 서버 존재하나 Android 직접 호출 미검증
- Android Credential Manager: <https://developer.android.com/identity/sign-in/credential-manager> — 권장 방식 확인, Gemini 연계는 MEDIUM
- EncryptedSharedPreferences 안정성 — 알려진 이슈, 공식 라이브러리 문서

### Tertiary (LOW confidence)

- Gemini REST API OAuth 스코프 (`generative-language`): 공식 문서 접근 실패, 훈련 데이터 기반 — 실기기 테스트 필수
- 삼성 Voice Recorder 내부 파일 저장 구조: 비공개, 실기기 검증 필수
- Google AI Client SDK OAuth 미지원: 코드 분석 기반 추론 — 공식 확인 필요
- googleid 라이브러리 v1.1.1: 훈련 데이터 기반 — Maven Central 최신 버전 확인 권장

---

*Research completed: 2026-03-25*
*Ready for roadmap: yes*
