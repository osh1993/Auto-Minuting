# Project Research Summary

**Project:** Auto Minuting
**Domain:** BLE 녹음기 연동 + 온디바이스 AI 전사 + AI 회의록 자동 생성 Android 앱
**Researched:** 2026-03-24
**Confidence:** MEDIUM

## Executive Summary

Auto Minuting은 Plaud 하드웨어 녹음기에서 오디오 파일을 자동으로 수집하고, 삼성 Galaxy AI 온디바이스 전사를 거쳐 NotebookLM 또는 Gemini API로 회의록을 생성하는 자동화 파이프라인 Android 앱이다. 이 앱의 본질은 **데이터 파이프라인**이며, 각 단계(오디오 수집 -> 전사 -> 회의록 생성)가 독립적으로 실패하고 재시도할 수 있는 구조로 설계해야 한다. Clean Architecture + WorkManager 기반의 상태 머신 패턴이 이 도메인에 가장 적합하다. 스택은 Kotlin 2.3.20 + Jetpack Compose BOM 2026.03.00 + Hilt 1.3.0 + Room 2.8.4 + WorkManager 2.11.1 조합으로 확정이며 모두 높은 신뢰도다.

프로젝트의 가장 큰 특징은 **3개의 핵심 외부 의존성 모두에 공식 API가 없거나 불확실하다**는 점이다: Plaud BLE 프로토콜(역공학 필요), Samsung Galaxy AI 전사(공식 SDK 미확인), NotebookLM(공식 REST API 없음). 이는 일반 앱 개발과 근본적으로 다른 리스크 프로파일이며, 빌드를 시작하기 전에 이 3가지 기술 가능성 검증이 필수다. 각각에 대해 실용적인 대안(파일 시스템 감시, Whisper STT, Gemini API 직접 호출)이 존재하므로 최악의 시나리오에서도 앱의 핵심 기능은 구현 가능하다.

권장 접근법은 Phase 0을 리서치/PoC 단계로 별도 분리하여 3개 질문에 답한 뒤 아키텍처를 확정하고 구현을 시작하는 것이다. 불확실한 부분(STT 엔진, 회의록 생성 경로)은 Repository 패턴으로 추상화하여 구현체를 교체 가능하게 설계해야 한다. Galaxy AI가 불가능하면 Whisper 온디바이스로, NotebookLM이 불안정하면 Gemini API로 즉시 전환할 수 있는 구조가 핵심이다.

## Key Findings

### 추천 스택

현대적인 Android 앱 개발 표준 스택이 이 프로젝트에 완전히 적합하다. 불확실한 외부 의존성(BLE 프로토콜, Galaxy AI, NotebookLM)을 제외한 모든 기술 선택은 높은 신뢰도로 확정 가능하다. KSP 기반 annotation processing이 KAPT 대비 빌드 성능을 2배 이상 향상시키므로 반드시 채택해야 한다.

**핵심 기술:**

- **Kotlin 2.3.20 + Coroutines/Flow**: 비동기 BLE 콜백 및 파이프라인 상태 스트림 처리 — Android 공식 언어, 비동기 처리 최적
- **Jetpack Compose BOM 2026.03.00 + Material 3**: 선언적 UI 구성 — 신규 프로젝트 표준, XML 대비 생산성 우위
- **Hilt 1.3.0**: 컴포넌트 간 의존성 주입 — 컴파일 타임 검증, Jetpack 완벽 통합
- **Room 2.8.4**: 회의 메타데이터 및 파이프라인 상태 영속화 — Coroutines/Flow 지원, SQL 컴파일 타임 검증
- **WorkManager 2.11.1**: 전사/회의록 생성 배치 작업 — 시스템 재시작 후에도 재개 가능, 지수 백오프 재시도
- **Android BLE API (Native BluetoothGatt)**: Plaud 기기 GATT 통신 — 역공학에는 저수준 API 직접 접근 필수
- **Foreground Service**: BLE 연결 유지 — Android 14+ connectedDevice foregroundServiceType 필수
- **Min SDK API 31 (Android 12)**: createOnDeviceSpeechRecognizer API 최소 요구사항

**Phase 0에서 결정되는 불확실 스택:**

