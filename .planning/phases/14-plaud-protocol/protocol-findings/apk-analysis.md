# Plaud 앱 APK 디컴파일 분석 결과

**분석 일시:** 2026-03-26
**분석 대상:** Plaud 앱 (`ai.plaud.android.plaud` v3.12.0)
**분석 방법:** SDK 문서 교차 분석 + 공개 정보 종합 (APK 직접 디컴파일 미수행)

## APK 확보 상태

### 다운로드 시도 결과

| 경로 | 결과 | 비고 |
|------|------|------|
| APK 미러 사이트 (apkmirror.com 등) | **시도 불가** | 현재 환경에서 APK 바이너리 다운로드 불가 |
| `adb pull` (기기 추출) | **시도 불가** | 실기기 미연결 |
| Play Store 직접 | **불가** | CLI 환경에서 Play Store 접근 불가 |
| jadx 디컴파일 | **미수행** | APK 확보 불가로 인해 불가 |

**결론:** 현재 실행 환경에서 APK를 확보하여 jadx로 디컴파일하는 것이 불가능하다. 대신 SDK 공식 문서에서 확인된 정보와 SDK 분석 결과를 교차 검증하여 BLE 연결 흐름과 프로토콜을 문서화한다.

### 향후 APK 확보 방법 (수동 수행 필요)

```bash
# 방법 1: adb로 기기에서 추출
adb shell pm path ai.plaud.android.plaud
# 출력 예: package:/data/app/~~.../ai.plaud.android.plaud-.../base.apk
adb pull /data/app/~~.../base.apk plaud-app.apk

# 방법 2: APK 미러 사이트
# https://www.apkmirror.com/apk/plaud/ 에서 v3.12.0 다운로드

# 방법 3: jadx 디컴파일
jadx -d ./decompiled-app/ plaud-app.apk
```

## 난독화 수준 평가 (추정)

| 항목 | 추정 | 근거 |
|------|------|------|
| ProGuard/R8 적용 | **높은 확률** | Play Store 배포 앱은 거의 모두 난독화 적용 |
| 클래스명 난독화 | **적용됨** (추정) | 내부 비즈니스 로직 클래스는 a.b.c 형태 |
| 문자열 상수 보존 | **보존됨** (추정) | UUID, 에러 메시지, 로그 태그는 난독화 불가 |
| SDK 클래스 보존 | **보존됨** | `com.nicebuild.sdk.*`는 라이브러리이므로 consumer ProGuard rules 적용 |
| 리소스 난독화 | **미적용** (추정) | Android 리소스 파일은 난독화가 어려움 |

**분석 전략 (APK 확보 시):**
- 난독화된 클래스에서 BLE API 호출을 역추적: `BluetoothGatt.*`, `writeCharacteristic`, `readCharacteristic`
- UUID 문자열 상수 검색: `grep -ri "e3d196c2" ./decompiled-app/`
- 에러 메시지 문자열로 관련 클래스 위치 파악

## BLE 연결 흐름 (SDK 문서 기반 재구성)

### 전체 시퀀스 다이어그램

```
┌──────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────┐
│ Plaud 앱 │     │ SDK (AAR)│     │ Android BLE  │     │ Plaud    │
│ (APK)    │     │          │     │ Stack        │     │ 녹음기   │
└────┬─────┘     └────┬─────┘     └──────┬───────┘     └────┬─────┘
     │                │                  │                   │
     │ 1. initSdk()   │                  │                   │
     │ (appKey/secret) │                  │                   │
     │───────────────>│                  │                   │
     │                │                  │                   │
     │ 2. scanBle(true)│                 │                   │
     │───────────────>│                  │                   │
     │                │ startLeScan()    │                   │
     │                │─────────────────>│                   │
     │                │                  │  BLE Advertisement│
     │                │                  │<──────────────────│
     │                │ onLeScanResult() │                   │
     │                │<─────────────────│                   │
     │ scanBleDevice  │                  │                   │
     │ Receiver()     │                  │                   │
     │<───────────────│                  │                   │
     │                │                  │                   │
     │ 3. connectionBLE()               │                   │
     │───────────────>│                  │                   │
     │                │ connectGatt()    │                   │
     │                │─────────────────>│                   │
     │                │                  │ BLE Connection    │
     │                │                  │──────────────────>│
     │                │                  │ GATT Connected    │
     │                │                  │<──────────────────│
     │                │ discoverServices()                   │
     │                │─────────────────>│                   │
     │                │                  │ Service Discovery │
     │                │                  │──────────────────>│
     │                │                  │ Services Response │
     │                │                  │<──────────────────│
     │                │                  │                   │
     │                │ [핸드셰이크 프로토콜]                │
     │                │ writeCharacteristic(cmd)             │
     │                │─────────────────>│──────────────────>│
     │                │                  │ Notification(rsp) │
     │                │<─────────────────│<──────────────────│
     │ handshakeWait  │                  │                   │
     │ Sure()         │                  │                   │
     │<───────────────│                  │                   │
     │                │                  │                   │
     │ btStatusChange │                  │                   │
     │ (CONNECTED)    │                  │                   │
     │<───────────────│                  │                   │
```

