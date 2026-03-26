# Phase 14: Plaud 연결 프로토콜 분석 - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Plaud Note 녹음기와 스마트폰 간 통신 프로토콜을 리버스 엔지니어링하여, BLE GATT UUID와 파일 전송 프로토콜을 파악한다. 자체 구현 가능 여부를 Go/No-Go로 판정한다.

</domain>

<decisions>
## Implementation Decisions

### 보유 장비
- **D-01:** Plaud Note (카드형 AI 녹음기) 사용
- **D-02:** 관찰 결과: 기본 연결은 BLE (Bluetooth 설정에 안 보이는 건 BLE 특성), 파일 전송도 BLE로 진행. "빠른 전송" 선택 시 Wi-Fi Direct 연결

### 분석 방법론
- **D-03:** APK 디컴파일 (jadx) — Plaud 앱에서 BLE GATT 서비스/특성 UUID, 파일 전송 명령어/응답 패턴 추출
- **D-04:** BLE 스니핑 (nRF Connect) — 실제 Plaud 앱↔녹음기 BLE 통신을 실시간 관찰, GATT 프로파일 확인
- **D-05:** Wi-Fi Direct 흐름도 확인 — 빠른 전송 모드의 프로토콜 파악 (부차적)

### Go/No-Go 기준
- **D-06:** Go 조건: BLE GATT 서비스/특성 UUID를 파악하고, 파일 전송 프로토콜(명령어/응답 패턴)을 문서화할 수 있으면 Go
- **D-07:** No-Go 조건: 프로토콜이 암호화되어 있거나, 인증 토큰이 서버 의존적이어서 재현 불가능하면 No-Go
- **D-08:** No-Go 시 대안: Plaud SDK 의존 유지 (현재 Phase 13 코드)

### Claude's Discretion
- APK 디컴파일 도구 선택 (jadx 권장, apktool 보조)
- BLE 스니핑 상세 절차
- 분석 결과 문서 구조

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 기존 BLE 코드 (Phase 13)
- `app/src/main/java/com/autominuting/data/audio/PlaudSdkManager.kt` — 현재 BLE 연결/파일전송 코드
- `app/src/main/java/com/autominuting/data/audio/PlaudBleAgentListener.kt` — BLE 콜백 구현
- `app/src/main/java/com/nicebuild/sdk/` — SDK 스텁 (실제 SDK 시그니처 참조용)

### Plaud SDK 스텁 분석
- `app/src/main/java/com/nicebuild/sdk/ble/TntAgent.kt` — BLE Agent 패턴
- `app/src/main/java/com/nicebuild/sdk/ble/BleCore.kt` — 파일 목록 조회

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PlaudSdkManager` — SDK 스텁 기반 BLE 연결 흐름 (리버스 엔지니어링 결과로 교체 가능)
- `PlaudBleAgentListener` — BLE 콜백 StateFlow 래핑 패턴 (재사용 가능)
- `AudioCollectionService` — Foreground Service BLE 유지 패턴

### Integration Points
- 리버스 엔지니어링 결과가 Phase 13 코드의 실제 구현 기반이 됨
- GATT UUID 파악 시 `PlaudSdkManager`를 직접 BLE API 호출로 교체 가능

</code_context>

<specifics>
## Specific Ideas

- Plaud 앱 APK는 Play Store에서 다운로드 가능 (패키지: ai.plaud.android.plaud)
- nRF Connect for Mobile (Nordic Semiconductor)로 BLE 스캔/연결/서비스 탐색 가능
- Android HCI snoop log 활성화로 Bluetooth 패킷 캡처 가능

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-plaud-protocol*
*Context gathered: 2026-03-26*
