# Plaud SDK AAR 디컴파일 분석 결과

**분석 일시:** 2026-03-26
**분석 대상:** Plaud SDK (plaud-sdk.aar) — 공식 문서 + 기존 스텁 코드 기반 분석
**분석 방법:** 공식 SDK 문서(docs.plaud.ai) 정적 분석, 기존 프로젝트 스텁 코드 비교

## SDK AAR 확보 상태

### 다운로드 시도 결과

| 경로 | 결과 | 비고 |
|------|------|------|
| GitHub Releases (`Plaud-AI/plaud-sdk`) | **접근 불가 (404)** | 저장소가 비공개(private)로 설정됨 |
| GitHub API (`/repos/Plaud-AI/plaud-sdk`) | **404 Not Found** | 공개 API로 접근 불가 |
| GitHub Raw Content | **404 Not Found** | README.md 등 직접 접근 불가 |
| Maven Central / JitPack | **미등록** | 공개 패키지 저장소에 없음 |
| Plaud 공식 문서 | **문서만 접근 가능** | docs.plaud.ai/api_guide/sdks/android |

**결론:** SDK AAR 파일을 직접 다운로드하여 jadx로 디컴파일하는 것은 불가능하다. GitHub 저장소(`Plaud-AI/plaud-sdk`)는 private이며, AAR은 개발자 플랫폼 등록 후 제공되는 것으로 추정된다.

### 대체 분석 방법

SDK AAR 직접 디컴파일 대신 다음 두 가지 소스로 분석을 수행하였다:

1. **Plaud 공식 SDK 문서** (`docs.plaud.ai/api_guide/sdks/android`) — API 시그니처, 패키지 구조, 사용 흐름이 상세히 기술됨
2. **기존 프로젝트 스텁 코드** (`com.nicebuild.sdk.*`) — Phase 13에서 SDK API 시그니처를 기반으로 작성한 스텁

## SDK 패키지 구조 (공식 문서 기반)

```
com.nicebuild.sdk/
├── NiceBuildSdk                          # SDK 진입점 (초기화, exportAudio)
├── audio/
│   ├── AudioExportFormat                 # 오디오 포맷 enum (WAV, PCM)
│   └── AudioExporter                     # 오디오 내보내기 콜백
├── penblesdk/
│   ├── TntAgent                          # BLE 에이전트 싱글턴 (getInstant())
│   ├── Constants                         # SDK 상수 (ConnectBleFailed, ScanFailed)
│   ├── impl/
│   │   └── ble/
│   │       └── BleAgentListener          # BLE 이벤트 리스너 인터페이스
│   └── entity/
│       ├── BleDevice                     # BLE 디바이스 모델
│       ├── BluetoothStatus               # 연결 상태 enum
│       └── bean/
│           └── ble/
│               └── response/
│                   ├── RecordStartRsp    # 녹음 시작 응답
│                   ├── RecordStopRsp     # 녹음 종료 응답
│                   └── GetStateRsp       # 디바이스 상태 응답
└── (BleCore — getInstance())             # BLE 코어 (파일 목록 조회)
```

## GATT 서비스/특성 UUID

### 발견된 UUID

| UUID | 유형 | 용도 (추정) | 발견 위치 |
|------|------|-------------|-----------|
| `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` | Custom (128-bit) | **Plaud BLE 통신 서비스** | SDK 문서 HTML |
| `0x1800` (Generic Access) | Standard (16-bit) | BLE 표준 — 디바이스 이름/외관 | SDK 문서 |
| `0x180A` (Device Information) | Standard (16-bit) | BLE 표준 — 제조사/모델/시리얼 | 일반 BLE 패턴 |

### UUID 분석

**`e3d196c2-d2c0-4eea-b9d0-a228422e8ad3`**: 이 UUID는 Plaud의 커스텀 128-bit UUID로, SDK 문서 페이지에서 발견되었다. 표준 BLE 16-bit UUID 형식(`0000xxxx-0000-1000-8000-00805f9b34fb`)이 아닌 완전한 128-bit UUID이므로, Plaud가 자체 정의한 GATT 서비스 또는 특성 UUID이다.

**주의:** SDK가 BLE 프로토콜을 완전히 추상화하고 있어, 추가 GATT 특성 UUID(Read/Write/Notify용)는 SDK 내부에 캡슐화되어 있을 가능성이 높다. AAR 디컴파일 없이는 개별 특성 UUID를 확인할 수 없다.

## SDK API 분석 — BLE 연결 흐름

### 1단계: SDK 초기화

