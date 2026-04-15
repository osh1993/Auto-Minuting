---
phase: 51
slug: gemini-api-ui
status: draft
shadcn_initialized: false
preset: none
created: 2026-04-15
---

# Phase 51 — UI Design Contract

> Gemini 다중 API 키 설정 화면의 시각·상호작용 계약. gsd-ui-researcher 작성, gsd-ui-checker 검증.
> 플랫폼: Android Native (Jetpack Compose + Material 3) — shadcn 비적용.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (Android Native) |
| Preset | not applicable |
| Component library | Material 3 (`androidx.compose.material3:material3:1.4.0`) |
| Icon library | `androidx.compose.material.icons.Icons.Default` (Material Icons) |
| Font | `FontFamily.Default` (시스템 기본 — 한국어 최적화) |

Notes:
- 기존 `app/src/main/java/com/autominuting/presentation/theme/{Color,Type,Theme}.kt` 토큰을 재사용한다.
- 새 디자인 토큰을 추가하지 않는다 — Material 3 `MaterialTheme.colorScheme` / `MaterialTheme.typography` 경유 접근만 허용.

---

## Spacing Scale

Jetpack Compose의 `.dp` 단위로 선언 (4 배수 규칙 준수):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4.dp | 보조 텍스트와 본문 간 간격, 아이콘·텍스트 미세 간격 |
| sm | 8.dp | 입력 필드–버튼 사이, 리스트 행 내부 요소 간격 |
| md | 16.dp | 섹션 내 기본 요소 간격, 카드 내부 패딩 |
| lg | 24.dp | 섹션 간 구분 간격, 화면 좌우 padding |
| xl | 32.dp | 최상위 섹션 분리 (필요 시) |

Exceptions:
- 삭제 아이콘 버튼 터치 타겟 48.dp (Material Touch Target 최소치) — 시각 아이콘은 24.dp, 주변 padding 12.dp.
- `CircularProgressIndicator` 인디케이터 사이즈 16.dp (검증 중 버튼 내부 표시).

---

## Typography

Material 3 `Typography` 스타일만 사용. 새 스타일 추가 금지.

| Role | Token | Size | Weight | Line Height |
|------|-------|------|--------|-------------|
| Section title | `titleMedium` | 16.sp | Medium (500) | 24.sp |
| List row label (별명) | `bodyLarge` | 16.sp | Normal (400) | 24.sp |
| Masked key value | `bodyMedium` (monospace) | 14.sp | Normal (400) | 20.sp |
| Helper / error text | `bodySmall` | 12.sp | Normal (400) | 16.sp |
| Button label | `labelLarge` | 14.sp | Medium (500) | 20.sp |

Notes:
- 마스킹 키 `AIza****WXYZ`는 `FontFamily.Monospace`로 표시 (가독성·정렬).
- 기타 본문 한국어는 `FontFamily.Default` 유지.
- 무게는 총 2단계만 사용: Normal(400) + Medium(500).

---

## Color

Material 3 `colorScheme` 토큰만 사용. Hex 직접 참조 금지.

| Role | Token | Usage |
|------|-------|-------|
| Dominant (60%) | `colorScheme.background` / `colorScheme.surface` | 설정 화면 배경, 섹션 배경 |
| Secondary (30%) | `colorScheme.surfaceVariant` / `colorScheme.secondaryContainer` | 키 목록 행 배경 (Card), 헬퍼 텍스트 surface |
| Accent (10%) | `colorScheme.primary` | "새 키 추가" / "검증 후 추가" 버튼, 검증 성공 메시지 |
| Destructive | `colorScheme.error` | 삭제 확인 다이얼로그 `확인` 버튼, 검증 실패 오류 메시지, 🗑 아이콘 tint |

