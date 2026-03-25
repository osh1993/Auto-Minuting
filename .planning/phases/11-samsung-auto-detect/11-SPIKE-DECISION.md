# Phase 11: 삼성 자동 감지 스파이크 - 판정 결과

**판정일:** 2026-03-26
**판정:** Partial Go

## 검증 결과 요약

| 항목 | 결과 | 상세 |
|------|------|------|
| 오디오 파일(m4a) MediaStore 감지 | YES (예상) | 삼성 녹음앱은 `Recordings/Voice Recorder/` 경로에 m4a 파일을 저장하며, MediaStore.Audio에 등록된다. ContentObserver로 감지 가능 |
| 전사 텍스트 파일 MediaStore 감지 | NO | 전사 텍스트는 앱 내부 DB(`/data/data/com.sec.android.app.voicenote/`)에만 저장. 사용자가 "공유 > 텍스트 파일"을 선택해야만 외부로 전달 |
| ContentObserver 이벤트 수신 | YES (오디오만) | MediaStore.Audio.Media.EXTERNAL_CONTENT_URI 변경 이벤트는 수신 가능. MediaStore.Files에서 전사 텍스트 파일 이벤트는 미수신 |
| FileObserver 이벤트 수신 | 제한적 | Scoped Storage(Android 11+) 환경에서 타 앱 파일에 대한 inotify 접근 제한. 보조 수단으로만 활용 가능 |
| OWNER_PACKAGE_NAME 반환 | 미확인 (nullable 가능) | API 29+에서 사용 가능하나 일관성 없을 수 있음. RELATIVE_PATH 기반 필터링이 더 신뢰성 높음 |

## 판정 근거

### 왜 "Partial Go"인가?

1. **오디오 파일 감지는 가능하다:** 삼성 녹음앱(com.sec.android.app.voicenote)은 녹음 파일을 `Internal Storage/Recordings/Voice Recorder/` 경로에 m4a 형식으로 저장하며, 이 파일은 MediaStore.Audio에 등록된다. ContentObserver를 통해 새 녹음 파일 추가를 감지할 수 있다.

2. **전사 텍스트 자동 감지는 불가능하다:** 삼성 녹음앱의 전사(Speech-to-Text) 결과는 앱 내부 데이터베이스에만 저장된다. Scoped Storage 정책에 의해 다른 앱이 이 내부 DB에 접근할 수 없다. 전사 텍스트를 외부로 내보내려면 사용자가 반드시 삼성 녹음앱에서 "공유" 버튼을 눌러야 한다.

3. **대안 경로가 존재한다:** "새 녹음 감지 → 사용자에게 전사 공유 프롬프트 알림"이라는 하이브리드 자동화가 가능하다. 완전 자동은 아니지만, 사용자가 전사 공유를 한 번만 탭하면 되므로 현재 완전 수동 공유 방식보다 UX가 개선된다.

### 연구 근거 (11-RESEARCH.md)

- 삼성 녹음앱 전사 텍스트는 앱 내부 DB에만 존재 (Confidence: MEDIUM)
- 오디오 파일(m4a)은 공유 저장소에 저장되어 MediaStore 감지 가능 (Confidence: HIGH)
- AccessibilityService / MANAGE_EXTERNAL_STORAGE는 Play Store 정책 위반으로 사용 불가

### Go/No-Go 시나리오 매핑

연구에서 정의한 판정 시나리오 B에 해당:
> **시나리오 B: 오디오 파일만 감지 가능, 전사 텍스트 감지 불가 → Partial Go**

## 후속 조치

### Partial Go 경로

1. **오디오 파일 감지 → 사용자 알림 프롬프트 구현 검토 (v2.1)**
   - ContentObserver로 `Recordings/Voice Recorder/`에 새 m4a 파일 추가 감지
   - 감지 시 "삼성 녹음앱에서 전사 텍스트를 공유하세요" 알림 표시
   - 알림 탭 시 삼성 녹음앱의 공유 화면으로 안내 (가능 여부 확인 필요)
   - SREC-F01을 v2.1 로드맵에 조건부 추가

2. **Phase 9의 ShareReceiverActivity가 기본 전사 수신 경로로 확정**
   - 사용자가 삼성 녹음앱에서 "공유 > 텍스트 파일"을 선택하면 Auto Minuting이 수신
   - 이 경로는 이미 Phase 9에서 구현 완료
   - 전사 텍스트 자동 감지 불가로 인해 공유 수신이 유일한 확정 경로

3. **spike/ 패키지 코드 유지**
   - ContentObserver 프로토타입은 v2.1 알림 기능 구현 시 참고 코드로 활용
   - 삼성 녹음앱 업데이트 시 전사 텍스트 외부 저장 가능성 모니터링

### 비적용 경로

- **Go 시나리오 (전사 텍스트 자동 감지):** 삼성 녹음앱이 전사 텍스트를 외부 파일로 저장하는 업데이트가 없는 한 불가
- **No-Go 시나리오 (모든 감지 실패):** 오디오 파일 감지는 가능하므로 완전 No-Go는 아님

## 삼성 녹음앱 파일 경로 정보

### 확인된 정보 (연구 기반)

| 항목 | 값 |
|------|-----|
| 패키지명 | `com.sec.android.app.voicenote` |
| 오디오 저장 경로 | `Internal Storage/Recordings/Voice Recorder/` |
| 오디오 파일 형식 | m4a (기본), 3GP, WAV |
| MediaStore 테이블 | `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` |
| RELATIVE_PATH 패턴 | `Recordings/Voice Recorder/` |
| 전사 텍스트 저장 | 앱 내부 DB (외부 접근 불가) |
| 전사 내보내기 | 사용자 명시적 "공유 > 텍스트 파일" 선택 필요 |

### 실기기 검증 상태

- 실기기 검증은 미수행 (연구 데이터 기반 판정)
- 프로토타입 코드(spike/ 패키지)는 실기기 검증 준비 완료
- 향후 실기기 접근 시 SpikeLogActivity의 검증 시나리오로 확인 가능

## 결론

삼성 녹음앱의 전사 텍스트 **완전 자동 감지는 불가**하다. 그러나 오디오 파일 감지를 통한 **반자동 프롬프트 경로**가 존재하므로, Phase 9의 수동 공유 방식을 기본으로 유지하면서 v2.1에서 "새 녹음 감지 → 공유 프롬프트" 알림 기능을 검토한다.

---
*판정일: 2026-03-26*
*근거: Phase 11 Research (11-RESEARCH.md) + ContentObserver/FileObserver 프로토타입 분석*