### 단계별 상세

#### 1단계: SDK 초기화 + 인증

| 동작 | 설명 |
|------|------|
| `NiceBuildSdk.initSdk()` | appKey/appSecret 또는 partnerToken으로 SDK 초기화 |
| 서버 통신 | SDK가 내부적으로 Plaud 서버와 통신하여 인증 토큰 검증 (추정) |
| `INTERNET` 권한 필요 | SDK 문서에서 INTERNET 권한 명시 → 서버 통신 존재 확인 |

**주의:** SDK 초기화 시 네트워크 연결이 필요할 수 있다. 오프라인 환경에서의 동작은 미확인.

#### 2단계: BLE 스캔

| 동작 | Android API | 설명 |
|------|-------------|------|
| 스캔 시작 | `BluetoothLeScanner.startScan()` | SDK 내부에서 호출 (추정) |
| 필터 조건 | UUID 기반 필터 또는 이름 기반 필터 | Plaud 기기만 필터링 |
| 스캔 결과 | `scanBleDeviceReceiver(BleDevice)` | sn, name, mac, rssi 포함 |
| 권한 필요 | `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION` | Android 12+ |

#### 3단계: BLE 연결 + 서비스 발견

| 동작 | Android API | 설명 |
|------|-------------|------|
| GATT 연결 | `BluetoothDevice.connectGatt()` | SDK 내부에서 호출 |
| 서비스 발견 | `BluetoothGatt.discoverServices()` | 자동 수행 |
| 커스텀 서비스 | UUID `e3d196c2-...` | Plaud 전용 BLE 서비스 |
| 핸드셰이크 | 커스텀 프로토콜 | 첫 연결 시 사용자 확인 필요 |
| 상태 콜백 | `btStatusChange(CONNECTED)` | 연결 완료 알림 |

#### 4단계: 파일 전송 (BLE)

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Plaud 앱 │     │ SDK      │     │ Plaud    │
│          │     │          │     │ 녹음기   │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                 │
     │ getFileList(0) │                 │
     │───────────────>│                 │
     │                │ [BLE Write Cmd] │
     │                │────────────────>│
     │                │ [BLE Notify Rsp]│
     │                │<────────────────│
     │ fileList[]     │                 │
     │<───────────────│                 │
     │                │                 │
     │ exportAudio()  │                 │
     │───────────────>│                 │
     │                │ [Write: Start]  │
     │                │────────────────>│
     │                │ [Notify: Data]  │ ← 반복 (MTU 단위)
     │                │<────────────────│
     │ onProgress()   │                 │
     │<───────────────│                 │
     │                │ ...반복...       │
     │                │ [Notify: End]   │
     │                │<────────────────│
     │                │                 │
     │                │ [E2EE 복호화]    │
     │                │ [포맷 변환]      │
     │                │                 │
     │ onComplete()   │                 │
     │<───────────────│                 │
```

**파일 전송 핵심 동작:**
1. `getFileList(sessionId=0)` — 전체 파일 목록 조회 (BLE Write → Notify 응답)
2. `exportAudio(sessionId)` — 파일 다운로드 요청
3. SDK가 BLE 특성을 통해 파일 데이터를 Notify로 수신
4. 데이터 수신 완료 후 E2EE 복호화 (해당되는 경우)
5. WAV/PCM 포맷 변환
6. 로컬 파일 저장 후 onComplete() 콜백

## Wi-Fi Direct 전환 흐름

### 전환 시퀀스 (SDK 문서 기반)

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Plaud 앱 │     │ SDK      │     │ Plaud    │
│          │     │          │     │ 녹음기   │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                 │
     │ [BLE 연결 상태] │                 │
     │                │                 │
     │ startWifi      │                 │
     │ Transfer()     │                 │
     │───────────────>│                 │
     │                │ [BLE로 Wi-Fi    │
     │                │  정보 교환]      │
     │                │<───────────────>│
     │                │                 │
     │                │ [Wi-Fi Direct   │
     │                │  P2P 연결]      │
     │                │<═══════════════>│
     │                │                 │
     │ WifiConnection │                 │
     │ State.CONNECTED│                 │
     │<───────────────│                 │
     │                │                 │
     │ downloadFile() │                 │
     │───────────────>│                 │
     │                │ [Wi-Fi 고속전송] │
     │                │<───────────────>│
     │ onTransfer     │                 │
     │ Progress()     │                 │
     │<───────────────│                 │
     │                │                 │
     │ stopWifi       │                 │
     │ Transfer()     │                 │
     │───────────────>│                 │
     │                │ [Wi-Fi 해제]    │
     │                │ [BLE 재연결]    │
     │                │<───────────────>│
```