```kotlin
// 구 펌웨어 (Note/NotePin 구형)
NiceBuildSdk.initSdk(
    context,
    appKey = "your_app_key",       // 개발자 플랫폼에서 발급
    appSecret = "your_app_secret", // 개발자 플랫폼에서 발급
    bleAgentListener,              // BLE 이벤트 콜백
    appName = "AppName"
)

// 신 펌웨어 (NotePro, NotePins, 최신 Note/NotePin)
NiceBuildSdk.initSdk(
    context,
    partnerToken = "your_partner_token", // Partner API에서 발급
    bleAgentListener,
    appName = "AppName"
)
```

**인증 방식 이원화 확인:**
- **appKey/appSecret**: 구형 펌웨어용, 개발자 플랫폼에서 발급
- **partnerToken**: 신형 펌웨어용, Partner API(`platform.plaud.ai`)에서 OAuth로 발급
- 두 방식 모두 서버 인증이 필요 (자체 발급 불가)

### 2단계: BLE 스캔

```kotlin
val bleAgent = TntAgent.getInstant().bleAgent
bleAgent.scanBle(true) { errorCode ->
    // errorCode != 0 이면 스캔 에러
}

// BleAgentListener.scanBleDeviceReceiver(device: BleDevice) 콜백으로 기기 발견
```

### 3단계: BLE 연결

```kotlin
bleAgent.connectionBLE(
    device,                    // BleDevice (스캔에서 발견)
    bindToken = "user_token",  // 바인딩 토큰 (첫 연결 시 null)
    devToken = null,           // 디바이스 토큰
    appName = "AutoMinuting",
    connectTimeout = 30000L,   // 연결 타임아웃 (ms)
    handshakeTimeout = 15000L  // 핸드셰이크 타임아웃 (ms)
)
```

**핸드셰이크 확인 필요:** `BleAgentListener.handshakeWaitSure()` 콜백이 호출되면 사용자 확인이 필요하다 (최초 페어링 시).

### 4단계: 연결 상태 콜백

```kotlin
// BleAgentListener 구현
override fun btStatusChange(sn: String?, status: BluetoothStatus) {
    when (status) {
        BluetoothStatus.CONNECTED -> // 연결 완료
        BluetoothStatus.DISCONNECTED -> // 연결 해제
    }
}

override fun bleConnectFail(sn: String?, reason: Constants.ConnectBleFailed) {
    // TIMEOUT, REJECTED, UNKNOWN
}
```

## SDK API 분석 — 파일 전송

### 파일 목록 조회

```kotlin
val bleCore = BleCore.getInstance()
bleCore.getFileList(sessionId = 0) { file ->
    // sessionId = 0: 모든 파일 조회
    // sessionId > 0: 해당 sessionId 이후 파일만 조회
    // file.sessionId — 녹음 세션 식별자
}
```

### 오디오 내보내기 (BLE)

```kotlin
NiceBuildSdk.exportAudio(
    sessionId,
    outputDir,
    AudioExportFormat.WAV,   // WAV 또는 PCM
    channels = 1,            // 모노
    object : AudioExporter {
        override fun onProgress(progress: Float) { /* 0.0 ~ 1.0 */ }
        override fun onComplete(filePath: String) { /* 저장 경로 */ }
        override fun onError(error: String) { /* 에러 메시지 */ }
    }
)
```

**핵심 발견: E2EE (End-to-End Encryption)**
- SDK 문서에 "E2EE decryption (if needed)" 명시
- `exportAudio`가 내부적으로 BLE 다운로드 + E2EE 복호화 + 포맷 변환을 자동 처리
- 즉, 녹음 파일이 기기에서 **암호화된 상태**로 전송될 수 있으며, SDK가 복호화 키를 관리

### 오디오 내보내기 (Wi-Fi Fast Transfer)

```kotlin
NiceBuildSdk.exportAudioViaWiFi(
    sessionId,
    outputDir,
    AudioExportFormat.WAV,
    channels = 1,
    object : AudioExporter { /* 동일 콜백 */ }
)
```

### 파일 삭제

```kotlin
TntAgent.getInstant().deleteFile(sessionId)
```

## SDK API 분석 — Wi-Fi Fast Transfer

### Wi-Fi 전송 시작/중지

```kotlin
val wifiAgent: IWifiAgent = TntAgent.getInstant().getWifiAgent()

// Wi-Fi 전송 시작
wifiAgent.startWifiTransfer()

// Wi-Fi 전송 중지 + BLE 재연결
wifiAgent.stopWifiTransfer()

// 전송 상태 확인
val isActive = wifiAgent.isWifiTransferActive
```

### Wi-Fi 전송 콜백

```kotlin
interface WifiTransferCallback {
    fun onHandshakeCompleted(sessionId: Int)
    fun onTransferProgress(
        sessionId: Int,
        progress: Float,
        speedKbps: Float,
        bytesTransferred: Long,
        totalBytes: Long
    )
    fun onFileTransferCompleted(sessionId: Int, filePath: String)
    fun onWifiTransferStopped()
}
```

