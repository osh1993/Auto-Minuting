# Requirements: Auto Minuting

**Defined:** 2026-03-24
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### 기술 검증 (PoC)

- [ ] **POC-01**: Plaud 앱 APK 디컴파일 및 BLE 프로토콜/파일 저장 경로 분석 완료
- [ ] **POC-02**: Galaxy AI 전사 기능의 서드파티 앱 접근 방법 확인 (API/Intent/Accessibility)
- [ ] **POC-03**: Galaxy AI 불가 시 대안 STT 경로 확인 (Whisper 온디바이스 / Google STT)
- [ ] **POC-04**: NotebookLM Android 연동 방식 확인 (MCP 릴레이 / Gemini API 직접 호출)

### 오디오 수집

- [ ] **AUD-01**: Plaud 녹음기에서 전송되는 음성 파일을 감지하여 로컬 저장소에 저장
- [ ] **AUD-02**: 음성 파일 저장이 Foreground Service로 백그라운드에서 자동 처리
- [ ] **AUD-03**: 새로운 오디오 파일 감지 시 파이프라인이 자동으로 시작

### 전사 (STT)

- [ ] **STT-01**: 저장된 한국어 음성 파일을 Galaxy AI(또는 확인된 대안)로 텍스트 전사
- [ ] **STT-02**: 전사 완료된 텍스트를 로컬에 저장
- [ ] **STT-03**: 사용자가 전사된 텍스트를 편집할 수 있음 (STT 오류 수정)

### 회의록 생성

- [ ] **MIN-01**: 전사된 텍스트를 NotebookLM(또는 확인된 대안)에 소스로 등록
- [ ] **MIN-02**: 지정된 노트 또는 새 노트에 소스 등록 선택 가능
- [ ] **MIN-03**: 프롬프팅으로 회의록 자동 생성
- [ ] **MIN-04**: 생성된 회의록을 스마트폰 로컬에 저장
- [ ] **MIN-05**: 회의록 형식 선택 가능 (구조화된 회의록 / 요약 / 커스텀 템플릿)
- [ ] **MIN-06**: 생성된 회의록을 외부 앱으로 공유 (Android Share Intent)

### UI/UX

- [ ] **UI-01**: 회의록 뷰어 — 생성된 회의록을 읽기 쉽게 표시
- [ ] **UI-02**: 파이프라인 진행 상태 알림 (각 단계별 진행률)
- [ ] **UI-03**: 과거 회의록 아카이브 — 검색 및 브라우징
- [ ] **UI-04**: 자동화 수준 설정 — 완전 자동 / 하이브리드 모드 선택

### 앱 기반

- [ ] **APP-01**: Android 네이티브 앱 (Kotlin + Jetpack Compose)
- [ ] **APP-02**: Clean Architecture (Domain/Data/Presentation 레이어 분리)
- [ ] **APP-03**: Room DB를 이용한 로컬 데이터 관리 (녹음/전사/회의록)
- [ ] **APP-04**: WorkManager를 이용한 파이프라인 단계 체이닝

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### 고급 기능

- **ADV-01**: 화자 분리 (Speaker Diarization) — 누가 말했는지 구분
- **ADV-02**: 다국어 전사 지원 (영어, 일본어 등)
- **ADV-03**: 회의록 템플릿 커스터마이징 (사용자 정의 프롬프트)
- **ADV-04**: 회의 통계 대시보드 (발언 비율, 주제 분포 등)

### 연동 확장

- **EXT-01**: 캘린더 연동 — 회의 일정과 회의록 자동 매칭
- **EXT-02**: 클라우드 백업 (Google Drive 등)
- **EXT-03**: 다른 녹음기 기기 지원

## Out of Scope

| Feature | Reason |
|---------|--------|
| iOS 지원 | Galaxy AI 온보드 기능에 의존, Android 전용 |
| 자체 STT 엔진 개발 | 대규모 엔지니어링, Galaxy AI가 한국어에 최적화 |
| 실시간 스트리밍 전사 | 복잡도 극대화, 녹음 완료 후 배치 처리 |
| Plaud 클라우드 연동 | 네트워크 의존성 추가, 프라이버시 우려 |
| 다중 기기 동기화 | 클라우드 인프라 복잡도, v1에서 불필요 |
| 화상회의 봇 (Zoom/Teams 등) | 별도 플랫폼 통합 필요, 스코프 초과 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| POC-01 | Phase ? | Pending |
| POC-02 | Phase ? | Pending |
| POC-03 | Phase ? | Pending |
| POC-04 | Phase ? | Pending |
| AUD-01 | Phase ? | Pending |
| AUD-02 | Phase ? | Pending |
| AUD-03 | Phase ? | Pending |
| STT-01 | Phase ? | Pending |
| STT-02 | Phase ? | Pending |
| STT-03 | Phase ? | Pending |
| MIN-01 | Phase ? | Pending |
| MIN-02 | Phase ? | Pending |
| MIN-03 | Phase ? | Pending |
| MIN-04 | Phase ? | Pending |
| MIN-05 | Phase ? | Pending |
| MIN-06 | Phase ? | Pending |
| UI-01 | Phase ? | Pending |
| UI-02 | Phase ? | Pending |
| UI-03 | Phase ? | Pending |
| UI-04 | Phase ? | Pending |
| APP-01 | Phase ? | Pending |
| APP-02 | Phase ? | Pending |
| APP-03 | Phase ? | Pending |
| APP-04 | Phase ? | Pending |

**Coverage:**
- v1 requirements: 24 total
- Mapped to phases: 0
- Unmapped: 24 ⚠️

---
*Requirements defined: 2026-03-24*
*Last updated: 2026-03-24 after initial definition*
