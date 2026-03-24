# POC-02: Galaxy AI 서드파티 접근 조사 결과

**작성일:** 2026-03-24
**상태:** 완료
**상세 조사:** [poc/stt-test/galaxy-ai-investigation.md](../stt-test/galaxy-ai-investigation.md)

## 검증 요약

Galaxy AI 전사 기능(Transcript Assist)은 Samsung 자체 앱 내부에서만 동작하며, 서드파티 앱에서 프로그래밍적으로 접근할 수 있는 공식 경로가 존재하지 않는다.

## 조사한 경로

### a) SpeechRecognizer 온디바이스 경로

| 항목 | 결과 |
|------|------|
| 판정 | REQUIRES_DEVICE_TEST |
| API | `createOnDeviceSpeechRecognizer()` (API 31+) |
| 문제점 | 파일 입력 미지원 (스트리밍 전용), Galaxy AI 엔진 사용 여부 미확인 |
| 근본 한계 | 녹음 파일 전사 용도로 부적합 (파일 → 마이크 루프백은 비현실적) |

- Samsung 기기에서 SpeechRecognizer가 Galaxy AI 엔진을 사용하는지는 실기기 확인이 필요하다.
- 단, 파일 입력을 지원하지 않으므로 본 프로젝트의 핵심 요구사항(녹음 파일 전사)에는 부적합하다.

### b) Samsung 녹음앱/노트앱 Intent 경로

| 항목 | 결과 |
|------|------|
| 판정 | NOT_ACCESSIBLE |
| Voice Recorder | 전사 트리거 공개 Intent 없음 |
| Samsung Notes | 전사 트리거 공개 Intent 없음 |
| Developer 문서 | 해당 Intent 문서 미제공 |

### c) Samsung SDK/API 경로

| 항목 | 결과 |
|------|------|
| 판정 | NOT_ACCESSIBLE |
| Samsung Developer Portal | 전사 관련 공개 SDK/API 없음 |
| Galaxy AI SDK | 별도의 공개 SDK 미존재 |
| Knox SDK | MDM 전용, 전사 기능 무관 |

## Go/No-Go

### **Pending** (실기기 테스트 1건 잔존, 단 실질적으로는 No-Go에 가까움)

- 경로 b)와 c)는 **No-Go** 확정: 공식 API/Intent/SDK가 존재하지 않음.
- 경로 a)는 **REQUIRES_DEVICE_TEST**이나, 파일 입력 미지원이라는 근본 한계로 인해 프로젝트 요구사항에 부합하지 않음.
- 실질적으로 Galaxy AI를 서드파티에서 녹음 파일 전사 용도로 사용하는 것은 불가능하다.

## 실기기 테스트 항목

Galaxy AI 관련으로 남은 실기기 확인 항목:

1. **SpeechRecognizer 엔진 확인**
   - Samsung Galaxy 기기에서 `Settings > General management > Speech services` 확인
   - `createOnDeviceSpeechRecognizer()` 호출 시 사용되는 엔진명 확인
   - 참고: 파일 입력 미지원이므로 프로젝트 용도에는 무관하지만 기술적 사실 확인 차원

## 결론

Galaxy AI 전사 기능의 서드파티 접근은 사실상 불가능하다.
Samsung은 Galaxy AI 전사를 자체 앱(Voice Recorder, Notes)의 부가 기능으로 제공하고 있으며,
서드파티 개발자를 위한 API/SDK/Intent를 공개하지 않았다.

**대안 STT 경로(ML Kit GenAI, Whisper)로 전환하는 것이 적절하다.**
이는 프로젝트 초기 논의(D-07, D-08)에서 이미 예상된 시나리오이며,
PROJECT.md의 "Out of Scope" 섹션에 있는 "자체 STT 엔진 구현" 제약을 Whisper와 같은
기존 오픈소스 모델 활용으로 해석하여 진행한다.
