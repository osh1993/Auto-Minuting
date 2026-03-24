# Plaud SDK 평가

**평가일:** 2026-03-24
**SDK 버전:** v0.2.8
**GitHub:** https://github.com/Plaud-AI/plaud-sdk

## SDK 개요

| 항목 | 상세 |
|------|------|
| 버전 | v0.2.8 (2025년 12월 릴리스) |
| 라이선스 | MIT |
| 플랫폼 | Android (API 21+), iOS (13.0+), ReactNative |
| 개발사 | Plaud AI (IFLYTEK 관련) |
| 패키지 형태 | AAR 라이브러리 |

### 의존성

| 라이브러리 | 버전 | 비고 |
|------------|------|------|
| Guava | 28.2 | 프로젝트 Guava 버전과 충돌 가능성 (최신: 33.x) |
| Retrofit | 2.9.0 | 프로젝트 권장 2.11.x와 호환 가능 |
| OkHttp | 4.10.0 | 프로젝트 권장 4.12.x와 호환 가능 |

## 핵심 API

### 초기화

```kotlin
// SDK 초기화 (appKey 필수)
Sdk.initSdk(
    context: Context,
    appKey: String,
    bleAgentListener: BleAgentListener,
    cloudAgentListener: CloudAgentListener
)
```

### BLE 연결

```kotlin
// BLE 스캔 시작/중지
agent.scanBle(isStart: Boolean)

// BLE 연결 (bindToken은 최초 페어링 시 발급)
agent.connectionBLE(
    device: BluetoothDevice,
    bindToken: String,
    listener: ConnectionListener
)
```

### 파일 다운로드

```kotlin
// 녹음 세션 목록 조회
agent.getRecSessions(
    sessionId: String?,   // null이면 전체 목록
    listener: RecSessionListener
)

// 파일 동기화(다운로드) 시작
agent.syncFileStart(
    sessionId: String,
    start: Long,          // 시작 시간
    end: Long,            // 종료 시간
    format: String,       // "mp3" 또는 "wav"
    listener: SyncFileListener
)
```

### 클라우드 전사 (부가 기능)

```kotlin
// SDK 내장 클라우드 전사 기능
agent.transcribe(
    sessionId: String,
    language: String,     // "ko-KR" 등
    listener: TranscribeListener
)
```

## appKey 획득

### 신청 방법

1. **이메일 신청:** support@plaud.ai로 appKey 발급 요청
2. **필요 정보 (추정):**
   - 앱 패키지명
   - 개발 목적/사용 사례 설명
   - 개발자/회사 정보
3. **예상 소요 시간:** 1~2주 (비즈니스 영업일 기준, 추정)

### 개인 개발자 가능 여부

- **불확실:** Plaud Developer Platform이 기업 대상인지 개인 개발자에게도 열려있는지 명시적 정보 없음
- **리스크:** 개인 프로젝트라는 이유로 거절당할 가능성 있음
- **대안:** 거절 시 Cloud API 폴백 경로 사용

## 리스크

### 높은 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| appKey 발급 거절/지연 | SDK 사용 불가 | Cloud API 폴백 (D-04) |
| SDK 초기 단계 (v0.2.8) | API 변경, 미완성 기능 가능 | 핵심 API(BLE 연결/파일 다운로드)만 사용 |

### 중간 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| Guava 버전 충돌 | 빌드 실패/런타임 오류 | Gradle dependency resolution 또는 exclude 처리 |
| SDK 업데이트 빈도 낮음 | 버그 수정 지연 | 이슈 리포팅 후 workaround 또는 Cloud API 병행 |
| BLE 연결 안정성 미검증 | 파일 다운로드 실패 | 재시도 로직 구현, 연결 타임아웃 관리 |

### 낮은 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| Retrofit/OkHttp 버전 차이 | 일반적으로 하위 호환 | Gradle force resolution |
| MIT 라이선스 | 제한 없음 | 라이선스 고지 포함 |

## 평가: SDK_RISKY

**판정 근거:**

SDK가 존재하고 핵심 기능(BLE 연결, 파일 다운로드)을 제공하지만, 다음 이유로 "RISKY" 판정:

1. **appKey 획득 불확실성:** 개인 개발자에게 발급 여부 미확인. 발급 거절 시 SDK 사용 불가.
2. **초기 버전(v0.2.8):** 안정성 미검증, API 변경 가능성.
3. **Guava 28.2 의존성:** 최신 Android 프로젝트와 버전 충돌 가능.
4. **커뮤니티/문서 부족:** 사용 사례, 트러블슈팅 자료 부재.

**그럼에도 1차 채택 후보인 이유:**
- APK 역공학/BLE 역공학보다 유지보수성과 안정성이 월등히 높음
- 공식 지원 채널 존재 (support@plaud.ai)
- MIT 라이선스로 사용 제한 없음
- BLE 통신 복잡성을 SDK가 추상화하여 개발 부담 대폭 감소

**권장 행동:**
1. 즉시 support@plaud.ai에 appKey 신청 이메일 발송
2. 대기 중 Cloud API 폴백 경로 병행 준비
3. appKey 수신 후 SDK 테스트 프로젝트 생성하여 BLE 연결/파일 다운로드 동작 확인
