# Phase 14: Plaud 연결 프로토콜 분석 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-03-26
**Phase:** 14-plaud-protocol
**Areas discussed:** 보유 장비/환경, 분석 방법론, Go/No-Go 기준

---

## 보유 장비/환경

**User's observation:** Plaud 앱에서 자동으로 녹음기와 연결. 녹음기 전원 켜면 자동 연결. 파일 전송 시작. 스마트폰 Bluetooth 설정에 안 보임. "빠른 전송" 누르면 Wi-Fi 연결(스마트폰-녹음기) 맺음.

**결론:** 기본 BLE 연결 + Wi-Fi Direct 빠른 전송. Bluetooth 설정에 안 보이는 건 BLE 특성(Classic BT와 다름).

## 분석 방법론

| Option | Description | Selected |
|--------|-------------|----------|
| APK 디컴파일 + 네트워크 캡처 | jadx로 GATT UUID 추출 + nRF Connect BLE 스니핑 | ✓ |
| BLE 스니핑만 | 실제 통신만 관찰 | |
| APK 디컴파일만 | 코드만 분석 | |

## Go/No-Go 기준

| Option | Description | Selected |
|--------|-------------|----------|
| GATT UUID + 파일전송 프로토콜 파악 | UUID 파악 + 프로토콜 문서화 가능하면 Go | ✓ |
| SDK 없이 연결만 되면 Go | 기기 발견+연결 성공만으로 Go | |

## Deferred Ideas

None
