package com.autominuting.presentation.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.MinutesFormat
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.data.repository.MinutesRepositoryImpl
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
 * 디버그 모드에서 테스트 데이터 삽입 및 Gemini API 테스트를 지원한다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val minutesRepository: MinutesRepository,
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

    /** 테스트 상태 메시지 */
    private val _testStatus = MutableStateFlow("")
    val testStatus: StateFlow<String> = _testStatus.asStateFlow()

    /** Gemini API 테스트 진행 중 여부 */
    private val _isTestingGemini = MutableStateFlow(false)
    val isTestingGemini: StateFlow<Boolean> = _isTestingGemini.asStateFlow()

    /** 더미 회의록 3건을 DB에 삽입한다 */
    fun insertTestData() {
        viewModelScope.launch {
            _testStatus.value = "테스트 데이터 삽입 중..."
            try {
                val now = Instant.now()
                val sampleMinutes = listOf(
                    "# 주간 정례 회의\n\n### 1. 회의 개요\n- 날짜: 2026-03-24\n- 참석자: 김철수, 이영희, 박민수\n- 회의 시간: 30분\n\n### 2. 주요 안건 및 논의 내용\n\n#### 프로젝트 진행 현황\n- Auto Minuting v1.0 개발 완료\n- QA 테스트 일정: 3월 25~27일\n\n#### 출시 준비\n- 앱 스토어 등록 자료 준비 필요\n- 마케팅 페이지 디자인 검토\n\n### 3. 결정 사항\n1. QA 완료 후 3월 28일 내부 배포\n2. 4월 1일 Play Store 출시 목표\n3. 마케팅 페이지는 이영희 담당\n\n### 4. 액션 아이템\n| 담당자 | 할 일 | 기한 |\n|--------|--------|------|\n| 김철수 | QA 테스트 시나리오 작성 | 3/25 |\n| 이영희 | 마케팅 페이지 디자인 | 3/27 |\n| 박민수 | 앱 스토어 등록 자료 | 3/27 |",
                    "# 기술 리뷰 회의\n\n### 1. 회의 개요\n- 날짜: 2026-03-23\n- 참석자: 박민수, 정지원\n- 회의 시간: 45분\n\n### 2. 주요 안건 및 논의 내용\n\n#### 아키텍처 검토\n- Clean Architecture 패턴 적용 확인\n- Hilt DI 구성 정상 동작\n\n#### 성능 이슈\n- Whisper 전사 시간이 긴 오디오에서 3분 이상 소요\n- 백그라운드 Worker로 처리하므로 UX 영향 없음\n\n### 3. 결정 사항\n1. 현재 아키텍처 유지 (추가 리팩토링 불필요)\n2. Whisper 모델은 small 유지 (medium은 메모리 부담)\n\n### 4. 액션 아이템\n| 담당자 | 할 일 | 기한 |\n|--------|--------|------|\n| 박민수 | 전사 성능 벤치마크 문서화 | 3/25 |\n| 정지원 | 메모리 사용량 모니터링 추가 | 3/26 |",
                    "# 제품 기획 회의\n\n### 1. 회의 개요\n- 날짜: 2026-03-22\n- 참석자: 김철수, 이영희, 정지원\n- 회의 시간: 60분\n\n### 2. 주요 안건 및 논의 내용\n\n#### v2.0 기능 논의\n- 화자 분리 기능 요청이 많음\n- 다국어 전사 지원 (영어, 일본어)\n- 캘린더 연동으로 회의 자동 매칭\n\n#### 사용자 피드백\n- \"녹음만 하면 회의록이 자동으로 나와서 편하다\"\n- \"검색 기능이 있으면 좋겠다\" → v1.0에 이미 포함!\n\n### 3. 결정 사항\n1. v2.0 핵심 기능: 화자 분리 + 다국어\n2. 캘린더 연동은 v2.1로 미룸\n3. 클라우드 백업은 v3.0에서 검토\n\n### 4. 액션 아이템\n| 담당자 | 할 일 | 기한 |\n|--------|--------|------|\n| 김철수 | v2.0 로드맵 초안 | 4/1 |\n| 이영희 | 화자 분리 기술 리서치 | 4/5 |\n| 정지원 | 다국어 Whisper 모델 벤치마크 | 4/5 |"
                )
                val titles = listOf("주간 정례 회의", "기술 리뷰 회의", "제품 기획 회의")

                for (i in titles.indices) {
                    val meetingId = meetingRepository.insertMeeting(
                        Meeting(
                            title = titles[i],
                            recordedAt = now.minusSeconds((i * 86400).toLong()),
                            audioFilePath = "/test/audio_${i}.wav",
                            pipelineStatus = PipelineStatus.COMPLETED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )

                    // 회의록 파일 저장
                    val minutesDir = File(context.filesDir, "minutes")
                    minutesDir.mkdirs()
                    val minutesFile = File(minutesDir, "${meetingId}.md")
                    minutesFile.writeText(sampleMinutes[i])

                    // DB에 minutesPath 업데이트
                    meetingRepository.updateMeeting(
                        Meeting(
                            id = meetingId,
                            title = titles[i],
                            recordedAt = now.minusSeconds((i * 86400).toLong()),
                            audioFilePath = "/test/audio_${i}.wav",
                            minutesPath = minutesFile.absolutePath,
                            pipelineStatus = PipelineStatus.COMPLETED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                _testStatus.value = "✓ 테스트 데이터 3건 삽입 완료! 회의록 탭에서 확인하세요."
            } catch (e: Exception) {
                _testStatus.value = "✗ 오류: ${e.message}"
            }
        }
    }

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

    /** 샘플 전사 텍스트로 Gemini API를 테스트한다 */
    fun testGeminiApi() {
        viewModelScope.launch {
            _isTestingGemini.value = true
            _testStatus.value = "Gemini API 호출 중..."
            try {
                val sampleTranscript = """
                    김철수: 안녕하세요, 오늘 주간 회의를 시작하겠습니다.
                    이영희: 네, 지난주 개발 진행 상황부터 공유드리겠습니다.
                    이영희: Auto Minuting 앱의 전사 엔진 통합이 완료되었고, 회의록 생성 기능도 테스트 중입니다.
                    박민수: 저는 UI 쪽을 마무리했습니다. Markdown 뷰어랑 검색 기능까지 다 들어갔습니다.
                    김철수: 좋습니다. 그러면 이번 주 내로 QA를 시작할 수 있겠네요.
                    이영희: 네, 내일부터 테스트 케이스 작성하고 수요일부터 실행할 수 있을 것 같습니다.
                    김철수: 출시 일정은 어떻게 잡을까요?
                    박민수: QA 3일이면 금요일에 마무리되고, 다음 주 월요일에 Play Store 제출 가능합니다.
                    김철수: 좋아요, 그럼 4월 1일 출시를 목표로 합시다. 다른 안건 있나요?
                    이영희: 마케팅 랜딩 페이지 디자인 시안이 필요합니다.
                    김철수: 그건 이영희님이 이번 주 금요일까지 준비해주세요. 회의 마치겠습니다.
                """.trimIndent()

                val result = minutesRepository.generateMinutes(sampleTranscript, MinutesFormat.STRUCTURED)

                if (result.isSuccess) {
                    val minutesText = result.getOrThrow()

                    // DB에 저장
                    val now = Instant.now()
                    val meetingId = meetingRepository.insertMeeting(
                        Meeting(
                            title = "[Gemini 테스트] 주간 회의",
                            recordedAt = now,
                            audioFilePath = "/test/gemini_test.wav",
                            pipelineStatus = PipelineStatus.COMPLETED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )

                    // 파일 저장
                    if (minutesRepository is MinutesRepositoryImpl) {
                        val path = (minutesRepository as MinutesRepositoryImpl).saveMinutesToFile(meetingId, minutesText)
                        meetingRepository.updateMeeting(
                            Meeting(
                                id = meetingId,
                                title = "[Gemini 테스트] 주간 회의",
                                recordedAt = now,
                                audioFilePath = "/test/gemini_test.wav",
                                minutesPath = path,
                                pipelineStatus = PipelineStatus.COMPLETED,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }

                    _testStatus.value = "✓ Gemini API 성공! ${minutesText.length}자 회의록 생성됨. 회의록 탭에서 확인하세요."
                } else {
                    _testStatus.value = "✗ Gemini 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _testStatus.value = "✗ 오류: ${e.message}"
            } finally {
                _isTestingGemini.value = false
            }
        }
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}
