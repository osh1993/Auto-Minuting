# Architecture Patterns

**Domain:** BLE 녹음기 연동 + 온디바이스 AI 전사 + 클라우드 회의록 생성 Android 앱
**Researched:** 2026-03-24

## Recommended Architecture

### 개요

Clean Architecture 기반의 파이프라인 구조를 권장한다. 이 앱은 본질적으로 **데이터 파이프라인**이다: 음성 파일이 BLE를 통해 들어오고, 전사되고, 클라우드에서 회의록으로 변환되어 로컬에 저장된다. 각 단계는 독립적으로 실패하고 재시도할 수 있어야 한다.

### System Diagram

    UI Layer: Jetpack Compose UI + ViewModels (상태 관리)
      |  StateFlow / UDF
    Domain Layer: Use Cases - PipelineOrchestrator, TranscriptionUseCase 등
      |
    Data Layer: Repositories - AudioRepo, TranscriptRepo, MinutesRepo
      |          |           |           |
    BLE        File        Galaxy AI   NotebookLM/
    DataSource System      Source      Gemini Source

### Component Boundaries

| Component | Responsibility | Communicates With | 격리 이유 |
|-----------|---------------|-------------------|-----------|
| **BleManager** | Plaud 녹음기 스캔, 연결, GATT 통신, 오디오 파일 수신 | FileStorage, PipelineOrchestrator | BLE 프로토콜은 리버스 엔지니어링 대상이므로 변경 시 이 컴포넌트만 수정 |
| **FileStorage** | 오디오 파일/전사본/회의록 로컬 저장/조회 | Room DB, 내부 저장소 | 파일 관리 로직 통합 |
| **TranscriptionEngine** | Galaxy AI 전사 호출, 결과 수신 | FileStorage, PipelineOrchestrator | Galaxy AI 접근 방식 변경 시 격리 |
| **NotebookLmClient** | NotebookLM 소스 등록, 회의록 생성 요청 | PipelineOrchestrator, FileStorage | 클라우드 API 변경에 대한 격리 |
| **PipelineOrchestrator** | 전체 파이프라인 단계 조율, 상태 관리, 재시도 | 모든 컴포넌트 | 파이프라인 로직의 단일 제어점 |
| **UI (Compose + ViewModel)** | 파이프라인 상태 표시, 설정, 회의록 조회 | PipelineOrchestrator, FileStorage | 표준 UI 분리 |

---

## 주요 컴포넌트 상세

### 1. BLE Communication Layer (BleManager)

**역할:** Plaud 녹음기와의 BLE GATT 통신을 전담.

**핵심 설계:**
- Android BLE API (BluetoothGatt, BluetoothGattCallback) 직접 사용
- **Foreground Service** 필수: BLE 연결 유지 및 대용량 파일 전송 시 앱이 백그라운드로 가도 끊기지 않도록
- BLE 스캔 시 ScanFilter로 Plaud 디바이스만 필터링 (UUID 또는 디바이스 이름 기반)
- GATT 서비스/특성 구조는 리버스 엔지니어링으로 파악 필요

**권한 (API 31+):** BLUETOOTH_SCAN, BLUETOOTH_CONNECT, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE

**대안 접근법 (리버스 엔지니어링 우회):**

Plaud 앱이 다운로드한 파일을 감시하는 방식도 고려해야 한다:
- FileObserver 또는 ContentObserver로 Plaud 앱의 저장 디렉토리 감시
- 이 경우 BLE 직접 통신이 불필요해지므로 아키텍처가 크게 단순화됨
- **권장:** 리서치 Phase에서 두 접근법 모두 탐색하고, 파일 감시가 가능하면 우선 채택

### 2. File Storage Layer (FileStorage)

**역할:** 모든 파일 I/O와 메타데이터 관리.

**핵심 설계:**
- **Room Database:** 회의 메타데이터 (날짜, 제목, 상태, 파일 경로 등)
- **내부 저장소 (app-specific):** 오디오 파일, 전사 텍스트, 회의록
- 파이프라인 상태 추적: RECEIVED, TRANSCRIBING, TRANSCRIBED, GENERATING_MINUTES, COMPLETED, FAILED

**Room Entity 설계 (Kotlin):**

    @Entity(tableName = "meetings")
    data class MeetingEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val title: String,
        val recordedAt: Instant,
        val audioFilePath: String,
        val transcriptPath: String? = null,
        val minutesPath: String? = null,
        val pipelineStatus: PipelineStatus,
        val notebookId: String? = null,
        val errorMessage: String? = null
    )

    enum class PipelineStatus {
        AUDIO_RECEIVED, TRANSCRIBING, TRANSCRIBED,
        GENERATING_MINUTES, COMPLETED, FAILED
    }