- STT 엔진: Galaxy AI(최선) > Google ML Kit > Whisper 온디바이스(폴백) 순서로 탐색
- 회의록 생성: Gemini API 직접 호출(MVP 권장, 공식 API) > NotebookLM MCP 브릿지 순서

### 기능 목록

이 앱은 자동화 파이프라인이 핵심이며, 기능 복잡도보다 파이프라인 안정성이 우선이다. MVP에서는 기능을 최소화하고 엔드-투-엔드 파이프라인 완성에 집중해야 한다.

**반드시 있어야 하는 기능 (table stakes):**

- Plaud 오디오 파일 수집 — 앱의 존재 이유. 최고 난이도 리스크
- 한국어 음성-텍스트 전사 — 핵심 파이프라인 단계. Galaxy AI 접근 방식 미확정
- AI 회의록 생성 — 핵심 출력물. NotebookLM/Gemini 연동
- 로컬 저장 및 뷰어 — 프라이버시 및 오프라인 접근
- 백그라운드 처리 + 진행 알림 — 사용자 기대치 (설정하고 잊기)

**있으면 좋은 기능 (차별화):**

- 원클릭 완전 자동화 파이프라인 — 제품의 핵심 가치 정의
- 자동화 레벨 설정 (완전 자동 vs 하이브리드) — 사용자 컨트롤
- 회의록 형식 선택 — 프롬프트 엔지니어링으로 구현

**v2+로 미루기:**

- 히스토리 아카이브 + 검색 — 파이프라인 안정화 후 구현
- 전사 텍스트 편집기 — 기본 흐름 완성 후 UX 개선
- 외부 앱 내보내기 — 낮은 우선순위, Android share intent로 간단 구현 가능

### 아키텍처 접근법

Clean Architecture 기반의 파이프라인 상태 머신이 이 도메인의 핵심 패턴이다. 6개 컴포넌트가 명확히 분리되며, 각 컴포넌트는 Repository 인터페이스 뒤에 숨어 구현체 교체가 가능해야 한다. WorkManager를 통한 파이프라인 체이닝이 Android 시스템 제약을 우회하면서 신뢰성 있는 백그라운드 처리를 가능하게 한다. 파이프라인 상태(AUDIO_RECEIVED -> TRANSCRIBING -> TRANSCRIBED -> GENERATING_MINUTES -> COMPLETED/FAILED)는 반드시 Room DB에 영속화해야 한다.

**주요 컴포넌트:**

1. **BleManager** — Plaud 기기 스캔/연결/GATT 통신/파일 수신. Foreground Service 내에서 동작. BLE vs FileObserver 구현체 교체 가능
2. **FileStorage** — Room DB(메타데이터, 파이프라인 상태) + 내부 저장소(오디오/텍스트/회의록 파일). 모든 파일 I/O 단일 진입점
3. **TranscriptionEngine** — Galaxy AI/SpeechRecognizer/Whisper 추상화 인터페이스. 구현체 교체 가능 설계
4. **NotebookLmClient** — Gemini API/NotebookLM MCP 추상화 인터페이스. 구현체 교체 가능 설계
5. **PipelineOrchestrator** — WorkManager 기반 단계 체이닝. 상태 머신 관리. 단계별 독립 재시도
6. **UI Layer (Compose + ViewModel)** — StateFlow 기반 단방향 데이터 흐름. 대시보드/목록/상세/설정 4개 화면

### 핵심 함정

1. **Plaud BLE 프로토콜 암호화** — Phase 0에서 nRF Connect로 즉시 검증. 2주 타임박스 후 파일 시스템 감시(Plan B)로 전환. BLE가 아닌 Wi-Fi Direct 사용 가능성도 확인 필요
2. **Samsung Galaxy AI 전사 API 미존재** — Samsung Developer 포털 정밀 조사 필수. 불가 시 Google ML Kit 또는 Whisper 온디바이스 폴백으로 즉시 전환
3. **NotebookLM MCP 불안정성** — Phase 1 설계 시 회의록 생성 레이어 추상화 필수. Gemini API 직접 호출을 1차 MVP 경로로 채택 권장
4. **BLE 대용량 파일 전송 복잡도** — BLE MTU 한계(기본 20바이트)로 대용량 오디오 역공학이 예상보다 훨씬 어려울 수 있음. 파일 시스템 감시 대안이 훨씬 단순하고 안정적
5. **Android Scoped Storage 파일 접근 제한** — Plaud 앱이 저장하는 위치(내부 저장소/앱별 외부/공유 저장소) 정확히 확인. MediaStore API 또는 SAF 활용 가능성 탐색

