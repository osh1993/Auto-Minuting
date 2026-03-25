# Phase 10: NotebookLM 반자동 연동 - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

회의록을 NotebookLM에 전달하는 3가지 경로를 구현한다: (1) NotebookLM 앱으로 직접 공유 Intent, (2) Custom Tabs로 NotebookLM 웹 열기, (3) MCP 서버 API를 통한 노트북 생성/소스 추가 실동작 통합 + 검토 문서.

</domain>

<decisions>
## Implementation Decisions

### NotebookLM 공유 (NLMK-01)
- **D-01:** 기존 MinutesDetailScreen의 Share 버튼(ACTION_SEND)은 유지 — NotebookLM 앱이 설치되어 있으면 공유 대상에 자동 표시
- **D-02:** 별도 NotebookLM 전용 버튼을 MinutesDetailScreen TopAppBar에 추가 — NotebookLM 아이콘(또는 텍스트 버튼)
- **D-03:** 전용 버튼 클릭 시 NotebookLM 앱(com.google.android.apps.notebooklm)이 설치되어 있으면 직접 Intent 전송
- **D-04:** NotebookLM 앱 미설치 시 Custom Tabs로 notebooklm.google.com 열기 (폴백)

### Custom Tabs (NLMK-02)
- **D-05:** 회의록 상세화면에서 NotebookLM 전용 버튼의 폴백으로 Custom Tabs 사용
- **D-06:** 설정화면에 "NotebookLM 열기" 링크 추가 — Custom Tabs로 notebooklm.google.com 열기
- **D-07:** androidx.browser (Custom Tabs) 의존성 추가

### MCP 서버 통합 (NLMK-03)
- **D-08:** MCP 서버 API를 통한 노트북 생성/소스 추가 실동작 통합 구현
- **D-09:** 검토 문서(MCP-REVIEW.md) 작성 — API 가능성, 제약사항, 앱 내 통합 방안 정리
- **D-10:** 타임박스 초과 시 검토 문서만으로 NLMK-03 충족 (성공 기준이 "검토 문서로 정리")

### Claude's Discretion
- NotebookLM 전용 버튼의 아이콘/디자인 (Material 3 스타일 준수)
- Custom Tabs 색상 테마 (앱 Primary Color 사용 권장)
- MCP 통합 시 에러 처리 및 인증 방식
- 공유 시 회의록 텍스트 형식 (Markdown vs plain text)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 공유 기능
- `app/src/main/java/com/autominuting/presentation/minutes/MinutesDetailScreen.kt` — 기존 Share 버튼, TopAppBar 구조
- `app/src/main/java/com/autominuting/receiver/PipelineActionReceiver.kt` — ACTION_SEND Intent 생성 패턴
- `app/src/main/java/com/autominuting/service/PipelineNotificationHelper.kt` — 공유 Intent 생성 패턴

### 설정 화면
- `app/src/main/java/com/autominuting/presentation/settings/SettingsScreen.kt` — 설정 섹션 추가 패턴
- `app/src/main/java/com/autominuting/presentation/settings/SettingsViewModel.kt` — 설정 상태 관리

### MCP 서버
- NotebookLM MCP 서버가 이미 Claude Code에 설정되어 있음 — source_add, notebook_create, note_create API 사용 가능

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MinutesDetailScreen` Share 버튼 — ACTION_SEND Intent 패턴 확립
- `PipelineActionReceiver` — Intent.createChooser 패턴
- `SettingsScreen` — 설정 섹션 추가 패턴 (OutlinedTextField, Switch, 링크)

### Established Patterns
- TopAppBar IconButton — Share, Back 등 기존 패턴
- Hilt @AndroidEntryPoint / @HiltViewModel — DI 패턴
- Material 3 컴포넌트 — SuggestionChip, AlertDialog 등

### Integration Points
- `MinutesDetailScreen` TopAppBar — NotebookLM 전용 버튼 추가 지점
- `SettingsScreen` Column — NotebookLM 섹션 추가 지점
- `build.gradle.kts` — androidx.browser 의존성 추가 지점

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

*Phase: 10-notebooklm*
*Context gathered: 2026-03-26*
