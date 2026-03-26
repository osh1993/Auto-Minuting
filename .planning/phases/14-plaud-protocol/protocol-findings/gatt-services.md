# Plaud Note BLE GATT 프로파일 문서

**작성 일시:** 2026-03-26
**데이터 출처:** SDK 문서 정적 분석 (실기기 BLE 스캔 미수행)
**검증 상태:** 실기기 미검증 - nRF Connect 스캔 보류

> **참고:** 이 문서는 Plan 14-01의 SDK/APK 정적 분석 결과를 기반으로 작성되었다.
> nRF Connect 실기기 BLE 스캔은 물리적 Plaud Note 녹음기 접근이 필요하여 보류 상태이다.
> 향후 실기기 스캔 수행 시 이 문서를 업데이트해야 한다.

## GATT 서비스 목록

### 확인된 서비스

| # | Service UUID | 유형 | 설명 | 발견 위치 | 실기기 검증 |
|---|-------------|------|------|-----------|------------|
| 1 | `0x1800` (Generic Access) | Standard 16-bit | BLE 표준 - 디바이스 이름, 외관 정보 | SDK 문서 | **미검증** |
| 2 | `0x180A` (Device Information) | Standard 16-bit | BLE 표준 - 제조사, 모델, 시리얼, 펌웨어 버전 | 일반 BLE 패턴 추정 | **미검증** |
| 3 | `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` | Custom 128-bit | **Plaud 전용 BLE 통신 서비스** - 파일 전송, 디바이스 제어, 상태 조회 | SDK 문서 HTML | **미검증** |

### 추정 서비스 (BLE 표준 패턴 기반)

| # | Service UUID | 유형 | 설명 | 존재 근거 |
|---|-------------|------|------|-----------|
| 4 | `0x180F` (Battery Service) | Standard 16-bit | 배터리 잔량 보고 | Plaud Note가 배터리 기기이므로 높은 확률로 존재 |
| 5 | `0xFE59` (Nordic DFU) 또는 커스텀 | Vendor-specific | OTA 펌웨어 업데이트 | Plaud 앱이 펌웨어 업데이트 기능 제공 |

## GATT 특성 목록

### 커스텀 서비스 (`e3d196c2-...`) 하위 특성 (추정)

SDK가 BLE 프로토콜을 완전히 추상화하고 있어, 개별 특성 UUID는 AAR 디컴파일 없이 확인 불가하다.
SDK API 동작 패턴에서 추론한 특성 구조:

| # | 특성 역할 | Properties (추정) | 용도 | 추론 근거 |
|---|----------|-------------------|------|-----------|
| C-1 | 명령 전송 (Command Write) | Write / Write Without Response | 파일 목록 요청, 다운로드 시작, 녹음 제어 등 명령 전송 | `getFileList()`, `exportAudio()` 호출 시 SDK가 BLE Write 수행 |
| C-2 | 응답 수신 (Response Notify) | Notify / Indicate | 명령 응답, 파일 데이터, 진행률 수신 | `onProgress()`, `onComplete()` 콜백 — Notification 기반 |
| C-3 | 상태 읽기 (Status Read) | Read | 디바이스 상태, 배터리, 연결 정보 조회 | `GetStateRsp` 콜백 존재 |
| C-4 | 핸드셰이크 (Handshake) | Write + Notify | 최초 페어링 시 인증 교환 | `handshakeWaitSure()` 콜백 — 양방향 통신 필요 |

### 표준 서비스 특성 (BLE 스펙 기반)

| 서비스 | 특성 UUID | Properties | 설명 |
|--------|-----------|------------|------|
| Generic Access (0x1800) | `0x2A00` (Device Name) | Read | 디바이스 이름 ("PLAUD" 또는 "Plaud Note") |
| Generic Access (0x1800) | `0x2A01` (Appearance) | Read | 디바이스 외관 카테고리 |
| Device Information (0x180A) | `0x2A29` (Manufacturer Name) | Read | "Plaud" 또는 "NiceBuild" |
| Device Information (0x180A) | `0x2A24` (Model Number) | Read | 모델 번호 |
| Device Information (0x180A) | `0x2A25` (Serial Number) | Read | 시리얼 번호 (sn) |
| Device Information (0x180A) | `0x2A26` (Firmware Revision) | Read | 펌웨어 버전 |
| Battery Service (0x180F) | `0x2A19` (Battery Level) | Read / Notify | 배터리 잔량 (0-100%) |

## 정적 분석 교차 검증

### SDK 분석 (sdk-analysis.md) vs GATT 프로파일

| 항목 | SDK 분석 결과 | GATT 프로파일 반영 | 일치 여부 |
|------|--------------|-------------------|-----------|
| 커스텀 서비스 UUID | `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` | 서비스 #3으로 등록 | **일치** (단일 소스) |
| Generic Access (0x1800) | SDK 문서에서 언급 | 서비스 #1으로 등록 | **일치** |
| Device Information (0x180A) | 일반 BLE 패턴으로 추정 | 서비스 #2로 등록 | **추정** (실기기 미확인) |
| 개별 특성 UUID | SDK 내부 캡슐화 - 미확인 | C-1~C-4로 역할 추정 | **미확인** |
| Write 특성 | SDK가 BLE Write 수행 (추정) | C-1 (Command Write) | **추론 기반** |
| Notify 특성 | 콜백 패턴으로 Notification 사용 확인 | C-2 (Response Notify) | **추론 기반** |