## 로드맵에 대한 시사점

ARCHITECTURE.md가 제안한 7단계 빌드 순서는 연구 결과와 일치하며 권장한다. 핵심은 **Phase 0을 독립적인 기술 검증 단계로 분리**하는 것이다.

### Phase 0: 기술 가능성 검증 (PoC)

**근거:** 3개 외부 의존성 모두 공식 API 미확정. 이 단계 없이 아키텍처를 확정할 수 없음

**산출물:** 3개 질문에 대한 명확한 답 + 아키텍처 결정 문서

- Q1: Plaud 파일을 가져올 수 있는가? (BLE 직접 / 파일 시스템 감시 / APK 분석)
- Q2: 프로그래밍적으로 STT를 수행할 수 있는가? (Galaxy AI / ML Kit / Whisper)
- Q3: 프로그래밍적으로 회의록을 생성할 수 있는가? (NotebookLM MCP / Gemini API)

**예방 함정:** C1(BLE 암호화), C2(Galaxy AI API 미존재), C3(NotebookLM 불안정), C4(대용량 전송 복잡도)

### Phase 1: 기반 구조

**근거:** 가장 확실한 기술 스택부터. Phase 0 결과와 무관하게 필요한 인프라

**산출물:** 동작하는 앱 뼈대 — Room DB, Hilt DI, Compose Navigation, 빈 화면 4개

**구현:** Room 스키마(MeetingEntity + PipelineStatus 상태 머신), FileStorage, 기본 UI 쉘

**스택:** Kotlin 2.3.20, Compose BOM 2026.03.00, Hilt 1.3.0, Room 2.8.4, DataStore 1.2.1

### Phase 2: 오디오 수집

**근거:** Phase 0 결과에 따라 구현 경로 결정. FileStorage 의존

**산출물:** Plaud 오디오 파일이 앱 내부 저장소에 자동 저장됨

**구현:** BleManager (Foreground Service) 또는 FileObserver 기반 감시. 권한 처리 필수

**예방 함정:** M2(Scoped Storage), M5(백그라운드 제한), L1(오디오 포맷 호환성)

### Phase 3: 전사 엔진

**근거:** Phase 0 STT 검증 결과 + Phase 1 FileStorage 의존

**산출물:** 오디오 파일이 텍스트로 전사되어 로컬 저장됨

**구현:** TranscriptionEngine 인터페이스 + Galaxy AI/SpeechRecognizer/Whisper 구현체. 인터페이스 추상화로 폴백 전환 용이하게

**예방 함정:** C2(Galaxy AI API 미존재), M3(한국어 품질 기대치)

### Phase 4: 회의록 생성

**근거:** Phase 3 전사 결과물 의존

**산출물:** 전사 텍스트가 구조화된 회의록으로 변환되어 저장됨

**구현:** NotebookLmClient 인터페이스 + Gemini API 구현체(MVP). 프롬프트 템플릿 설계. few-shot 예제 포함

**예방 함정:** C3(NotebookLM 불안정), M4(인증 관리 복잡성), L2(포맷 일관성), L3(컨텍스트 윈도우 초과)

### Phase 5: 파이프라인 통합 및 자동화

**근거:** Phase 2, 3, 4 모두 완성 후 통합

**산출물:** 완전 자동화 엔드-투-엔드 파이프라인 동작. 원클릭 자동화 실현

**구현:** PipelineOrchestrator + WorkManager 체이닝. 완전 자동/하이브리드 모드. 지수 백오프 재시도. 상태 영속화

### Phase 6: UI 완성 및 폴리싱

**근거:** 파이프라인 완성 후 UX 다듬기

**산출물:** 대시보드, 회의록 목록/상세, 설정 화면 완성. 알림 연동

**구현:** 파이프라인 실시간 상태 표시, 회의록 형식 선택, 자동화 레벨 설정

### 페이즈 순서 근거

- Phase 0 선행 필수: 3개 외부 의존성 기술 가능성이 전체 아키텍처를 결정
- Phase 1은 Phase 0과 일부 병렬 진행 가능: 기반 인프라는 결과와 무관
- Phase 2-4 순차: 데이터 파이프라인의 각 단계가 앞 단계 출력에 의존
- Phase 5 통합은 컴포넌트가 모두 동작한 후에야 의미 있음
- Phase 6 최후: 비즈니스 로직 완성 후 UX 개선이 바람직

