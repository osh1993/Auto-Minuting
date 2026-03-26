---
phase: 14-plaud-protocol
plan: 01
subsystem: plaud-ble-protocol
tags: [reverse-engineering, ble, gatt, protocol-analysis, documentation]
dependency_graph:
  requires: []
  provides: [gatt-uuid-list, protocol-security-assessment, sdk-api-mapping]
  affects: [14-02-PLAN]
tech_stack:
  added: []
  patterns: [sdk-docs-analysis, cross-reference-validation]
key_files:
  created:
    - .planning/phases/14-plaud-protocol/protocol-findings/sdk-analysis.md
    - .planning/phases/14-plaud-protocol/protocol-findings/apk-analysis.md
  modified: []
decisions:
  - SDK AAR 저장소 비공개 확인 — 공식 문서 기반 분석으로 전환
  - E2EE 파일 암호화 존재 확인 — SDK 없이 복호화 불가
  - 초기 판정 Conditional No-Go — 인증 서버 의존 + E2EE
metrics:
  duration: 7.5min
  completed: "2026-03-26T07:30:00Z"
---

# Phase 14 Plan 01: SDK AAR + Plaud APK 디컴파일 정적 분석 Summary

Plaud 공식 SDK 문서(docs.plaud.ai)에서 GATT UUID, 인증 이원화(appKey/partnerToken), E2EE 암호화를 확인하고, BLE 연결 5단계 흐름과 다층 보안 구조를 문서화

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Plaud SDK AAR 확보 및 디컴파일 분석 | 49a6736 | protocol-findings/sdk-analysis.md |
| 2 | Plaud 앱 APK 디컴파일 분석 | 960765f | protocol-findings/apk-analysis.md |

## Key Findings

### 1. SDK AAR 직접 디컴파일 불가

GitHub 저장소 `Plaud-AI/plaud-sdk`가 비공개(private)로 확인됨. AAR 파일은 개발자 플랫폼 등록 후 제공되는 것으로 추정. 대안으로 공식 SDK 문서(docs.plaud.ai/api_guide/sdks/android)에서 API 시그니처, 패키지 구조, 사용 흐름을 상세 분석하였다.

### 2. GATT UUID 발견

- **`e3d196c2-d2c0-4eea-b9d0-a228422e8ad3`**: Plaud 커스텀 128-bit UUID (SDK 문서 HTML에서 추출)
- 표준 BLE 서비스 UUID(0x1800, 0x180A)는 일반 패턴으로 존재 추정
- 개별 GATT 특성 UUID는 SDK 내부에 캡슐화 — AAR 디컴파일 없이 확인 불가

### 3. E2EE 파일 암호화 확인

SDK 문서에서 "E2EE decryption (if needed)" 명시. 녹음 파일이 E2EE로 암호화될 수 있으며, SDK가 자동 복호화 처리. SDK 없이 파일 복호화 불가 가능성 높음.

### 4. 인증 이원화 확인

- **구 펌웨어**: appKey/appSecret (개발자 플랫폼 발급)
- **신 펌웨어 (NotePro, NotePins)**: partnerToken (Partner API OAuth)
- 두 방식 모두 서버 의존적 — 자체 생성 불가

### 5. SDK 패키지 경로 차이

기존 스텁 코드의 패키지 경로가 실제 SDK와 다름:
- 스텁: `com.nicebuild.sdk.ble.TntAgent`
- 실제: `com.nicebuild.sdk.penblesdk.TntAgent`
- Wi-Fi Fast Transfer, deleteFile, unBindDevice 등 누락 API 다수 확인

### 6. 다층 보안 구조

- Layer 1: BLE Link Layer 암호화 (LE Secure Connections)
- Layer 2: SDK 인증 (appKey/partnerToken + handshake)
- Layer 3: E2EE 데이터 암호화 (선택적)

### 초기 Go/No-Go 판단

**Conditional No-Go (SDK 의존 유지 권장)**: 인증 토큰이 서버 의존적이고, E2EE 복호화가 SDK 내부에서 관리되므로, 자체 BLE 구현만으로는 실질적 파일 추출이 어렵다. Plan 02의 BLE 스니핑으로 E2EE 적용 범위를 최종 확인해야 한다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SDK AAR 다운로드 불가 -> 공식 문서 기반 분석 전환**
- **Found during:** Task 1
- **Issue:** GitHub 저장소(Plaud-AI/plaud-sdk)가 private이어서 AAR 다운로드 불가
- **Fix:** Plaud 공식 SDK 문서(docs.plaud.ai)에서 API 시그니처, 패키지 구조, UUID를 추출하여 분석
- **Files modified:** sdk-analysis.md
- **Commit:** 49a6736

**2. [Rule 3 - Blocking] APK 확보 불가 -> SDK 문서 교차 분석 전환**
- **Found during:** Task 2
- **Issue:** 현재 실행 환경에서 APK 바이너리 다운로드 및 jadx 디컴파일 불가
- **Fix:** SDK 문서 기반으로 BLE 연결 흐름을 재구성하고, Plan 02 검증 항목 8개를 정의
- **Files modified:** apk-analysis.md
- **Commit:** 960765f

## Known Stubs

없음 - 이 Plan은 문서 산출물만 생성하며, 코드 스텁이 없다.

## Plan 02 연계 사항

Plan 02(BLE GATT 실기기 캡처 + Go/No-Go 판정)에서 검증해야 할 핵심 항목:

1. GATT UUID `e3d196c2-d2c0-4eea-b9d0-a228422e8ad3` 실기기 존재 확인 (nRF Connect)
2. 추가 GATT 특성 UUID 발견 (서비스 탐색)
3. E2EE 적용 여부 실제 확인 (HCI snoop log 패킷 분석)
4. BLE Link Layer 암호화 확인 (LL_ENC_REQ 존재 여부)
5. Wi-Fi Direct 핸드오프 프로토콜 관찰

## Self-Check: PASSED

- [x] sdk-analysis.md 존재 확인
- [x] apk-analysis.md 존재 확인
- [x] 14-01-SUMMARY.md 존재 확인
- [x] Commit 49a6736 존재 확인 (Task 1)
- [x] Commit 960765f 존재 확인 (Task 2)
