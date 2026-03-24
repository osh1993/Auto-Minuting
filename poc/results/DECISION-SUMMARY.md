# PoC 검증 종합 결정 문서

## PoC 검증 총괄 결과

- **검증 일자:** 2026-03-24
- **검증 항목:** 3개 핵심 외부 의존성
  1. 오디오 수집 (Plaud 녹음기 → 스마트폰)
  2. STT 전사 (음성 → 텍스트)
  3. 회의록 생성 (텍스트 → 구조화된 회의록)
- **전체 Go/No-Go 판단:** **Go**
  - 3개 의존성 모두 최소 1개 이상의 실현 가능한 경로 확보
  - 각 의존성별 채택 경로와 폴백 경로가 확정됨

## 의존성별 결과 요약

| 의존성 | Go/No-Go | 채택 경로 | 폴백 경로 | 비고 |
|--------|----------|-----------|-----------|------|
| 오디오 수집 (POC-01) | **Go** | Plaud SDK (v0.2.8, appKey 필요) | Cloud API (비공식, JWT 인증) | FileObserver 경로 폐기 (Scoped Storage) |
| STT 전사 (POC-02/03) | **Go** | Whisper (whisper.cpp, small 모델) | ML Kit GenAI (Basic 모드, 실기기 테스트 필요) | Galaxy AI 서드파티 접근 불가 확인 |
| 회의록 생성 (POC-04) | **Go** | Gemini API 직접 호출 (Firebase AI Logic SDK) | NotebookLM MCP 서버 (PC 의존) | 오디오 직접 입력 대안 파이프라인도 존재 |

## 최종 아키텍처 경로

PoC 검증 결과에 기반하여 확정된 전체 파이프라인의 기술 경로:

### 오디오 수집: Plaud SDK (1차) / Cloud API (폴백)

- **채택:** Plaud SDK v0.2.8 (MIT 라이선스)
  - BLE 스캔/연결, 파일 다운로드(MP3/WAV), 녹음 세션 관리
  - appKey 발급이 전제 조건 (support@plaud.ai 신청)
- **폴백:** Plaud Cloud API (비공식)
  - JWT Bearer 토큰 인증, S3 presigned URL 다운로드
  - SDK appKey 발급 거절/2주 이상 미응답 시 전환
- **폐기:** FileObserver -- Scoped Storage(API 30+)로 타 앱 파일 감시 불가

### STT 전사: Whisper (1차) / ML Kit GenAI (2차) / 클라우드 STT (최후)

- **채택:** Whisper (whisper.cpp, small 모델 ~500MB)
  - 오픈소스(MIT), 파일 입력 지원, 한국어 지원
  - 예상 성능: S24 Ultra 기준 1~1.5x 실시간
  - hallucination 대응: VAD 전처리 + temperature=0.0 + 후처리 필터링
- **2차 폴백:** ML Kit GenAI SpeechRecognizer (Basic 모드)
  - Google 공식 API, 파일 입력 네이티브 지원 (AudioSource.fromFileDescriptor)
  - Samsung 기기 실기기 테스트에서 동작 확인 시 채택 가능
  - 리스크: alpha 단계, API 변경/폐기 가능성
- **최후:** 클라우드 STT (Google Cloud Speech-to-Text, Naver Clova Speech)
  - D-06(온디바이스 우선) 원칙에 따라 온디바이스 경로 모두 실패 시에만 고려
- **폐기:** Galaxy AI 전사 -- 서드파티 접근 공식 경로 없음 (API/SDK/Intent 미제공)

### 회의록 생성: Gemini API (1차) / NotebookLM MCP (폴백)

- **채택:** Gemini API 직접 호출 (Firebase AI Logic SDK 경유)
  - gemini-2.5-flash 모델, 무료 티어 내 운용 가능 (일일 500회)
  - 모바일 독립 실행 가능, 프롬프트 완전 제어
  - 4섹션 구조화 출력: 회의 개요 > 안건 > 결정 사항 > 액션 아이템
- **폴백:** NotebookLM MCP 서버
  - PC 의존성 (모바일 독립 실행 불가)
  - 비공식 경로, Google UI 변경 시 깨질 수 있음
  - 소스 관리/멀티소스 분석 등 고유 기능 존재

### 대안 파이프라인: Gemini API 오디오 직접 입력

- **개념:** STT 단계를 건너뛰고 오디오 파일을 Gemini에 직접 입력하여 회의록 생성
- **지원:** Gemini 2.5 Flash -- 최대 9.5시간 오디오 처리 가능
- **장점:** 파이프라인 단순화 (녹음 -> 회의록, STT 의존성 제거)
- **활용 시점:** Whisper/ML Kit 온디바이스 STT 품질이 낮거나 실패하는 경우
- **검증 예정:** Phase 5에서 STT 경유 경로와 품질 비교 예정