### APK 분석 (apk-analysis.md) vs GATT 프로파일

| 항목 | APK 분석 결과 | GATT 프로파일 반영 | 일치 여부 |
|------|-------------|-------------------|-----------|
| BLE 연결 흐름 5단계 | 초기화 → 스캔 → 연결 → 핸드셰이크 → 데이터 전송 | 특성 C-1~C-4로 구현됨 (추정) | **구조적 일치** |
| E2EE 복호화 | exportAudio 내부에서 자동 처리 | 특성 C-2 Notify 데이터가 암호화 상태일 수 있음 | **확인 필요** |
| Wi-Fi Direct 전환 | BLE로 Wi-Fi 정보 교환 후 P2P 연결 | 커스텀 서비스 내 별도 특성 가능 | **미확인** |

### 미확인 항목 (실기기 스캔 필요)

| # | 항목 | 확인 방법 | 우선순위 |
|---|------|-----------|---------|
| 1 | 커스텀 서비스 하위 특성 개수 및 UUID | nRF Connect 서비스 탐색 | 최우선 |
| 2 | 각 특성의 실제 Properties (R/W/N/I) | nRF Connect 특성 조회 | 최우선 |
| 3 | 추가 커스텀 서비스 존재 여부 (OTA 등) | nRF Connect 전체 스캔 | 높음 |
| 4 | MTU 협상 결과 (기본 23 vs 확장) | HCI snoop log | 보통 |
| 5 | BLE 광고 패킷 내용 (이름, UUID 포함 여부) | nRF Connect Scanner | 보통 |

## 프로토콜 추론

### 파일 전송 프로토콜 (추정)

SDK API 동작 패턴에서 추론한 BLE 레벨 파일 전송 프로토콜:

```
1. 파일 목록 요청
   App → Device: Write(C-1, CMD_GET_FILE_LIST + sessionId)
   Device → App: Notify(C-2, FILE_LIST_RESPONSE + file_entries[])

2. 파일 다운로드 시작
   App → Device: Write(C-1, CMD_START_DOWNLOAD + sessionId)
   Device → App: Notify(C-2, DOWNLOAD_ACK + total_size)

3. 데이터 수신 (반복)
   Device → App: Notify(C-2, DATA_CHUNK + offset + payload)
   -- MTU 단위로 반복, onProgress() 콜백 트리거

4. 다운로드 완료
   Device → App: Notify(C-2, DOWNLOAD_COMPLETE)
   -- SDK 내부: E2EE 복호화 (해당 시) → WAV/PCM 변환 → onComplete() 콜백

5. 파일 삭제
   App → Device: Write(C-1, CMD_DELETE_FILE + sessionId)
   Device → App: Notify(C-2, DELETE_ACK)
```

### 제어 명령 프로토콜 (추정)

```
1. 디바이스 상태 조회
   App → Device: Write(C-1, CMD_GET_STATE)
   Device → App: Notify(C-2, STATE_RESPONSE + battery + storage + ...)

2. 핸드셰이크 (최초 페어링)
   App → Device: Write(C-4, HANDSHAKE_INIT + bindToken)
   Device → App: Notify(C-4, HANDSHAKE_CHALLENGE)
   -- handshakeWaitSure() 콜백 → 사용자 확인
   App → Device: Write(C-4, HANDSHAKE_CONFIRM)
   Device → App: Notify(C-4, HANDSHAKE_COMPLETE + devToken)
```

### MTU / 데이터 길이 추정

| 항목 | 추정값 | 근거 |
|------|--------|------|
| 기본 MTU | 23 bytes (BLE 기본) | BLE 스펙 기본값 |
| 협상 MTU | 247~512 bytes | BLE 5.2 지원 기기는 확장 MTU 협상 |
| 유효 페이로드 | MTU - 3 bytes (ATT 헤더) | BLE GATT 표준 |
| 전송 속도 | ~2-4 kBps (BLE), 수 MBps (Wi-Fi) | SDK 문서 Wi-Fi Fast Transfer 존재 근거 |

## 결론

### 문서화 상태

| 항목 | 상태 | 비고 |
|------|------|------|
| GATT 서비스 UUID | **Partial** | 1개 커스텀 + 2개 표준 확인, 추가 서비스 미확인 |
| GATT 특성 UUID | **미확인** | SDK 내부 캡슐화, AAR 디컴파일 또는 실기기 스캔 필요 |
| 특성 Properties | **추론** | SDK API 패턴에서 역추론 (Write/Notify/Read) |
| 파일 전송 프로토콜 | **추론** | SDK API 레벨 확인, BLE 프레임 레벨 미확인 |
| 프로토콜 암호화 | **E2EE 존재 확인** | SDK 문서에서 명시, 적용 범위 미확인 |

### Go/No-Go 판정 입력

이 GATT 프로파일 분석 결과는 Go/No-Go 판정(go-nogo.md)의 핵심 입력이다:

- GATT UUID 파악: **Partial** (서비스 레벨만, 특성 레벨 미확인)
- 파일 전송 프로토콜: **SDK API 레벨만 확인** (BLE 프레임 레벨 미문서화)
- 실기기 교차 검증: **미수행** (nRF Connect 스캔 보류)

---

*작성: Phase 14 Plan 02 Task 2*
*다음 단계: go-nogo.md에서 최종 판정*
