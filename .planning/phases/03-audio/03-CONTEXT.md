# Phase 3: 오디오 수집 - Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Plaud 녹음기에서 전송되는 오디오 파일을 앱 내부 저장소에 자동으로 저장한다. Foreground Service로 백그라운드 동작을 보장하고, 새 파일 감지 시 파이프라인 다음 단계(전사)를 자동 트리거한다.

</domain>

<decisions>
## Implementation Decisions

### Phase 1 결정 반영 (Locked)

- **D-01:** Plaud SDK v0.2.8 (MIT 라이선스)을 1차 오디오 수집 경로로 채택 — appKey 발급 필요
- **D-02:** Cloud API (비공식, JWT 인증)를 2차 폴백 경로로 유지
- **D-03:** FileObserver 경로는 Scoped Storage 제한으로 폐기 확정
- **D-04:** 온디바이스 처리 우선 원칙 유지

### Phase 2 코드 연동 (Locked)

- **D-05:** `AudioRepository` 인터페이스가 domain 레이어에 정의됨 — 이 인터페이스의 구현체를 data 레이어에 작성
- **D-06:** `MeetingEntity` + `MeetingDao` + Room DB가 존재 — 수집된 오디오 메타데이터를 여기에 저장
- **D-07:** `PipelineStatus.AUDIO_RECEIVED` 상태가 이미 정의됨 — 파일 저장 완료 시 이 상태로 전환
- **D-08:** Hilt DI 그래프 구성됨 — 새 모듈은 기존 패턴(DatabaseModule, RepositoryModule) 따름
- **D-09:** WorkManager 초기화 완료 — 파이프라인 트리거에 활용 가능

### Claude's Discretion

사용자가 모든 기술적 세부사항을 Claude에 위임. 다음 항목은 리서치/플래닝에서 결정:

- **Plaud SDK 연동:** BLE 연결 흐름, 파일 다운로드 API 사용법, appKey 처리
- **Foreground Service 설계:** 알림 디자인, foregroundServiceType, 배터리 최적화 대응
- **파이프라인 트리거:** 파일 저장 완료 시 WorkManager로 전사 단계 트리거하는 방식
- **오류 처리:** BLE 연결 실패, 파일 전송 중단, 저장 공간 부족 등 예외 처리
- **삼성 기기 특화:** Samsung 배터리 최적화 우회, Foreground Service 킬 방지

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프로젝트 컨텍스트
- `.planning/PROJECT.md` — Key Decisions (Plaud SDK 채택, FileObserver 폐기 등)
- `.planning/REQUIREMENTS.md` — AUD-01~03 요구사항

### PoC 결과
- `poc/results/POC-01-plaud.md` — Plaud SDK 평가, Cloud API 테스트 결과
- `poc/plaud-analysis/apk-analysis.md` — APK 분석 결과 (BLE UUID, 파일 경로 등)
- `poc/plaud-analysis/sdk-evaluation.md` — SDK 평가 상세
- `poc/plaud-analysis/cloud-api-test.py` — Cloud API 테스트 스크립트

### 기존 코드 (Phase 2)
- `app/src/main/java/com/autominuting/domain/repository/AudioRepository.kt` — 오디오 수집 인터페이스 (구현 대상)
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — DB Entity
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` — DAO
- `app/src/main/java/com/autominuting/domain/model/PipelineStatus.kt` — 상태 머신

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AudioRepository` 인터페이스: `suspend fun startCollection()`, `suspend fun stopCollection()` 등 계약 정의됨
- `MeetingRepositoryImpl`: Room DB CRUD 패턴 참조 가능
- `WorkerModule` + `TestWorker`: WorkManager 패턴 참조
- `DatabaseModule`: Hilt `@Provides` 패턴 참조

### Established Patterns
- Clean Architecture: domain 인터페이스 → data 구현체 → di 바인딩
- Hilt DI: `@Module` + `@InstallIn(SingletonComponent::class)` + `@Provides`/`@Binds`
- Room: Entity + DAO + TypeConverter 패턴

### Integration Points
- `AudioRepository` 구현체 → `RepositoryModule`에 DI 바인딩 추가
- `PipelineStatus.AUDIO_RECEIVED` → `MeetingDao.updateStatus()` 호출
- 파일 저장 완료 → WorkManager로 전사 Worker enqueue

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

*Phase: 03-audio*
*Context gathered: 2026-03-24*