### 3. Transcription Engine (TranscriptionEngine)

**역할:** Galaxy AI 온디바이스 전사 기능 호출.

**핵심 설계 (불확실성 높음 - LOW confidence):**

Samsung Galaxy AI의 전사 기능에 대한 공식 개발자 API가 확인되지 않았다. 아래는 가능한 접근 방식 우선순위:

1. **Android SpeechRecognizer API 활용 (우선 시도)**
   - Samsung 디바이스에서 SpeechRecognizer의 기본 엔진이 Samsung의 온디바이스 모델일 수 있음
   - SpeechRecognizer.createOnDeviceSpeechRecognizer(context) (API 31+)로 온디바이스 인식 시도
   - 제한: 실시간 스트리밍 인식 전용으로, 긴 오디오 파일 배치 전사에는 부적합할 수 있음

2. **Samsung의 녹음 앱 Intent 연동**
   - Samsung Voice Recorder 앱의 전사 기능을 Intent로 트리거하는 방식
   - 가능 여부 리서치 필요

3. **Accessibility Service 기반 자동화 (최후 수단)**
   - Galaxy AI 전사 UI를 프로그래밍 방식으로 조작
   - 유지보수 부담이 크고 fragile함

4. **대체안: Whisper 온디바이스 (폴백)**
   - OpenAI Whisper의 경량 모델을 ONNX/TFLite로 변환하여 온디바이스 실행
   - 한국어 지원 양호, Galaxy AI 불가 시 실용적 대안
   - 단점: 모델 크기 (약 75MB~1GB), 추론 시간

### 4. NotebookLM Integration (NotebookLmClient)

**역할:** 전사된 텍스트를 NotebookLM에 업로드하고 회의록 생성.

**핵심 설계:**

NotebookLM에는 공식 REST API가 없다. 현재 프로젝트에 NotebookLM MCP 서버가 설정되어 있으므로, 연동 방식은 다음과 같이 계층화:

1. **MCP 서버 기반 연동 (현재 가용)**
   - 이미 설정된 notebooklm-mcp 서버를 중간 다리로 활용
   - Android 앱에서 MCP 서버를 호스팅하는 PC/서버에 HTTP 요청
   - 장점: 이미 인증 완료, 안정적 접근
   - 단점: 별도 서버 필요, 모바일 독립 실행 불가

2. **NotebookLM 웹 자동화 (대안)**
   - 헤드리스 브라우저 또는 HTTP 요청으로 NotebookLM 웹 인터페이스 자동화
   - fragile하고 Google ToS 위반 가능성

3. **Google Gemini API 직접 사용 (실용적 대안)**
   - NotebookLM 대신 Gemini API에 전사 텍스트와 회의록 프롬프트를 직접 전송
   - 공식 API, 안정적, 모바일에서 직접 호출 가능
   - 단점: NotebookLM의 노트북 관리/소스 관리 기능 없음

**권장:** Phase 1에서는 Gemini API 직접 호출로 MVP 구현. 이후 MCP 브릿지 서버 연동을 Phase 2에서 추가.

### 5. Pipeline Orchestrator (PipelineOrchestrator)

**역할:** 전체 자동화 파이프라인의 상태 머신 관리.

**핵심 설계:**
- **WorkManager** 기반: 각 파이프라인 단계를 Worker로 구현하여 체이닝
- 단계별 실패 시 독립적 재시도 가능
- 네트워크 상태, 배터리 상태 등 제약 조건 설정 가능
- WorkManager의 beginWith().then() 패턴으로 단계 체이닝
- BackoffPolicy.EXPONENTIAL로 재시도 간격 설정
- Constraints로 네트워크/배터리 조건 지정

**자동화 모드:**
- **완전 자동:** BLE 파일 수신 즉시 파이프라인 전체 실행
- **하이브리드:** 각 단계 사이에 사용자 확인 요청 (Notification + Action)

### 6. UI Layer

**역할:** 파이프라인 상태 표시, 설정, 결과 조회.

