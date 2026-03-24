# Whisper 온디바이스 평가

**평가일:** 2026-03-24
**참조:** https://github.com/ggml-org/whisper.cpp

## 개요

OpenAI Whisper를 C/C++로 포팅한 whisper.cpp는 Android를 포함한 다양한 플랫폼에서
온디바이스 음성 인식을 제공한다. GPU 가속 없이 CPU만으로 동작 가능하며,
한국어를 포함한 다국어 전사를 지원한다.

### 핵심 특징

| 항목 | 내용 |
|------|------|
| 구현 | whisper.cpp (C/C++, ggml 텐서 라이브러리) |
| 라이선스 | MIT |
| 한국어 | 지원 (multi-language 모델) |
| Android | JNI 바인딩, Kotlin/Java 예제 앱 존재 |
| 입력 | WAV/MP3 등 오디오 파일 직접 입력 |

## 모델 선택

### 모델별 비교

| 모델 | 크기 | 파라미터 | 한국어 적합성 | 비고 |
|------|------|---------|-------------|------|
| tiny | ~75MB | 39M | 낮음 | 한국어 정확도 부족 예상 |
| base | ~150MB | 74M | 보통 | 한국어 정확도 불충분 가능 |
| **small** | **~500MB** | **244M** | **양호** | **PoC 권장 - 정확도와 속도 균형** |
| medium | ~1.5GB | 769M | 우수 | 모바일 메모리 제약 |
| large-v3 | ~3GB | 1550M | 최고 | 모바일 비현실적 |

### PoC 권장: small 모델

- **근거**: 한국어는 Whisper에서 medium-resource 언어로 분류된다. tiny/base 모델은 한국어 정확도가 부족할 가능성이 높고, medium 이상은 모바일 메모리/속도 제약이 있다.
- **small 모델**은 정확도와 속도의 균형점으로, PoC 검증에 적합하다.
- **Distilled 모델**: `distil-small.en`은 영어 전용이므로 부적합. 한국어 지원 distilled 모델 여부 확인 필요.

## Android 빌드

### 빌드 방법

1. **whisper.android 예제 앱** (Kotlin):
   - 위치: `whisper.cpp/examples/whisper.android/`
   - Android Studio에서 직접 빌드 가능.
   - JNI를 통해 whisper.cpp 네이티브 라이브러리 호출.

2. **빌드 요구사항**:
   - Android NDK (CMake 빌드)
   - 최소 API: 24 (Android 7.0) 이상 권장
   - ABI: arm64-v8a (Samsung Galaxy 기기 호환)

3. **통합 복잡도**:
   - JNI 바인딩 필요 → 네이티브 크래시 디버깅 난이도 높음.
   - 모델 파일을 assets 또는 앱 저장소에 포함해야 함 (~500MB).
   - APK 크기 증가 → App Bundle 분리 또는 런타임 다운로드 권장.

### 예제 앱 구조

```
whisper.android/
├── app/
│   ├── src/main/
│   │   ├── java/com/.../WhisperLib.kt    // JNI 바인딩
│   │   └── jni/whisper/                    // 네이티브 코드
│   └── build.gradle
└── ...
```

## 한국어 성능 예상

### 벤치마크 참고

- **S24 Ultra에서 distil-small.en**: 2x 실시간 속도 (영어 기준).
- **한국어 small 모델**: 영어 대비 느릴 수 있음 (multi-language 모델이 영어 전용 distilled 모델보다 무거움).
- **예상 성능** (small 모델, S24 Ultra 기준):
  - 실시간 대비: 1~1.5x (한국어)
  - 1시간 녹음 전사: 40분~60분 소요 예상
  - 이는 PoC 기준으로 허용 가능한 범위.

### 한국어 정확도

- Whisper는 한국어를 medium-resource 언어로 지원.
- CER(Character Error Rate) 기준으로 측정하며, small 모델에서 일상 대화 수준의 정확도 기대.
- 전문 용어, 고유명사에서는 정확도 저하 예상.
- 회의 녹음 특성상 여러 화자 겹침, 배경 소음 등이 정확도에 영향.

## Hallucination 대응

### 문제점

Whisper v3에서 한국어 전사 시 hallucination/반복 현상이 보고되어 있다:
- 무음 구간에서 의미 없는 텍스트 생성.
- 특정 문장의 반복 출력.
- 존재하지 않는 내용의 환각 생성.

### 완화 방법

1. **VAD(Voice Activity Detection) 전처리**:
   - Silero VAD 등으로 무음 구간 제거 후 전사.
   - whisper.cpp에 `--vad` 옵션 내장 (Silero VAD 모델 지원).
   - 무음 구간 hallucination 방지에 효과적.

2. **Temperature 조정**:
   - `temperature=0.0`으로 설정하여 결정론적 출력.
   - 반복/환각 감소 효과.

3. **Prompt 활용**:
   - 초기 프롬프트에 한국어 문맥 키워드 제공.
   - 예: `"회의록, 한국어, 비즈니스 미팅"`

4. **후처리**:
   - 반복 문장 탐지 및 제거 로직.
   - 무의미한 짧은 세그먼트 필터링.

## 평가

### 판정: **VIABLE**

### 근거

- **장점**:
  - 오픈소스(MIT), 커뮤니티 활발.
  - 한국어 공식 지원, multi-language 모델.
  - 파일 입력 직접 지원.
  - 온디바이스 처리 (D-06 원칙 부합).
  - Android 빌드 및 예제 앱 존재.
  - 모델 선택 유연성 (tiny ~ large).

- **단점**:
  - JNI 빌드 복잡도.
  - 모델 크기 (~500MB for small).
  - 한국어 hallucination 리스크 (VAD로 완화 가능).
  - 실시간 대비 전사 속도가 느릴 수 있음.

- **결론**: 기술적으로 실현 가능하며, hallucination 완화 방법이 존재한다.
  small 모델로 PoC 검증 후 품질 판단이 가능하다.
  가장 확실한 온디바이스 STT 폴백 경로이다.
