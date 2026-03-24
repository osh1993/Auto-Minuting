package com.autominuting.data.audio

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 오디오 파일 저장/검증/공간 확인을 담당하는 매니저.
 *
 * 앱 내부 저장소(filesDir/audio)에 오디오 파일을 관리하며,
 * 저장 전 가용 공간 확인 및 파일 유효성 검증을 수행한다.
 */
@Singleton
class AudioFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** 기본 필요 공간: 100MB */
        private const val DEFAULT_REQUIRED_BYTES = 100L * 1024 * 1024

        /** 지원하는 오디오 파일 확장자 */
        private val SUPPORTED_EXTENSIONS = listOf("mp3", "wav")

        /** 오디오 파일 디렉토리 이름 */
        private const val AUDIO_DIR_NAME = "audio"
    }

    /**
     * 오디오 파일 저장 디렉토리를 반환한다.
     * 디렉토리가 존재하지 않으면 생성한다.
     *
     * @return 오디오 파일 저장 디렉토리
     */
    fun getAudioDirectory(): File =
        File(context.filesDir, AUDIO_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }

    /**
     * 저장소에 충분한 공간이 있는지 확인한다.
     *
     * @param requiredBytes 필요한 바이트 수 (기본: 100MB)
     * @return 충분한 공간이 있으면 true
     */
    fun hasEnoughSpace(requiredBytes: Long = DEFAULT_REQUIRED_BYTES): Boolean {
        val statFs = StatFs(context.filesDir.path)
        return statFs.availableBytes >= requiredBytes
    }

    /**
     * 오디오 파일의 유효성을 검증한다.
     * 파일이 존재하고, 크기가 0보다 크며, 지원하는 확장자인지 확인한다.
     *
     * @param file 검증할 파일
     * @return 유효하면 true
     */
    fun validateAudioFile(file: File): Boolean =
        file.exists() &&
            file.length() > 0 &&
            file.extension.lowercase() in SUPPORTED_EXTENSIONS

    /**
     * 타임스탬프 기반 파일명을 생성한다.
     *
     * @param prefix 파일명 접두사 (기본: "meeting")
     * @return 생성된 파일명 (예: "meeting_20260324_143000.mp3")
     */
    fun generateFileName(prefix: String = "meeting"): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "${prefix}_${timestamp}.mp3"
    }
}
