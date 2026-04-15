# Milestones

## v9.0 Groq 대용량 파일 분할 및 다중 키 관리 (Shipped: 2026-04-15)

**Phases completed:** 53 phases, 87 plans, 143 tasks

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
- Conditional No-Go (SDK 의존 유지 권장)
- Commit:
- One-liner:
- 전사 카드에 MoreVert 드롭다운 메뉴(재전사/공유/삭제) 추가 및 ViewModel 재전사/공유 함수 구현
- 전사 카드에 파일 종류 아이콘(AudioFile/TextSnippet) + 전사 상태 배지 + 회의록 상태 배지를 추가하여 한눈에 상태 파악 가능
- 공유 음성 파일의 원본 파일명 자동 추출 및 전사 카드 이름 편집 다이얼로그 구현
- Room DB v3->v4 마이그레이션으로 minutesTitle 컬럼 추가, MinutesGenerationWorker에서 Gemini 응답 첫 줄을 자동 제목으로 추출/저장
- MinutesScreen에 MoreVert 드롭다운(공유/삭제), minutesTitle 우선 표시, 이름 편집 다이얼로그 추가
- 대시보드에 URL 입력 카드를 추가하여 OkHttp로 음성 파일을 다운로드하고 TranscriptionTriggerWorker를 통해 전사 파이프라인에 자동 진입시키는 기능 구현
- 설정 화면 3개 섹션(회의록/전사/인증) 그룹화 + 대시보드 테스트 도구(더미 삽입, Gemini 테스트) 완전 제거
- 하이브리드 모드 전사 완료 시 대시보드 확인/무시 배너 추가 + 전사 카드 회의록 버튼을 MoreVert 메뉴로 이동하여 카드 높이 절약
- 기본 템플릿 DataStore 설정 + ManualMinutesDialog 연동으로 회의록 생성 시 템플릿 선택/자동 적용 구현
- DataStore 기반 Gemini Free RPD 1500 일일 쿼터 추적 + 대시보드 사용량 카드 + 90% 초과 경고 배너
- ShareReceiverActivity에서 web.plaud.ai 링크 감지 -> WebView S3 인터셉트 -> OkHttp 다운로드 -> 전사 파이프라인 자동 진입
- 4개 화면에 Scaffold+TopAppBar 통일, 모든 아이콘 contentDescription 접근성 설정, 빈 상태 아이콘 추가, 날짜 포맷 yyyy.MM.dd HH:mm 통일
- Commit:
- Commit:
- One-liner:
- MinutesEntity/MinutesDao/Minutes 도메인 모델 신설 + Room DB v4->v5 마이그레이션으로 기존 minutesPath/minutesTitle 데이터 이관 및 컬럼 제거
- MinutesDataRepository CRUD 신설, MeetingRepository에서 minutes 메서드 4개 제거, Worker의 Minutes 테이블 INSERT 전환, regenerateMinutes() 워크어라운드 제거
- MinutesViewModel/MinutesDetailViewModel을 MinutesDataRepository 기반으로 전환, Screen/Navigation을 minutesId 기반으로 교체, assembleDebug BUILD SUCCESSFUL
- 재생성 다이얼로그 텍스트를 실제 동작(추가 생성, 기존 유지)에 맞게 수정하고 assembleDebug 빌드 성공 확인
- 1. [Rule 3 - Blocking] MinutesScreen 컴파일 호환성 유지
- 회의록 카드에 출처 전사명 표시 + 전사 카드에 실제 회의록 수 badge + 출처 전사 탭 네비게이션 연결
- 버그 1 & 2 (치명)
- 한 줄 요약:
- 한 줄 요약:
- 한 줄 요약:
- 한 줄 요약:
- Commit:
- One-liner:
- One-liner:
- One-liner:
- 1. [Rule 1 - Bug] classifyAudioFormat getDisplayName() 확장자 누락 버그
- One-liner:
- GroqSttEngine.transcribe()의 25MB 실패 경로를 AudioChunker 기반 자동 분할 → 순차 전사 → joinToString 이어붙이기 경로로 교체 (GROQ-01/02/03 완성)

---

## v6.0 멀티 엔진 확장 (Shipped: 2026-04-03)

**Phases:** 39-42 (4 phases, 7 plans)

**Goal:** STT 및 회의록 생성 엔진을 확장하여 Groq Whisper / Deepgram Nova-3 / Naver CLOVA를 설정에서 선택 가능하게 하고, 릴리스 번호가 포함된 APK를 생성한다.

**Key accomplishments:**

- GroqSttEngine, DeepgramSttEngine, NaverClovaSttEngine 구현 (SttEngineType enum 확장)
- DeepgramMinutesEngine, NaverClovaMinutesEngine 구현 (MinutesEngineType enum 신설)
- 설정 UI에서 STT 5종 / 회의록 엔진 3종 선택 + Groq/Deepgram/Naver API 키 암호화 저장
- build.gradle.kts archivesName 설정으로 AutoMinuting-v6.0-release.apk 자동 생성

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
- connectedDevice Foreground Service로 백그라운드 BLE 오디오 수신, WorkManager로 전사 파이프라인 자동 트리거
- 전사 완료 회의 목록 Card UI + 전사 텍스트 전체 화면 편집/파일 저장 기능 + Navigation 연동

---