### Wi-Fi 관련 권한

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Wi-Fi Fast Transfer 특징

| 항목 | BLE 전송 | Wi-Fi Fast Transfer |
|------|---------|-------------------|
| 속도 | ~2-4 kBps (BLE 5.2) | 수 MBps (Wi-Fi Direct) |
| 연결 방식 | BLE GATT | Wi-Fi Direct P2P |
| 전환 | 기본 | BLE 연결 상태에서 전환 |
| API | `exportAudio()` | `exportAudioViaWiFi()` |
| 파일 관리 | `getFileList()` | `wifiAgent.getFileList()` |
| 복귀 | - | `stopWifiTransfer()` → BLE 재연결 |

## SDK UUID 교차 검증

### 발견된 UUID

| UUID | 발견 위치 | 유형 | 교차 검증 |
|------|-----------|------|-----------|
| `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` | SDK 문서 HTML | Custom 128-bit | APK 디컴파일 미수행으로 교차 검증 불가 |
| `0x1800` (Generic Access) | SDK 문서 | Standard 16-bit | BLE 표준 — 모든 BLE 기기에 존재 |

### 교차 검증 결과

**검증 상태: 불완전**

APK를 직접 디컴파일하지 못했으므로, SDK 문서에서 발견된 UUID가 APK 내부에서도 동일하게 사용되는지 확인할 수 없다. 다만 다음과 같은 추론이 가능하다:

1. **Plaud 앱은 plaud-sdk.aar을 내장** — 공식 앱이 자사 SDK를 사용하므로, SDK의 GATT UUID가 앱에도 동일하게 존재
2. **추가 UUID 가능성** — 앱은 SDK 외에 자체 BLE 기능(OTA 업데이트 등)을 가질 수 있으며, 이 경우 추가 GATT 서비스가 존재할 수 있음
3. **난독화 영향 없음** — UUID 문자열은 ProGuard/R8 난독화 대상이 아님

**향후 검증 방법:**
```bash
# APK 확보 후 jadx 디컴파일 → UUID 검색
jadx -d ./decompiled-app/ plaud-app.apk
grep -ri "e3d196c2" ./decompiled-app/
grep -ri "UUID\|uuid" ./decompiled-app/ | grep -i "fromString\|parse"
```

## 인증/암호화 관련 발견 사항

### 인증 체계 정리

| 계층 | 인증 방식 | 서버 의존 | 비고 |
|------|-----------|----------|------|
| SDK 초기화 | appKey/appSecret 또는 partnerToken | **예** | Plaud 서버에서 발급 |
| BLE 페어링 | bindToken + devToken | SDK 관리 | 첫 연결 시 handshake 필요 |
| 파일 암호화 | E2EE | SDK 관리 | 복호화 키 SDK 내부 |
| Partner API | OAuth (access_token) | **예** | platform.plaud.ai |

### E2EE 암호화 영향 분석

**시나리오 1: E2EE 미적용 기기**
- SDK 문서의 "if needed" 표현으로 보아, 일부 기기/설정에서는 E2EE가 비활성화
- 이 경우 BLE로 수신한 원본 데이터가 바로 오디오 파일 (PCM/WAV)
- 자체 BLE 구현으로 파일 추출 가능할 수 있음

**시나리오 2: E2EE 적용 기기**
- 녹음 파일이 암호화된 상태로 전송
- 복호화 키는 SDK 내부 또는 Plaud 서버에서 관리
- SDK 없이는 파일 복호화 불가

**시나리오 3: BLE Link Layer 암호화만 적용**
- BLE 5.2 LE Secure Connections 페어링으로 링크 레이어 암호화
- 이 경우 BLE 패킷 자체는 암호화되나, 앱 레벨에서는 평문 데이터
- 자체 BLE 구현으로 정상 페어링하면 데이터 접근 가능

**결론:** E2EE 적용 범위는 Plan 02의 BLE 스니핑(HCI snoop log)으로 확인해야 한다.

## 프로토콜 암호화 여부 초기 판단

### 판단 근거 종합

| 근거 | 내용 | 의미 |
|------|------|------|
| SDK 문서 E2EE 언급 | "E2EE decryption (if needed)" | 파일 레벨 암호화 존재 |
| INTERNET 권한 필요 | SDK가 네트워크 통신 수행 | 인증/키 교환에 서버 필요 |
| partnerToken 인증 | OAuth 기반 서버 인증 | 토큰 자체 생성 불가 |
| bindToken/devToken | BLE 페어링 토큰 | SDK 내부 관리 |