Accent reserved for:
- Primary CTA 버튼 `검증 후 추가` (Filled Button)
- Primary CTA 버튼 `새 키 추가` (FilledTonalButton 또는 TextButton with leading Icons.Default.Add)
- 검증 성공 안내 텍스트 `"'{별명}' 키가 추가되었습니다"` (primary color)

Accent 사용 금지 영역:
- 키 목록 행 별명·마스킹 텍스트 (기본 onSurface)
- 헬퍼 설명 텍스트 (onSurfaceVariant)
- 삭제 아이콘 버튼 tint (error — destructive 전용 색)

Destructive 사용 규칙:
- 🗑 아이콘 tint, 삭제 확인 다이얼로그 확인 버튼, API 검증 실패 메시지 **오직 이 3곳**.

---

## Component Inventory

Phase 51에서 사용할 Material 3 컴포넌트 (모두 기존 코드에 사용됨):

| 용도 | Material 3 Composable |
|------|------------------------|
| 키 목록 컨테이너 | `LazyColumn` (SettingsScreen 내부, 최대 높이 제한 없음 — 화면 스크롤 사용) |
| 키 행(Row) | `Card` (Outlined variant) + `Row` |
| 별명 + 키 입력 | `OutlinedTextField` × 2 |
| 키 값 마스킹 토글 | `IconButton` + `Icons.Default.Visibility / VisibilityOff` |
| 검증 후 추가 버튼 | `Button` (filled) + `CircularProgressIndicator(16.dp)` inline |
| 새 키 추가 버튼 | `FilledTonalButton` with leading `Icons.Default.Add` |
| 삭제 아이콘 버튼 | `IconButton` + `Icons.Default.Delete` |
| 삭제 확인 | `AlertDialog` |
| 빈 상태 | 중앙 정렬 `Column` + `bodyMedium` 텍스트 |
| 검증 진행 표시 | 버튼 내부 `CircularProgressIndicator` (16.dp, strokeWidth=2.dp) |

---

## Interaction Contract

### 목록 뷰 (default)

- 섹션 타이틀 `Gemini API 키`
- 헬퍼 텍스트 (bodySmall, onSurfaceVariant): `등록된 키는 회의록 생성 시 순환 사용됩니다`
- 키가 0개일 때: 빈 상태 컴포넌트 노출
- 키가 1개 이상일 때: 각 키를 카드 행(Card)으로 표시
  - 좌: 별명 (`bodyLarge`) + 줄바꿈 + 마스킹 키 (`bodyMedium` monospace, onSurfaceVariant)
  - 우: 🗑 `IconButton` (tint = error)
  - 카드 내부 padding: 16.dp, 카드 간 vertical spacing 8.dp
- 목록 하단: `FilledTonalButton` "새 키 추가" (leading `Icons.Default.Add`) — 목록 폭 full width, top margin 16.dp

### 추가 플로우 (inline expand, not dialog)

"새 키 추가" 버튼을 탭하면 버튼이 폼으로 확장 전환:

1. `OutlinedTextField` — 라벨 "별명" (예: 회사용, 개인용), singleLine=true
2. 상단 간격 8.dp
3. `OutlinedTextField` — 라벨 "Gemini API 키", `PasswordVisualTransformation` 기본, trailingIcon = Visibility toggle
4. 상단 간격 8.dp
5. `Row` — `Button("검증 후 추가")` + `TextButton("취소")` (horizontalArrangement spacedBy 8.dp)

검증 상태 전이:
- Idle → 버튼 `검증 후 추가` 활성 (두 필드 모두 비어있지 않을 때)
- Validating → 버튼 비활성 + 내부 `CircularProgressIndicator`, 두 필드 readOnly
- Success → 목록에 새 항목 append, 폼 닫힘(버튼으로 복귀), 스낵바 or inline success text
- Error → 폼 유지, 필드 아래 `bodySmall` error 색으로 메시지 표시, 두 필드 편집 가능

### 삭제 플로우