| 화면 | 목적 | 주요 기능 |
|------|------|-----------|
| **대시보드** | 파이프라인 실시간 상태 | 진행 중 작업, 최근 회의록, 연결 상태 |
| **회의록 목록** | 생성된 회의록 조회 | 검색, 필터, 정렬 |
| **회의록 상세** | 개별 회의록 확인 | 전사 텍스트, 회의록, 오디오 재생 |
| **설정** | 앱 설정 | 자동화 모드, 회의록 형식, NotebookLM 계정, Plaud 연결 |

---

## 전체 Data Flow

    [Plaud 녹음기]
        | (1) BLE GATT 파일 전송
    [BleManager / FileObserver]
        | (2) 오디오 파일 로컬 저장
    [FileStorage] ---- Room DB 메타데이터 기록 (status: AUDIO_RECEIVED)
        | (3) 파이프라인 트리거
    [PipelineOrchestrator]
        | (4) TranscribeWorker 실행
    [TranscriptionEngine]
        | (5) Galaxy AI / SpeechRecognizer 호출 (오디오 to 텍스트)
    [FileStorage] ---- 전사 텍스트 저장 (status: TRANSCRIBED)
        | (6) GenerateMinutesWorker 실행
    [NotebookLmClient]
        | (7) Gemini API / MCP Bridge 호출 (전사 텍스트 to 회의록)
    [FileStorage] ---- 회의록 저장 (status: COMPLETED)
        | (8) UI 알림
    [사용자에게 Notification + UI 업데이트]

---

## Patterns to Follow

### Pattern 1: Repository Pattern (데이터 추상화)
**What:** 각 데이터 소스(BLE, 파일, 네트워크)를 Repository 인터페이스 뒤에 숨김
**When:** 항상. 모든 데이터 접근은 Repository를 통해.
**Why:** BLE 직접 통신 vs 파일 감시, Gemini API vs MCP 등 구현이 변경될 때 나머지 코드에 영향 없음. AudioRepository 인터페이스를 정의하고 BleAudioRepository, FileObserverAudioRepository 등으로 구현체를 교체 가능하게 설계.

### Pattern 2: State Machine (파이프라인 상태 관리)
**What:** 파이프라인 진행 상태를 명시적 상태 머신으로 관리
**When:** 파이프라인 각 단계의 전이와 에러 처리
**Why:** 중간 실패 시 어디서 재시작할지 명확, 상태 추적 용이. Kotlin sealed class로 AudioReceived, Transcribing, Transcribed, GeneratingMinutes, Completed, Failed 상태를 정의.

### Pattern 3: Foreground Service + WorkManager 조합
**What:** BLE 연결은 Foreground Service, 후속 처리는 WorkManager
**When:** BLE 통신은 지속 연결 필요, 전사/회의록 생성은 비동기 배치
**Why:** BLE는 실시간 연결 유지가 필요하지만, 전사/API 호출은 시스템이 적절한 시점에 실행해도 됨

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: 단일 서비스에 모든 로직 집중
**What:** BLE 통신, 전사, API 호출을 하나의 Service에 구현
**Why bad:** 한 단계 실패가 전체를 중단, 테스트 불가, 코드 비대화
**Instead:** 각 관심사를 독립 컴포넌트로 분리, WorkManager 체이닝으로 연결

### Anti-Pattern 2: BLE 작업을 메인 스레드에서 실행
**What:** GATT 콜백을 메인 스레드에서 처리하고 무거운 작업 수행
**Why bad:** ANR, UI 멈춤
**Instead:** Coroutine Dispatcher.IO에서 BLE 데이터 처리, 메인 스레드는 UI 업데이트만

### Anti-Pattern 3: 파이프라인 상태를 메모리에만 보관
**What:** 파이프라인 진행 상태를 ViewModel이나 변수에만 저장
**Why bad:** 프로세스 종료 시 상태 유실, 재시작 시 어디서부터 재개할지 모름
**Instead:** Room DB에 상태 영속화, WorkManager로 시스템 재시작 후에도 재개

### Anti-Pattern 4: Galaxy AI에 강결합
**What:** 전사 로직이 Galaxy AI 특정 API에 직접 의존
**Why bad:** Galaxy AI API가 없거나 변경되면 전체 전사 기능 불가
**Instead:** TranscriptionEngine 인터페이스 뒤에 추상화, 폴백 구현 준비

---

## Scalability Considerations

이 앱은 개인/소규모 팀 사용 목적이므로 대규모 스케일링보다는 안정성과 오프라인 대응이 중요.