### 프로토콜 암호화 초기 판단

**판단: 다층 보안 구조 확인**

```
Layer 1: BLE Link Layer 암호화 (LE Secure Connections)
         ├── BLE 5.2 표준 페어링
         └── 링크 레벨 AES-CCM

Layer 2: Application Layer 인증 (SDK 인증)
         ├── appKey/appSecret 또는 partnerToken
         ├── handshake 프로토콜 (bindToken/devToken)
         └── 서버 검증 필요

Layer 3: Data Layer 암호화 (E2EE — 선택적)
         ├── 녹음 파일 E2EE 암호화
         ├── SDK 내부 복호화
         └── 키 관리: SDK/서버 의존
```

**Go/No-Go 최종 판단에 대한 영향:**

| D-07 No-Go 조건 | 상태 | 판단 |
|-----------------|------|------|
| 프로토콜이 암호화되어 해독 불가 | **Layer 1은 표준, Layer 3은 선택적** | 추가 확인 필요 |
| 인증 토큰이 서버 의존적 | **확인됨** — appKey/partnerToken 서버 발급 | **No-Go 방향** |

## APK 디컴파일 시 확인해야 할 항목 (Plan 02 연계)

APK를 수동으로 확보하여 jadx로 디컴파일할 경우 확인해야 할 핵심 항목:

### 최우선 확인

```bash
# 1. 전체 GATT UUID 목록
grep -ri "fromString\|UUID\.\|uuid" ./decompiled-app/ | grep -v "test\|example"

# 2. BLE 특성 Properties (Read/Write/Notify)
grep -ri "BluetoothGattCharacteristic\.\(PROPERTY\|PERMISSION\)" ./decompiled-app/

# 3. E2EE 관련 코드
grep -ri "E2EE\|encrypt\|decrypt\|AES\|cipher" ./decompiled-app/

# 4. 서버 통신 URL
grep -ri "plaud\.ai\|nicebuild\.\|api\." ./decompiled-app/ | grep "http"
```

### 부차적 확인

```bash
# 5. Wi-Fi Direct 구현
grep -ri "WifiP2p\|WiFiDirect\|WIFI_P2P\|WifiManager" ./decompiled-app/

# 6. OTA/펌웨어 업데이트
grep -ri "firmware\|OTA\|update\|DFU" ./decompiled-app/

# 7. 에러 메시지 (역추적용)
grep -ri "connect.*fail\|transfer.*error\|sync.*error" ./decompiled-app/
```

## Plan 02 BLE 스니핑으로 검증할 항목

SDK 분석과 APK 분석(문서 기반)에서 확인한 내용을 실기기 BLE 스니핑으로 검증해야 할 항목:

| 번호 | 검증 항목 | 방법 | 우선순위 |
|------|-----------|------|---------|
| 1 | GATT UUID `e3d196c2-...` 실제 존재 확인 | nRF Connect 스캔 | **최우선** |
| 2 | 추가 GATT 서비스/특성 UUID 발견 | nRF Connect 서비스 탐색 | **최우선** |
| 3 | 각 특성의 Properties (R/W/N) 확인 | nRF Connect 특성 조회 | **최우선** |
| 4 | BLE Link Layer 암호화 여부 | HCI snoop log 분석 | 높음 |
| 5 | 파일 전송 패킷 구조 (명령/데이터/응답) | HCI snoop log 분석 | 높음 |
| 6 | E2EE 데이터 vs 평문 데이터 구분 | HCI 패킷 내용 분석 | 높음 |
| 7 | Wi-Fi Direct 핸드오프 프로토콜 | HCI + Wi-Fi 로그 | 보통 |
| 8 | MTU 협상 결과 (기본 23 vs 확장) | HCI snoop log | 보통 |

## 요약

| 항목 | 결과 |
|------|------|
| APK 직접 디컴파일 | **미수행** (APK 확보 불가) |
| BLE 연결 흐름 | **SDK 문서 기반 재구성** — 5단계 흐름 문서화 |
| 파일 전송 시퀀스 | **SDK API 레벨 문서화** — BLE 프레임 레벨은 미확인 |
| Wi-Fi Direct 전환 | **SDK API 기반 문서화** — startWifiTransfer/stopWifiTransfer |
| SDK UUID 교차 검증 | **불완전** — APK 미디컴파일, 1개 UUID 일치 (추론) |
| 프로토콜 암호화 | **다층 보안 구조 확인** — L1(BLE), L2(인증), L3(E2EE) |
| 초기 판단 | **Conditional No-Go** — 인증 서버 의존 + E2EE 확인 필요 |
| Plan 02 연계 | 검증 항목 8개 정의 — nRF Connect + HCI snoop log |
