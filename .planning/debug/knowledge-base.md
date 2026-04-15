# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## audiochunker-oom-streaming-decode — AudioChunker.split() 대용량 파일 OOM
- **Date:** 2026-04-15
- **Error patterns:** OutOfMemoryError, Failed to allocate, decodeAudioToPcm, AudioChunker, split, ByteArray, OOM, 536870928, M4A, 65MB
- **Root cause:** AudioChunker.split()이 AudioConverter.decodeAudioToPcm()으로 파일 전체 PCM을 단일 ByteArray로 로드. 65MB M4A → ~537MB 할당 시도 → 힙 한도(512MB) 초과 OOM.
- **Fix:** AudioChunker.split()을 MediaExtractor + MediaCodec 직접 사용 스트리밍 방식으로 재구현. 청크 단위(최대 19.2MB) ByteArrayOutputStream에 누적 후 WAV 플러시. 오버랩은 원시 PCM 마지막 320,000 bytes를 다음 청크 시작에 prepend.
- **Files changed:** app/src/main/java/com/autominuting/util/AudioChunker.kt
---

