# Phase 13: Plaud BLE 실기기 디버깅 - Research

**Researched:** 2026-03-26
**Domain:** Plaud SDK BLE 실기기 통합, Android BLE 디버깅, E2E 파이프라인 검증
**Confidence:** MEDIUM

## Summary

Phase 13은 v1.0 Phase 3에서 스텁으로 구현한 Plaud SDK BLE 연동을 실제 Plaud 녹음기 하드웨어에서 동작하도록 디버깅하는 단계이다. 현재 코드베이스에는 `NiceBuildSdkWrapper`라는 스텁 객체가 모든 SDK 호출을 대행하며, `scanBle()`은 항상 에러(-1)를, `exportAudio()`는 항상 실패를 반환한다. 실기기 디버깅의 핵심 작업은 (1) plaud-sdk.aar을 libs/에 배치, (2) NiceBuildSdkWrapper 스텁을 실제 SDK 호출로 교체, (3) BleAgentListener 콜백 구현, (4) 실기기에서 스캔/연결/파일전송 검증이다.

Plaud SDK v0.2.8 공식 문서(docs.plaud.ai)에서 확인한 API 구조는 `NiceBuildSdk.initSdk()`로 초기화, `TntAgent.getInstant().bleAgent`로 BLE 제어, `NiceBuildSdk.exportAudio()`로 파일 내보내기, `BleAgentListener`로 이벤트 수신하는 패턴이다. 현재 스텁 코드의 API 시그니처는 공식 문서와 대체로 일치하지만, `BleAgentListener` 구현이 빠져있고, `connectionBLE()` 연결 흐름이 누락되어 있다.

이 Phase는 하드웨어 의존적 디버깅이므로 원격 자동화가 불가능하며, 실기기 테스트 시나리오와 디버깅 체크리스트를 명확히 정의하여 개발자가 실행할 수 있도록 구조화해야 한다. E2E 파이프라인(BLE 수신 -> Whisper STT -> Gemini 회의록)의 각 단계별 검증 포인트를 분리하여 문제 격리를 용이하게 한다.

**Primary recommendation:** NiceBuildSdkWrapper 스텁을 실제 SDK 호출로 교체하되, 기존 PlaudSdkManager의 인터페이스(scanAndConnect, exportAudio, disconnect)는 유지하라. BleAgentListener를 구현하여 연결 상태 변화를 로깅하고, 디버그 UI에 BLE 상태를 실시간 표시하라.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLUD-01 | Plaud 녹음기와 BLE 연결을 실기기에서 디버깅하여 실제 오디오 파일 수신이 동작하도록 한다 | SDK AAR 배치 + NiceBuildSdkWrapper 스텁 교체 + BleAgentListener 구현 + 실기기 테스트 시나리오 |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **응답 언어:** 한국어
- **코드 주석:** 한국어
- **커밋 메시지:** 한국어
- **문서화:** 한국어
- **변수명/함수명:** 영어
- **GSD 워크플로우:** Edit/Write 전 GSD 명령 사용
- **Plaud SDK:** v0.2.8 (MIT), AAR 파일 libs/에 배치
- **BLE 통신:** Android BLE API (Native) 사용 결정
- **STT:** Whisper(whisper.cpp small) 온디바이스 1차
- **회의록:** Gemini 2.5 Flash API

## Standard Stack

### Core (Phase 13 관련)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Plaud SDK | v0.2.8 (AAR) | BLE 연결 + 오디오 파일 다운로드 | 공식 SDK, GATT 통신 추상화 |
| Guava | 28.2-android | Plaud SDK 필수 의존성 | SDK 내부 의존 (resolutionStrategy.force 적용 중) |

### Supporting (기존 스택 활용)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Foreground Service | Platform API | BLE 연결 유지 | connectedDevice 타입, AudioCollectionService |
| WorkManager | 2.11.1 | 전사 파이프라인 트리거 | 오디오 수신 완료 시 |
| Room | 2.8.4 | 메타데이터 저장 | MeetingEntity에 오디오 파일 경로/상태 기록 |
| Hilt | 2.56 | DI | PlaudSdkManager 싱글턴 주입 |
| Whisper (whisper.cpp) | small model | 한국어 STT | E2E 파이프라인 검증 |
| Gemini 2.5 Flash | API | 회의록 생성 | E2E 파이프라인 검증 |