### Wi-Fi 파일 관리

```kotlin
// Wi-Fi로 파일 목록 조회
wifiAgent.getFileList() // -> List<WifiFileInfo>

// Wi-Fi로 단일 파일 다운로드
wifiAgent.downloadFile(sessionId)

// Wi-Fi로 전체 파일 다운로드
wifiAgent.downloadAllFiles()
```

### 연결 상태

```kotlin
enum class WifiConnectionState {
    // 문서에서 확인된 상태값 (정확한 enum 이름은 추정)
    CONNECTED,
    DISCONNECTED,
    CONNECTING
}
```

## SDK API 분석 — 디바이스 제어

### 녹음 시작/종료 (원격 제어)

```kotlin
// BleAgentListener 콜백
override fun deviceOpRecordStart(response: RecordStartRsp) {
    val sessionId = response.sessionId
}

override fun deviceOpRecordStop(response: RecordStopRsp) {
    val sessionId = response.sessionId
}
```

### 디바이스 상태 조회

```kotlin
// GetStateRsp 콜백으로 디바이스 상태 수신
// (정확한 호출 메서드는 문서에서 명시적으로 확인 불가)
```

### 바인딩 해제 (Unbind)

```kotlin
TntAgent.getInstant().unBindDevice()
// disconnectBle()와 차이: unBind는 페어링 관계도 제거
```

## 기존 스텁 코드와의 비교

### 패키지 구조 차이

| 항목 | 스텁 코드 | SDK 실제 (문서 기반) | 차이 |
|------|-----------|---------------------|------|
| BleAgentListener 위치 | `com.nicebuild.sdk.ble` | `com.nicebuild.sdk.penblesdk.impl.ble` | 패키지 경로 다름 |
| BleDevice 위치 | `com.nicebuild.sdk.ble` | `com.nicebuild.sdk.penblesdk.entity` | 패키지 경로 다름 |
| BluetoothStatus 위치 | `com.nicebuild.sdk.ble` | `com.nicebuild.sdk.penblesdk.entity` | 패키지 경로 다름 |
| Constants 위치 | `com.nicebuild.sdk.constants` | `com.nicebuild.sdk.penblesdk` | 패키지 경로 다름 |
| TntAgent 위치 | `com.nicebuild.sdk.ble` | `com.nicebuild.sdk.penblesdk` | 패키지 경로 다름 |
| AudioExportFormat 위치 | `com.nicebuild.sdk.export` | `com.nicebuild.sdk.audio` | 패키지 경로 다름 |

### API 시그니처 차이

| 메서드 | 스텁 코드 | SDK 실제 (문서 기반) | 상태 |
|--------|-----------|---------------------|------|
| `NiceBuildSdk.initSdk()` | appKey, appSecret 방식만 | appKey/appSecret + partnerToken 이원화 | **스텁 불완전** |
| `BleAgentListener` | 5개 콜백 | 5개 기본 + deviceOpRecordStart/Stop 추가 | **스텁 불완전** |
| `BleCore.getFileList()` | `List<String>` 반환 | sessionId 파라미터 + 콜백 방식 | **스텁 시그니처 불일치** |
| `NiceBuildSdk.exportAudio()` | 기본 콜백 | AudioExporter 인터페이스 (동일) | 일치 |
| `TntAgent.getInstant()` | 존재 | 존재 (동일) | 일치 |
| `bleAgent.connectionBLE()` | bindToken, devToken 포함 | 동일 시그니처 | 일치 |
| `bleAgent.scanBle()` | 에러코드 콜백 | 동일 | 일치 |
| `bleAgent.disconnect()` | 존재 | `disconnectBle()` (이름 차이 가능) | **확인 필요** |
| Wi-Fi 전송 관련 | **없음** | getWifiAgent(), IWifiAgent 전체 | **스텁 미구현** |
| `deleteFile()` | **없음** | TntAgent에서 제공 | **스텁 미구현** |
| `unBindDevice()` | **없음** | TntAgent에서 제공 | **스텁 미구현** |
| 녹음 원격 제어 | **없음** | deviceOpRecordStart/Stop | **스텁 미구현** |

### 주요 발견

1. **스텁 코드의 패키지 경로가 모두 다르다** — 실제 SDK AAR을 `libs/`에 배치하면 import 경로를 전면 수정해야 함
2. **partnerToken 인증 방식이 추가됨** — 최신 펌웨어는 appKey/appSecret 대신 Partner API OAuth 토큰 사용
3. **Wi-Fi Fast Transfer API가 완전히 누락** — SDK가 BLE + Wi-Fi 이중 전송을 지원하나 스텁에 미반영
4. **getFileList의 시그니처가 다름** — 스텁은 `List<String>` 반환, 실제는 sessionId 기반 콜백 방식

## 인증 흐름 분석

### 인증 체계

