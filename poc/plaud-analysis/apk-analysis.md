# Plaud 앱 APK 분석 결과

**분석일:** 2026-03-24
**분석 방법:** Plaud SDK 소스 코드 분석 + 공개 역공학 자료 종합 (직접 APK 디컴파일 불가)

## 분석 불가 사유

현재 환경(Windows PC)에서 Plaud 앱이 설치된 Android 기기에 접근할 수 없어 직접 APK 추출 및 jadx 디컴파일을 수행하지 못했다.
대안으로 Plaud 공식 SDK(v0.2.8, GitHub 공개) 소스 코드와 비공식 Cloud API 역공학 자료를 종합하여 분석했다.

> **대안 경로:** Plaud 공식 SDK가 2025년 말 출시되어, APK 역공학 없이도 BLE 연결/파일 다운로드가 가능하다. SDK 경로를 1차 채택 후보로 전환 권장. 상세는 `sdk-evaluation.md` 참조.

---

## 패키지 정보

| 항목 | 값 | 출처 |
|------|-----|------|
| 패키지명 | `ai.plaud.note` | Play Store 확인 |
| 최소 SDK | API 21 (Android 5.0) | Plaud SDK README |
| 타깃 SDK | 미확인 (APK 추출 불가) | - |
| 주요 퍼미션 (추정) | BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION, INTERNET, READ/WRITE_EXTERNAL_STORAGE | BLE 통신 + 파일 저장 + 클라우드 동기화에 필수 |

## 파일 저장 경로

### SDK 기반 분석

Plaud SDK의 파일 다운로드 API(`syncFileStart`)는 녹음 파일을 앱이 지정한 로컬 경로에 저장한다.
SDK 소스 분석 결과:

| 저장소 유형 | 사용 여부 | 근거 |
|-------------|----------|------|
| `filesDir` (내부 저장소) | **가능성 높음** | SDK가 앱 내부 저장소에 파일을 다운로드하는 것이 일반적 패턴. Plaud 앱도 동일할 가능성 높음 |
| `getExternalFilesDir` (앱별 외부 저장소) | **가능성 있음** | 대용량 오디오 파일 저장 시 외부 저장소 사용 가능 |
| `MediaStore` (공유 저장소) | **가능성 낮음** | 녹음 앱이 MediaStore에 직접 등록하는 경우는 드묾 |
| `Environment.getExternalStorageDirectory` | **가능성 매우 낮음** | API 29+ Scoped Storage 정책으로 deprecated |

### Scoped Storage 분석

Android 11(API 30) 이상에서는 Scoped Storage가 강제 적용된다:
- 다른 앱의 `filesDir`(내부 저장소)에 접근 불가
- 다른 앱의 `getExternalFilesDir`에도 접근 불가
- `MediaStore`에 등록된 파일만 `ContentResolver`로 접근 가능
- SAF(Storage Access Framework)를 통한 사용자 명시적 접근은 가능

### FileObserver 가능 여부 판단

- **Plaud 앱이 `filesDir` 사용 시 (가능성 높음):** FileObserver로 다른 앱의 내부 저장소를 감시할 수 없음
- **Plaud 앱이 `getExternalFilesDir` 사용 시:** Android 11+에서 접근 제한, FileObserver 사용 불가
- **Plaud 앱이 `MediaStore` 사용 시 (가능성 낮음):** `ContentObserver`로 감시 가능하나 확률 낮음

> **FileObserver: NOT_FEASIBLE**
>
> Android Scoped Storage 정책(API 30+)으로 인해 Plaud 앱의 파일 저장소를 FileObserver로 감시하는 것은 사실상 불가능하다.
> Plaud 앱이 내부 저장소(`filesDir`) 또는 앱별 외부 저장소(`getExternalFilesDir`)를 사용할 가능성이 높으며, 두 경우 모두 서드파티 앱에서 접근할 수 없다.
> MediaStore 등록 가능성은 낮으며, 실제 APK 디컴파일로 확인이 필요하다.

## 오디오 포맷

Plaud SDK 및 공개 자료에서 확인된 오디오 포맷:

| 포맷 | 지원 여부 | 상세 |
|------|----------|------|
| MP3 | **지원** | SDK에서 MP3 다운로드 지원 명시 |
| WAV | **지원** | SDK에서 WAV 다운로드 지원 명시 |
| 기타 | 미확인 | Plaud 기기 내부에서는 독자 포맷 사용 가능성 있음 (BLE 전송 시 변환) |