## Architecture Patterns

### 현재 코드 구조 (변경 대상)
```
app/src/main/java/com/autominuting/
├── data/audio/
│   ├── PlaudSdkManager.kt          # 스텁 -> 실제 SDK 호출로 교체
│   ├── NiceBuildSdkWrapper          # 제거 대상 (internal object)
│   ├── PlaudCloudApiService.kt     # 유지 (Cloud API 폴백)
│   └── AudioFileManager.kt         # 유지 (파일 저장/검증)
├── service/
│   └── AudioCollectionService.kt   # 유지 (Foreground Service)
├── data/repository/
│   └── AudioRepositoryImpl.kt     # 유지 (SDK/Cloud 이중 경로)
└── worker/
    └── TranscriptionTriggerWorker.kt  # 유지 (전사 파이프라인)
```

### Pattern 1: NiceBuildSdkWrapper 스텁 -> 실제 SDK 교체

**What:** 스텁 객체를 실제 `NiceBuildSdk`, `TntAgent` API 호출로 교체
**When to use:** AAR 배치 후
**Example:**
```kotlin
// Source: https://docs.plaud.ai/api_guide/sdks/android

// 교체 전 (현재 스텁)
NiceBuildSdkWrapper.initSdk(context, appKey, appSecret, "AutoMinuting")

// 교체 후 (실제 SDK)
NiceBuildSdk.initSdk(
    context = context,
    appKey = appKey,
    appSecret = appSecret,
    bleAgentListener = PlaudBleAgentListener(), // 신규 구현 필요
    hostName = "AutoMinuting"
)
```

### Pattern 2: BleAgentListener 구현

**What:** SDK 이벤트 콜백 인터페이스 구현
**When to use:** SDK 초기화 시 전달
**Example:**
```kotlin
// Source: https://docs.plaud.ai/api_guide/sdks/android

class PlaudBleAgentListener : BleAgentListener {
    override fun scanBleDeviceReceiver(device: BleDevice) {
        Log.d(TAG, "기기 발견: ${device.sn}, RSSI: ${device.rssi}")
    }

    override fun btStatusChange(sn: String?, status: BluetoothStatus) {
        Log.d(TAG, "BLE 상태 변경: $sn -> $status")
        // CONNECTED, DISCONNECTED 등 상태에 따라 처리
    }

    override fun bleConnectFail(sn: String?, reason: Constants.ConnectBleFailed) {
        Log.e(TAG, "BLE 연결 실패: $sn, 이유: $reason")
    }

    override fun handshakeWaitSure(sn: String?, param: Long) {
        Log.d(TAG, "핸드셰이크 대기: $sn, param: $param")
    }

    override fun scanFail(reason: Constants.ScanFailed) {
        Log.e(TAG, "BLE 스캔 실패: $reason")
    }

    // ... 기타 콜백
}
```

### Pattern 3: BLE 스캔 -> 연결 -> 파일 다운로드 흐름

**What:** 실제 SDK API를 사용한 전체 오디오 수집 흐름
**When to use:** AudioCollectionService에서 오디오 수집 시작 시
**Example:**
```kotlin
// Source: https://docs.plaud.ai/api_guide/sdks/android

// 1단계: BLE 스캔
val bleAgent = TntAgent.getInstant().bleAgent
bleAgent.scanBle(true) { errorCode ->
    // 스캔 에러 처리
}

// 2단계: scanBleDeviceReceiver 콜백에서 기기 발견 후 연결
// connectionBLE(device, bindToken, devToken, userName, connectTimeout, handshakeTimeout)
bleAgent.connectionBLE(
    device = discoveredDevice,
    bindToken = null,    // 첫 연결 시
    devToken = null,     // 첫 연결 시
    userName = "AutoMinuting",
    connectTimeout = 30000L,
    handshakeTimeout = 15000L
)

// 3단계: btStatusChange 콜백에서 CONNECTED 확인 후 파일 다운로드
NiceBuildSdk.exportAudio(
    sessionId = sessionId,
    outputDir = audioDir,
    format = AudioExportFormat.WAV,
    channels = 1,
    callback = exportCallback
)
```

