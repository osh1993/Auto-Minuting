package com.autominuting.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * 여러 오디오 파일(M4A/AAC, WAV 등)을 하나의 M4A 파일로 합치는 유틸리티.
 *
 * MediaExtractor + MediaMuxer를 사용하여 재인코딩 없이 오디오 트랙을 이어붙인다.
 * 첫 번째 파일의 포맷을 기준으로 출력 파일을 생성한다.
 */
object AudioMerger {

    private const val TAG = "AudioMerger"

    /**
     * 여러 오디오 파일 경로를 하나의 M4A 파일로 합친다.
     *
     * @param inputPaths 합칠 오디오 파일의 절대 경로 리스트 (순서대로 이어붙임)
     * @param outputFile 합쳐진 결과 M4A 파일
     * @throws IllegalArgumentException 오디오 트랙을 찾을 수 없거나 입력 리스트가 비어 있을 때
     * @throws java.io.IOException 파일 읽기/쓰기 실패 시
     */
    fun merge(inputPaths: List<String>, outputFile: File) {
        require(inputPaths.isNotEmpty()) { "합칠 파일이 없습니다" }

        outputFile.parentFile?.mkdirs()

        // 첫 번째 파일에서 출력 포맷 결정
        val firstExtractor = MediaExtractor()
        firstExtractor.setDataSource(inputPaths.first())
        val audioTrackIndex = findAudioTrack(firstExtractor)
        require(audioTrackIndex >= 0) { "오디오 트랙을 찾을 수 없습니다: ${inputPaths.first()}" }
        val outputFormat = firstExtractor.getTrackFormat(audioTrackIndex)
        firstExtractor.release()

        Log.d(TAG, "출력 포맷: $outputFormat")

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrackIndex = muxer.addTrack(outputFormat)
        muxer.start()

        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB 버퍼
        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeOffsetUs = 0L

        try {
            for (path in inputPaths) {
                val extractor = MediaExtractor()
                extractor.setDataSource(path)
                val trackIndex = findAudioTrack(extractor)
                if (trackIndex < 0) {
                    Log.w(TAG, "오디오 트랙 없음, 건너뜀: $path")
                    extractor.release()
                    continue
                }
                extractor.selectTrack(trackIndex)

                var maxPresentationTimeUs = 0L

                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.presentationTimeUs = extractor.sampleTime + presentationTimeOffsetUs

                    if (bufferInfo.presentationTimeUs > maxPresentationTimeUs) {
                        maxPresentationTimeUs = bufferInfo.presentationTimeUs
                    }

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                // 다음 파일의 타임스탬프 오프셋 = 현재 파일 마지막 타임스탬프 + 1프레임
                presentationTimeOffsetUs = maxPresentationTimeUs + 23220L // ~23ms (AAC 프레임)
                Log.d(TAG, "파일 처리 완료: $path, 다음 오프셋: ${presentationTimeOffsetUs}us")

                extractor.release()
            }
        } finally {
            muxer.stop()
            muxer.release()
        }

        Log.d(TAG, "합치기 완료: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
    }

    /**
     * MediaExtractor에서 첫 번째 오디오 트랙 인덱스를 반환한다.
     *
     * @return 오디오 트랙 인덱스, 없으면 -1
     */
    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }
}
