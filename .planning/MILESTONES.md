# Milestones

## v6.0 멀티 엔진 확장 (In Progress: 2026-03-31)

**Phases:** 39-42 (4 phases, 7 plans)

**Goal:** STT 및 회의록 생성 엔진을 확장하여 Groq Whisper / Deepgram Nova-3 / Naver CLOVA를 설정에서 선택 가능하게 하고, 릴리스 번호가 포함된 APK를 생성한다.

---

## v1.0 Auto Minuting MVP (Shipped: 2026-03-24)

**Phases completed:** 7 phases, 18 plans, 39 tasks

**Key accomplishments:**

- Plaud SDK(v0.2.8) 1차 채택 + Cloud API 폴백 결정, FileObserver Scoped Storage 불가 확정, Go 판정
- Galaxy AI 서드파티 접근 불가 확인, Whisper(small) 1차 / ML Kit GenAI 2차 온디바이스 STT 채택 경로 결정
- Gemini API 직접 호출을 1차 채택 경로로 결정하고, NotebookLM MCP를 폴백으로 설정한 회의록 생성 PoC 검증
- 3개 의존성 전체 Go 판정, 최종 파이프라인 Plaud SDK > Whisper > Gemini API 확정, PROJECT.md Key Decisions 5건 추가
- Kotlin 2.3.20 + Compose BOM 2026.03 + Hilt 2.56 기반 Android 프로젝트 뼈대 구성 및 Clean Architecture 3레이어 패키지 구조 생성
- Room DB(MeetingEntity/DAO) + PipelineStatus 상태 머신 + 4개 Repository 인터페이스 정의 및 MeetingRepository DI 바인딩 완성
- Material 3 Dynamic Color 테마가 적용된 4개 빈 화면을 Bottom Navigation으로 연결하고, WorkManager TestWorker로 백그라운드 작업 인프라를 검증
- Plaud SDK BLE + Cloud API 이중 경로 AudioRepository 구현체 및 Retrofit/OkHttp/Guava 의존성 구성
- connectedDevice Foreground Service로 백그라운드 BLE 오디오 수집, WorkManager로 전사 파이프라인 자동 트리거, Application에서 Plaud SDK 조건부 초기화
- 1. [Rule 3 - Blocking] SttModule @Provides 중복 바인딩 방지
- 전사 완료 회의 목록 Card UI + 전사 텍스트 전체 화면 편집/파일 저장 기능 + Navigation 연동

---