### 리서치 플래그

더 깊은 조사가 필요한 페이즈:

- **Phase 0 (PoC):** 전체가 깊은 리서치. nRF Connect BLE 분석, Samsung One UI SDK 정밀 조사, Plaud APK jadx 디컴파일, Gemini API + NotebookLM MCP 실제 동작 검증
- **Phase 2 (오디오 수집):** Plaud 파일 저장 경로 및 포맷이 Phase 0 결과에 따라 달라짐
- **Phase 3 (전사):** Galaxy AI 접근 방식 불확실하면 추가 리서치 필요

표준 패턴으로 리서치 불필요한 페이즈:

- **Phase 1 (기반 구조):** Kotlin + Compose + Hilt + Room은 잘 문서화된 표준 스택
- **Phase 4 (회의록, Gemini API 경로):** Gemini API는 공식 문서화. 프롬프트 설계는 반복 개선
- **Phase 6 (UI):** Jetpack Compose UI 패턴은 표준화됨

## 신뢰도 평가

| 영역 | 신뢰도 | 비고 |
|------|--------|------|
| 스택 | HIGH | Kotlin/Compose/Hilt/Room/WorkManager 모두 공식 문서에서 검증됨 |
| 기능 | HIGH | PROJECT.md 요구사항 기반. 기능 범위와 우선순위 명확 |
| 아키텍처 | HIGH | Clean Architecture + WorkManager 파이프라인은 잘 검증된 패턴 |
| 함정 | MEDIUM | BLE/Samsung/NotebookLM 함정 분석 일부 훈련 데이터 기반. PoC로 검증 필수 |

**전체 신뢰도:** MEDIUM

스택과 아키텍처는 확실하지만, 프로젝트 핵심 가치를 전달하는 3개 외부 연동이 모두 불확실하기 때문이다.

### 해결해야 할 공백

- **Plaud BLE 프로토콜 실제 구조**: Phase 0 PoC에서 nRF Connect로 직접 확인. 결과에 따라 BleManager vs FileObserver 구현 경로 결정
- **Samsung Galaxy AI 전사 API 존재 여부**: Phase 0에서 Samsung Developer 포털 정밀 조사 + 실기기 테스트. 불가 시 즉시 대안 STT로 전환
- **NotebookLM MCP 서버 안정성**: Phase 0에서 현재 MCP 서버 실제 동작 테스트. MVP는 Gemini API 직접 호출로 확정 권장
- **Plaud 오디오 포맷**: Phase 0에서 파일 분석. 독자 포맷이면 FFmpeg 변환 레이어 필요
- **Google Play Store 배포 가능성**: Scoped Storage 제한, MANAGE_EXTERNAL_STORAGE 정책, Accessibility Service 위반 가능성으로 인해 사이드로딩 방식이 현실적

## 출처

### 1차 (HIGH 신뢰도)

- [Android Jetpack Releases](https://developer.android.com/jetpack/androidx/releases) — Jetpack 전체 버전
- [Compose BOM Mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — Compose BOM 버전 매핑
- [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin) — AGP 버전
- [Android BLE Overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview) — Android BLE API
- [Android App Architecture](https://developer.android.com/topic/architecture) — Android 앱 아키텍처 가이드
- [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer) — SpeechRecognizer API
- [Kotlin Releases](https://kotlinlang.org/docs/releases.html) — Kotlin 릴리스

### 2차 (MEDIUM 신뢰도)

- PROJECT.md 요구사항 분석 — 기능 범위 및 목표
- NotebookLM MCP 서버 (프로젝트 내 설정 확인) — NotebookLM 연동 방식
- Android Scoped Storage 정책 (훈련 데이터) — 파일 접근 제한 분석

### 3차 (LOW 신뢰도 — 반드시 검증 필요)

- Samsung Galaxy AI 개발자 API — 공식 문서 접근 실패. PoC로 실기기 검증 필수
- Plaud BLE 프로토콜 구조 — 공개 문서 없음. 역공학으로 확인 필요

---

*리서치 완료: 2026-03-24*
*로드맵 준비: yes*
