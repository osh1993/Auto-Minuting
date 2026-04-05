# Auto Minuting

> 녹음에서 회의록까지 — 원클릭 자동화 파이프라인

Plaud 녹음기에서 동기화되는 음성 파일을 가로채어 로컬에 저장하고, 클라우드 STT로 전사한 뒤 AI가 회의록을 자동 생성하는 Android 앱.

---

## APK 다운로드

| 버전 | 날짜 | 주요 변경 | 다운로드 |
|------|------|-----------|----------|
| **v8.0** | 2026-04-06 | 다중 M4A 파일 자동 합치기 (Share Intent), Groq 크기 초과 안내 알림 | [AutoMinuting-v8.0-release.apk](https://github.com/osh1993/Auto-Minuting/releases/download/v8.0/AutoMinuting-v8.0-release.apk) |
| **v7.0** | 2026-04-05 | Google Drive 연동, 회의록 편집, API 사용량 대시보드 | [AutoMinuting-v7.0-release.apk](https://github.com/osh1993/Auto-Minuting/releases/download/v7.0/AutoMinuting-v7.0-release.apk) |
| **v6.0** | 2026-04-01 | 멀티 STT/회의록 엔진 (Groq · Deepgram · Naver CLOVA), Gemini API 키 보안 강화 | [AutoMinuting-v6.0-release.apk](https://github.com/osh1993/Auto-Minuting/releases/download/v6.0/AutoMinuting-v6.0-release.apk) |

> 전체 릴리스 목록: [GitHub Releases](https://github.com/osh1993/Auto-Minuting/releases)

### 설치 방법

1. 위 링크에서 APK 다운로드
2. Android 기기에서 **설정 → 보안 → 출처를 알 수 없는 앱 허용**
3. 다운로드한 APK 파일 실행하여 설치

### 요구 사항

- Android 12 (API 31) 이상
- Samsung Galaxy 기기 권장 (Galaxy AI 온보드 전사 사용 시)

---

## 기능 개요

### STT 엔진 (설정에서 선택)

| 엔진 | 특징 |
|------|------|
| Gemini Flash | Google 클라우드, 한국어 지원 |
| Groq Whisper | 빠른 처리 속도 |
| Deepgram Nova-3 | 고정밀 인식 |
| Naver CLOVA Speech | 한국어 특화 |

### 회의록 생성 엔진 (설정에서 선택)

| 엔진 | 특징 |
|------|------|
| Gemini Flash | 구조화된 Markdown 회의록 |
| Deepgram Intelligence | 텍스트 분석 기반 (영어) |
| Naver CLOVA Summary | 한국어 요약 |

---

## 버전 히스토리

| 버전 | 주요 기능 |
|------|-----------|
| v8.0 | 다중 M4A 파일 자동 합치기, Groq 크기 초과 엔진 변경 안내 |
| v7.0 | Google Drive 연동, 회의록 편집, API 사용량 대시보드, 설정 UI 재구성 |
| v6.0 | 멀티 STT/회의록 엔진, 설정 UI 확장, API 키 보안 강화 |
| v5.x | 회의록 자동 생성, NotebookLM 연동 |
| v2.0 | 실동작 파이프라인, STT 통합 |
| v1.0 | 기본 녹음 파일 수신 및 저장 |