**인코딩 상세:**
- MP3: 비트레이트 미확인 (일반적으로 128kbps ~ 320kbps)
- WAV: 샘플레이트 미확인 (일반적으로 16kHz ~ 44.1kHz, 16bit PCM)
- 실제 포맷 파라미터는 SDK를 통한 파일 다운로드 시 확인 필요

## BLE 프로토콜

### SDK에서 확인된 BLE 통신 구조

Plaud SDK는 BLE 통신을 추상화하여 제공한다:

| API | 기능 | BLE 동작 |
|-----|------|----------|
| `scanBle(isStart)` | BLE 스캔 시작/중지 | GATT 서비스 디스커버리 |
| `connectionBLE(device, bindToken)` | BLE 연결 | GATT 연결 + 인증(bindToken) |
| `syncFileStart(sessionId, start, end)` | 파일 동기화 | GATT 파일 전송 프로토콜 |
| `getRecSessions(sessionId)` | 녹음 세션 목록 | GATT 명령 전송 |

### GATT 서비스/특성 UUID

직접 APK 디컴파일을 수행하지 못하여 GATT UUID를 확인하지 못했다.

**확인 방법 (추후):**
1. nRF Connect 앱으로 Plaud 기기 스캔하여 서비스/특성 UUID 확인
2. Plaud 앱 APK 디컴파일 후 `UUID.fromString` 검색
3. Plaud SDK 소스에서 BLE 통신 모듈 분석 (난독화 가능성)

### BLE 통신 패턴 (추정)

Plaud SDK의 API 구조로부터 추정되는 통신 패턴:
1. BLE 스캔으로 Plaud 기기 발견
2. GATT 연결 + `bindToken`으로 인증
3. 녹음 세션 목록 요청 (GATT 명령)
4. 파일 다운로드 시작 (GATT 파일 전송 -- chunk 단위 전송 추정)
5. 파일 다운로드 완료 후 로컬 저장

## 클라우드 API

### 확인된 엔드포인트

비공식 역공학 결과(plaud-api 프로젝트)에서 확인:

| 엔드포인트 | 메서드 | 기능 |
|------------|--------|------|
| `https://api.plaud.ai` | - | 기본 API 서버 |
| `https://api-euc1.plaud.ai` | - | EU 리전 서버 |
| `/recordings` (추정) | GET | 녹음 목록 조회 |
| `/recordings/{id}/download` (추정) | GET | 오디오 다운로드 URL (S3 presigned URL 반환) |

### 인증 방식

- **JWT Bearer 토큰**: 브라우저에서 Plaud 웹사이트 로그인 후 localStorage에서 추출
- **토큰 유효 기간**: 약 10개월 (역공학 자료 기준)
- **추출 방법**: 브라우저 개발자 도구 > Application > Local Storage > `plaud.ai` > JWT 토큰 복사

## 결론

### FileObserver 가능 여부

> **FileObserver: NOT_FEASIBLE**

Android Scoped Storage 정책으로 인해 Plaud 앱의 파일을 FileObserver로 감시하는 것은 현실적으로 불가능하다. 이는 D-02(FileObserver 1차 채택 검토) 결정에 대한 기술적 반증이다.

### 대안 경로 권장

FileObserver 경로가 불가능하므로, 다음 대안을 권장한다:

1. **1차 채택: Plaud SDK** -- 공식 SDK(v0.2.8)를 통한 BLE 연결 및 파일 다운로드. appKey 신청 필요.
2. **2차 폴백: Cloud API** -- JWT 토큰 기반 비공식 API로 오디오 파일 다운로드. 네트워크 의존.
3. **3차 (보류): APK 디컴파일 + BLE 역공학** -- 실제 기기 접근 가능 시 jadx 디컴파일로 GATT UUID 확인 후 직접 BLE 통신 구현. 유지보수 부담 큼.

### 미확인 사항 (추후 APK 디컴파일 시 확인 필요)

- Plaud 앱의 정확한 파일 저장 경로 (`filesDir` vs `getExternalFilesDir`)
- GATT 서비스/특성 UUID 목록
- BLE 파일 전송 프로토콜 상세 (chunk 크기, 체크섬 등)
- 오디오 파일 정확한 인코딩 파라미터 (비트레이트, 샘플레이트)
