---
status: awaiting_human_verify
trigger: "약 30분 정도 되는 음성 파일 전사가 실패하고, 짧은 음성 파일은 전사가 됨"
created: 2026-03-27T00:00:00Z
updated: 2026-03-27T00:00:00Z
---

## Current Focus

hypothesis: AudioConverter.decodeAudioToPcm()에서 PCM 청크를 ByteArray + ByteArray로 합치는 O(n^2) 메모리 복사가 30분 오디오(~300MB PCM)에서 OOM 유발
test: 코드 분석으로 확인 완료
expecting: ByteArrayOutputStream으로 교체하면 해결
next_action: ByteArrayOutputStream 기반 스트리밍 방식으로 수정

## Symptoms

expected: 30분짜리 음성 파일도 Whisper STT로 정상 전사되어야 한다
actual: 긴 음성 파일 전사 실패, 짧은 파일은 성공
errors: 구체적 에러 메시지 미확인 - OOM 또는 메모리 과다 사용으로 추정
reproduction: 삼성 녹음앱에서 30분+ 음성 파일을 앱으로 공유하면 전사 실패
started: Whisper STT 엔진 사용 시

## Eliminated

## Evidence

- timestamp: 2026-03-27T00:01:00Z
  checked: AudioConverter.kt line 168 - pcmChunks 합치기 로직
  found: `pcmChunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }` 사용 중. ByteArray + ByteArray는 매번 새 배열 할당+복사하므로 O(n^2) 메모리 소비. 30분 44.1kHz stereo 16bit PCM = ~317MB. fold 과정에서 누적 복사량은 수십GB 수준
  implication: 긴 오디오 파일에서 OOM 크래시의 직접적 원인

- timestamp: 2026-03-27T00:02:00Z
  checked: AudioConverter.kt resampleToTarget() - 리샘플링 로직
  found: 전체 PCM을 ShortArray로 변환 후 메모리에서 리샘플링. 30분 오디오의 경우 rawPcm(~317MB) + monoSamples ShortArray + resampled ShortArray + 최종 ByteArray까지 동시에 메모리 보유
  implication: fold 문제 해결 후에도 메모리 피크가 높을 수 있으나, O(n) 수준이면 모바일에서 처리 가능

- timestamp: 2026-03-27T00:03:00Z
  checked: WhisperEngine.kt - nativeTranscribe 호출
  found: whisper.cpp의 nativeTranscribe에 WAV 파일 경로를 전달. whisper.cpp 자체는 스트리밍으로 파일을 읽으므로 문제 없음
  implication: 병목은 AudioConverter의 PCM 합치기 단계

## Resolution

root_cause: AudioConverter.decodeAudioToPcm()에서 pcmChunks를 fold+ByteArray 연결로 합치는 O(n^2) 패턴이 30분 이상 오디오에서 OOM 유발. 30분 44.1kHz stereo 16bit 오디오 = ~317MB PCM이며, fold 과정에서 누적 메모리 할당이 기하급수적으로 증가.
fix: ByteArrayOutputStream 사용으로 O(n) 메모리 할당 패턴으로 변경. pcmChunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk } 를 ByteArrayOutputStream.write(chunk) + toByteArray()로 교체
verification: 빌드 성공 확인 (compileDebugKotlin BUILD SUCCESSFUL)
files_changed: [app/src/main/java/com/autominuting/data/stt/AudioConverter.kt]
