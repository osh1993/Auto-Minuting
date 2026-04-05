package com.autominuting.util

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 파일 헤더 정보.
 *
 * @param audioFormat 오디오 포맷 (1 = PCM)
 * @param numChannels 채널 수
 * @param sampleRate 샘플레이트 (Hz)
 * @param byteRate 바이트레이트 (= sampleRate * numChannels * bitsPerSample / 8)
 * @param blockAlign 블록 정렬 (= numChannels * bitsPerSample / 8)
 * @param bitsPerSample 비트 뎁스
 * @param dataSize data 청크의 PCM 데이터 크기 (바이트)
 * @param dataOffset 파일 시작부터 PCM 데이터 시작점까지의 오프셋 (바이트)
 */
data class WavHeader(
    val audioFormat: Short,
    val numChannels: Short,
    val sampleRate: Int,
    val byteRate: Int,
    val blockAlign: Short,
    val bitsPerSample: Short,
    val dataSize: Int,
    val dataOffset: Int
)

/**
 * 여러 WAV 파일의 PCM 데이터를 하나로 이어붙이는 유틸리티.
 *
 * 동일 포맷(샘플레이트, 채널 수, 비트 뎁스)의 WAV 파일만 합칠 수 있다.
 * 합쳐진 파일의 헤더는 첫 번째 파일의 fmt 정보를 기준으로 재계산된다.
 */
object WavMerger {

    /**
     * 여러 WAV InputStream을 하나의 WAV 파일로 합친다.
     *
     * @param inputStreams 합칠 WAV 파일들의 InputStream 리스트 (순서대로 이어붙임)
     * @param outputFile 합쳐진 결과 WAV 파일
     * @throws IllegalArgumentException WAV 형식이 아니거나 fmt 정보가 불일치할 때
     * @throws java.io.IOException 파일 읽기/쓰기 실패 시
     */
    fun merge(inputStreams: List<InputStream>, outputFile: File) {
        require(inputStreams.isNotEmpty()) { "합칠 파일이 없습니다" }

        // 1. 모든 파일의 헤더 파싱
        val headers = inputStreams.map { parseWavHeader(it) }
        val first = headers.first()

        // 2. fmt 일치 검증 (첫 번째 파일 기준)
        headers.drop(1).forEachIndexed { index, h ->
            require(
                h.sampleRate == first.sampleRate
                    && h.numChannels == first.numChannels
                    && h.bitsPerSample == first.bitsPerSample
            ) {
                "파일 ${index + 2}의 오디오 포맷이 첫 번째 파일과 다릅니다 " +
                    "(sampleRate=${h.sampleRate}/${first.sampleRate}, " +
                    "channels=${h.numChannels}/${first.numChannels}, " +
                    "bits=${h.bitsPerSample}/${first.bitsPerSample})"
            }
        }

        // 3. 총 PCM 데이터 크기 계산
        val totalDataSize = headers.sumOf { it.dataSize.toLong() }

        // 4. 합친 WAV 파일 작성
        outputFile.outputStream().buffered().use { out ->
            // WAV 헤더 작성 (44바이트 표준)
            val headerBuf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            headerBuf.put("RIFF".toByteArray())
            headerBuf.putInt((36 + totalDataSize).toInt()) // ChunkSize
            headerBuf.put("WAVE".toByteArray())
            headerBuf.put("fmt ".toByteArray())
            headerBuf.putInt(16) // SubChunk1Size (PCM)
            headerBuf.putShort(first.audioFormat)
            headerBuf.putShort(first.numChannels)
            headerBuf.putInt(first.sampleRate)
            headerBuf.putInt(first.byteRate)
            headerBuf.putShort(first.blockAlign)
            headerBuf.putShort(first.bitsPerSample)
            headerBuf.put("data".toByteArray())
            headerBuf.putInt(totalDataSize.toInt()) // SubChunk2Size
            out.write(headerBuf.array())

            // 5. 각 파일의 PCM 데이터 순차 기록
            //    parseWavHeader()에서 이미 헤더를 소비했으므로
            //    dataOffset > 44인 경우 추가 skip 필요
            inputStreams.forEachIndexed { index, stream ->
                val header = headers[index]
                // 이미 44바이트는 parseWavHeader에서 소비됨
                // dataOffset이 44보다 크면 추가 바이트를 건너뛴다 (비표준 청크 대응)
                val remaining = header.dataOffset - 44
                if (remaining > 0) {
                    stream.skip(remaining.toLong())
                }
                // PCM 데이터만 정확히 dataSize만큼 복사
                var bytesRemaining = header.dataSize.toLong()
                val buffer = ByteArray(8192)
                while (bytesRemaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                    val read = stream.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    bytesRemaining -= read
                }
            }
        }
    }