## Phase 2 이후 사전 조건

### 공통

| 항목 | 설명 | 필요 시점 |
|------|------|-----------|
| Samsung Galaxy 기기 | Galaxy AI 지원 모델 (실기기 테스트용) | Phase 2 |
| Android Studio 환경 | Kotlin 2.3.20, AGP 9.1.0, JDK 17 | Phase 2 |

### 오디오 수집 (Phase 3)

| 항목 | 설명 | 상태 |
|------|------|------|
| Plaud SDK appKey | support@plaud.ai에 신청 필요 | 미완료 |
| Plaud 녹음기 | BLE 연결 테스트용 실기기 | 필요 |
| BLE 퍼미션 | BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION | 구현 필요 |
| Plaud SDK AAR | v0.2.8 의존성 추가 | Phase 3에서 추가 |
| Cloud API JWT 토큰 | 브라우저에서 Plaud 웹사이트 로그인 후 추출 (폴백 경로) | 필요 시 |

### STT 전사 (Phase 4)

| 항목 | 설명 | 상태 |
|------|------|------|
| whisper.cpp 빌드 | Android NDK + JNI 빌드 환경 | 구현 필요 |
| Whisper small 모델 | ~500MB, 다운로드/내장 전략 결정 필요 | Phase 4에서 결정 |
| Silero VAD | VAD 전처리 통합 (hallucination 대응) | Phase 4에서 구현 |
| ML Kit GenAI | alpha SDK 의존성 추가 (2차 폴백) | 실기기 테스트 후 |
| 오디오 전처리 | Plaud 녹음 포맷 -> Whisper 입력 포맷(16kHz mono WAV) 변환 | Phase 4에서 구현 |

### 회의록 생성 (Phase 5)

| 항목 | 설명 | 상태 |
|------|------|------|
| Firebase 프로젝트 | Firebase 콘솔에서 프로젝트 생성 + google-services.json | 미완료 |
| Gemini API 키 | Google AI Studio에서 발급 | 미완료 |
| Firebase AI Logic SDK | Android 의존성 추가 | Phase 5에서 추가 |
| 프롬프트 튜닝 | 한국어 회의록 품질 최적화 | Phase 5에서 진행 |

## 리스크 및 완화 방안

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|-----------|
| Plaud SDK appKey 발급 거절/지연 | 높음 | Cloud API 폴백 준비 완료. JWT 토큰 수동 추출 방식으로 즉시 전환 가능 |
| Whisper JNI 빌드 복잡도 | 중간 | whisper.android 예제 앱 존재. 커뮤니티 활발하여 참조 자료 충분 |
| Whisper 한국어 hallucination | 중간 | VAD 전처리 + temperature=0.0 + 후처리 필터링으로 대응. 심각 시 Gemini 오디오 직접 입력 대안 |
| ML Kit GenAI alpha 불안정 | 낮음 | 2차 폴백이므로 Whisper 1차 경로로 우회 가능 |
| NotebookLM MCP 비공식 경로 깨짐 | 낮음 | Gemini API가 1차 경로이므로 MCP 의존도 낮음 |
| Gemini API 무료 티어 할당량 | 낮음 | 일일 500회 제한, 실사용 5회 미만으로 충분. 초과 시 유료 전환 가능 |
| Plaud SDK 초기 버전 불안정 | 중간 | v0.2.8로 초기 버전. Guava 의존성 충돌 가능. Cloud API 폴백 활용 |

## ROADMAP 조정 제안

PoC 검증 결과 원래 ROADMAP의 Phase 순서 및 목표는 유효하다. 다만 다음 사항을 반영할 것을 제안한다:

1. **Phase 2 (앱 기반 구조):** 변경 없음. Clean Architecture 뼈대 구축 진행.

2. **Phase 3 (오디오 수집):** SDK appKey 발급 상태에 따라 구현 경로가 달라짐.
   - appKey 발급 완료 시: Plaud SDK 기반 BLE 연결/다운로드 구현
   - appKey 미발급 시: Cloud API 기반 원격 다운로드 구현 (BLE 없이)

3. **Phase 4 (전사 엔진):** Galaxy AI 대신 Whisper(whisper.cpp) 기반으로 구현.
   - STT 엔진 전환에 따른 추가 작업: JNI 빌드, 모델 다운로드 관리, VAD 통합
   - Out of Scope "자체 STT 엔진 구현"을 "Whisper 등 기존 오픈소스 모델 활용"으로 재해석

4. **Phase 5 (회의록 생성):** NotebookLM 대신 Gemini API 직접 호출로 구현.
   - 오디오 직접 입력 대안 파이프라인 비교 검증 포함

5. **Phase 6-7:** 변경 없음. 파이프라인 통합 및 UI 완성 진행.