### Anti-Patterns to Avoid
- **스텁 코드 부분 잔존:** NiceBuildSdkWrapper의 스텁 메서드가 실제 SDK 호출과 혼재하면 디버깅이 극히 어려워진다. 스텁을 완전히 제거하거나, AAR 존재 여부에 따라 컴파일 타임에 분기하라.
- **BLE 콜백에서 직접 UI 업데이트:** BLE 콜백은 비-UI 스레드에서 호출된다. StateFlow나 Channel로 메인 스레드에 전달하라.
- **연결 타임아웃 미설정:** BLE 연결은 무한 대기할 수 있다. connectionBLE의 connectTimeout/handshakeTimeout을 반드시 설정하라.
- **실기기 테스트 없이 통과 선언:** 이 Phase는 하드웨어 연동이므로 에뮬레이터 테스트는 의미가 없다.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BLE GATT 프로토콜 | 직접 GATT 서비스 디스커버리 | Plaud SDK BleAgent | SDK가 GATT 통신, 청크 전송, 체크섬을 추상화 |
| 파일 전송 프로토콜 | 직접 바이트 스트림 조립 | NiceBuildSdk.exportAudio() | SDK가 파일 분할 전송/재조립 처리 |
| BLE 스캔 필터링 | 직접 UUID 필터 | SDK scanBle() | SDK가 Plaud 기기 전용 필터 적용 |
| BLE 재연결 로직 | 직접 재연결 상태머신 | SDK connectionBLE() | SDK 내부 재시도/핸드셰이크 처리 |

**Key insight:** Plaud SDK가 BLE 프로토콜 전체를 추상화하므로, 이 Phase의 작업은 "SDK API를 올바르게 호출하고 콜백을 처리하는 것"이지 BLE 저수준 코드를 작성하는 것이 아니다.

## Common Pitfalls

### Pitfall 1: AAR 미배치 상태에서 SDK 클래스 참조
**What goes wrong:** plaud-sdk.aar이 libs/에 없으면 `NiceBuildSdk`, `TntAgent` 등 클래스가 존재하지 않아 컴파일 에러
**Why it happens:** build.gradle.kts에 `if (plaudAar.exists())` 조건부 dependency가 있어 AAR 없이도 빌드되지만, 실제 SDK 클래스를 참조하는 코드는 컴파일 불가
**How to avoid:** AAR 배치 전까지 스텁 래퍼를 유지하되, AAR 배치 후에는 래퍼를 실제 SDK로 완전 교체. 조건부 컴파일(`if (plaudAar.exists())`)로 분기하거나, AAR을 필수 의존성으로 전환
**Warning signs:** `Unresolved reference: NiceBuildSdk`, `ClassNotFoundException`

### Pitfall 2: BLE 퍼미션 런타임 요청 누락
**What goes wrong:** BLUETOOTH_CONNECT, BLUETOOTH_SCAN 퍼미션이 granted 되지 않은 상태에서 SDK 호출 시 SecurityException
**Why it happens:** 매니페스트에 퍼미션이 선언되어 있지만, Android 12+(API 31) 이상에서 런타임 요청이 필수
**How to avoid:** 오디오 수집 시작 전 DashboardScreen에서 런타임 퍼미션 요청 확인. 현재 코드에는 퍼미션 요청 로직이 없으므로 추가 필요
**Warning signs:** `SecurityException: Need BLUETOOTH_CONNECT permission`