    /**
     * InputStream에서 WAV 헤더를 파싱한다.
     * 호출 후 InputStream 위치는 44바이트 이후로 이동한다.
     *
     * @param inputStream WAV 파일의 InputStream
     * @return 파싱된 WavHeader
     * @throws IllegalArgumentException RIFF/WAVE 매직 넘버가 없거나 헤더가 부족할 때
     */
    fun parseWavHeader(inputStream: InputStream): WavHeader {
        // 최소 44바이트 읽기
        val headerBytes = ByteArray(44)
        var totalRead = 0
        while (totalRead < 44) {
            val read = inputStream.read(headerBytes, totalRead, 44 - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        require(totalRead >= 44) { "WAV 헤더를 읽을 수 없습니다 (읽은 바이트: $totalRead)" }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF 매직 넘버 검증
        val riff = String(headerBytes, 0, 4)
        require(riff == "RIFF") { "WAV 파일이 아닙니다: RIFF 헤더 없음 ($riff)" }

        // WAVE 매직 넘버 검증
        val wave = String(headerBytes, 8, 4)
        require(wave == "WAVE") { "WAVE 형식이 아닙니다: $wave" }

        // fmt 청크 파싱
        val audioFormat = buffer.getShort(20)
        val numChannels = buffer.getShort(22)
        val sampleRate = buffer.getInt(24)
        val byteRate = buffer.getInt(28)
        val blockAlign = buffer.getShort(32)
        val bitsPerSample = buffer.getShort(34)

        // data 청크 찾기 — 36바이트 오프셋에서 "data" 검색
        // 비표준 청크가 있을 수 있으므로 순차 검색 (Pitfall 4)
        var dataOffset = 36
        var dataSize = 0

        val dataId = String(headerBytes, 36, 4)
        if (dataId == "data") {
            // 표준 44바이트 헤더
            dataSize = buffer.getInt(40)
            dataOffset = 44
        } else {
            // 비표준 청크가 있는 경우: 추가 바이트를 읽어서 "data" 순차 검색
            // 이미 읽은 44바이트 이후부터 검색
            val searchBuf = ByteArray(1)
            val marker = "data".toByteArray()
            var matchIndex = 0
            var currentOffset = 44

            while (matchIndex < 4) {
                val r = inputStream.read(searchBuf)
                if (r <= 0) break
                if (searchBuf[0] == marker[matchIndex]) {
                    matchIndex++
                } else {
                    matchIndex = 0
                }
                currentOffset++
            }

            if (matchIndex == 4) {
                // "data" 다음 4바이트가 dataSize
                val sizeBuf = ByteArray(4)
                var sizeRead = 0
                while (sizeRead < 4) {
                    val r = inputStream.read(sizeBuf, sizeRead, 4 - sizeRead)
                    if (r <= 0) break
                    sizeRead += r
                }
                dataSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt()
                dataOffset = currentOffset + 4 // "data"(이미 포함) + 4바이트 size
            }
        }

        return WavHeader(
            audioFormat = audioFormat,
            numChannels = numChannels,
            sampleRate = sampleRate,
            byteRate = byteRate,
            blockAlign = blockAlign,
            bitsPerSample = bitsPerSample,
            dataSize = dataSize,
            dataOffset = dataOffset
        )
    }
}
