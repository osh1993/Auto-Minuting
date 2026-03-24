# Phase 4: 전사 엔진 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

저장된 한국어 오디오 파일을 Whisper 온디바이스(1차) 또는 ML Kit GenAI(2차)로 텍스트 전사하여 로컬에 저장한다. 사용자가 전사된 텍스트를 편집할 수 있는 화면을 제공한다. STT 실패 시 폴백 엔진으로 자동 전환한다.

</domain>

<decisions>
## Implementation Decisions

### Phase 1 결정 반영 (Locked)

- **D-01:** Whisper 온디바이스(whisper.cpp small 모델)를 1차 STT 경로로 채택
- **D-02:** ML Kit GenAI SpeechRecognizer를 2차 폴백으로 채택
- **D-03:** 온디바이스 처리 우선 — 클라우드 STT는 최후의 수단
- **D-04:** Galaxy AI 서드파티 접근 불가 확인 — 사용하지 않음

### Phase 2-3 코드 연동 (Locked)

- **D-05:** `TranscriptionRepository` 인터페이스가 domain 레이어에 정의됨 — 구현체 작성 대상
- **D-06:** `TranscriptionTriggerWorker`가 `audioFilePath`를 inputData로 전달 — 이 Worker에서 전사 로직 호출
- **D-07:** `MeetingEntity`에 `transcriptText` 필드와 `PipelineStatus.TRANSCRIBED` 상태 정의됨
- **D-08:** Clean Architecture + Hilt DI 패턴 확립됨

### Claude's Discretion

사용자가 모든 기술적 세부사항을 Claude에 위임:

- **Whisper 모델:** tiny/base/small/medium 중 선택 (정확도 vs 속도 트레이드오프)
- **whisper.cpp 통합:** JNI 바인딩 vs whisper-android 라이브러리 선택
- **전사 편집 UI:** 편집 화면 레이아웃, 저장 방식, 타임스탬프 연동 여부
- **폴백 전략:** Whisper 실패 시 ML Kit 자동 전환 로직
- **진행률 표시:** 전사 중 진행률 알림 방식
- **오디오 포맷 변환:** Whisper 입력 포맷(16kHz WAV) 변환 필요 여부

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프로젝트 컨텍스트
- `.planning/PROJECT.md` — Key Decisions (Whisper 채택, ML Kit 폴백)
- `.planning/REQUIREMENTS.md` — STT-01~03 요구사항

### PoC 결과
- `poc/results/POC-02-galaxy-ai.md` — Galaxy AI 조사 결과 (No-Go)
- `poc/results/POC-03-stt-fallback.md` — Whisper/ML Kit 평가 결과
- `poc/stt-test/whisper-evaluation.md` — Whisper 상세 평가 (VIABLE)
- `poc/stt-test/mlkit-evaluation.md` — ML Kit 평가 (NEEDS_DEVICE_TEST)

### 기존 코드 (Phase 2-3)
- `app/src/main/java/com/autominuting/domain/repository/TranscriptionRepository.kt` — 전사 인터페이스 (구현 대상)
- `app/src/main/java/com/autominuting/worker/TranscriptionTriggerWorker.kt` — 전사 시작 Worker (연동 대상)
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — DB Entity
- `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` — 상태 머신

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TranscriptionRepository` 인터페이스: `suspend fun transcribe(audioFilePath: String): Result<String>` 계약
- `TranscriptionTriggerWorker`: audioFilePath inputData 수신 패턴
- `AudioRepositoryImpl`: SDK/Cloud API 이중 경로 폴백 패턴 — 전사 폴백에 동일 패턴 적용 가능
- `MeetingRepositoryImpl`: Room DB CRUD 패턴

### Established Patterns
- Clean Architecture: domain 인터페이스 → data 구현체 → di 바인딩
- 이중 경로 폴백: 1차 실패 → 2차 자동 전환 (AudioRepositoryImpl 참조)
- WorkManager: @HiltWorker + inputData 패턴

### Integration Points
- `TranscriptionTriggerWorker.doWork()` → `TranscriptionRepository.transcribe()` 호출
- 전사 완료 → `MeetingDao.updateTranscript()` + `PipelineStatus.TRANSCRIBED`
- 전사 편집 → 기존 `TranscriptsScreen` (현재 빈 화면) 확장

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. 사용자가 모든 구현 세부사항을 Claude에 위임.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-stt*
*Context gathered: 2026-03-24*
