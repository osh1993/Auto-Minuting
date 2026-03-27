---
status: awaiting_human_verify
trigger: "삼성 녹음기 앱에서 전사된 텍스트 파일을 공유(Share Intent)로 보내면, 앱에서 수신은 되지만 텍스트 파일이 저장되지 않는다"
created: 2026-03-27T00:00:00
updated: 2026-03-27T00:00:00
---

## Current Focus

hypothesis: contentResolver.getType(streamUri) 호출이 Samsung content provider에서 예외를 발생시켜 onCreate 전체가 크래시되고, 텍스트 처리 코드에 도달하지 못함
test: 코드 분석 + Android getType() 예외 발생 가능성 조사
expecting: getType() 호출이 try-catch 없이 호출되는 것 확인
next_action: fix 적용 -- getType()을 try-catch로 감싸기

## Symptoms

expected: 삼성 녹음기 앱에서 전사 텍스트 파일을 공유하면, Auto Minuting 앱이 수신하여 텍스트 파일을 로컬에 저장하고 전사 데이터로 등록
actual: 앱에서 공유 수신은 되지만 텍스트 파일이 저장되지 않는다
errors: 알 수 없음 (logcat 확인 필요)
reproduction: 삼성 녹음기 앱 -> 녹음 전사 -> 공유 -> Auto Minuting 선택
started: 바이브코딩으로 다른 기능을 수정한 이후 발생한 것으로 추정

## Eliminated

## Evidence

- timestamp: 2026-03-27T00:10:00
  checked: git diff 77f18b4..HEAD -- ShareReceiverActivity.kt
  found: ce175b2 커밋에서 음성 파일 공유 기능 추가 시, contentResolver.getType(streamUri) 호출이 onCreate 메인 경로에 추가됨 (line 65). 이전 코드에서는 getType()이 메인 경로에 없었음.
  implication: getType()이 Samsung content provider에서 예외를 발생시키면, try-catch 없이 호출되므로 onCreate 전체가 크래시됨

- timestamp: 2026-03-27T00:12:00
  checked: Android contentResolver.getType() 예외 발생 사례 조사
  found: getType()은 SecurityException, IllegalArgumentException 등을 throw할 수 있음 (GitHub issue opendatakit/collect#1362 등 다수 사례). 특히 외부 앱의 content:// URI에서 provider를 찾을 수 없을 때 발생.
  implication: Samsung 녹음앱의 content:// URI에서 getType() 호출 시 예외 발생 가능성 높음

- timestamp: 2026-03-27T00:13:00
  checked: 기존 코드(126fcda)와 현재 코드 비교
  found: 기존 코드에서는 getType()이 SEND_MULTIPLE 디버그 로깅에서만 사용됨. 현재 코드에서는 모든 EXTRA_STREAM이 있는 공유 intent에 대해 getType() 호출이 필수 경로에 있음.
  implication: ce175b2 커밋 이후에만 발생하는 버그. 기존 텍스트 공유가 정상 동작했던 이유 설명됨.

## Resolution

root_cause: ce175b2 커밋에서 추가된 contentResolver.getType(streamUri) 호출(line 65)이 try-catch 없이 호출됨. Samsung 녹음앱의 content:// URI에서 getType()이 SecurityException 등을 throw하면 onCreate 전체가 크래시되어, 텍스트 처리 코드에 도달하지 못하고 Activity가 종료됨.
fix: getType() 호출을 try-catch로 감싸서 예외 발생 시 null로 폴백. 추가로 intent.type을 보조 MIME type 판별 소스로 활용.
verification: 빌드 성공 확인. 실 기기 테스트 필요.
files_changed: [app/src/main/java/com/autominuting/presentation/share/ShareReceiverActivity.kt]