```
Plaud 개발자 플랫폼 (platform.plaud.ai)
├── Partner API (서버 간 통신)
│   ├── POST /oauth/partner/access-token     → partner_access_token
│   ├── POST /oauth/partner/access-token/refresh
│   └── POST /open/partner/users/access-token → user_access_token (partnerToken)
│
├── 구 펌웨어 인증 (Note/NotePin 구형)
│   └── appKey + appSecret → NiceBuildSdk.initSdk()
│
└── 신 펌웨어 인증 (NotePro, NotePins, 최신)
    └── partnerToken (user_access_token) → NiceBuildSdk.initSdk()
```

### 인증 의존성 평가

| 항목 | 평가 | 자체 재현 가능성 |
|------|------|-----------------|
| appKey/appSecret | 서버 발급 | **불가** — 개발자 플랫폼 등록 필요 |
| partnerToken | Partner API OAuth | **불가** — 파트너 계약 필요 |
| bindToken | BLE 페어링 시 생성 | SDK 내부 관리 (추정) |
| devToken | 디바이스 토큰 | SDK 내부 관리 (추정) |
| E2EE 키 | 파일 복호화 키 | SDK 내부 관리 — **자체 재현 불가** |

**결론:** 인증 토큰(appKey/appSecret 또는 partnerToken)은 Plaud 서버에서만 발급 가능하며, 자체 생성이 불가능하다. E2EE 복호화 키도 SDK 내부에서 관리되므로, SDK 없이 파일을 복호화할 수 없다.

## 난독화 수준 평가

SDK AAR을 직접 디컴파일하지 못했으므로, 공식 문서에서 노출된 정보 기반으로 추정:

| 항목 | 추정 | 근거 |
|------|------|------|
| 공개 API 클래스 | **난독화 안 됨** | 문서에서 전체 패키지 경로 노출 (com.nicebuild.sdk.penblesdk.*) |
| 내부 BLE 구현 | **난독화 가능** | 공개 API 뒤에 숨겨진 GATT 통신 레이어 |
| GATT UUID | **코드 내 상수** | e3d196c2-... UUID가 코드에 하드코딩된 것으로 추정 |
| E2EE 구현 | **난독화 가능** | 보안 관련 코드는 높은 확률로 난독화 |

## 프로토콜 암호화 여부 초기 판단

### E2EE (End-to-End Encryption) 확인

SDK 문서에서 다음이 명시적으로 확인되었다:

> "This method handles downloading from device, E2EE decryption (if needed), and format conversion automatically."

- **녹음 파일은 E2EE로 암호화될 수 있다** ("if needed" — 선택적 적용)
- SDK가 자동으로 복호화를 처리하므로, SDK 없이는 파일 복호화가 불가능
- E2EE 키 관리 방식은 문서에서 명시되지 않음

### Go/No-Go 관련 판단

| 판단 기준 (D-06/D-07) | 결과 | 비고 |
|----------------------|------|------|
| GATT UUID 파악 | **Partial** | 1개 커스텀 UUID 발견, 특성 UUID는 미확인 |
| 파일 전송 프로토콜 문서화 | **SDK 레벨 확인** | SDK API로 전송 흐름 파악, BLE 프레임 레벨은 미확인 |
| 프로토콜 암호화 여부 | **E2EE 적용 확인** | SDK 없이 파일 복호화 불가 가능성 높음 |
| 인증 토큰 자체 생성 | **불가** | 서버 의존적 (D-07 No-Go 조건 해당) |

**초기 판정: Conditional No-Go (SDK 의존 유지 권장)**
- 자체 BLE 구현으로 파일을 전송받더라도, E2EE 복호화와 인증 토큰 없이는 실질적 사용이 불가능
- SDK AAR을 개발자 플랫폼에서 정식 다운로드하여 통합하는 것이 유일한 실용적 경로
- Plan 02의 BLE 스니핑으로 E2EE 적용 범위와 프로토콜 암호화 여부를 최종 확인해야 함

## 요약

| 항목 | 결과 |
|------|------|
| SDK AAR 직접 디컴파일 | **불가** (저장소 private) |
| 공식 문서 기반 분석 | **완료** — API 시그니처, 패키지 구조, 인증 흐름 |
| 발견된 GATT UUID | `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` (커스텀) |
| E2EE 암호화 | **존재 확인** — SDK가 자동 복호화 처리 |
| 인증 의존성 | **서버 의존** — appKey/appSecret 또는 partnerToken 필요 |
| 스텁 코드 정확도 | **패키지 경로 불일치**, API 시그니처 일부 차이 |
| Wi-Fi Fast Transfer | **SDK 지원 확인** — IWifiAgent 인터페이스 |
| 자체 BLE 구현 가능성 | **Conditional No-Go** — E2EE + 인증 의존 |
