package com.autominuting.data.stt

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 오디오 파일을 Whisper 입력 포맷(16kHz, mono, PCM WAV)으로 변환하는 유틸리티.
 *
 * Android MediaCodec/MediaExtractor를 사용하여 디코딩 후 리샘플링한다.
 * 이미 16kHz mono WAV 포맷이면 변환을 생략한다.
 */
@Singleton
class AudioConverter @Inject constructor() {

    companion object {
        private const val TAG = "AudioConverter"
        /** Whisper 입력 요구 사양 */
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
    }

    /**
     * 오디오 파일을 Whisper 입력 포맷(16kHz, mono, PCM WAV)으로 변환한다.
     *
     * @param inputPath 원본 오디오 파일 경로
     * @param outputDir 변환된 WAV 파일을 저장할 디렉토리
     * @return 변환된 WAV 파일의 절대 경로
     */
    suspend fun convertToWhisperFormat(
        inputPath: String,
        outputDir: String
    ): String = withContext(Dispatchers.IO) {
        val inputFile = File(inputPath)
        require(inputFile.exists()) { "입력 오디오 파일이 존재하지 않습니다: $inputPath" }

        // 이미 16kHz mono WAV인지 확인
        if (isAlreadyWhisperFormat(inputPath)) {
            Log.d(TAG, "이미 Whisper 포맷 WAV입니다, 변환 생략: $inputPath")
            return@withContext inputPath
        }

        val outputFile = File(outputDir, "${inputFile.nameWithoutExtension}_16k.wav")
        File(outputDir).mkdirs()

        Log.d(TAG, "오디오 변환 시작: $inputPath -> ${outputFile.absolutePath}")

        val pcmData = decodeAudioToPcm(inputPath)
        writeWavFile(outputFile, pcmData)

        Log.d(TAG, "오디오 변환 완료: ${outputFile.absolutePath}")
        outputFile.absolutePath
    }

    /**
     * WAV 헤더를 분석하여 이미 Whisper 포맷(16kHz, mono, PCM)인지 확인한다.
     */
    private fun isAlreadyWhisperFormat(filePath: String): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            if (extractor.trackCount == 0) {
                extractor.release()
                return false
            }

            val format = extractor.getTrackFormat(0)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)

            extractor.release()

            sampleRate == TARGET_SAMPLE_RATE &&
                channels == TARGET_CHANNELS &&
                mime == MediaFormat.MIMETYPE_AUDIO_RAW
        } catch (e: Exception) {
            Log.w(TAG, "포맷 확인 실패, 변환 진행: ${e.message}")
            false
        }
    }

    /**
     * MediaCodec/MediaExtractor로 오디오를 디코딩하여 16kHz mono PCM 데이터를 추출한다.
     */
    private fun decodeAudioToPcm(inputPath: String): ByteArray {
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

        require(audioTrackIndex >= 0) { "오디오 트랙을 찾을 수 없습니다: $inputPath" }

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("MIME 타입을 확인할 수 없습니다")
        val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        // ByteArrayOutputStream으로 PCM 청크를 효율적으로 합침 (O(n) 메모리)
        // 기존 fold + ByteArray 연결은 O(n^2) 복사가 발생하여 긴 오디오에서 OOM 유발
        val pcmStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEos = false

        while (!isEos) {
            // 입력 버퍼에 데이터 공급
            val inputIndex = codec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    codec.queueInputBuffer(
                        inputIndex, 0, 0, 0L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    isEos = true
                } else {
                    codec.queueInputBuffer(
                        inputIndex, 0, sampleSize,
                        extractor.sampleTime, 0
                    )
                    extractor.advance()
                }
            }

            // 출력 버퍼에서 PCM 데이터 추출
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                pcmStream.write(chunk)
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val rawPcm = pcmStream.toByteArray()

        // 리샘플링 및 모노 변환
        return resampleToTarget(rawPcm, sourceSampleRate, sourceChannels)
    }

    /**
     * PCM 데이터를 16kHz mono로 리샘플링한다.
     * 간단한 선형 보간법을 사용한다.
     */
    private fun resampleToTarget(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ByteArray {
        val shortBuffer = ByteBuffer.wrap(pcmData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val sampleCount = shortBuffer.remaining()
        val samples = ShortArray(sampleCount)
        shortBuffer.get(samples)

        // 모노로 변환 (다채널이면 평균)
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
     * PCM 데이터를 WAV 파일로 작성한다.
     */
    private fun writeWavFile(outputFile: File, pcmData: ByteArray) {
        val byteRate = TARGET_SAMPLE_RATE * TARGET_CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = TARGET_CHANNELS * BITS_PER_SAMPLE / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                // RIFF 헤더
                put("RIFF".toByteArray())
                putInt(fileSize)
                put("WAVE".toByteArray())
                // fmt 청크
                put("fmt ".toByteArray())
                putInt(16) // fmt 청크 크기
                putShort(1) // PCM 포맷
                putShort(TARGET_CHANNELS.toShort())
                putInt(TARGET_SAMPLE_RATE)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(BITS_PER_SAMPLE.toShort())
                // data 청크
                put("data".toByteArray())
                putInt(dataSize)
            }
            fos.write(header.array())
            fos.write(pcmData)
        }
    }
}
