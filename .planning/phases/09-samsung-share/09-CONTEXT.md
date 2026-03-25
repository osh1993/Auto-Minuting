# Phase 9: 삼성 공유 수신 - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

삼성 녹음앱에서 전사 텍스트를 공유 Intent로 수신하여, STT 단계를 건너뛰고 Gemini 회의록 생성 파이프라인에 자동 진입시킨다. 공유로 생성된 회의록은 기존 회의 목록에 출처(SAMSUNG_SHARE)와 함께 표시된다.

</domain>

<decisions>
## Implementation Decisions

### 공유 수신 진입점
- **D-01:** 별도 ShareReceiverActivity를 생성하여 ACTION_SEND Intent를 수신한다 (MainActivity와 분리)
- **D-02:** AndroidManifest에 intent-filter 등록: action=SEND, category=DEFAULT, mimeType=text/plain
- **D-03:** Intent 수신 즉시 파이프라인 자동 시작 — 사용자 확인 대화상자 없음 (원클릭 컨셉)
- **D-04:** 스낵바로 "회의록 생성 중..." 표시 후 Activity 즉시 종료. 진행/완료는 알림으로 표시

### 회의록 형식
- **D-05:** 설정 화면의 기본 회의록 형식(구조화/요약/액션아이템) 자동 적용 — 형식 선택 대화상자 없음

### 데이터 모델
- **D-06:** MeetingEntity.source = "SAMSUNG_SHARE"로 저장 (Phase 8 D-12에서 source 필드 추가 완료)
- **D-07:** 공유 수신 회의록은 audioFilePath = null, transcriptPath에 공유 텍스트 저장

### Claude's Discretion
- 삼성 녹음앱이 보내는 공유 데이터의 정확한 형식 파싱 (text/plain 본문에서 제목/날짜 추출 방식)
- 공유 텍스트를 파일로 저장할지 DB에 직접 저장할지 — 기존 transcriptPath 패턴 유지 권장
- 에러 처리 (빈 텍스트, 너무 짧은 텍스트 등)
- 알림 표시 방식 (기존 PipelineNotificationManager 패턴 활용 권장)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 파이프라인 (회의록 생성 경로)
- `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` — 전사 텍스트 → Gemini 회의록 생성 Worker. KEY_MEETING_ID, KEY_TRANSCRIPT_PATH, KEY_MINUTES_FORMAT 입력
- `app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt` — 기존 파이프라인에서 MinutesGenerationWorker 체인 패턴 참조
- `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt` — 하이브리드 모드 Worker enqueue 패턴

### 데이터 모델
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — source 필드 ("PLAUD_BLE", "SAMSUNG_SHARE"), audioFilePath/transcriptPath/minutesPath
- `app/src/main/java/com/autominuting/domain/model/Meeting.kt` — 도메인 모델, source 필드 매핑
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` — insert/update 쿼리

### 설정
- `app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt` — minutesFormat 기본값 읽기 패턴 (DataStore)

### Manifest
- `app/src/main/AndroidManifest.xml` — Activity/Service/Receiver 등록 패턴

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MinutesGenerationWorker` — 전사 텍스트 → 회의록 Worker. 공유 수신 시에도 동일 Worker 사용 가능
- `MeetingDao.insert()` — Meeting 레코드 생성
- `PipelineNotificationManager` — 파이프라인 진행/완료 알림 (기존 패턴)
- `UserPreferencesRepository` — minutesFormat 기본값 읽기

### Established Patterns
- WorkManager OneTimeWorkRequest + inputData로 Worker enqueue (TranscriptionTriggerWorker 참조)
- Hilt @AndroidEntryPoint Activity DI 패턴
- MeetingEntity ↔ Meeting 도메인 모델 매핑

### Integration Points
- `AndroidManifest.xml` — ShareReceiverActivity + intent-filter 등록
- `MeetingRepositoryImpl` — 새 Meeting 레코드 생성 후 MinutesGenerationWorker enqueue
- 회의 목록 UI — source 필드에 따른 출처 뱃지 표시 (MinutesScreen)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-samsung-share*
*Context gathered: 2026-03-26*
