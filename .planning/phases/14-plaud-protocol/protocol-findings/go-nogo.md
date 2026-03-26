# Plaud BLE 자체 구현 Go/No-Go 판정

**판정 일자:** 2026-03-26
**판정 기준:** D-06 (Go 조건), D-07 (No-Go 조건), D-08 (No-Go 시 대안)
**입력 자료:** sdk-analysis.md, apk-analysis.md, gatt-services.md

## 판정 결과

### **No-Go (SDK 의존 유지)**

Plaud BLE 프로토콜 자체 구현은 **No-Go**로 판정한다.
E2EE 파일 암호화와 서버 의존 인증이 확인되어, SDK 없이는 실질적 파일 추출이 불가능하다.

**권장 방향:** Plaud SDK(AAR) 정식 통합을 유지하고, Plaud 개발자 플랫폼에서 API Key(appKey/appSecret 또는 partnerToken) 발급을 추진한다.

## 판정 근거

### 1. GATT UUID 파악 여부

**결과: Partial (서비스 레벨만 파악)**

| 항목 | 상태 | 상세 |
|------|------|------|
| 커스텀 서비스 UUID | 1개 확인 | `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` (SDK 문서 HTML에서 추출) |
| 표준 서비스 UUID | 2개 추정 | Generic Access (0x1800), Device Information (0x180A) |
| 특성 UUID | **미확인** | SDK 내부 캡슐화 - AAR 디컴파일 또는 실기기 nRF Connect 스캔 필요 |

**D-06 Go 조건 미충족:** "BLE GATT 서비스/특성 UUID를 파악" 조건에서 특성 UUID가 미파악 상태이다.

### 2. 파일 전송 프로토콜 문서화 여부

**결과: SDK API 레벨만 문서화 (BLE 프레임 레벨 미확인)**

| 항목 | 상태 | 상세 |
|------|------|------|
| SDK API 흐름 | 문서화 완료 | getFileList() → exportAudio() → onProgress → onComplete |
| BLE Write/Notify 패턴 | 추론만 가능 | SDK가 내부적으로 처리, 명령 바이트 구조 미확인 |
| 명령어/응답 포맷 | **미확인** | 바이트 레벨 프로토콜 (opcode, 페이로드 구조) 미파악 |
| MTU 협상 | **미확인** | 실기기 HCI snoop log 필요 |

**D-06 Go 조건 미충족:** "파일 전송 프로토콜(명령어/응답 패턴)을 문서화" 조건에서 BLE 프레임 레벨 프로토콜이 미문서화 상태이다.

### 3. 프로토콜 암호화 여부

**결과: E2EE 암호화 적용 확인 - SDK 없이 복호화 불가**

| 보안 계층 | 상태 | 영향 |
|-----------|------|------|
| Layer 1: BLE Link Layer | LE Secure Connections (표준) | 정상 페어링 시 극복 가능 |
| Layer 2: SDK 인증 | appKey/partnerToken 필수 | **서버 발급만 가능 - 자체 생성 불가** |
| Layer 3: E2EE 데이터 암호화 | "E2EE decryption (if needed)" 명시 | **SDK 없이 복호화 불가** |

**D-07 No-Go 조건 해당:** "프로토콜이 암호화되어 있어 해독 불가"
- SDK 문서에서 E2EE 존재가 명시적으로 확인됨
- 복호화 키는 SDK 내부에서 관리되며, 외부 접근 불가
- BLE 패킷을 직접 캡처하더라도 암호화된 데이터만 획득

### 4. 인증 토큰 서버 의존 여부

**결과: 서버 의존 - 자체 생성 불가**

| 인증 요소 | 발급 방식 | 자체 재현 |
|-----------|-----------|----------|
| appKey | Plaud 개발자 플랫폼(platform.plaud.ai) 등록 후 발급 | **불가** |
| appSecret | Plaud 개발자 플랫폼 등록 후 발급 | **불가** |
| partnerToken | Partner API OAuth (`POST /oauth/partner/access-token`) | **불가** (파트너 계약 필요) |
| bindToken | SDK 내부 BLE 페어링 시 생성 | SDK 필요 |
| devToken | SDK 내부 관리 | SDK 필요 |
| E2EE 키 | SDK 내부 키 관리 | SDK 필요 |

**D-07 No-Go 조건 해당:** "인증 토큰이 서버 의존적이어서 재현 불가능"
- SDK 초기화(`NiceBuildSdk.initSdk()`)에 appKey/appSecret 또는 partnerToken이 필수
- 이 토큰 없이는 SDK 자체가 초기화되지 않음
- 자체 BLE 구현을 해도, 핸드셰이크 인증을 통과할 수 없음

## 분석 요약

### SDK 분석에서 발견한 핵심 사항

1. **SDK AAR 저장소 비공개**: GitHub `Plaud-AI/plaud-sdk` private, AAR 직접 디컴파일 불가
2. **E2EE 암호화 존재 확인**: `exportAudio`가 내부적으로 E2EE 복호화 + 포맷 변환 자동 처리
3. **인증 이원화**: 구 펌웨어(appKey/appSecret) + 신 펌웨어(partnerToken), 모두 서버 의존
4. **커스텀 GATT UUID**: `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` 발견, 특성 UUID는 미확인
5. **Wi-Fi Fast Transfer**: SDK가 BLE + Wi-Fi 이중 전송 지원, 스텁에는 미반영

### APK 분석에서 발견한 핵심 사항

1. **APK 직접 디컴파일 미수행**: 현재 환경에서 APK 확보 불가
2. **BLE 연결 5단계 흐름 문서화**: 초기화 → 스캔 → 연결 → 핸드셰이크 → 데이터 전송
3. **다층 보안 구조 확인**: L1(BLE Link) + L2(SDK 인증) + L3(E2EE)
4. **INTERNET 권한 필요**: SDK가 서버 통신 수행 (인증/키 교환)

