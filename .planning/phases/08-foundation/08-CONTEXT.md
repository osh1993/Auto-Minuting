# Phase 8: 기반 강화 - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning

<domain>
## Phase Boundary

파일 삭제(DB + 연관 파일 정합성), Gemini API 키 설정 UI(암호화 저장 + 유효성 검증), Room DB 마이그레이션(v1→v2), GeminiEngine 인증 추상화(BuildConfig 하드코딩 제거)를 구현한다.

</domain>

<decisions>
## Implementation Decisions

### 삭제 UX
- **D-01:** 회의 목록에서 카드 길게 누르기(long press)로 삭제 트리거
- **D-02:** 삭제 전 확인 대화상자(AlertDialog) 표시 — "이 회의록을 삭제할까요?" 확인 후 삭제
- **D-03:** 삭제 시 DB 레코드 + audioFilePath + transcriptPath + minutesPath 연관 파일 모두 삭제

### API 키 설정 UI
- **D-04:** 기존 SettingsScreen에 "Gemini API" 섹션 추가 (별도 화면 아님)
- **D-05:** OutlinedTextField + 마스킹 토글(눈 아이콘) + 저장 버튼 구성
- **D-06:** 저장 시 Gemini API 테스트 호출로 유효성 검증. 성공 시 저장, 실패 시 에러 표시
- **D-07:** API 키는 Security Crypto(EncryptedSharedPreferences)로 암호화 저장

### 인증 추상화
- **D-08:** Phase 8에서는 단순 구조: DataStore에 저장된 API 키 우선 사용, 없으면 BuildConfig.GEMINI_API_KEY 폴백
- **D-09:** GeminiEngine이 API 키를 외부에서 주입받도록 변경 (생성자 또는 파라미터). Phase 12에서 OAuth 추가 시 interface 도입
- **D-10:** BuildConfig 하드코딩은 폴백으로만 유지, 사용자 설정 API 키가 우선

### Room DB 마이그레이션
- **D-11:** AppDatabase version 1→2 마이그레이션 작성. exportSchema=true로 변경
- **D-12:** MeetingEntity에 source 필드 추가 (Phase 9 삼성 공유 수신 대비). 기본값 "PLAUD_BLE"

### Claude's Discretion
- Room migration 전략 (addMigration vs fallbackToDestructiveMigration) — 데이터 보존 필수이므로 addMigration 권장
- API 키 암호화 저장 방식의 구체적 구현 (EncryptedSharedPreferences vs DataStore + Cipher)
- 삭제 확인 대화상자의 정확한 문구

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 기존 코드 (삭제 관련)
- `app/src/main/java/com/autominuting/data/local/dao/MeetingDao.kt` — delete(id) 쿼리 존재, 파일 삭제 없음
- `app/src/main/java/com/autominuting/data/repository/MeetingRepositoryImpl.kt` — deleteMeeting()이 DB만 삭제
- `app/src/main/java/com/autominuting/data/local/entity/MeetingEntity.kt` — audioFilePath, transcriptPath, minutesPath 필드

### 기존 코드 (API 키 / 인증)
- `app/src/main/java/com/autominuting/data/minutes/GeminiEngine.kt` — BuildConfig.GEMINI_API_KEY 하드코딩, GenerativeModel 생성 방식
- `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` — 기존 설정 UI 구조 (드롭다운 + 스위치)
- `app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt` — DataStore 기반 설정 관리 패턴

### 기존 코드 (DB)
- `app/src/main/java/com/autominuting/data/local/AppDatabase.kt` — version=1, exportSchema=false

### 리서치
- `.planning/research/PITFALLS.md` — 파일 삭제 정합성, Room 마이그레이션 함정
- `.planning/research/ARCHITECTURE.md` — 인증 추상화 설계, source 필드 추가 권장

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MeetingDao.delete(id)` — DB 삭제 쿼리 이미 존재
- `SettingsScreen` — 섹션 추가만으로 API 키 UI 가능 (ExposedDropdownMenuBox, Switch 패턴 확립)
- `SettingsViewModel` + `UserPreferencesRepository` — DataStore 읽기/쓰기 패턴 확립
- `GeminiEngine.generate()` — API 키 주입 지점 명확 (line 110: `val apiKey = BuildConfig.GEMINI_API_KEY`)

### Established Patterns
- DataStore + Flow + collectAsStateWithLifecycle — 설정 저장/표시 패턴
- Hilt @Inject constructor — DI 패턴
- MeetingEntity ↔ Meeting 매핑 — Entity/Domain 분리 패턴
- OutlinedTextField — 텍스트 입력 패턴 (Phase 7)

### Integration Points
- `MeetingRepositoryImpl.deleteMeeting()` — 파일 삭제 로직 추가 지점
- `GeminiEngine` 생성자 — API 키 주입 파라미터 추가 지점
- `SettingsScreen` Column — API 키 섹션 추가 지점
- `AppDatabase` — version 증가 + Migration 추가 지점
- `DatabaseModule` — Migration 객체 제공 지점

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

*Phase: 08-foundation*
*Context gathered: 2026-03-25*
