# Plaud 앱 분석

## 목적

Plaud 녹음기에서 오디오 파일을 획득할 수 있는 기술 경로를 검증한다.
이 디렉토리에는 Plaud 앱 APK 디컴파일 분석 결과, SDK 평가, Cloud API 테스트 스크립트가 포함된다.

## 파일 구조

```
plaud-analysis/
  README.md              # 이 파일
  apk-analysis.md        # APK 디컴파일 분석 결과
  sdk-evaluation.md      # Plaud SDK 평가
  cloud-api-test.py      # Cloud API 폴백 테스트 스크립트
```

## APK 디컴파일 방법 (참고)

### 사전 준비

1. **adb 설치**: Android SDK Platform Tools 필요
2. **jadx 설치**: https://github.com/skylot/jadx/releases 에서 최신 버전 다운로드
   - Windows: `jadx-x.x.x.zip` 다운로드 후 압축 해제
   - PATH에 jadx 바이너리 위치 추가

### APK 추출 및 디컴파일 절차

```bash
# 1. Plaud 앱 APK 경로 확인
adb shell pm path ai.plaud.note

# 2. APK 파일 추출
adb pull /data/app/.../base.apk plaud.apk

# 3. jadx로 디컴파일
jadx -d plaud-decompiled/ plaud.apk

# 4. 분석 대상 검색
# 파일 저장 경로
grep -rn "filesDir\|getExternalFilesDir\|MediaStore\|getExternalStorageDirectory" plaud-decompiled/

# BLE UUID
grep -rn "UUID.fromString\|BluetoothGattService\|BluetoothGattCharacteristic" plaud-decompiled/

# API 엔드포인트
grep -rn "api.plaud.ai\|Retrofit\|@GET\|@POST" plaud-decompiled/
```

### 현재 상태

현재 환경에서는 Plaud 앱이 설치된 Android 기기에 접근할 수 없어 직접 APK 추출이 불가능하다.
대신 다음 대안 경로로 분석을 진행했다:

1. **Plaud SDK 소스 분석**: 공식 SDK GitHub 저장소의 코드를 분석하여 BLE 프로토콜, 파일 포맷 정보 추출
2. **Cloud API 역공학 결과 활용**: 기존 비공식 Python 클라이언트(plaud-api)의 분석 결과 활용
3. **공개 정보 종합**: Plaud Developer Platform 문서, SDK README 등에서 기술 사양 수집

상세 분석 결과는 `apk-analysis.md`를 참조한다.