### Pitfall 3: BLE 스캔 후 connectionBLE 호출 누락
**What goes wrong:** scanBle()로 기기를 발견하지만, connectionBLE()을 호출하지 않아 연결이 되지 않음
**Why it happens:** 현재 스텁 코드의 scanBle()은 onConnected 콜백만 있어 연결이 자동으로 보이지만, 실제 SDK는 스캔(기기 발견) -> 연결(connectionBLE) 2단계
**How to avoid:** scanBleDeviceReceiver 콜백에서 기기를 발견하면 connectionBLE()을 호출하고, btStatusChange에서 CONNECTED 확인 후 다음 단계 진행
**Warning signs:** 기기는 발견되지만 연결되지 않음

### Pitfall 4: exportAudio 포맷 불일치
**What goes wrong:** SDK의 지원 포맷은 WAV, PCM인데 현재 코드에서 MP3를 기본값으로 설정
**Why it happens:** Phase 3 리서치에서는 MP3/WAV 지원으로 기술했으나, 최신 SDK 문서에서는 WAV/PCM만 명시
**How to avoid:** `NiceBuildSdk.getSupportedExportFormats()`로 지원 포맷 확인 후 WAV를 기본값으로 변경. AudioFileManager의 SUPPORTED_EXTENSIONS에 "wav" 확인 (이미 포함됨)
**Warning signs:** `onError("Unsupported format")`

### Pitfall 5: Wi-Fi 전송 경로 미고려
**What goes wrong:** BLE 전송은 대용량 오디오에서 느림 (BLE 5.2 기준 ~1Mbps)
**Why it happens:** Plaud SDK는 BLE 외에 Wi-Fi Direct 고속 전송을 지원하지만, 현재 코드에 Wi-Fi 경로가 없음
**How to avoid:** 1차 디버깅은 BLE로 진행하되, 파일 크기가 큰 경우 `exportAudioViaWiFi()` 경로를 후속 작업으로 고려
**Warning signs:** 30분+ 회의 녹음 파일 전송에 수분 이상 소요

### Pitfall 6: Plaud 기기 펌웨어 호환성
**What goes wrong:** SDK v0.2.8과 Plaud 기기의 펌웨어 버전이 맞지 않으면 핸드셰이크 실패
**Why it happens:** SDK 문서에 따르면 NotePro/NotePins는 partnerToken, 구형 Note/NotePin은 appKey/appSecret 사용
**How to avoid:** 보유 기기의 모델/펌웨어 버전 확인 후 인증 방식 결정. 디버그 로그에 기기 정보 출력
**Warning signs:** `handshakeWaitSure` 콜백 반복 호출, `bleConnectFail` with ConnectBleFailed reason

### Pitfall 7: Foreground Service에서 BLE 연결 끊김
**What goes wrong:** 장시간 파일 전송 중 BLE 연결이 끊어짐
**Why it happens:** Android 배터리 최적화, Doze 모드, BLE 연결 타임아웃
**How to avoid:** Foreground Service(connectedDevice 타입)로 BLE 연결 유지. btStatusChange 콜백에서 DISCONNECTED 감지 시 자동 재연결 시도
**Warning signs:** 파일 전송 중 진행률이 멈추고 onError 호출

## Code Examples

### 현재 스텁 코드 vs 실제 SDK 교체 매핑

```kotlin
// ===== PlaudSdkManager.kt 교체 가이드 =====
// Source: https://docs.plaud.ai/api_guide/sdks/android

// [교체 1] initialize() - 스텁 -> SDK
// 현재: NiceBuildSdkWrapper.initSdk(context, appKey, appSecret, "AutoMinuting")
// 교체: NiceBuildSdk.initSdk(context, appKey, appSecret, bleAgentListener, "AutoMinuting")
// 핵심: BleAgentListener 인스턴스 전달 필수

// [교체 2] scanAndConnect() - 스텁 -> SDK 2단계
// 현재: NiceBuildSdkWrapper.scanBle(onConnected, onError) -- 1단계로 통합
// 교체:
//   1. TntAgent.getInstant().bleAgent.scanBle(true) -- 스캔
//   2. BleAgentListener.scanBleDeviceReceiver()에서 기기 발견
//   3. bleAgent.connectionBLE(...) -- 연결
//   4. BleAgentListener.btStatusChange()에서 CONNECTED 확인
// 핵심: 비동기 콜백 체인. suspendCancellableCoroutine으로 래핑

// [교체 3] exportAudio() - 스텁 -> SDK
// 현재: NiceBuildSdkWrapper.exportAudio(sessionId, outputDir, format, ...)
// 교체: NiceBuildSdk.exportAudio(sessionId, outputDir, AudioExportFormat.WAV, 1, callback)
// 핵심: format 파라미터가 String -> AudioExportFormat enum으로 변경
```

