# STT 전사 경로 검증

## 목적

한국어 음성을 텍스트로 전사할 수 있는 STT 경로를 조사하고 평가한다.
온디바이스 처리를 우선시하여(D-06) 프라이버시를 보장하면서도 한국어 정확도가 충분한 경로를 찾는 것이 목표이다.

## 검증 순서

1. **Galaxy AI 서드파티 접근 조사** (`galaxy-ai-investigation.md`)
   - SpeechRecognizer 온디바이스 경로
   - Samsung 녹음앱/노트앱 Intent 경로
   - Samsung SDK/API 경로
   - 목적: Galaxy AI 전사 기능을 서드파티 앱에서 접근할 수 있는지 확인

2. **ML Kit GenAI SpeechRecognizer 평가** (`mlkit-evaluation.md`)
   - Google 공식 온디바이스 STT API (alpha)
   - 한국어 지원 여부, 파일 입력 지원, Samsung 기기 호환성 분석
   - 목적: Galaxy AI 대안으로서의 가능성 평가

3. **Whisper 온디바이스 평가** (`whisper-evaluation.md`)
   - whisper.cpp 기반 Android 온디바이스 전사
   - 모델 크기별 성능, 한국어 성능 예상, hallucination 대응
   - 목적: 가장 확실한 폴백 경로로서의 평가

## 판정 기준

| STT 경로 | 성공 기준 | 실패 기준 |
|----------|----------|----------|
| Galaxy AI | 서드파티 앱에서 프로그래밍적 호출 가능 | API/Intent/SDK 미존재 확인 |
| ML Kit GenAI | 한국어 오디오 파일 전사 가능, 내용 이해 가능 수준 | 한국어 모델 다운로드 불가 또는 Samsung 기기 미지원 |
| Whisper | 1시간 회의 녹음을 10분 이내 전사, 내용 70%+ 이해 가능 | OOM 또는 30분+ 소요 |

## 결과 문서

- `poc/results/POC-02-galaxy-ai.md` — Galaxy AI 조사 결과 종합
- `poc/results/POC-03-stt-fallback.md` — 대안 STT 경로 검증 결과 + 채택 결정