🗑 아이콘 탭 → `AlertDialog`:
- 제목: `API 키 삭제`
- 본문: `"{별명}" 키를 삭제할까요? 이 작업은 되돌릴 수 없습니다.`
- 확인 버튼 (TextButton, text color = error): `삭제`
- 취소 버튼 (TextButton): `취소`
- 확인 시 `removeGeminiApiKey(index)` 호출, 인덱스 재정렬됨.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Section title | `Gemini API 키` |
| Section helper | `등록된 키는 회의록 생성 시 순환 사용됩니다` |
| Empty state heading | `등록된 Gemini API 키가 없습니다` |
| Empty state body | `아래 '새 키 추가' 버튼으로 첫 API 키를 등록하세요. aistudio.google.com에서 발급받을 수 있습니다.` |
| Primary CTA (list) | `새 키 추가` |
| Primary CTA (form) | `검증 후 추가` |
| Secondary CTA (form) | `취소` |
| Label field placeholder | `별명 (예: 회사용, 개인용)` |
| Key field placeholder | `Gemini API 키` |
| Validating state (button) | `검증 중...` (CircularProgressIndicator 동반) |
| Validation success | `'{별명}' 키가 추가되었습니다` |
| Validation error — invalid | `API 키 검증에 실패했습니다. 키 값을 다시 확인해주세요.` |
| Validation error — network | `네트워크 오류로 검증하지 못했습니다. 연결 상태를 확인 후 다시 시도하세요.` |
| Validation error — duplicate | `이미 등록된 API 키입니다.` |
| Destructive confirmation title | `API 키 삭제` |
| Destructive confirmation body | `"{별명}" 키를 삭제할까요? 이 작업은 되돌릴 수 없습니다.` |
| Destructive confirm button | `삭제` |
| Destructive cancel button | `취소` |
| Key masking format | `AIza****WXYZ` (앞 4자 + `****` + 뒤 4자) |

Tone 규칙:
- 존댓말 + 간결체 (기존 설정 화면 톤 일치).
- 구체 행동 동사 사용 (`추가`, `삭제`, `검증`) — `확인`, `OK` 금지.

---

## Accessibility

| 요구 | 구현 |
|------|------|
| 터치 타겟 최소 크기 | 삭제 `IconButton` 48.dp × 48.dp |
| `contentDescription` | 🗑 아이콘 = `"'{별명}' 키 삭제"`, Visibility 아이콘 = `"키 보기" / "키 숨기기"` |
| 색 대비 | Material 3 `colorScheme.error` / `primary`는 기본 4.5:1 이상 보장 — 커스텀 색 추가 금지 |
| 키 입력 기본 마스킹 | `PasswordVisualTransformation` 기본 적용 — 어깨너머 노출 방지 |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| 해당 없음 (Android Native) | 해당 없음 | 해당 없음 |

이 프로젝트는 shadcn/React 생태계가 아니므로 레지스트리 안전성 게이트는 적용되지 않는다.
모든 UI는 `androidx.compose.material3` 공식 라이브러리 내 컴포넌트만 사용한다.

---

## Design Decisions — Source Trace

| Decision | Source |
|----------|--------|
| LazyColumn + Card 행 패턴 | 51-CONTEXT.md §1 |
| 별명 + 키 값 2필드 추가 폼 | 51-CONTEXT.md §2 |
| 검증 후 추가 버튼 레이블 | 51-CONTEXT.md §3 (버튼 레이블 변경 명시) |
| 마스킹 포맷 `AIza****WXYZ` | 51-CONTEXT.md §1 |
| Material 3 colorScheme / Typography 재사용 | 기존 `theme/Color.kt`, `theme/Type.kt` |
| 삭제 확인 다이얼로그 | UI 연구자 판단 (destructive action guardrail) |
| 인라인 확장 폼 (다이얼로그 대신) | UI 연구자 판단 (키 입력 중 라벨 문맥 유지) |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS (N/A — Android Native)

**Approval:** pending
