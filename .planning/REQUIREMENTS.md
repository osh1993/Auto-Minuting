# Requirements: v8.0 다중 파일 합치기

**Defined:** 2026-04-05
**Core Value:** 녹음에서 회의록까지의 전 과정을 자동화하여, 사용자가 수동 작업 없이 완성된 회의록을 받을 수 있어야 한다.

## Milestone Goal

Share Intent로 여러 오디오 파일을 받았을 때 첫 번째 파일명으로 하나로 합쳐 단일 파이프라인으로 처리한다.

---

## v8.0 Requirements

### 다중 파일 합치기

- [ ] **MERGE-01**: 사용자가 Share Intent로 여러 오디오 파일을 공유했을 때 앱이 자동으로 하나의 파일로 합쳐 처리한다
- [ ] **MERGE-02**: 합쳐진 파일의 파일명은 Intent로 전달된 첫 번째 파일의 파일명을 사용한다
- [ ] **MERGE-03**: 합쳐진 단일 파일이 기존 STT → 회의록 파이프라인에 그대로 전달된다

---

## Future Requirements

- 합치기 전 파일 순서를 사용자가 직접 조정할 수 있는 UI (v9.0 이후)
- 합치기 결과 미리보기 (파일 개수, 총 길이 표시)

## Out of Scope

- 다른 파일 형식 간 변환 합치기 (WAV + MP3 등 혼합 포맷) — 동일 포맷만 지원
- 실시간 스트리밍 중 합치기 — 파일 완료 후 처리

---

## Traceability

| REQ-ID   | Phase | Status  |
|----------|-------|---------|
| MERGE-01 | TBD   | Pending |
| MERGE-02 | TBD   | Pending |
| MERGE-03 | TBD   | Pending |
