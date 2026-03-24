# POC-01: Plaud 오디오 획득 경로 검증 결과

**검증일:** 2026-03-24
**상태:** 완료
**요구사항:** POC-01 (Plaud 앱 APK 디컴파일 및 BLE 프로토콜/파일 저장 경로 분석)

## 검증 요약

Plaud 녹음기에서 오디오 파일을 획득할 수 있는 3가지 기술 경로를 검증했다:

1. **APK 디컴파일 + FileObserver** -- 직접 디컴파일 불가, Scoped Storage로 FileObserver 불가 판정
2. **Plaud 공식 SDK** -- SDK 존재 확인(v0.2.8), appKey 신청 필요, SDK_RISKY 판정
3. **Cloud API 폴백** -- 비공식 API 존재 확인, JWT 인증, 테스트 스크립트 준비 완료

**핵심 발견:** Plaud가 2025년 말 공식 Developer Platform과 SDK를 출시하여, 당초 계획했던 APK 역공학/BLE 역공학 없이도 공식 경로로 오디오 획득이 가능하다. 단, appKey 발급이 필요하며 개인 개발자 대상 발급 여부는 미확인.

## 경로별 결과

### 경로 1: APK 디컴파일 + FileObserver (D-01, D-02)

| 항목 | 결과 |
|------|------|
| APK 디컴파일 | **불가** -- 현재 환경에서 Plaud 앱 설치 기기 미접근 |
| FileObserver 가능 여부 | **NOT_FEASIBLE** -- Android Scoped Storage(API 30+)로 타 앱 파일 접근 불가 |
| 결론 | FileObserver 경로는 기술적으로 불가능. 이 경로 폐기 권장 |

**상세:** `apk-analysis.md` 참조. Plaud 앱이 내부 저장소(`filesDir`) 또는 앱별 외부 저장소(`getExternalFilesDir`)를 사용할 가능성이 높으며, 두 경우 모두 서드파티 앱에서 FileObserver로 감시 불가.

### 경로 2: Plaud 공식 SDK (신규 발견)

| 항목 | 결과 |
|------|------|
| SDK 존재 | **확인** -- v0.2.8, MIT 라이선스, GitHub 공개 |
| 핵심 기능 | BLE 스캔/연결, 파일 다운로드(MP3/WAV), 녹음 세션 관리 |
| appKey 필요 | **예** -- support@plaud.ai에 신청 필요 |
| 개인 개발자 가능 여부 | **미확인** -- 발급 거절 가능성 있음 |
| 리스크 | 초기 버전, Guava 28.2 의존성 충돌 가능, 커뮤니티 부족 |
| 평가 | **SDK_RISKY** |

**상세:** `sdk-evaluation.md` 참조

### 경로 3: Cloud API 폴백 (D-04)

| 항목 | 결과 |
|------|------|
| API 존재 | **확인** -- https://api.plaud.ai, 비공식 역공학 |
| 인증 | JWT Bearer 토큰 (브라우저에서 추출, ~10개월 유효) |
| 기능 | 녹음 목록 조회, S3 presigned URL로 다운로드 |
| Python 클라이언트 | 존재 (arbuzmell/plaud-api, leonardsellem/plaud-sync-for-obsidian) |
| 테스트 스크립트 | **준비 완료** -- `cloud-api-test.py` |
| 리스크 | 비공식 API (변경/차단 가능), 네트워크 의존, JWT 수동 추출 필요 |

**상세:** `cloud-api-test.py` 참조. JWT 토큰을 환경변수로 설정하여 실행.

## Go/No-Go

### 판정: **Go**

**근거:**
- 최소 2개 경로(SDK, Cloud API)에서 기술적 실현 가능성이 확인됨
- SDK는 공식 경로로 장기적 안정성이 기대됨 (appKey 발급 전제)
- Cloud API는 즉시 사용 가능한 폴백으로 MVP 개발 기간 동안 활용 가능
- FileObserver 경로는 폐기하지만 오디오 획득 자체는 다른 경로로 가능

**조건:**
- SDK 경로: appKey 발급 승인이 전제. 발급 거절 시 Cloud API로 전환
- Cloud API 경로: JWT 토큰 수동 추출이 필요하므로 완전 자동화에는 한계

## 채택 경로

### 1차 채택: Plaud SDK (조건부)

**이유:**
- 공식 지원 경로로 안정적 유지보수 가능
- BLE 연결/파일 다운로드를 SDK가 추상화하여 개발 부담 최소화
- MIT 라이선스로 사용 제한 없음

**사전 조건:**
- [ ] support@plaud.ai에 appKey 신청 이메일 발송
- [ ] appKey 수신 후 테스트 Android 프로젝트에서 SDK 연동 확인
- [ ] BLE 스캔 > 연결 > 파일 다운로드 E2E 테스트

**SDK 채택 기준:**
- appKey 발급 성공
- SDK 초기화 및 BLE 연결 동작 확인
- 파일 다운로드(MP3/WAV) 동작 확인

## 폴백 경로

### 2차 폴백: Cloud API

**언제 전환:**
- SDK appKey 발급 거절 또는 2주 이상 미응답
- SDK BLE 연결 불안정 (3회 이상 연속 실패)
- SDK 기능 미완성으로 파일 다운로드 불가

**특징:**
- JWT 토큰 기반 인증 (브라우저에서 수동 추출)
- 네트워크 의존 (오프라인 사용 불가)
- S3 presigned URL로 오디오 파일 다운로드
- 비공식 API이므로 장기 안정성 불보장

**사용 방식:**
- 사용자가 주기적으로 JWT 토큰을 앱에 입력 (설정 화면)
- 앱에서 Cloud API로 녹음 목록 조회 및 다운로드
- BLE 연결 없이 원격으로 오디오 획득

### 3차 (최후 수단): BLE 역공학

실제 기기에서 APK 디컴파일 + nRF Connect로 GATT UUID 확인 후 직접 BLE 통신 구현.
유지보수 부담이 크므로 SDK/Cloud API 모두 실패 시에만 고려.

## 다음 단계

Phase 2에서 Plaud 오디오 수집 모듈을 구현하기 위한 사전 조건:

### 즉시 필요 (Phase 1 중)

1. **SDK appKey 신청:** support@plaud.ai에 이메일 발송
   - 제목: "Request for Plaud SDK appKey for Android Development"
   - 내용: 앱 목적(회의록 자동화), 패키지명, 개발자 정보 포함
2. **Cloud API JWT 토큰 추출:** 브라우저에서 Plaud 웹사이트 로그인 후 토큰 추출하여 `cloud-api-test.py` 실행

### Phase 2 진입 시 필요

3. **SDK 테스트 프로젝트 설정:** Android Studio에서 Plaud SDK AAR 의존성 추가
4. **BLE 퍼미션 구현:** BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION 런타임 권한
5. **Plaud 기기 페어링 테스트:** SDK `scanBle` > `connectionBLE` 동작 확인
6. **파일 다운로드 테스트:** SDK `syncFileStart`로 MP3/WAV 다운로드 확인

### D-02 결정사항 수정 필요

기존 D-02 "FileObserver로 감시하는 방식을 1차 채택 경로로 검토"는 Scoped Storage로 인해 불가능함이 확인되었다.
**수정 권장:** "Plaud SDK를 통한 BLE 파일 다운로드를 1차 채택 경로로 전환"