### BLE 스니핑 결과 (실기기 미수행)

- nRF Connect 실기기 스캔: **보류** (물리적 녹음기 접근 필요)
- HCI snoop log 캡처: **미수행**
- E2EE 적용 범위 실측: **미확인**

> **참고:** 실기기 BLE 스캔이 수행되지 않았으나, 정적 분석만으로도 No-Go 판정에 충분한 근거가 확보되었다. E2EE 암호화와 서버 의존 인증은 BLE 스캔 결과와 무관하게 SDK 의존을 불가피하게 만든다.

## 판정 기준 체크리스트

| D-06 Go 조건 | 충족 여부 | 비고 |
|--------------|-----------|------|
| BLE GATT 서비스/특성 UUID 파악 | **미충족** | 서비스 UUID만 부분 파악, 특성 UUID 미확인 |
| 파일 전송 프로토콜 문서화 | **미충족** | SDK API 레벨만, BLE 프레임 레벨 미문서화 |

| D-07 No-Go 조건 | 해당 여부 | 비고 |
|-----------------|-----------|------|
| 프로토콜 암호화 | **해당** | E2EE 존재 확인 (SDK 문서 명시) |
| 인증 토큰 서버 의존 | **해당** | appKey/appSecret/partnerToken 모두 서버 발급 |

**결론: D-06 Go 조건 미충족 + D-07 No-Go 조건 2개 해당 = No-Go**

## 다음 단계 (D-08: SDK 의존 유지 전략)

### 즉시 조치

1. **Plaud 개발자 플랫폼 등록 추진**
   - `platform.plaud.ai`에서 개발자 계정 생성
   - appKey/appSecret 발급 신청
   - 또는 Partner API 접근 신청 (partnerToken 방식)

2. **SDK AAR 정식 확보**
   - 개발자 플랫폼 등록 후 SDK AAR 다운로드
   - `app/libs/plaud-sdk.aar`로 배치
   - 현재 스텁 코드(`com.nicebuild.sdk.*`)를 실제 SDK로 교체

3. **스텁 코드 패키지 경로 수정 준비**
   - 스텁: `com.nicebuild.sdk.ble.*`
   - 실제: `com.nicebuild.sdk.penblesdk.*`
   - SDK AAR 통합 시 import 경로 전면 수정 필요

### 중기 과제

4. **Wi-Fi Fast Transfer 지원 추가**
   - SDK의 `IWifiAgent` 인터페이스 활용
   - BLE 전송 대비 수십~수백 배 빠른 파일 전송 가능
   - `exportAudioViaWiFi()` API 통합

5. **누락 SDK API 통합**
   - `deleteFile()` - 기기 내 파일 삭제
   - `unBindDevice()` - 페어링 해제
   - `deviceOpRecordStart/Stop` - 원격 녹음 제어

### 대안 경로 (SDK API Key 발급 실패 시)

6. **Plaud 앱 공유 연동**
   - Plaud 앱에서 파일 동기화 후 Share Intent로 Auto Minuting에 전달
   - Phase 9의 삼성 녹음앱 공유 수신과 동일한 패턴
   - SDK 의존 없이 사용 가능하나, 수동 공유 단계 필요

7. **Plaud Cloud API 활용**
   - Plaud 클라우드에 동기화된 파일을 REST API로 다운로드
   - 사용자 로그인 인증 필요
   - 네트워크 의존적 (오프라인 불가)

## Wi-Fi Direct 분석 결과 (부차적, D-05)

### SDK 문서 기반 발견 사항

- SDK가 `IWifiAgent` 인터페이스로 Wi-Fi Direct 전송 지원
- `startWifiTransfer()` → BLE 연결 상태에서 Wi-Fi P2P로 전환
- `stopWifiTransfer()` → Wi-Fi 해제 + BLE 재연결
- 전송 속도: 수 MBps (BLE 대비 수백 배)
- SDK 내부에서 Wi-Fi Direct 연결 수립, 파일 전송, 연결 해제를 자동 관리

### 자체 Wi-Fi Direct 구현 가능성

**No-Go** - SDK와 동일한 이유:
- Wi-Fi Direct 핸드오프 프로토콜이 SDK 내부에 캡슐화
- 파일 전송 후 E2EE 복호화가 여전히 필요
- 인증 토큰 없이 Wi-Fi 전송 시작 불가 (BLE 연결이 선행)

## 최종 권고

### 핵심 메시지

> Plaud BLE/Wi-Fi 프로토콜을 자체 구현하는 것은 기술적으로 불가능하지 않으나,
> E2EE 파일 암호화와 서버 의존 인증으로 인해 **실질적 가치가 없다**.
> 자체 BLE 구현으로 연결은 가능하더라도, 받은 파일을 복호화할 수 없다.
>
> **유일한 실용적 경로: Plaud SDK(AAR) 정식 통합 + API Key 발급**

### 리스크 관리

| 리스크 | 완화 방안 |
|--------|-----------|
| API Key 발급 거부/지연 | Plaud 앱 공유 연동(Share Intent)으로 폴백 |
| SDK 업데이트로 API 변경 | 스텁 레이어 유지로 변경 격리 |
| SDK AAR 라이선스 문제 | MIT 라이선스 확인됨 (sdk-analysis.md) |
| Plaud 서비스 종료 | Cloud API 폴백 + 공유 연동 유지 |

---

*판정: Phase 14 Plan 02 Task 3*
*판정자: 정적 분석(Plan 01) + GATT 프로파일 분석(Plan 02) 종합*
*다음 단계: SDK API Key 발급 추진*
