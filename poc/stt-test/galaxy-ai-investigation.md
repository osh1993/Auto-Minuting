# Galaxy AI 전사 서드파티 접근 경로 조사

**조사일:** 2026-03-24
**목적:** Samsung Galaxy AI의 Transcript Assist 기능을 서드파티 앱에서 프로그래밍적으로 호출할 수 있는지 확인

## 배경

Samsung Galaxy AI Transcript Assist는 Samsung Voice Recorder 앱에 내장된 온디바이스 전사 기능이다.
한국어 전사 정확도가 높고 네트워크 불필요라는 장점이 있으나, 서드파티 앱에서 접근할 수 있는
공식 API가 존재하는지가 핵심 의문이다.

---

## 경로 a) SpeechRecognizer 온디바이스 경로

### 조사 내용

- **Android SpeechRecognizer API**: Android 표준 API로 음성 인식 기능을 제공한다.
- **`createOnDeviceSpeechRecognizer(context)`**: API 31+ (Android 12)에서 도입된 온디바이스 전용 메서드.
  - 이 API는 기기에 설치된 기본 STT 엔진의 온디바이스 모드를 사용한다.
  - Samsung 기기에서 기본 STT 엔진이 Galaxy AI인지 확인 필요.

### Samsung 기기 기본 STT 엔진 확인 방법

```
Settings > General management > Keyboard list and default > Speech services
```

- Samsung 기기에서 기본 음성 인식 서비스는 일반적으로 "Samsung Voice Input" 또는 Google 음성 인식이다.
- Galaxy AI Transcript Assist는 별도의 기능으로, SpeechRecognizer 엔진과 별개로 동작할 가능성이 높다.

### 제한사항

- **SpeechRecognizer는 스트리밍 입력만 지원**: 마이크 실시간 입력을 전제로 설계되었다.
- **파일 입력 미지원**: 녹음된 오디오 파일을 직접 전달하는 API가 없다.
- **파일 재생 + 마이크 루프백**: 기술적으로 가능하지만 음질 손실이 심하고, 1시간 녹음 시 실시간 소요되어 비현실적이다.
- **Samsung 기기에서 온디바이스 SpeechRecognizer가 Galaxy AI 엔진을 사용하는지 미확인**: 실기기 테스트 필요.

### 판정: **REQUIRES_DEVICE_TEST**

Samsung Galaxy 기기에서 `createOnDeviceSpeechRecognizer()`가 Galaxy AI 엔진을 사용하는지 실기기 확인이 필요하다.
단, 파일 입력 미지원이라는 근본적 제한이 있어 사용 가능하더라도 본 프로젝트의 요구사항(녹음 파일 전사)에는 부적합하다.

---

## 경로 b) Samsung 녹음앱/노트앱 Intent 경로

### 조사 내용

- **Samsung Voice Recorder 패키지명**: `com.sec.android.app.voicenote`
- **Samsung Notes 패키지명**: `com.samsung.android.app.notes`

#### Intent 조사

- Samsung Voice Recorder:
  - `ACTION_TRANSCRIBE` 또는 유사한 전사 전용 Intent는 공개되어 있지 않다.
  - 매니페스트 분석(`adb shell dumpsys package com.sec.android.app.voicenote`)으로 exported Activity/Service 확인 가능하나, 전사 기능을 트리거하는 Intent-filter는 알려진 바 없다.
  - Samsung Voice Recorder는 앱 내부에서만 전사 기능을 제공하며, 외부 앱이 전사를 요청할 수 있는 공개 Intent를 제공하지 않는다.

- Samsung Notes:
  - Samsung Notes의 전사 기능도 앱 내부 기능으로, 외부에서 트리거할 수 있는 공개 Intent가 없다.
  - `android.intent.action.SEND`로 오디오 파일을 Samsung Notes에 공유할 수 있으나, 전사 기능이 자동 트리거되지는 않는다.

#### Samsung Developer 문서 조사

- Samsung Developer Portal(developer.samsung.com)에서 Voice Recorder 또는 Notes 앱의 Intent 문서는 제공되지 않는다.
- Galaxy AI 관련 API/SDK 문서에 전사 기능의 서드파티 호출 방법은 기술되어 있지 않다.

### 판정: **NOT_ACCESSIBLE**

Samsung 녹음앱/노트앱의 전사 기능을 외부에서 트리거할 수 있는 공개 Intent가 존재하지 않는다.

---

## 경로 c) Samsung SDK/API 경로

### 조사 내용

- **Samsung Developer Portal 검색** (키워드: transcript, speech, AI, STT):
  - Samsung Galaxy AI SDK라는 별도의 공개 SDK는 존재하지 않는다.
  - Samsung에서 제공하는 서드파티 SDK:
    - Samsung Health SDK
    - Samsung Pay SDK
    - Samsung Blockchain SDK
    - Samsung IAP SDK
  - 음성 인식/전사 관련 공개 SDK는 없다.

- **Samsung One UI / Good Lock Plugin 경로**:
  - 전사 기능은 Good Lock 플러그인으로도 접근할 수 없다.

- **Samsung Knox SDK**:
  - Knox SDK는 기업용 MDM 기능이며, 전사 기능 접근과는 관련이 없다.

- **결과**: Galaxy AI 전사 기능에 접근할 수 있는 서드파티 SDK/API가 존재하지 않는다.

### 판정: **NOT_ACCESSIBLE**

Samsung Developer Portal 및 공개 자료에서 Galaxy AI 전사 기능에 접근할 수 있는 SDK/API를 발견하지 못했다.

---

## 종합 결론

| 경로 | 판정 | 비고 |
|------|------|------|
| a) SpeechRecognizer 온디바이스 | REQUIRES_DEVICE_TEST | 파일 입력 미지원 → 사용 가능해도 프로젝트 요구사항 미충족 |
| b) Samsung 녹음앱/노트앱 Intent | NOT_ACCESSIBLE | 전사 트리거 공개 Intent 없음 |
| c) Samsung SDK/API | NOT_ACCESSIBLE | 전사 관련 공개 SDK/API 없음 |

**핵심 결론:** Galaxy AI 전사 기능은 Samsung의 자체 앱(Voice Recorder, Notes) 내부에서만 사용 가능하며,
서드파티 앱에서 프로그래밍적으로 호출할 수 있는 공식적인 경로가 존재하지 않는다.
경로 a)의 SpeechRecognizer는 실기기 테스트 후 확인이 필요하지만, 파일 입력 미지원이라는 근본적 한계로 인해
녹음 파일 전사 용도로는 부적합하다.

**대안 STT 경로(ML Kit GenAI, Whisper)로의 전환이 필요하다.**
