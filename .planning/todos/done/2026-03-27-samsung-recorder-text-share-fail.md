---
created: "2026-03-27T01:08:37.855Z"
updated: "2026-03-27T01:20:00.000Z"
title: 삼성 녹음기에서 공유하는 텍스트 파일이 저장되지 않는다
area: audio
files: []
---

## Problem

삼성 녹음기 앱에서 전사된 텍스트 파일을 공유(Share Intent)로 보내면, 앱에서 수신은 되지만 텍스트 파일이 저장되지 않는다.
Phase 9에서 삼성 공유 수신 기능을 구현했으나, 텍스트 파일 저장 경로에서 문제 발생.
바이브코딩으로 다른 기능을 수정한 이후 발생한 것으로 추정.

## Solution

- ShareReceiverActivity에서 text/* MIME 타입 처리 확인
- Intent extras에서 텍스트 파일 URI 추출 로직 검증
- ContentResolver를 통한 파일 읽기/저장 과정 디버깅
- 삼성 녹음앱의 실제 공유 Intent 데이터 구조 확인 (logcat)
- 파일 저장 경로 및 권한 확인
