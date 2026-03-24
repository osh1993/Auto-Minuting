# POC-03: 대안 STT 경로 검증 결과

**작성일:** 2026-03-24
**상태:** 완료
**상세 평가:** [ML Kit](../stt-test/mlkit-evaluation.md) | [Whisper](../stt-test/whisper-evaluation.md)

## 검증 요약

Galaxy AI 서드파티 접근 불가(POC-02)에 따라, 한국어 온디바이스 STT 대안으로 Whisper(whisper.cpp)를 1차 채택 경로로, ML Kit GenAI를 2차 경로로 결정한다.

## 경로별 결과

### 1. ML Kit GenAI SpeechRecognizer

| 항목 | 결과 |
|------|------|
| 판정 | NEEDS_DEVICE_TEST |
| 한국어 | ko-KR 지원 (Basic 모드) |
| 파일 입력 | 지원 (AudioSource.fromFileDescriptor) |
| Samsung 호환 | Basic 모드만 가능성 있음, Advanced 모드는 Pixel 10 전용 |
| 리스크 | alpha 단계, Samsung 기기 동작 미검증, 모델 크기 미공개 |

**평가:** Google 공식 API이지만 alpha 단계 + Samsung 호환 미검증이라는 이중 불확실성이 있다.
실기기 테스트 전까지 확실한 경로로 채택하기 어렵다.

### 2. Whisper (whisper.cpp)

| 항목 | 결과 |
|------|------|
| 판정 | VIABLE |
| 한국어 | 지원 (multi-language 모델) |
| 파일 입력 | 지원 (직접 오디오 파일 입력) |
| 권장 모델 | small (~500MB) |
| 예상 성능 | S24 Ultra 기준 1~1.5x 실시간 |
| 리스크 | JNI 빌드 복잡도, 한국어 hallucination (VAD 완화 가능) |

**평가:** 오픈소스(MIT), 활발한 커뮤니티, Android 예제 앱 존재.
온디바이스 처리가 확실하며, 가장 검증된 경로이다.

### 3. 클라우드 STT (Google Cloud Speech-to-Text 등)

| 항목 | 결과 |
|------|------|
| 판정 | 최후의 수단 |
| 한국어 | 지원 |
| 정확도 | 높음 (서버 모델) |
| 제한 | 네트워크 필수, 프라이버시 우려, 비용 발생 |

**평가:** D-06(온디바이스 우선) 원칙에 따라 최후의 수단으로만 고려한다 (per D-08).
온디바이스 경로가 모두 실패한 경우에만 클라우드 STT를 채택한다.

## Go/No-Go

### **Go** — 최소 1개 대안 경로(Whisper)가 기술적으로 VIABLE

- Whisper(whisper.cpp)가 VIABLE 판정으로 기술적 실현 가능성이 확인되었다.
- ML Kit GenAI도 실기기 테스트 후 추가 채택 가능.
- 온디바이스 STT가 기술적으로 가능함이 확인되어, 프로젝트 진행에 차단 요소가 없다.

## 채택 경로

D-06(온디바이스 우선) 원칙에 따른 STT 경로 우선순위:

### 1차: Whisper (whisper.cpp, small 모델)

- **근거:** VIABLE 판정. 오픈소스, 파일 입력 지원, 한국어 지원, Android 빌드 확인.
- **모델:** small (~500MB) — 한국어 정확도와 모바일 성능의 균형점.
- **hallucination 대응:** VAD 전처리 + temperature=0.0 + 후처리 필터링.
- **온디바이스 처리로 프라이버시 보장** (D-06 부합).

### 2차 (폴백): ML Kit GenAI SpeechRecognizer (Basic 모드)

- **근거:** Google 공식 API, 파일 입력 네이티브 지원, 한국어 지원.
- **조건:** Samsung 기기 실기기 테스트에서 Basic 모드 동작 확인 시.
- **리스크:** alpha 단계이므로 API 변경/폐기 가능성.

### 최후: 클라우드 STT (per D-08)

- **근거:** 온디바이스 경로 모두 실패 시에만 고려.
- **후보:** Google Cloud Speech-to-Text, Naver Clova Speech.
- **제약:** 네트워크 필수, 비용 발생, 프라이버시 우려.

## 실기기 테스트 항목

### ML Kit GenAI (Samsung Galaxy 기기)

1. AICore 앱 설치 및 초기화 확인
2. `SpeechRecognition.getClient()` 호출 성공 여부
3. `checkStatus()` 호출 시 모델 가용 상태 확인
4. Basic 모드에서 한국어 오디오 파일 전사 시도
5. 전사 품질 및 소요 시간 측정

### Whisper (Samsung Galaxy 기기)

1. whisper.android 예제 앱 빌드 및 설치
2. small 모델 로딩 (메모리 사용량 확인)
3. 한국어 회의 녹음 1분 샘플 전사
4. 전사 품질 평가 (내용 이해 가능 여부)
5. 전사 속도 측정 (실시간 대비)
6. VAD 적용 전후 hallucination 비교

## 다음 단계

Phase 4(STT 파이프라인 구현) 진행을 위한 사전 조건:

1. **Whisper 실기기 PoC**: small 모델로 한국어 회의 녹음 전사 품질 확인
2. **ML Kit 실기기 테스트**: Samsung Galaxy에서 Basic 모드 동작 확인
3. **모델 배포 전략 결정**: APK 내장 vs 런타임 다운로드
4. **VAD 파이프라인 설계**: Silero VAD + Whisper 통합 아키텍처
5. **오디오 전처리**: Plaud 녹음 포맷 → Whisper 입력 포맷 변환 확인
