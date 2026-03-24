# Phase 6: 파이프라인 통합 및 자동화 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

전체 파이프라인(오디오 감지 → 전사 → 회의록 생성)이 사용자 개입 없이 자동으로 완료되는 엔드-투-엔드 자동화를 완성한다. 회의록 형식 선택(3종 프리셋), Android Share Intent 공유, 파이프라인 단계별 진행 알림, 자동화 모드 설정(완전 자동/하이브리드)을 구현한다.

</domain>

<decisions>
## Implementation Decisions

### 회의록 형식 선택 (MIN-05)
- **D-01:** 3종 프리셋 형식 제공: 구조화된 회의록(현재 기본값), 요약, 액션 아이템 중심. 각 형식은 GeminiEngine에 별도 프롬프트로 구현
- **D-02:** 설정 화면에서 기본 형식을 선택하고 DataStore에 저장. 개별 회의록 생성 전에도 변경 가능
- **D-03:** 커스텀 프롬프트 편집은 v2로 미룸. v1은 프리셋만 지원

### 자동화 모드 (UI-04)
- **D-04:** 완전 자동 모드(기본값): 오디오 감지 → 전사 → 회의록 생성이 모두 자동으로 진행
- **D-05:** 하이브리드 모드: 전사 완료 후 1회 사용자 확인을 거쳐 회의록 생성 진행. 전사 결과를 먼저 확인하고 싶을 때 사용
- **D-06:** 모드 전환은 설정 화면에서 DataStore 기반 토글로 구현

### 파이프라인 진행 알림 (UI-02)
- **D-07:** AudioCollectionService의 기존 NotificationChannel/NotificationCompat 패턴을 재사용. 파이프라인 전용 알림 채널 추가
- **D-08:** 각 Worker에서 단계 전환 시 알림 업데이트: "전사 중...", "회의록 생성 중...", "회의록 완료"
- **D-09:** 앱 내 진행 상태는 홈 화면 상단 배너(현재 진행 중인 파이프라인)와 기존 SuggestionChip 상태 표시 패턴 활용

### 공유 기능 (MIN-06)
- **D-10:** Android Share Intent(ACTION_SEND)로 text/plain 타입 Markdown 텍스트 공유. 파일 첨부 없이 텍스트만
- **D-11:** 공유 트리거 위치: MinutesDetailScreen 상단 AppBar에 공유 아이콘 + 회의록 생성 완료 알림에서 직접 공유 가능

### Claude's Discretion
- 알림 아이콘 디자인 및 색상
- 설정 화면 레이아웃 세부 구성
- 프리셋 프롬프트 문구 최적화 (기존 MINUTES_PROMPT를 기반으로 변형)
- 하이브리드 모드의 확인 UI (알림 액션 vs 앱 내 다이얼로그)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 파이프라인 체이닝
- `app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt` — 전사→회의록 Worker 체이닝 패턴 (OneTimeWorkRequestBuilder)
- `app/src/main/java/com/autominuting/worker/MinutesGenerationWorker.kt` — 회의록 생성 Worker 구현

### 회의록 생성 엔진
- `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` — 현재 하드코딩된 MINUTES_PROMPT, 형식별 프롬프트로 확장 필요
- `app/src/main/java/com/autominuting/data/repository/MinutesRepositoryImpl.kt` — MinutesRepository 구현체

### 알림 패턴
- `app/src/main/java/com/autominuting/service/AudioCollectionService.kt` — NotificationChannel 생성 및 Foreground Service 알림 패턴 참조

### 상태 관리
- `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` — 파이프라인 상태 enum (AUDIO_RECEIVED ~ COMPLETED)
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — DB 엔티티 구조

### UI 패턴
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` — 공유 아이콘 추가 대상
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesScreen.kt` — SuggestionChip 상태 표시 패턴

### 설정
- `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` — 설정 화면 (형식 선택/모드 토글 추가 대상)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **AudioCollectionService**: Notification 채널 생성 + 알림 업데이트 패턴. 파이프라인 알림에 동일 패턴 적용 가능
- **SuggestionChip 상태 표시**: Phase 4에서 확립된 tertiary/primary/error 색상 구분 패턴
- **Worker 체이닝 패턴**: TranscriptionTriggerWorker → MinutesGenerationWorker 체이닝이 이미 동작 중
- **DataStore**: 설정 관리에 이미 사용 중 (Phase 2에서 설정)
- **GeminiEngine.MINUTES_PROMPT**: 구조화된 회의록 프롬프트가 이미 검증됨 — 이를 기반으로 요약/액션아이템 프롬프트 파생

### Established Patterns
- Hilt DI + @Singleton + @Inject constructor 패턴
- CoroutineWorker + WorkManager 백그라운드 처리
- StateFlow + collectAsStateWithLifecycle UI 상태 관리
- Clean Architecture (domain/data/presentation 레이어 분리)

### Integration Points
- MinutesGenerationWorker에서 형식 선택값을 inputData로 전달받아 GeminiEngine에 형식별 프롬프트 적용
- SettingsScreen에 형식 선택 드롭다운 + 자동화 모드 스위치 추가
- MinutesDetailScreen AppBar에 Share 아이콘 추가
- Worker에서 NotificationManager를 통해 단계별 알림 업데이트

</code_context>

<specifics>
## Specific Ideas

- 프리셋 프롬프트는 기존 MINUTES_PROMPT(구조화된 회의록)를 기반으로 요약 프롬프트("핵심 내용 3~5줄로 요약"), 액션아이템 프롬프트("결정 사항과 할 일만 추출")를 파생
- 하이브리드 모드에서 전사 완료 알림에 "회의록 생성 시작" 액션 버튼 추가 고려
- 완전 자동 모드가 기본값이어야 Core Value("수동 작업 없이")에 부합

</specifics>

<deferred>
## Deferred Ideas

- 커스텀 프롬프트 편집 기능 (v2 ADV-03)
- 파이프라인 실행 이력/통계 (v2)
- 회의록 생성 취소/재생성 기능 (필요시 추후 Phase에서)

</deferred>

---

*Phase: 06-pipeline-integration*
*Context gathered: 2026-03-24*
