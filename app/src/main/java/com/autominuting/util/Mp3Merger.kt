package com.autominuting.util

import java.io.File

/**
 * MP3 프레임 헤더에서 추출한 오디오 포맷 정보.
 * frame concat 전 포맷 일치 검증용.
 */
data class Mp3FrameInfo(
    val mpegVersion: Int,      // 00=2.5, 01=reserved, 10=MPEG2, 11=MPEG1
    val layer: Int,            // 00=reserved, 01=Layer3, 10=Layer2, 11=Layer1
    val bitrateIdx: Int,       // 0..15 (11=reserved에 주의)
    val sampleRateIdx: Int,    // 0..3 (11=reserved)
    val channelMode: Int       // 00=Stereo, 01=JointStereo, 10=Dual, 11=Mono
)

/**
 * 여러 MP3 파일을 재인코딩 없이 하나의 MP3 파일로 합치는 유틸리티.
 *
 * - ID3v2 헤더는 첫 번째 파일의 것만 유지 (플레이어 메타데이터 호환).
 * - 이후 파일들의 ID3v2/ID3v1 태그는 제거하고 MP3 프레임 바이트만 concat.
 * - 모든 입력 파일의 첫 frame header에서 version/layer/bitrateIdx/sampleRateIdx/channelMode가 일치해야 한다.
 * - MediaMuxer/MediaCodec을 사용하지 않으므로 Android Framework 의존성 없음 (JVM 단위 테스트 가능).
 */
object Mp3Merger {

    /**
     * 여러 MP3 파일을 하나의 MP3 파일로 합친다 (재인코딩 없음).
     *
     * @param inputPaths 합칠 MP3 파일들의 절대 경로 (순서대로 이어붙임)
     * @param outputFile 합쳐진 결과 MP3 파일
     * @throws IllegalArgumentException MP3 frame header가 없거나 bitrate/sampleRate/channel이 불일치할 때
     * @throws java.io.IOException 파일 읽기/쓰기 실패 시
     */
    fun merge(inputPaths: List<String>, outputFile: File) {
        require(inputPaths.isNotEmpty()) { "합칠 MP3 파일이 없습니다" }

        // 1. 모든 파일 읽기 + 태그 경계 계산 + frame header 파싱
        val parsed = inputPaths.mapIndexed { idx, path ->
            val bytes = File(path).readBytes()
            val (audioStart, audioEnd) = stripTags(bytes)
            val frameInfo = parseFrameHeader(bytes, audioStart)
                ?: throw IllegalArgumentException(
                    "파일 ${idx + 1}에서 MP3 frame sync를 찾을 수 없습니다 (offset=$audioStart)"
                )
            Triple(bytes, audioStart to audioEnd, frameInfo)
        }

        // 2. frame header 일치 검증 (첫 파일 기준)
        val first = parsed.first().third
        parsed.drop(1).forEachIndexed { idx, (_, _, info) ->
            require(
                info.mpegVersion == first.mpegVersion &&
                    info.layer == first.layer &&
                    info.bitrateIdx == first.bitrateIdx &&
                    info.sampleRateIdx == first.sampleRateIdx &&
                    info.channelMode == first.channelMode
            ) {
                "파일 ${idx + 2}의 MP3 포맷이 첫 번째 파일과 다릅니다 " +
                    "(version=${info.mpegVersion}/${first.mpegVersion}, " +
                    "layer=${info.layer}/${first.layer}, " +
                    "bitrateIdx=${info.bitrateIdx}/${first.bitrateIdx}, " +
                    "sampleRateIdx=${info.sampleRateIdx}/${first.sampleRateIdx}, " +
                    "channelMode=${info.channelMode}/${first.channelMode})"
            }
        }

        // 3. 출력 파일 작성
        outputFile.outputStream().buffered().use { out ->
            parsed.forEachIndexed { idx, (bytes, range, _) ->
                val (audioStart, audioEnd) = range
                if (idx == 0) {
                    // 첫 파일: ID3v2 태그 유지 (0 ~ audioEnd), ID3v1 트레일러는 이미 audioEnd에서 잘림
                    out.write(bytes, 0, audioEnd)
                } else {
                    // 이후 파일: 태그 제거, audio frame만
                    out.write(bytes, audioStart, audioEnd - audioStart)
                }
            }
        }
    }

    /**
     * ID3v2 헤더와 ID3v1 트레일러 경계를 계산한다.
     * @return Pair(audioStart, audioEnd) — audioEnd는 exclusive (audioEnd - audioStart = 유효 MP3 바이트 수)
     */
    internal fun stripTags(bytes: ByteArray): Pair<Int, Int> {
        var start = 0
        var end = bytes.size

        // ID3v2 헤더 감지 (파일 시작의 "ID3" 3바이트 magic)
        if (bytes.size > 10 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
        ) {
            val size = synchsafeToInt(bytes, 6)
            // flags 바이트(bytes[5])의 bit 4 = footer present → +10 바이트
            val hasFooter = (bytes[5].toInt() and 0x10) != 0
            start = 10 + size + if (hasFooter) 10 else 0
            require(start <= bytes.size) { "ID3v2 size가 파일 크기를 초과합니다 (size=$size, file=${bytes.size})" }
        }

        // ID3v1 트레일러 감지 (파일 끝 128바이트, "TAG" 3바이트 magic)
        if (bytes.size >= 128 &&
            bytes[bytes.size - 128] == 'T'.code.toByte() &&
            bytes[bytes.size - 127] == 'A'.code.toByte() &&
            bytes[bytes.size - 126] == 'G'.code.toByte()
        ) {
            end = bytes.size - 128
        }

        require(end > start) { "유효한 MP3 오디오 데이터가 없습니다 (start=$start, end=$end)" }
        return start to end
    }

    /**
     * synchsafe 32-bit 정수를 읽는다 (ID3v2 size 필드용).
     * 각 바이트의 MSB=0, 7비트씩 유효. bytes[offset..offset+3] 사용.
     */
    internal fun synchsafeToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    /**
     * MP3 frame header를 파싱한다.
     * 4바이트 header: AAAAAAAA AAABBCCD EEEEFFGH IIJJKLMM
     *   A=sync(11), B=version(2), C=layer(2), D=CRC(1)
     *   E=bitrate(4), F=samplerate(2), G=padding(1), H=private(1)
     *   I=channel(2), J=modeExt(2), K=copyright(1), L=original(1), M=emphasis(2)
     *
     * @return Mp3FrameInfo 또는 null (frame sync 불일치 시)
     */
    internal fun parseFrameHeader(bytes: ByteArray, offset: Int): Mp3FrameInfo? {
        if (offset + 4 > bytes.size) return null
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        // frame sync: 11 bits = 0xFFE
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
        return Mp3FrameInfo(
            mpegVersion = (b1 shr 3) and 0x03,
            layer = (b1 shr 1) and 0x03,
            bitrateIdx = (b2 shr 4) and 0x0F,
            sampleRateIdx = (b2 shr 2) and 0x03,
            channelMode = (b3 shr 6) and 0x03
        )
    }
}
