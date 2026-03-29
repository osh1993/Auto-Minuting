package com.autominuting.presentation.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.worker.TranscriptionTriggerWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 대시보드 화면의 상태를 관리하는 ViewModel.
 * 현재 진행 중인 파이프라인이 있으면 해당 회의 정보를 제공한다.
 * URL 입력으로 음성 파일을 다운로드하여 전사 파이프라인에 진입시킨다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 진행 중인 파이프라인 상태에 해당하는 목록 */
    private val activeStatuses = listOf(
        PipelineStatus.AUDIO_RECEIVED,
        PipelineStatus.TRANSCRIBING,
        PipelineStatus.TRANSCRIBED,
        PipelineStatus.GENERATING_MINUTES
    )

    /** 현재 진행 중인 파이프라인의 회의 정보 (없으면 null) */
    val activePipeline: StateFlow<Meeting?> = meetingRepository.getMeetings()
        .map { meetings ->
            meetings.firstOrNull { it.pipelineStatus in activeStatuses }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    // --- URL 다운로드 기능 ---

    /** URL 다운로드 상태 */
    sealed interface DownloadState {
        /** 대기 중 */
        data object Idle : DownloadState
        /** Plaud 공유 링크에서 오디오 URL 추출 중 */
        data object ExtractingAudioUrl : DownloadState
        /** 다운로드 진행 중 */
        data class Downloading(val progress: Float) : DownloadState
        /** 파이프라인 진입 중 */
        data object Processing : DownloadState
        /** 오류 발생 */
        data class Error(val message: String) : DownloadState
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /** 지원하는 음성 확장자 목록 */
    private val supportedAudioExtensions = setOf("m4a", "mp3", "wav", "ogg", "flac", "mp4")

    /**
     * URL에서 음성 파일을 다운로드하고 전사 파이프라인에 진입시킨다.
     *
     * @param url 음성 파일의 HTTP/HTTPS URL
     */
    /** Plaud 공유 링크인지 판별한다. */
    private fun isPlaudShareUrl(url: String): Boolean =
        url.contains("web.plaud.ai/s/") || url.contains("web.plaud.ai/nshare/")

    /**
     * Plaud 공유 링크에서 WebView로 오디오 URL을 추출한다.
     * WebView가 S3 presigned URL 요청을 인터셉트하여 실제 오디오 URL을 반환한다.
     * Main 스레드에서 호출되어야 한다 (WebView 제약).
     */
    fun extractPlaudAudio(url: String) {
        if (_downloadState.value is DownloadState.ExtractingAudioUrl ||
            _downloadState.value is DownloadState.Downloading) return

        _downloadState.value = DownloadState.ExtractingAudioUrl
        _plaudAudioUrl.value = null
        _plaudShareUrl.value = url
    }

    /** Plaud WebView에서 추출된 오디오 URL */
    private val _plaudAudioUrl = MutableStateFlow<String?>(null)
    private val _plaudShareUrl = MutableStateFlow<String?>(null)
    val plaudShareUrl: StateFlow<String?> = _plaudShareUrl.asStateFlow()

    /** Plaud WebView에서 오디오 URL이 추출되었을 때 호출 */
    fun onPlaudAudioUrlExtracted(audioUrl: String) {
        Log.d(TAG, "Plaud 오디오 URL 추출됨: ${audioUrl.take(100)}...")
        _plaudShareUrl.value = null
        // 추출된 S3 URL로 직접 다운로드 진행
        downloadDirectUrl(audioUrl)
    }

    /** Plaud WebView 추출 실패/타임아웃 시 호출 */
    fun onPlaudExtractionFailed(error: String) {
        Log.w(TAG, "Plaud 오디오 추출 실패: $error")
        _plaudShareUrl.value = null
        _downloadState.value = DownloadState.Error("Plaud 오디오 추출 실패: $error")
    }

    fun downloadFromUrl(url: String) {
        // URL 유효성 검증
        if (url.isBlank()) {
            _downloadState.value = DownloadState.Error("올바른 URL을 입력해주세요")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _downloadState.value = DownloadState.Error("올바른 URL을 입력해주세요")
            return
        }

        // Plaud 공유 링크 감지 → WebView 추출 경로
        if (isPlaudShareUrl(url)) {
            extractPlaudAudio(url)
            return
        }

        // 중복 실행 방지
        if (_downloadState.value is DownloadState.Downloading) return

        downloadDirectUrl(url)
    }

    /** 직접 URL에서 오디오 파일을 다운로드한다. */
    private fun downloadDirectUrl(url: String) {
        if (_downloadState.value is DownloadState.Downloading) return

        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                _downloadState.value = DownloadState.Downloading(0f)

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Error("다운로드 실패: HTTP ${response.code}")
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState.Error("응답 본문이 비어있습니다")
                    return@launch
                }

                // 파일명 및 확장자 추출
                val rawFileName = url.substringAfterLast("/").substringBefore("?")
                val fileName = rawFileName.ifBlank { "download_${System.currentTimeMillis()}" }
                val extractedExt = fileName.substringAfterLast(".", "m4a")
                val extension = if (extractedExt in supportedAudioExtensions) {
                    extractedExt
                } else {
                    // Content-Type 헤더에서 확장자 추출 시도
                    val contentType = response.header("Content-Type") ?: ""
                    when {
                        contentType.contains("m4a") || contentType.contains("mp4") -> "m4a"
                        contentType.contains("wav") -> "wav"
                        contentType.contains("mp3") || contentType.contains("mpeg") -> "mp3"
                        contentType.contains("ogg") -> "ogg"
                        contentType.contains("flac") -> "flac"
                        else -> "m4a"
                    }
                }

                // 오디오 디렉터리에 임시 파일 생성
                val audioDir = File(context.filesDir, "audio")
                audioDir.mkdirs()
                val now = System.currentTimeMillis()
                tempFile = File(audioDir, "url_${now}.downloading")

                // 8KB 버퍼 + 진행률 패턴으로 다운로드
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    tempFile!!.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                _downloadState.value = DownloadState.Downloading(
                                    downloadedBytes.toFloat() / totalBytes
                                )
                            }
                        }
                    }
                }

                // 완료 후 최종 파일명으로 rename
                val finalFile = File(audioDir, "url_${now}.$extension")
                if (!tempFile!!.renameTo(finalFile)) {
                    _downloadState.value = DownloadState.Error("파일 이름 변경 실패")
                    tempFile?.delete()
                    return@launch
                }
                tempFile = null // rename 성공 — 더 이상 임시 파일 아님

                Log.d(TAG, "URL 다운로드 완료: ${finalFile.absolutePath} (${finalFile.length()} bytes)")

                // 파이프라인 진입
                _downloadState.value = DownloadState.Processing

                val titleFromFileName = fileName.substringBeforeLast(".")
                val instantNow = Instant.ofEpochMilli(now)
                val meeting = Meeting(
                    title = titleFromFileName,
                    recordedAt = instantNow,
                    audioFilePath = finalFile.absolutePath,
                    pipelineStatus = PipelineStatus.AUDIO_RECEIVED,
                    source = "URL_DOWNLOAD",
                    createdAt = instantNow,
                    updatedAt = instantNow
                )
                val meetingId = meetingRepository.insertMeeting(meeting)

                // TranscriptionTriggerWorker enqueue
                val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
                    .setInputData(
                        workDataOf(
                            TranscriptionTriggerWorker.KEY_AUDIO_FILE_PATH to finalFile.absolutePath,
                            TranscriptionTriggerWorker.KEY_MEETING_ID to meetingId
                        )
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)

                Log.d(TAG, "URL 다운로드 파이프라인 진입: meetingId=$meetingId, source=URL_DOWNLOAD")

                _downloadState.value = DownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "URL 다운로드 오류: ${e.message}", e)
                _downloadState.value = DownloadState.Error("다운로드 오류: ${e.message}")
                tempFile?.delete()
            }
        }
    }

    /** 다운로드 에러 상태를 초기화한다 */
    fun clearDownloadError() {
        _downloadState.value = DownloadState.Idle
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}