### BLE 런타임 퍼미션 요청 (추가 필요)

```kotlin
// Source: Android developer docs - runtime permissions
// DashboardScreen.kt 또는 별도 퍼미션 유틸에 추가

private val blePermissions = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)

// Compose에서:
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
        // BLE 오디오 수집 시작
        viewModel.toggleCollection()
    } else {
        // 퍼미션 거부 처리
    }
}

// 수집 버튼 클릭 시:
permissionLauncher.launch(blePermissions)
```

### 디버그 로깅 추가 패턴

```kotlin
// BLE 디버깅용 상세 로깅
// AudioCollectionService.kt 또는 PlaudSdkManager.kt에 추가

private fun logBleEvent(event: String, details: Map<String, Any?> = emptyMap()) {
    val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
    Log.d("PlaudBLE", "[$event] $detailStr")
    // 디버그 모드에서 UI에 표시 (설정 -> BLE 로그 화면)
}

// 사용 예:
logBleEvent("SCAN_START")
logBleEvent("DEVICE_FOUND", mapOf("sn" to device.sn, "rssi" to device.rssi))
logBleEvent("CONNECT_ATTEMPT", mapOf("sn" to sn, "timeout" to 30000))
logBleEvent("CONNECTED", mapOf("sn" to sn))
logBleEvent("EXPORT_START", mapOf("sessionId" to id, "format" to "WAV"))
logBleEvent("EXPORT_PROGRESS", mapOf("progress" to progress))
logBleEvent("EXPORT_COMPLETE", mapOf("path" to filePath, "size" to fileSize))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| NiceBuildSdkWrapper 스텁 | NiceBuildSdk 실제 API | Phase 13 (이번) | 스텁에서 실기기 동작으로 전환 |
| scanBle + 자동 연결 (스텁) | scanBle + connectionBLE 2단계 | Phase 13 (이번) | SDK의 실제 연결 흐름 반영 |
| MP3 기본 포맷 | WAV 기본 포맷 | Phase 13 (이번) | SDK 실제 지원 포맷 반영 |
| appKey만 필요 | appKey + appSecret (또는 partnerToken) | SDK 문서 최신 | 기기 모델별 인증 분기 |

**Deprecated/outdated:**
- `NiceBuildSdkWrapper` (internal object): Phase 13에서 완전 제거 대상
- `format: String = "MP3"` (PlaudSdkManager.exportAudio): WAV/PCM enum으로 교체

## Open Questions

1. **Plaud SDK AAR 파일 확보 여부**
   - What we know: libs/README.txt에 GitHub releases 다운로드 안내 있음. v0.2.8 (MIT)
   - What's unclear: GitHub 리포 접근 가능 여부 (현재 404 반환), 실제 다운로드 가능한 경로
   - Recommendation: Plaud 개발자 플랫폼(plaud.ai/pages/developer-platform)에서 SDK 다운로드. 불가 시 support@plaud.ai 문의

2. **appKey/appSecret 발급 상태**
   - What we know: support@plaud.ai에 신청 필요, v1.0 Phase 3에서도 미발급 상태로 스텁 구현
   - What's unclear: 현재 시점에서 발급 완료 여부
   - Recommendation: 발급 미완료 시 Cloud API 폴백으로 E2E 검증. 발급 완료 시 local.properties에 PLAUD_APP_KEY, PLAUD_APP_SECRET 설정

3. **보유 Plaud 기기 모델**
   - What we know: Plaud Note, Note Pro, NotePins 등 여러 모델 존재. 모델별 인증 방식 차이
   - What's unclear: 개발자가 보유한 기기의 정확한 모델 및 펌웨어 버전
   - Recommendation: 기기 모델 확인 후 인증 방식(appKey/appSecret vs partnerToken) 결정

4. **exportAudio sessionId 획득 방법**
   - What we know: 현재 코드에서 "latest" 하드코딩. SDK에 `BleCore.getInstance().getFileList()` API 존재
   - What's unclear: getFileList()의 반환 형태, sessionId 포맷
   - Recommendation: 연결 성공 후 getFileList()로 녹음 목록 조회 -> 최신 sessionId 획득 -> exportAudio 호출

5. **Wi-Fi 고속 전송 필요 여부**
   - What we know: SDK에 `exportAudioViaWiFi()`, `startWifiTransfer()` API 존재
   - What's unclear: BLE 전송 속도가 실사용에 충분한지 (30분 녹음 파일 기준)
   - Recommendation: 1차 BLE 전송 테스트 후 속도 부족 시 Wi-Fi 경로 추가. Phase 13 범위 내 Wi-Fi는 선택사항

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Plaud SDK AAR | BLE 통신 전체 | 확인 필요 (다운로드) | v0.2.8 | Cloud API 폴백 |
| appKey/appSecret | SDK 초기화 | 확인 필요 (발급) | -- | Cloud API 모드 |
| Plaud 녹음기 하드웨어 | 실기기 테스트 | 확인 필요 | -- | Cloud API 모드 (제한적) |
| Android 실기기 | BLE 테스트 | 확인 필요 | API 31+ | 에뮬레이터 (BLE 불가) |
| Whisper 모델 (ggml-small.bin) | E2E STT | 스텁 모드 가능 | small | ML Kit 폴백 |
| Gemini API 키 | E2E 회의록 | 설정 UI에서 입력 | 2.5 Flash | -- |

**Missing dependencies with no fallback:**
- Plaud 녹음기 하드웨어 + Android 실기기: BLE 테스트에 절대 필수. 에뮬레이터로 대체 불가

**Missing dependencies with fallback:**
- Plaud SDK AAR: 미확보 시 Cloud API 폴백으로 일부 검증 가능
- appKey/appSecret: 미발급 시 Cloud API 모드로 전환

## Sources

### Primary (HIGH confidence)
- [Plaud SDK 공식 문서](https://docs.plaud.ai/api_guide/sdks/android) - SDK API 전체 구조, 초기화, BLE 스캔/연결, 파일 내보내기, BleAgentListener 콜백, 퍼미션, Wi-Fi 전송
- [Android Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types) - connectedDevice 타입 요구사항
- 기존 코드베이스 분석 (PlaudSdkManager.kt, AudioCollectionService.kt, AudioRepositoryImpl.kt) - 현재 스텁 구조 파악

### Secondary (MEDIUM confidence)
- [Plaud SDK GitHub](https://github.com/Plaud-AI/plaud-sdk) - 리포 존재 확인했으나 직접 접근 시 404 (비공개 전환 가능)
- [Plaud Developer Platform](https://www.plaud.ai/pages/developer-platform) - SDK 다운로드 페이지
- Phase 3 리서치 (03-RESEARCH.md) - v1.0 당시 SDK 평가 결과

### Tertiary (LOW confidence)
- SDK v0.2.8의 정확한 AAR 파일 크기, 내부 ProGuard 규칙, 최소 API 레벨(문서에는 API 21이지만 프로젝트는 API 31) - 실제 AAR 확인 필요

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 기존 Phase 3에서 확정, 변경 없음
- Architecture: MEDIUM - SDK 실제 API 시그니처는 문서 기반이며 AAR 내부 확인 필요
- Pitfalls: HIGH - 스텁 코드 vs 실제 SDK 차이점을 구체적으로 식별 완료
- E2E 파이프라인: MEDIUM - 각 단계(BLE/Whisper/Gemini)는 개별 검증되었으나 통합 테스트 미수행

**Research date:** 2026-03-26
**Valid until:** 2026-04-02 (하드웨어 의존 디버깅이므로 실기기 테스트 결과에 따라 변동)
