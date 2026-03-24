# ML Kit GenAI SpeechRecognizer 평가

**평가일:** 2026-03-24
**공식 문서:** https://developers.google.com/ml-kit/genai/speech-recognition/android

## 개요

Google ML Kit GenAI SpeechRecognizer는 온디바이스 음성 인식 API로, alpha 단계(1.0.0-alpha1)에 있다.
기존 SpeechRecognizer API와 달리 오디오 파일 입력을 지원하며, GenAI 기반 고정밀 전사를 제공한다.

### 핵심 특징

| 항목 | 내용 |
|------|------|
| 버전 | 1.0.0-alpha1 |
| 최소 API | 26 (Android 8.0) |
| 모드 | Basic (전통 온디바이스), Advanced (GenAI, Pixel 10 전용) |
| 입력 | 마이크 + 오디오 파일 (AudioSource.fromFileDescriptor) |
| 의존성 | AICore 앱, Google Play Services |
| 라이선스 | Google ML Kit Terms |

## 한국어 지원

- **Basic 모드**: ko-KR 지원 확인됨.
  - 전통적인 온디바이스 음성 인식 모델 사용.
  - 정확도는 기존 온디바이스 모델과 유사할 것으로 예상.
- **Advanced 모드**: ko-KR 지원 목록에 포함.
  - GenAI 기반 고정밀 전사.
  - 단, Pixel 10 전용으로 Samsung 기기에서는 사용 불가.

### 한국어 품질 예상

- Basic 모드의 한국어 품질은 검증되지 않음 (alpha 단계).
- Advanced 모드는 Samsung 기기 미지원으로 평가 대상 외.
- 실기기에서 Basic 모드의 한국어 전사 품질 테스트가 필수.

## 파일 입력

### API 구조

```kotlin
// 오디오 파일에서 AudioSource 생성
val fileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")
val audioSource = AudioSource.fromFileDescriptor(fileDescriptor!!.fileDescriptor)

// SpeechRecognizer 생성 및 시작
val recognizer = SpeechRecognition.getClient(options)
recognizer.start(audioSource)
    .addOnSuccessListener { result ->
        // 전사 결과 처리
    }
```

- `AudioSource.fromFileDescriptor(fd)`: 오디오 파일의 FileDescriptor를 직접 전달.
- WAV, MP3 등 일반적인 오디오 포맷 지원 예상 (구체적 포맷 목록은 alpha 문서에 미기재).
- **핵심 장점**: 파일 입력을 네이티브로 지원하여 마이크 루프백 같은 우회 불필요.

## Samsung 호환성

### AICore 앱 의존성

- ML Kit GenAI는 Google AICore 앱에 의존한다.
- AICore는 Google Play Store를 통해 배포되며, Samsung 기기에서도 설치 가능.
- 단, AICore의 모델 다운로드 및 초기화 과정이 Samsung 기기에서 정상 동작하는지 미검증.

### Advanced 모드 미지원

- Advanced 모드는 **Pixel 10 전용**으로 명시되어 있다.
- Samsung Galaxy 기기에서는 Advanced 모드를 사용할 수 없다.
- **Basic 모드만 Samsung 기기에서 사용 가능할 가능성이 있다.**

### 모델 다운로드

- 모델 크기: alpha 단계로 구체적 크기 미공개.
- 다운로드 시점: `checkStatus()` API로 모델 가용 여부 확인 후 다운로드.
- Samsung 기기에서 모델 다운로드 가능 여부는 실기기 테스트 필요.

### Unlocked Bootloader 제한

- Unlocked bootloader 기기에서는 ML Kit GenAI가 동작하지 않는다.
- 일반 사용자의 Samsung 기기는 해당 없음 (bootloader 잠금 상태).

## 리스크

1. **Alpha 단계**: API 변경/폐기 가능성. 프로덕션 사용 비권장.
2. **Samsung 기기 호환성 미검증**: Basic 모드가 Samsung에서 동작하는지 확인 필요.
3. **모델 다운로드 크기 미공개**: 사용자 경험에 영향을 줄 수 있음.
4. **한국어 Basic 모드 품질 미검증**: 온디바이스 Basic 모델의 한국어 정확도 불확실.
5. **AICore 의존성**: Google Play Services 및 AICore 앱이 필수 → 일부 Samsung 기기에서 문제 가능.

## 평가

### 판정: **NEEDS_DEVICE_TEST**

### 근거

- **장점**:
  - Google 공식 API로 장기 지원 기대.
  - 파일 입력 네이티브 지원 (`AudioSource.fromFileDescriptor`).
  - 한국어(ko-KR) 공식 지원.
  - 온디바이스 처리 (D-06 원칙 부합).

- **단점**:
  - Alpha 단계로 안정성/완성도 미검증.
  - Samsung 기기에서 Basic 모드 동작 여부 미확인.
  - Advanced 모드(고품질)는 Pixel 10 전용.
  - 모델 크기/다운로드 시간 미공개.

### 실기기 테스트 필요 항목

1. Samsung Galaxy 기기에서 `SpeechRecognition.getClient()` 호출 성공 여부.
2. `checkStatus()` 호출 시 모델 가용 상태 확인.
3. Basic 모드에서 한국어 오디오 파일 전사 품질 확인.
4. 모델 다운로드 크기 및 소요 시간 측정.
