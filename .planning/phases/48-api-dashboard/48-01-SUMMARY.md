---
phase: 48-api-dashboard
plan: 48-01
subsystem: dashboard
tags: [api-usage, tracking, dashboard, datastore, compose]
dependency_graph:
  requires: []
  provides:
    - ApiUsageTracker (DataStore 기반 누적 API 호출 추적)
    - ApiUsageState / EngineCallStat (UI 데이터 모델)
    - DashboardScreen ApiUsageCard (엔진별 사용량 카드)
  affects:
    - GeminiSttEngine
    - GroqSttEngine
    - DeepgramSttEngine
    - NaverClovaSttEngine
    - WhisperEngine
    - MinutesRepositoryImpl
    - DashboardViewModel
    - DashboardScreen
tech_stack:
  added:
    - ApiUsageTracker (DataStore intPreferencesKey 기반)
    - ApiUsageState / EngineCallStat data class
  patterns:
    - DataStore 누적 카운터 패턴 (GeminiQuotaTracker와 병렬 구조)
    - StateFlow.map + stateIn 패턴 (DashboardViewModel)
key_files:
  created:
    - app/src/main/java/com/autominuting/data/quota/ApiUsageTracker.kt
    - app/src/main/java/com/autominuting/data/quota/ApiUsageState.kt
  modified:
    - app/src/main/java/com/autominuting/data/stt/GeminiSttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/GroqSttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/DeepgramSttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/NaverClovaSttEngine.kt
    - app/src/main/java/com/autominuting/data/stt/WhisperEngine.kt
    - app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/autominuting/presentation/dashboard/DashboardScreen.kt
decisions:
  - MinutesRepositoryImpl에서 minutesEngine.engineName() 대신 UserPreferencesRepository.getMinutesEngineTypeOnce() + MinutesEngineType when 분기를 사용 (MinutesEngineSelector가 항상 "MinutesEngineSelector" 반환하는 문제 회피)
  - GeminiQuotaTracker 기존 코드 완전 보존, ApiUsageTracker는 병렬로 추가
  - HorizontalDivider는 FQCN(androidx.compose.material3.HorizontalDivider)으로 호출하여 import 충돌 방지
metrics:
  duration: ~15분
  completed_date: 2026-04-03
  tasks: 3
  files: 10
---

# Phase 48 Plan 01: API 사용량 대시보드 구현 Summary

DataStore 기반 엔진별 API 호출 누적 추적기(ApiUsageTracker)를 구축하고, 5개 STT 엔진과 MinutesRepositoryImpl의 성공 분기에 record() 호출을 삽입하여 대시보드에 엔진별 사용량 요약 카드를 표시한다.

## 완료된 작업

### Task 1: ApiUsageTracker + ApiUsageState 신규 생성
- **ApiUsageTracker.kt**: STT 5개 + Minutes 3개 엔진 키 상수(ALL_KEYS 8개), DataStore 기반 usageMap Flow, record(), resetAll() 메서드
- **ApiUsageState.kt**: isEmpty 계산 프로퍼티, EngineCallStat data class (engineKey, displayName, callCount, estimatedCostUsd)
- 커밋: `bf49faf`

### Task 2: STT 엔진 5개 + MinutesRepositoryImpl에 ApiUsageTracker 호출 추가
- GeminiSttEngine: quotaTracker.recordUsage 아래에 KEY_GEMINI_STT 기록
- GroqSttEngine: callResult 반환 직전(if(callResult != null) 블록 안)에 KEY_GROQ_STT 기록
- DeepgramSttEngine: 전사 완료 로그 아래, Result.success 직전에 KEY_DEEPGRAM_STT 기록
- NaverClovaSttEngine: 전사 완료 로그 아래, Result.success 직전에 KEY_NAVER_STT 기록
- WhisperEngine: "전사 완료" 로그 아래, Result.success 직전에 KEY_WHISPER_STT 기록
- MinutesRepositoryImpl: UserPreferencesRepository + ApiUsageTracker 주입, quotaTracker.recordUsage 아래에 MinutesEngineType when 분기로 엔진별 Minutes 키 기록
- 커밋: `08cd628`

### Task 3: DashboardViewModel + DashboardScreen 수정
- DashboardViewModel: ApiUsageTracker 주입, apiUsageState StateFlow 추가 (sttEngineStats 5개 + minutesEngineStats 3개)
- DashboardScreen: apiUsageState 수집, 기존 "Gemini API 사용량" Card → ApiUsageCard 호출로 교체
- ApiUsageCard composable: 0회 엔진 숨김, 빈 기록 시 "아직 API 사용 기록이 없습니다", 유료 엔진 예상 비용 표시
- EngineStatRow composable: 엔진명 + 호출 횟수 + 예상 비용 행
- 커밋: `f58eaca`

## Deviations from Plan

None - 플랜 그대로 실행되었다.

## Known Stubs

None - 모든 데이터가 DataStore Flow에서 실시간으로 수집된다.

## Self-Check: PASSED

파일 존재 확인:
- `app/src/main/java/com/autominuting/data/quota/ApiUsageTracker.kt` — FOUND
- `app/src/main/java/com/autominuting/data/quota/ApiUsageState.kt` — FOUND

커밋 존재 확인:
- `bf49faf` (Task 1) — FOUND
- `08cd628` (Task 2) — FOUND
- `f58eaca` (Task 3) — FOUND

빌드: `./gradlew assembleDebug` — BUILD SUCCESSFUL
