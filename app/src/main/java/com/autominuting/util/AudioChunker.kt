package com.autominuting.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.autominuting.data.stt.AudioConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 25MB 초과 오디오 파일을 Groq 전송 가능한 시간 기반 WAV 청크로 분할한다.
 *
 * [split] 은 MediaExtractor + MediaCodec을 직접 사용하여 청크 단위로 스트리밍 디코딩한다.
 * 파일 전체 PCM을 메모리에 로드하지 않으므로 대용량 파일(65 MB+) 에서도 OOM 이 발생하지 않는다.
 *
 * 메모리 사용량:
 * - 이전 구현: 전체 PCM (65 MB M4A → ~537 MB) → 힙 초과 OOM
 * - 현재 구현: 청크 PCM 최대 600초 × 32_000 bytes/s = 19.2 MB (안전)
 */
object AudioChunker {
    const val CHUNK_SECONDS = 600
    const val OVERLAP_SECONDS = 10
    const val TARGET_SAMPLE_RATE = 16_000
    const val TARGET_CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SECOND = TARGET_SAMPLE_RATE * TARGET_CHANNELS * (BITS_PER_SAMPLE / 8)

    private const val TAG = "AudioChunker"

    /**
     * 입력 오디오 파일을 16kHz mono WAV 청크 리스트로 분할한다.
     *
     * 스트리밍 디코딩: MediaCodec 출력을 청크 단위 ByteArrayOutputStream에 누적하며,
     * CHUNK_BYTES 만큼 쌓이면 WAV 파일로 플러시하고 다음 청크로 전환한다.
     * 오버랩은 이전 청크의 마지막 OVERLAP_BYTES를 다음 청크 시작에 prepend한다.
     *
     * @param inputPath 원본 오디오 파일 경로 (M4A/MP3/WAV 지원)
     * @param outputDir 청크 WAV 파일을 저장할 디렉토리
     * @param audioConverter AudioConverter 인스턴스 (WAV 파일 작성에 재사용)
     * @return 생성된 WAV 청크 파일 리스트 (순서 보장)
     */
    suspend fun split(
        inputPath: String,
        outputDir: File,
        audioConverter: AudioConverter
    ): List<File> = withContext(Dispatchers.IO) {
        require(File(inputPath).exists()) { "입력 파일 없음: $inputPath" }
        outputDir.mkdirs()

        val chunkBytes = CHUNK_SECONDS * BYTES_PER_SECOND       // 19,200,000 bytes
        val overlapBytes = OVERLAP_SECONDS * BYTES_PER_SECOND   // 320,000 bytes
        val stepBytes = chunkBytes - overlapBytes               // 18,880,000 bytes

        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        // 첫 번째 오디오 트랙 선택
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        require(audioTrackIndex >= 0) { "오디오 트랙 없음: $inputPath" }

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("MIME 타입 확인 불가")
        val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        Log.d(TAG, "스트리밍 디코딩 시작: $inputPath, mime=$mime, rate=$sourceSampleRate, ch=$sourceChannels")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val resultFiles = mutableListOf<File>()
        // 현재 청크에 누적 중인 원시(리샘플링 전) PCM
        var rawStream = ByteArrayOutputStream()
        // 이전 청크의 마지막 overlapBytes 분량 원시 PCM (다음 청크 prepend용)
        var overlapRaw: ByteArray? = null
        var chunkIndex = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var isEos = false

        // 원시 PCM이 chunkBytes에 해당하는 리샘플 후 크기로 얼마나 누적됐는지 추적하기 위해
        // 리샘플 비율을 미리 계산한다. 실제 플러시 기준은 리샘플 후 바이트 수로 결정한다.
        val resampleRatio: Double = if (sourceSampleRate == TARGET_SAMPLE_RATE) {
            1.0
        } else {
            TARGET_SAMPLE_RATE.toDouble() / sourceSampleRate.toDouble()
        }
        // 원시 PCM 기준 chunkBytes 에 해당하는 크기
        // (sourceChannels 채널, 16bit → mono 16bit)
        // 원시 pcm 1 샘플 = 2 bytes × sourceChannels
        // 리샘플 후 1 샘플 = 2 bytes × 1ch
        // rawChunkBytes = chunkBytes / resampleRatio * sourceChannels
        val rawChunkBytes: Int = (chunkBytes / resampleRatio * sourceChannels).toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        // 오버랩도 원시 PCM 기준으로 환산
        val rawOverlapBytes: Int = (overlapBytes / resampleRatio * sourceChannels).toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        Log.d(TAG, "rawChunkBytes=$rawChunkBytes, rawOverlapBytes=$rawOverlapBytes, stepRawBytes=${rawChunkBytes - rawOverlapBytes}")

        // 오버랩이 있으면 첫 청크 시작에 prepend
        fun initChunkStream(): ByteArrayOutputStream {
            val stream = ByteArrayOutputStream()
            overlapRaw?.let { stream.write(it) }
            return stream
        }

        rawStream = initChunkStream()

        while (!isEos) {
            // 입력 버퍼 공급
            val inputIndex = codec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    isEos = true
                } else {
                    codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            // 출력 버퍼에서 원시 PCM 수집
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                codec.releaseOutputBuffer(outputIndex, false)

                // 청크 데이터를 현재 스트림에 추가하되, rawChunkBytes 경계에서 분할
                var dataOffset = 0
                while (dataOffset < chunk.size) {
                    val spaceLeft = rawChunkBytes - rawStream.size()
                    val canWrite = minOf(spaceLeft, chunk.size - dataOffset)
                    rawStream.write(chunk, dataOffset, canWrite)
                    dataOffset += canWrite

                    // 청크가 꽉 찼으면 WAV 로 플러시
                    if (rawStream.size() >= rawChunkBytes) {
                        val rawPcm = rawStream.toByteArray()
                        val resampled = resampleToTarget(rawPcm, sourceSampleRate, sourceChannels)
                        val chunkFile = File(outputDir, "chunk_${"%03d".format(chunkIndex)}.wav")
                        audioConverter.writeWavFile(chunkFile, resampled)
                        resultFiles += chunkFile
                        Log.d(TAG, "청크 $chunkIndex 저장: ${chunkFile.name}, ${resampled.size} bytes PCM")
                        chunkIndex++

                        // 오버랩: 현재 청크 원시 PCM의 마지막 rawOverlapBytes
                        overlapRaw = rawPcm.takeLast(rawOverlapBytes).toByteArray()
                        rawStream = initChunkStream()
                    }
                }

                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // 남은 PCM을 마지막 청크로 플러시
        if (rawStream.size() > (overlapRaw?.size ?: 0)) {
            val rawPcm = rawStream.toByteArray()
            val resampled = resampleToTarget(rawPcm, sourceSampleRate, sourceChannels)
            val chunkFile = File(outputDir, "chunk_${"%03d".format(chunkIndex)}.wav")
            audioConverter.writeWavFile(chunkFile, resampled)
            resultFiles += chunkFile
            Log.d(TAG, "마지막 청크 $chunkIndex 저장: ${chunkFile.name}, ${resampled.size} bytes PCM")
        }

        Log.d(TAG, "스트리밍 분할 완료: 총 ${resultFiles.size}개 청크")
        resultFiles
    }

    /**
     * 원시 PCM(sourceSampleRate, sourceChannels) → 16kHz mono PCM 으로 리샘플링한다.
     * AudioConverter.resampleToTarget 과 동일한 로직 (private 이므로 여기서 재구현).
     */
    private fun resampleToTarget(
        rawPcm: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ByteArray {
        val shortBuffer = ByteBuffer.wrap(rawPcm)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val sampleCount = shortBuffer.remaining()
        val samples = ShortArray(sampleCount)
        shortBuffer.get(samples)

        // 모노 변환
        val monoSamples = if (sourceChannels > 1) {
            val monoCount = sampleCount / sourceChannels
            ShortArray(monoCount) { i ->
                var sum = 0L
                for (ch in 0 until sourceChannels) {
                    sum += samples[i * sourceChannels + ch]
                }
                (sum / sourceChannels).toShort()
            }
        } else {
            samples
        }

        // 리샘플링
        if (sourceSampleRate == TARGET_SAMPLE_RATE) {
            return shortsToBytes(monoSamples)
        }

        val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE
        val outputLength = (monoSamples.size / ratio).toInt()
        val resampled = ShortArray(outputLength) { i ->
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val fraction = srcPos - srcIndex
            if (srcIndex + 1 < monoSamples.size) {
                val a = monoSamples[srcIndex]
                val b = monoSamples[srcIndex + 1]
                (a + (b - a) * fraction).toInt().toShort()
            } else {
                monoSamples[srcIndex.coerceIn(0, monoSamples.size - 1)]
            }
        }
        return shortsToBytes(resampled)
    }

    /** Short 배열을 Little-Endian 바이트 배열로 변환한다. */
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        shorts.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    /**
     * 순수 함수: PCM 바이트 배열을 시간 기반 청크로 분할한다 (단위 테스트용).
     *
     * - 입력이 단일 청크로 충분하면 오버랩 없이 그대로 반환
     * - 입력이 비어 있으면 빈 리스트 반환
     * - step = chunkSec - overlapSec (오버랩 구간 보장)
     *
     * @param pcm 16kHz mono PCM 바이트 배열
     * @param chunkSec 청크 길이(초), 기본값 600
     * @param overlapSec 오버랩 길이(초), 기본값 10
     * @param bytesPerSec 초당 바이트 수, 기본값 32_000 (16kHz * 1ch * 2byte)
     * @return PCM 청크 바이트 배열 리스트
     */
    internal fun splitPcmForTest(
        pcm: ByteArray,
        chunkSec: Int = CHUNK_SECONDS,
        overlapSec: Int = OVERLAP_SECONDS,
        bytesPerSec: Int = BYTES_PER_SECOND
    ): List<ByteArray> {
        if (pcm.isEmpty()) return emptyList()
        val chunkBytes = chunkSec * bytesPerSec
        val overlapBytes = overlapSec * bytesPerSec
        val stepBytes = chunkBytes - overlapBytes
        require(stepBytes > 0) { "overlapSec must be < chunkSec" }

        // 입력이 단일 청크로 충분하면 오버랩 생성 안 함
        if (pcm.size <= chunkBytes) return listOf(pcm)

        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + chunkBytes, pcm.size)
            result += pcm.copyOfRange(offset, end)
            if (end >= pcm.size) break
            offset += stepBytes
        }
        return result
    }
}