| Concern | 개인 사용 | 팀 사용 (10명) | 향후 확장 |
|---------|-----------|---------------|-----------|
| **오디오 저장** | 내부 저장소, 수동 정리 | 저장 정책 필요 (30일 자동 삭제 등) | 클라우드 백업 옵션 |
| **전사 처리** | 순차 처리 충분 | 큐 기반 순차 처리 | 병렬 처리, 우선순위 큐 |
| **API 호출** | Rate limit 걱정 없음 | Gemini API 쿼터 관리 | API 키 관리, 사용량 모니터링 |
| **BLE 연결** | 1:1 연결 | 여전히 1:1 (개인 녹음기) | N/A |

---

## Suggested Build Order (의존성 기반)

빌드 순서는 컴포넌트 간 의존성과 불확실성 수준에 의해 결정된다.

### Phase 0: 리버스 엔지니어링 리서치 (선행 필수)
- Plaud 앱 BLE 통신 분석 (GATT 서비스/특성 매핑)
- Plaud 앱의 로컬 파일 저장 경로 확인
- Galaxy AI 전사 API 접근 가능 여부 확인
- **이 Phase의 결과가 전체 아키텍처를 결정한다** (BLE 직접 통신 vs 파일 감시)

### Phase 1: 기반 구조
- 프로젝트 설정 (Kotlin, Compose, Hilt, Room, WorkManager)
- Room DB 스키마 및 FileStorage 구현
- 기본 UI 쉘 (Navigation, 빈 화면들)
- **의존성:** 없음. 가장 확실한 부분부터 시작.

### Phase 2: 오디오 수집
- BLE 직접 통신 또는 파일 감시 구현 (Phase 0 결과에 따라)
- Foreground Service 구현
- 오디오 파일 수신/저장 파이프라인
- **의존성:** Phase 0 (리서치 결과), Phase 1 (FileStorage)

### Phase 3: 전사 엔진
- Galaxy AI / SpeechRecognizer 연동 시도
- 불가 시 Whisper 온디바이스 폴백 구현
- TranscriptionEngine 인터페이스 + 구현체
- **의존성:** Phase 1 (FileStorage), Phase 0 (Galaxy AI 리서치)

### Phase 4: 회의록 생성
- Gemini API 연동 (MVP)
- 회의록 프롬프트 설계
- NotebookLM MCP 브릿지 연동 (선택)
- **의존성:** Phase 3 (전사 결과가 있어야 테스트 가능)

### Phase 5: 파이프라인 통합 및 자동화
- PipelineOrchestrator + WorkManager 체이닝
- 완전 자동 / 하이브리드 모드 구현
- 에러 처리 및 재시도 로직
- **의존성:** Phase 2, 3, 4 모두 완성

### Phase 6: UI 완성 및 폴리싱
- 대시보드, 회의록 목록/상세, 설정 화면
- Notification 연동
- 회의록 형식 선택 기능
- **의존성:** Phase 5 (파이프라인 완성)

**Phase 의존성 그래프:**

    Phase 0 (리서치)
        |
        +--> Phase 1 (기반) --> Phase 2 (오디오 수집) --+
        |                                              |
        +--> Phase 1 (기반) --> Phase 3 (전사) ---------+
                                                       |
                             Phase 4 (회의록) ----------+
                                                       |
                                            Phase 5 (통합) --> Phase 6 (UI)

---

## 기술 스택 요약 (아키텍처 관점)

| 영역 | 기술 | 역할 |
|------|------|------|
| DI | Hilt | 컴포넌트 간 의존성 주입 |
| 비동기 | Kotlin Coroutines + Flow | BLE 콜백 래핑, 데이터 스트림 |
| 백그라운드 | WorkManager | 파이프라인 단계 실행, 재시도 |
| BLE | Android BLE API (BluetoothGatt) | Plaud 통신 |
| 파일 감시 | FileObserver / ContentObserver | Plaud 앱 파일 감시 (대안) |
| DB | Room | 회의 메타데이터, 파이프라인 상태 |
| UI | Jetpack Compose | 선언적 UI |
| 네트워크 | Retrofit + OkHttp | Gemini API / MCP 브릿지 호출 |
| 서비스 | Foreground Service | BLE 연결 유지 |

---

## Sources

- [Android BLE Overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview) - HIGH confidence
- [Android App Architecture Guide](https://developer.android.com/topic/architecture) - HIGH confidence
- [Android SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer) - HIGH confidence
- Samsung Galaxy AI Developer API - 확인 불가 (LOW confidence, 공식 문서 접근 실패)
- NotebookLM 공식 API - 미존재 확인 (MEDIUM confidence, MCP 서버가 비공식 경로로 동작)
