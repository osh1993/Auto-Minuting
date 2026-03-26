# Phase 14: Plaud 연결 프로토콜 분석 - Research

**Researched:** 2026-03-26
**Domain:** BLE 프로토콜 리버스 엔지니어링 / Android APK 디컴파일
**Confidence:** MEDIUM

## Summary

Plaud Note 녹음기의 통신 프로토콜을 리버스 엔지니어링하여 BLE GATT UUID와 파일 전송 프로토콜을 파악하는 Phase이다. 핵심 발견으로, **Plaud 공식 SDK (plaud-sdk)가 GitHub에 공개되어 있으며**(https://github.com/Plaud-AI/plaud-sdk), 현재 v0.2.6까지 릴리즈되어 있다. 이 SDK는 AAR 형태로 Android 앱에 통합 가능하고, BLE 스캔/연결/파일 전송을 지원한다. 프로젝트의 기존 스텁 코드(`com.nicebuild.sdk.*`)는 이 SDK의 API 시그니처를 기반으로 작성된 것으로 확인된다.

리버스 엔지니어링은 두 가지 경로로 병행해야 한다: (1) 공식 SDK를 통합하여 정상 동작 확인, (2) APK 디컴파일 + BLE 스니핑으로 프로토콜 상세 파악. Go/No-Go 판정은 자체 BLE 구현 가능 여부에 달려 있으며, SDK 의존 없이 직접 GATT 통신이 가능한지 확인하는 것이 목표다.

**Primary recommendation:** 공식 Plaud SDK(v0.2.6) 다운로드 및 통합을 먼저 시도하고, 동시에 jadx로 Plaud 앱/SDK AAR을 디컴파일하여 GATT UUID와 프로토콜을 추출한다. nRF Connect와 HCI snoop log로 실제 통신을 검증한다.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Plaud Note (카드형 AI 녹음기) 사용
- **D-02:** 관찰 결과: 기본 연결은 BLE (Bluetooth 설정에 안 보이는 건 BLE 특성), 파일 전송도 BLE로 진행. "빠른 전송" 선택 시 Wi-Fi Direct 연결
- **D-03:** APK 디컴파일 (jadx) — Plaud 앱에서 BLE GATT 서비스/특성 UUID, 파일 전송 명령어/응답 패턴 추출
- **D-04:** BLE 스니핑 (nRF Connect) — 실제 Plaud 앱↔녹음기 BLE 통신을 실시간 관찰, GATT 프로파일 확인
- **D-05:** Wi-Fi Direct 흐름도 확인 — 빠른 전송 모드의 프로토콜 파악 (부차적)
- **D-06:** Go 조건: BLE GATT 서비스/특성 UUID를 파악하고, 파일 전송 프로토콜(명령어/응답 패턴)을 문서화할 수 있으면 Go
- **D-07:** No-Go 조건: 프로토콜이 암호화되어 있거나, 인증 토큰이 서버 의존적이어서 재현 불가능하면 No-Go
- **D-08:** No-Go 시 대안: Plaud SDK 의존 유지 (현재 Phase 13 코드)

### Claude's Discretion
- APK 디컴파일 도구 선택 (jadx 권장, apktool 보조)
- BLE 스니핑 상세 절차
- 분석 결과 문서 구조

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLUD-02 | Plaud 앱의 녹음기 연결 프로토콜을 리버스 엔지니어링하여 연결 방식(BLE/Wi-Fi/기타)을 파악한다 | APK 디컴파일(jadx) + BLE 스니핑(nRF Connect) + HCI snoop log 분석으로 프로토콜 문서화. 공식 SDK(v0.2.6) 존재 확인으로 대안 경로 확보 |
</phase_requirements>

## Standard Stack

### 핵심 발견: Plaud 공식 SDK 존재

| Resource | Detail | Confidence |
|----------|--------|------------|
| Plaud SDK GitHub | https://github.com/Plaud-AI/plaud-sdk | HIGH |
| 최신 버전 | v0.2.6 (2025-10 릴리즈) | MEDIUM |
| SDK 문서 | https://docs.plaud.ai/api_guide/sdks/android | MEDIUM |
| 형태 | plaud-sdk.aar (Android Archive) | HIGH |
| Min SDK | API 21 (Android 5.0) | MEDIUM |
| Target SDK | API 34 (Android 14) | MEDIUM |

기존 프로젝트의 `com.nicebuild.sdk.*` 스텁은 이 공식 SDK의 API 시그니처를 모방한 것이다. SDK를 실제 다운로드하여 AAR 내부를 jadx로 분석하면, BLE 프로토콜 전체를 파악할 수 있다.

### 리버스 엔지니어링 도구

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| jadx | latest (47k+ stars) | APK/AAR → Java 디컴파일 | 가장 신뢰할 수 있는 Android 디컴파일러. GUI + CLI 모두 지원 |
| apktool | 2.9+ | 리소스/매니페스트 디코딩 | XML 리소스 디코딩에 jadx보다 우수 |
| nRF Connect | latest | BLE GATT 서비스 탐색 | Nordic 공식 앱, GATT 서비스/특성 실시간 조회 |
| Wireshark | 4.x | BLE 패킷 분석 | HCI snoop log 분석 표준 도구 |

### 설치

```bash
# jadx (Windows — GitHub Releases에서 zip 다운로드 후 PATH 추가)
# https://github.com/skylot/jadx/releases
# jadx-gui.bat 실행 또는 jadx CLI 사용

# apktool (선택적)
# https://apktool.org/docs/install

# nRF Connect — Play Store에서 설치
# Wireshark — https://www.wireshark.org/download.html
```

## Architecture Patterns

### 리버스 엔지니어링 워크플로우 (권장 순서)

```
Step 1: SDK AAR 확보
├── GitHub Releases에서 plaud-sdk v0.2.6 다운로드
├── AAR 파일을 jadx로 디컴파일
└── GATT UUID, 프로토콜 명령어 추출

Step 2: Plaud 앱 APK 디컴파일
├── Play Store에서 APK 다운로드 (ai.plaud.android.plaud v3.12.0)
├── jadx-gui로 열기
├── BLE 관련 클래스 탐색 (BluetoothGatt, GATT UUID 검색)
└── 파일 전송 로직 추적

Step 3: BLE 실시간 스니핑
├── nRF Connect로 Plaud Note 스캔 → 서비스/특성 목록 수집
├── Android HCI snoop log 활성화
├── Plaud 앱으로 실제 파일 전송 수행
└── Wireshark로 HCI 로그 분석

Step 4: 프로토콜 문서화
├── GATT 서비스/특성 UUID 매핑
├── 파일 전송 명령어/응답 시퀀스 다이어그램
├── Wi-Fi Direct 전환 프로토콜 (부차적)
└── Go/No-Go 판정
```

### 분석 결과 문서 구조 (권장)

```
.planning/phases/14-plaud-protocol/
├── 14-RESEARCH.md          # 이 문서
├── 14-CONTEXT.md           # 사용자 결정
├── protocol-findings/
│   ├── gatt-services.md    # GATT 서비스/특성 UUID 목록
│   ├── file-transfer.md    # 파일 전송 프로토콜 시퀀스
│   ├── wifi-direct.md      # Wi-Fi Direct 전환 프로토콜
│   └── go-nogo.md          # 최종 판정 + 근거
└── captures/               # HCI 로그, 스크린샷 (gitignore 권장)
```

### Pattern 1: APK/AAR 디컴파일에서 GATT UUID 추출

**What:** jadx로 디컴파일된 코드에서 BLE GATT 관련 상수를 검색
**When to use:** SDK AAR 또는 Plaud APK를 jadx로 연 후
**Example:**
```java
// jadx-gui에서 검색할 키워드들:
// 1. UUID 문자열 패턴
"0000xxxx-0000-1000-8000-00805f9b34fb"  // 표준 BLE 16-bit UUID
"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  // 커스텀 128-bit UUID

// 2. BluetoothGattService / BluetoothGattCharacteristic 생성
new BluetoothGattService(uuid, ...)
new BluetoothGattCharacteristic(uuid, ...)

// 3. SDK 내부 클래스 탐색
// TntAgent, BleAgent, BleCore 등의 실제 구현체
// writeCharacteristic, readCharacteristic 호출부
```

### Pattern 2: HCI Snoop Log 캡처

**What:** Android 개발자 옵션에서 BLE 패킷을 캡처하여 Wireshark로 분석
**When to use:** 실제 Plaud 앱↔녹음기 통신을 관찰할 때
**Example:**
```bash
# 1. Android 설정 → 개발자 옵션 → "Bluetooth HCI 스눕 로그 사용" 활성화
# 2. Bluetooth 토글 OFF → ON (로그 초기화)
# 3. Plaud 앱으로 연결 + 파일 전송 수행
# 4. 로그 추출

adb pull /sdcard/btsnoop_hci.log ./captures/
# 또는 삼성 기기:
adb pull /data/misc/bluetooth/logs/btsnoop_hci.log ./captures/

# 5. Wireshark에서 열기, 필터 적용
# 필터: bluetooth.dst == [Plaud MAC 주소]
# ATT 프로토콜 → Write Request/Response 패턴 관찰
```

### Pattern 3: nRF Connect GATT 탐색

**What:** nRF Connect 앱으로 Plaud Note의 GATT 서비스/특성을 직접 탐색
**When to use:** 가장 먼저 수행 — GATT 프로파일 확인
**Example:**
```
1. nRF Connect 앱 설치 (Play Store)
2. Scanner 탭 → Plaud Note 장치 찾기 (BLE 광고 패킷)
3. CONNECT 터치
4. 서비스 목록 확인:
   - Generic Access (0x1800)
   - Generic Attribute (0x1801)
   - Device Information (0x180A)
   - [커스텀 서비스 UUID] ← 파일 전송 관련
5. 각 특성의 Properties 확인:
   - Read / Write / Write Without Response / Notify / Indicate
6. 스크린샷 촬영하여 문서화
```

### Anti-Patterns to Avoid
- **직접 BLE 프레임워크 구현 먼저 시작:** 프로토콜 분석 없이 코드를 작성하면 안 된다. 분석 결과를 문서화한 후 구현해야 한다
- **SDK 없이 처음부터 구현:** 공식 SDK가 존재하므로, SDK 통합이 불가능한 명확한 이유가 없으면 SDK 활용이 우선이다
- **ProGuard 난독화 무시:** Plaud 앱은 높은 확률로 난독화되어 있다. jadx에서 난독화된 클래스명이 보이면 mapping 파일 없이 분석해야 한다

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| APK 디컴파일 | 자체 dex 파서 | jadx (GUI + CLI) | 47k+ stars, 지속적 업데이트, 난독화 처리 내장 |
| BLE GATT 탐색 | 자체 BLE 스캐너 앱 | nRF Connect | Nordic 공식, GATT 전체 프로파일 즉시 확인 |
| BLE 패킷 분석 | 자체 로그 파서 | Wireshark + HCI snoop | BLE 프로토콜 디시섹션 완벽 지원 |
| Plaud 연결 | 자체 BLE 프로토콜 구현 | Plaud 공식 SDK (plaud-sdk.aar) | 공식 지원, API 안정성 보장 |

**Key insight:** 공식 SDK가 존재하므로, 리버스 엔지니어링의 목적은 "SDK 없이 자체 구현이 가능한가"를 판단하는 것이다. SDK가 사용 가능하면 Go/No-Go의 기준이 달라진다.

## Common Pitfalls

### Pitfall 1: 난독화된 코드에서 길을 잃음
**What goes wrong:** jadx로 디컴파일하면 대부분의 클래스가 `a.b.c`, `d.e.f` 같은 난독화된 이름으로 나타남
**Why it happens:** ProGuard/R8 난독화가 적용되어 원래 클래스명이 제거됨
**How to avoid:** UUID 문자열, BluetoothGatt API 호출, 에러 메시지 문자열을 기준으로 역추적. 문자열 상수는 난독화되지 않음
**Warning signs:** 모든 클래스가 한 글자 이름이면 난독화 적용됨

### Pitfall 2: HCI Snoop 로그가 비어 있음
**What goes wrong:** 개발자 옵션에서 활성화했는데 로그 파일이 비거나 없음
**Why it happens:** 일부 삼성 기기에서 로그 경로가 다르거나, Bluetooth를 토글하지 않아 로그가 시작되지 않음
**How to avoid:** Bluetooth OFF → 스눕 로그 활성화 → Bluetooth ON 순서 준수. 삼성 기기는 `/data/misc/bluetooth/logs/` 경로 확인
**Warning signs:** 파일 크기가 0바이트이거나 로그에 BLE 패킷이 없음

### Pitfall 3: nRF Connect로 연결하면 Plaud 앱이 끊김
**What goes wrong:** nRF Connect와 Plaud 앱이 동시에 같은 기기에 BLE 연결할 수 없음
**Why it happens:** BLE 기기는 보통 하나의 Central 연결만 허용 (Peripheral 제약)
**How to avoid:** nRF Connect는 GATT 탐색용으로만 사용하고, 실제 통신 관찰은 HCI snoop log로 수행
**Warning signs:** Plaud 앱에서 "연결 끊김" 오류 발생

### Pitfall 4: Wi-Fi Direct 분석에 시간 과다 소비
**What goes wrong:** Wi-Fi Direct "빠른 전송" 프로토콜 분석에 많은 시간을 투입하나, 핵심은 BLE 기본 전송임
**Why it happens:** Wi-Fi Direct는 Plaud의 "빠른 전송" 옵션이며 기본 전송은 BLE
**How to avoid:** BLE 프로토콜 분석을 우선하고, Wi-Fi Direct는 부차적으로 처리 (D-05에 명시됨)
**Warning signs:** BLE 분석이 완료되지 않았는데 Wi-Fi Direct에 착수

### Pitfall 5: 공식 SDK 존재를 무시하고 처음부터 리버스 엔지니어링
**What goes wrong:** SDK AAR을 분석하면 프로토콜을 직접 볼 수 있는데, APK에서 난독화 코드를 해독하느라 시간 낭비
**Why it happens:** SDK AAR은 보통 난독화 수준이 APK보다 낮음 (인터페이스가 공개 API이므로)
**How to avoid:** SDK AAR 디컴파일을 APK 분석보다 먼저 수행. SDK 내부에서 UUID, 프로토콜 상수를 먼저 추출
**Warning signs:** APK 난독화 클래스를 분석하는데 SDK에 같은 로직이 더 읽기 쉽게 존재

## Code Examples

### jadx CLI로 AAR/APK 디컴파일

```bash
# SDK AAR 디컴파일 (난독화 수준 낮을 가능성 높음 — 먼저 수행)
jadx -d ./decompiled-sdk/ plaud-sdk.aar

# Plaud 앱 APK 디컴파일
jadx -d ./decompiled-app/ plaud-app.apk

# 특정 패턴 검색 (UUID 상수)
grep -r "0000.*-0000-1000-8000-00805f9b34fb" ./decompiled-sdk/
grep -ri "BluetoothGattService\|BluetoothGattCharacteristic\|UUID" ./decompiled-sdk/
grep -ri "writeCharacteristic\|readCharacteristic\|setCharacteristicNotification" ./decompiled-sdk/
```

### nRF Connect 분석 결과 기록 양식

```markdown
## Plaud Note GATT Profile

### 서비스 목록
| Service UUID | Type | Description |
|-------------|------|-------------|
| 0x1800 | Standard | Generic Access |
| 0x1801 | Standard | Generic Attribute |
| 0x180A | Standard | Device Information |
| [UUID-TBD] | Custom | Plaud File Transfer Service |
| [UUID-TBD] | Custom | Plaud Control Service |

### 특성 목록 (Custom Service)
| Characteristic UUID | Properties | Description |
|--------------------|------------|-------------|
| [UUID-TBD] | Write | Command 전송 |
| [UUID-TBD] | Notify | Response 수신 |
| [UUID-TBD] | Write Without Response | 파일 데이터 전송 |
```

### BLE 파일 전송 일반 패턴 (참고용)

```kotlin
// 일반적인 BLE 파일 전송 프로토콜 패턴 (Nordic DFU 참고)
// Plaud가 이와 유사한 패턴을 사용할 가능성 높음

// 1. Control Point에 명령 Write
// 2. Data Characteristic에 파일 데이터 Write Without Response (MTU 단위)
// 3. Notification으로 진행률/완료 수신
// 4. CRC 검증으로 무결성 확인

// 일반적인 MTU: 20바이트 (기본) ~ 253바이트 (MTU 협상 후)
// Nordic DFU 기준 전송 속도: ~2 kBps (1M PHY) ~ ~4 kBps (2M PHY)

// Plaud BLE 5.2 지원 → 2M PHY, DLE (Data Length Extension) 가능
// 예상 최대 MTU: 247바이트 (BLE 5.x 표준)
```

## Plaud Note 하드웨어 사양 (확인된 정보)

| Spec | Value | Source | Confidence |
|------|-------|--------|------------|
| Bluetooth | BLE 5.2 | Plaud 공식 사이트 | HIGH |
| 연결 방식 | BLE (기본) + Wi-Fi Direct (빠른 전송) | 사용자 관찰 + 공식 지원 | HIGH |
| 앱 패키지 | ai.plaud.android.plaud | Play Store | HIGH |
| 앱 버전 | 3.12.0 | Play Store | HIGH |
| SDK 패키지 | com.nicebuild.sdk.* (추정) | 프로젝트 스텁 코드 | MEDIUM |
| SDK GitHub | Plaud-AI/plaud-sdk | GitHub 공개 | HIGH |
| SDK 최신 | v0.2.6 | GitHub Releases | MEDIUM |

## Go/No-Go 판정 기준 상세화

### Go 시나리오

| 조건 | 판정 | 다음 단계 |
|------|------|----------|
| GATT UUID 파악 + 파일 전송 프로토콜 문서화 완료 | **Go (자체 구현)** | Phase 13 코드를 직접 BLE API로 교체 |
| 공식 SDK AAR 통합 가능 + 정상 동작 확인 | **Go (SDK 활용)** | SDK AAR을 libs/에 배치, 스텁 코드 제거 |
| GATT UUID는 파악했으나 인증 토큰이 필요 | **Partial Go** | SDK 인증 흐름 분석 후 토큰 획득 방법 문서화 |

### No-Go 시나리오

| 조건 | 판정 | 대안 |
|------|------|------|
| 프로토콜이 암호화(AES 등)되어 키 없이 해독 불가 | **No-Go** | SDK 의존 유지 (D-08) |
| 인증 토큰이 Plaud 서버에서만 발급 가능 | **No-Go** | SDK 의존 유지 (D-08) |
| SDK AAR이 Play Store 전용 라이선스 | **Conditional No-Go** | SDK 조건 확인 후 재판정 |

## 법적/윤리적 고려사항

| 항목 | 판단 | 근거 |
|------|------|------|
| APK 디컴파일 합법성 (EU) | 허용 | EU Software Directive — 상호운용성 목적의 디컴파일은 합법 |
| APK 디컴파일 합법성 (US) | 허용 | Fair Use — Sega v. Accolade, Sony v. Connectix 판례 |
| APK 디컴파일 합법성 (한국) | 주의 필요 | 한국 저작권법 제101조의4 — 정당한 권한을 가진 자의 호환성 확보 목적 디컴파일 허용, 단 범위 제한적 |
| Play Store 정책 | 위반 가능 | Google Play ToS는 리버스 엔지니어링 금지 조항 포함. 단 개인 연구 목적은 회색 지대 |
| 공식 SDK 사용 | 안전 | 공개 SDK를 정상적으로 사용하는 것은 법적 문제 없음 |

**권장:** 공식 SDK를 최우선으로 활용하고, APK 디컴파일은 SDK에서 부족한 프로토콜 세부 사항 보완 목적으로만 사용한다. 디컴파일 결과를 공개 배포하지 않는다.

## Open Questions

1. **Plaud SDK AAR 접근성**
   - What we know: GitHub에 plaud-sdk 저장소 존재 (v0.2.6), docs 경로 확인
   - What's unclear: AAR 다운로드가 실제로 가능한지 (Releases 페이지 접근 필요), API 키 발급 조건
   - Recommendation: GitHub Releases에서 AAR 다운로드 시도, 불가 시 Plaud 개발자 플랫폼 등록

2. **SDK API 키 요구사항**
   - What we know: `NiceBuildSdk.initSdk(context, appKey, appSecret, ...)` 패턴으로 appKey/appSecret 필요
   - What's unclear: 개발자 등록 절차, 무료/유료 여부, 승인 소요 시간
   - Recommendation: https://www.plaud.ai/pages/developer-platform 에서 등록 시도

3. **BLE 프로토콜 암호화 여부**
   - What we know: BLE 5.2 지원, Plaud 앱이 BLE로 파일 전송
   - What's unclear: 데이터 전송이 평문인지, 암호화(AES-CCM 등)가 적용되었는지
   - Recommendation: HCI snoop log 분석 시 LL_ENC_REQ (Link Layer 암호화) 여부 확인

4. **Wi-Fi Direct 상세 프로토콜**
   - What we know: "빠른 전송" 시 Wi-Fi Direct 사용 (사용자 관찰)
   - What's unclear: BLE에서 Wi-Fi Direct로 전환하는 핸드오프 프로토콜, Wi-Fi Direct 위 전송 프로토콜 (HTTP? TCP 소켓? 독자 프로토콜?)
   - Recommendation: 부차적 분석 항목 (D-05). BLE 분석 완료 후 진행

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| 리버스 엔지니어링만으로 프로토콜 파악 | 공식 SDK(v0.2.6) + 리버스 엔지니어링 병행 | 2025 Q3 (SDK 공개) | SDK가 존재하므로 완전한 리버스 엔지니어링 불필요. SDK 분석이 더 효율적 |
| Plaud 클라우드 API만 존재 | BLE SDK + 클라우드 API 모두 제공 | 2025 | OpenPlaud(클라우드) 대비 로컬 BLE 연결이 가능해짐 |

## Sources

### Primary (HIGH confidence)
- Plaud 공식 사이트 (https://www.plaud.ai) — 하드웨어 사양, BLE 5.2 확인
- Plaud SDK GitHub (https://github.com/Plaud-AI/plaud-sdk) — 공식 SDK 존재 확인, v0.2.6
- jadx GitHub (https://github.com/skylot/jadx) — 디컴파일러 도구 확인
- Gadgetbridge BT Protocol RE Wiki (https://codeberg.org/Freeyourgadget/Gadgetbridge/wiki/BT-Protocol-Reverse-Engineering) — BLE RE 방법론
- Android Developer — Wi-Fi Direct (https://developer.android.com/develop/connectivity/wifi/wifip2p) — P2P API

### Secondary (MEDIUM confidence)
- Plaud SDK Docs (https://docs.plaud.ai/api_guide/sdks/android) — SDK 문서 (접근 실패, 존재 확인만)
- OpenPlaud (https://github.com/openplaud/openplaud) — 클라우드 API 기반 대안 (BLE 아님)
- Nordic DFU Protocol — BLE 파일 전송 일반 패턴 참고
- BLE GATT RE Guide (https://jcjc-dev.com/2023/03/19/reversing-domyos-el500-elliptical/) — 실전 RE 방법론

### Tertiary (LOW confidence)
- APK 디컴파일 법적 판단 (한국법) — 일반적 해석만 확인, 판례 미확인

## Metadata

**Confidence breakdown:**
- Standard stack (도구): HIGH — jadx, nRF Connect, Wireshark 모두 검증된 표준 도구
- Plaud SDK 존재: HIGH — GitHub에서 직접 확인
- Plaud SDK 통합 가능성: MEDIUM — AAR 다운로드 및 API 키 발급 미검증
- BLE 프로토콜 분석 방법론: HIGH — Gadgetbridge, Nordic 등 검증된 방법론
- Go/No-Go 예측: MEDIUM — 프로토콜 암호화 여부 미확인

**Research date:** 2026-03-26
**Valid until:** 2026-04-26 (Plaud SDK 업데이트 주기 약 1개월)
