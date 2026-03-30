package com.autominuting.presentation.share

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.service.PipelineNotificationHelper
import com.autominuting.worker.MinutesGenerationWorker
import com.autominuting.worker.TranscriptionTriggerWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 외부 앱(삼성 녹음앱 등)에서 ACTION_SEND intent로 공유된 콘텐츠를 수신하는 Activity.
 *
 * 지원하는 공유 유형:
 * - 텍스트(text): 전사 텍스트로 간주하고 회의록 생성 파이프라인에 진입
 * - 음성 파일(audio): STT 전사 후 회의록 생성 파이프라인에 진입
 *
 * UI 없이 투명하게 동작하며, MeetingEntity(source=SAMSUNG_SHARE)를 생성한다.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var meetingRepository: MeetingRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    /** 음성 공유 시 RECORD_AUDIO 권한 요청 후 전사 진행 */
    private var pendingAudioUri: android.net.Uri? = null

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val uri = pendingAudioUri
        pendingAudioUri = null
        if (granted && uri != null) {
            lifecycleScope.launch {
                try {
                    processSharedAudio(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "음성 파일 처리 실패", e)
                    Toast.makeText(this@ShareReceiverActivity, "음성 파일 처리 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                } finally {
                    finish()
                }
            }
        } else {
            Log.w(TAG, "RECORD_AUDIO 권한이 거부되어 전사를 진행할 수 없습니다")
            Toast.makeText(this, "음성 인식에 마이크 권한이 필요합니다", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intent 디버그 로깅
        Log.d(TAG, "=== Share Intent 수신 ===")
        Log.d(TAG, "action=${intent?.action}, type=${intent?.type}")
        Log.d(TAG, "EXTRA_TEXT=${intent?.getStringExtra(Intent.EXTRA_TEXT)?.take(100)}")
        Log.d(TAG, "EXTRA_STREAM=${intent?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)}")
        Log.d(TAG, "EXTRA_SUBJECT=${intent?.getStringExtra(Intent.EXTRA_SUBJECT)}")
        Log.d(TAG, "extras=${intent?.extras?.keySet()}")

        // ACTION_SEND 또는 ACTION_SEND_MULTIPLE 검증
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) {
            Log.w(TAG, "지원하지 않는 action: ${intent?.action}")
            Toast.makeText(this, "지원하지 않는 공유 형식입니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 음성 파일(audio) 공유 확인 — EXTRA_TEXT가 없고 EXTRA_STREAM이 audio MIME인 경우
        @Suppress("DEPRECATION")
        val streamUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        // getType()은 SecurityException/IllegalArgumentException을 throw할 수 있음
        // (외부 앱의 content:// URI에서 provider를 찾을 수 없는 경우 등)
        val mimeType = streamUri?.let { uri ->
            try {
                contentResolver.getType(uri)
            } catch (e: Exception) {
                Log.w(TAG, "contentResolver.getType() 실패, intent.type 사용: ${e.message}")
                null
            }
        } ?: intent.type // ContentResolver 실패 시 intent.type을 폴백으로 사용
        val isAudioShare = intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
            && streamUri != null
            && mimeType?.startsWith("audio/") == true

        if (isAudioShare) {
            Log.d(TAG, "음성 파일 공유 감지: uri=$streamUri, mimeType=$mimeType")
            // RECORD_AUDIO 권한 확인 (SpeechRecognizer 사용 시 필수)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                lifecycleScope.launch {
                    try {
                        processSharedAudio(streamUri!!)
                    } catch (e: Exception) {
                        Log.e(TAG, "음성 파일 처리 실패", e)
                        Toast.makeText(this@ShareReceiverActivity, "음성 파일 처리 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                    } finally {
                        finish()
                    }
                }
            } else {
                pendingAudioUri = streamUri
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }

        // Plaud 공유 링크 감지 (텍스트 공유 처리 이전에 체크)
        val sharedTextRaw = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedTextRaw.isNullOrBlank() && isPlaudShareUrl(sharedTextRaw)) {
            val plaudUrl = extractPlaudUrl(sharedTextRaw)
            if (plaudUrl != null) {
                Log.d(TAG, "Plaud 공유 링크 감지: $plaudUrl")
                processPlaudShareLink(plaudUrl)
                return
            }
        }

        // 텍스트 공유 처리 (기존 경로)
        var sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        // EXTRA_TEXT가 없으면 EXTRA_STREAM (단일 파일 URI)에서 텍스트 읽기
        if (sharedText.isNullOrBlank() && streamUri != null) {
            Log.d(TAG, "EXTRA_STREAM(단일)에서 텍스트 읽기 시도: $streamUri")
            sharedText = try {
                readTextAutoDetectEncoding(streamUri)
            } catch (e: Exception) {
                Log.e(TAG, "EXTRA_STREAM 읽기 실패", e)
                null
            }
        }

        // SEND_MULTIPLE: 여러 파일 URI에서 텍스트 읽기
        if (sharedText.isNullOrBlank() && intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            Log.d(TAG, "EXTRA_STREAM(다중): ${streamUris?.size}개 URI")
            if (!streamUris.isNullOrEmpty()) {
                val texts = streamUris.mapNotNull { uri ->
                    try {
                        Log.d(TAG, "  URI 읽기: $uri (type=${contentResolver.getType(uri)})")
                        readTextAutoDetectEncoding(uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "  URI 읽기 실패: $uri", e)
                        null
                    }
                }
                if (texts.isNotEmpty()) {
                    sharedText = texts.joinToString("\n\n")
                    Log.d(TAG, "다중 URI에서 텍스트 합침: ${texts.size}개, 총 ${sharedText?.length}자")
                }
            }
        }

        if (sharedText.isNullOrBlank() || sharedText.length < 10) {
            Log.w(TAG, "텍스트 없음 또는 너무 짧음: length=${sharedText?.length}")
            Toast.makeText(this, "공유된 텍스트가 없거나 너무 짧습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "공유 텍스트 확보: ${sharedText.length}자")

        // 제목 추출: 첫 줄 사용, 30자 초과 시 자르기
        val title = extractTitle(sharedText)

        lifecycleScope.launch {
            try {
                processSharedText(sharedText, title)
            } catch (e: Exception) {
                Log.e(TAG, "공유 텍스트 처리 실패", e)
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "처리 중 오류가 발생했습니다",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                finish()
            }
        }
    }

    /**
     * 공유된 텍스트를 파일로 저장하고 파이프라인에 진입시킨다.
     */
    private suspend fun processSharedText(sharedText: String, title: String) {
        val now = System.currentTimeMillis()

        // 1. 전사 텍스트를 임시 파일로 저장
        val transcriptsDir = File(filesDir, "transcripts")
        transcriptsDir.mkdirs()
        val tempFile = File(transcriptsDir, "share_${now}.txt")
        tempFile.writeText(sharedText)

        // 2. MeetingEntity 생성 및 DB insert
        val meeting = Meeting(
            title = title,
            recordedAt = Instant.ofEpochMilli(now),
            audioFilePath = "", // 공유 방식은 오디오 파일 없음 (Room non-null 필드)
            transcriptPath = tempFile.absolutePath,
            pipelineStatus = PipelineStatus.TRANSCRIBED, // STT 단계 건너뜀
            source = "SAMSUNG_SHARE",
            createdAt = Instant.ofEpochMilli(now),
            updatedAt = Instant.ofEpochMilli(now)
        )
        val meetingId = meetingRepository.insertMeeting(meeting)

        // 3. 파일명을 meetingId 기반으로 rename
        val finalFile = File(transcriptsDir, "${meetingId}.txt")
        if (tempFile.renameTo(finalFile)) {
            // transcriptPath 업데이트를 위해 meeting 업데이트
            meetingRepository.updateMeeting(
                meeting.copy(
                    id = meetingId,
                    transcriptPath = finalFile.absolutePath,
                    updatedAt = Instant.now()
                )
            )
        }

        val transcriptPath = if (finalFile.exists()) finalFile.absolutePath else tempFile.absolutePath

        // 4. 자동모드 확인 후 회의록 생성 (HYBRID 모드이면 자동 생성 안 함)
        val automationMode = userPreferencesRepository.getAutomationModeOnce()
        if (automationMode == com.autominuting.domain.model.AutomationMode.HYBRID) {
            Log.d(TAG, "HYBRID 모드 — 회의록 자동 생성 건너뜀: meetingId=$meetingId")
            Toast.makeText(this@ShareReceiverActivity, "전사 데이터가 저장되었습니다. 수동으로 회의록을 생성하세요.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val templateId = userPreferencesRepository.getDefaultTemplateIdOnce()
            val workData = when {
                templateId == UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID -> {
                    // 직접 입력 모드: 저장된 커스텀 프롬프트로 생성
                    val customPrompt = userPreferencesRepository.getDefaultCustomPromptOnce()
                    workDataOf(
                        MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                        MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                        MinutesGenerationWorker.KEY_CUSTOM_PROMPT to customPrompt.ifBlank { null }
                    )
                }
                templateId > 0 -> {
                    workDataOf(
                        MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                        MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                        MinutesGenerationWorker.KEY_TEMPLATE_ID to templateId
                    )
                }
                else -> {
                    workDataOf(
                        MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                        MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath
                    )
                }
            }

            val workRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
                .setInputData(workData)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 60L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(this@ShareReceiverActivity).enqueue(workRequest)

            Log.d(TAG, "회의록 생성 파이프라인 진입: meetingId=$meetingId, source=SAMSUNG_SHARE")

            PipelineNotificationHelper.updateProgress(this@ShareReceiverActivity, "회의록 생성 중...")
            Toast.makeText(this@ShareReceiverActivity, "회의록 생성 중...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "회의록 생성 Worker enqueue 실패 (전사 데이터는 보존됨)", e)
            Toast.makeText(
                this@ShareReceiverActivity,
                "전사 데이터가 저장되었습니다. 수동으로 회의록을 생성할 수 있습니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 공유된 음성 파일을 로컬에 저장하고 STT 전사 파이프라인에 진입시킨다.
     *
     * @param audioUri 공유된 음성 파일의 content:// URI
     */
    private suspend fun processSharedAudio(audioUri: android.net.Uri) {
        val now = System.currentTimeMillis()

        // 1. ContentResolver로 음성 파일을 앱 내부 저장소에 복사
        val audioDir = File(filesDir, "audio")
        audioDir.mkdirs()

        // 확장자 추출 (MIME 타입 기반)
        val mimeType = contentResolver.getType(audioUri) ?: "audio/mp4"
        val extension = when {
            mimeType.contains("m4a") || mimeType.contains("mp4") -> "m4a"
            mimeType.contains("wav") -> "wav"
            mimeType.contains("mp3") || mimeType.contains("mpeg") -> "mp3"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("flac") -> "flac"
            else -> "m4a" // 삼성 녹음앱 기본 포맷
        }
        val audioFile = File(audioDir, "share_${now}.$extension")

        contentResolver.openInputStream(audioUri)?.use { input ->
            audioFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: run {
            Log.e(TAG, "음성 파일 URI를 열 수 없습니다: $audioUri")
            Toast.makeText(this, "음성 파일을 읽을 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "음성 파일 저장 완료: ${audioFile.absolutePath} (${audioFile.length()} bytes)")

        // 2. MeetingEntity 생성 (AUDIO_RECEIVED 상태로 DB 저장)
        val meeting = Meeting(
            title = getDisplayName(audioUri) ?: "음성 공유 회의",
            recordedAt = Instant.ofEpochMilli(now),
            audioFilePath = audioFile.absolutePath,
            pipelineStatus = PipelineStatus.AUDIO_RECEIVED,
            source = "SAMSUNG_SHARE",
            createdAt = Instant.ofEpochMilli(now),
            updatedAt = Instant.ofEpochMilli(now)
        )
        val meetingId = meetingRepository.insertMeeting(meeting)

        Log.d(TAG, "음성 공유 MeetingEntity 생성: meetingId=$meetingId")

        // 3. TranscriptionTriggerWorker enqueue (기존 STT 파이프라인 활용)
        try {
            // automationMode를 전달해야 HYBRID 모드에서 회의록 자동 생성이 방지됨
            val automationMode = userPreferencesRepository.getAutomationModeOnce()
            val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
                .setInputData(
                    workDataOf(
                        TranscriptionTriggerWorker.KEY_AUDIO_FILE_PATH to audioFile.absolutePath,
                        TranscriptionTriggerWorker.KEY_MEETING_ID to meetingId,
                        TranscriptionTriggerWorker.KEY_AUTOMATION_MODE to automationMode.name
                    )
                )
                .build()
            WorkManager.getInstance(this@ShareReceiverActivity).enqueue(workRequest)

            Log.d(TAG, "전사 파이프라인 진입: meetingId=$meetingId, audioPath=${audioFile.absolutePath}, automationMode=$automationMode")

            PipelineNotificationHelper.updateProgress(this@ShareReceiverActivity, "음성 파일 전사 중...")
            Toast.makeText(this@ShareReceiverActivity, "음성 파일 전사 중...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Worker enqueue 실패 시에도 오디오 파일과 MeetingEntity는 보존됨
            Log.e(TAG, "전사 Worker enqueue 실패 (오디오 파일은 보존됨)", e)
            Toast.makeText(
                this@ShareReceiverActivity,
                "음성 파일이 저장되었습니다. 수동으로 전사를 시작할 수 있습니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * content URI에서 원본 파일명을 추출한다.
     * 확장자를 제거하여 제목으로 사용할 수 있는 이름을 반환한다.
     *
     * @param uri 공유된 파일의 content:// URI
     * @return 확장자가 제거된 파일명, 추출 실패 시 null
     */
    private fun getDisplayName(uri: android.net.Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            cursor.getString(nameIndex)?.substringBeforeLast(".")
                        } else null
                    } else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "파일명 추출 실패: ${e.message}")
            null
        }
    }

    /**
     * 공유 텍스트에서 제목을 추출한다.
     * 첫 줄을 제목으로 사용하되, 30자 초과 시 잘라내고 "..." 추가.
     * 첫 줄이 비어있으면 기본 제목을 사용한다.
     */
    private fun extractTitle(text: String): String {
        val firstLine = text.lines().firstOrNull()?.trim()
        if (firstLine.isNullOrBlank()) return DEFAULT_TITLE
        return if (firstLine.length > 30) {
            firstLine.substring(0, 30) + "..."
        } else {
            firstLine
        }
    }

    /**
     * URI에서 텍스트를 읽되, BOM 기반으로 인코딩을 자동 감지한다.
     * 삼성 녹음앱은 UTF-16 LE로 txt 파일을 저장한다.
     */
    private fun readTextAutoDetectEncoding(uri: android.net.Uri): String? {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        // BOM 기반 인코딩 감지
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                Log.d(TAG, "인코딩 감지: UTF-16 LE (BOM)")
                Charsets.UTF_16LE
            }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                Log.d(TAG, "인코딩 감지: UTF-16 BE (BOM)")
                Charsets.UTF_16BE
            }
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                Log.d(TAG, "인코딩 감지: UTF-8 (BOM)")
                Charsets.UTF_8
            }
            else -> {
                // BOM 없음 — 널 바이트 패턴으로 UTF-16 추정
                val hasNullBytes = bytes.size >= 4 &&
                    (bytes[1] == 0x00.toByte() || bytes[0] == 0x00.toByte())
                if (hasNullBytes) {
                    Log.d(TAG, "인코딩 추정: UTF-16 LE (널 바이트 패턴)")
                    Charsets.UTF_16LE
                } else {
                    Log.d(TAG, "인코딩 기본: UTF-8")
                    Charsets.UTF_8
                }
            }
        }

        // BOM 제거 후 디코딩
        val offset = when {
            charset == Charsets.UTF_16LE && bytes.size >= 2 && bytes[0] == 0xFF.toByte() -> 2
            charset == Charsets.UTF_16BE && bytes.size >= 2 && bytes[0] == 0xFE.toByte() -> 2
            charset == Charsets.UTF_8 && bytes.size >= 3 && bytes[0] == 0xEF.toByte() -> 3
            else -> 0
        }

        val text = String(bytes, offset, bytes.size - offset, charset)
        Log.d(TAG, "디코딩 완료: ${text.length}자 (charset=$charset, rawBytes=${bytes.size})")
        return text
    }

    /** Plaud 공유 링크인지 판별한다. */
    private fun isPlaudShareUrl(text: String): Boolean =
        text.contains("web.plaud.ai/s/") || text.contains("web.plaud.ai/nshare/")

    /** 텍스트 내에서 web.plaud.ai URL을 추출한다. (텍스트에 URL 외 다른 내용이 포함될 수 있음) */
    private fun extractPlaudUrl(text: String): String? {
        val regex = Regex("""https?://web\.plaud\.ai/(s|nshare)/[^\s]+""")
        return regex.find(text)?.value
    }

    /**
     * Plaud 공유 링크에서 WebView로 S3 오디오 URL을 추출하고 다운로드 파이프라인에 진입한다.
     * WebView를 투명 0dp 크기로 Activity에 추가하여 S3 presigned URL 요청을 인터셉트한다.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun processPlaudShareLink(plaudUrl: String) {
        Toast.makeText(this, "Plaud 오디오 추출 중...", Toast.LENGTH_SHORT).show()

        var audioFound = false
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
        }

        // 0dp 크기로 숨겨서 추가
        val params = android.widget.FrameLayout.LayoutParams(0, 0)
        addContentView(webView, params)

        // 30초 타임아웃
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!audioFound) {
                Log.w(TAG, "Plaud 오디오 URL 추출 타임아웃")
                Toast.makeText(this, "오디오 추출 타임아웃", Toast.LENGTH_SHORT).show()
                webView.destroy()
                finish()
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 30_000)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val requestUrl = request?.url?.toString() ?: return null
                // S3 presigned URL 패턴 감지 (amazonaws.com/audiofiles/)
                if (requestUrl.contains("amazonaws.com") &&
                    requestUrl.contains("audiofiles/") &&
                    !audioFound
                ) {
                    audioFound = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Handler(Looper.getMainLooper()).post {
                        Log.d(TAG, "Plaud S3 오디오 URL 추출: ${requestUrl.take(100)}...")
                        webView.destroy()
                        lifecycleScope.launch { downloadAndStartPipeline(requestUrl) }
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                // 재생 버튼 자동 클릭 (DashboardScreen과 동일 JS — 오디오 URL 요청 유도)
                view?.evaluateJavascript(
                    """
                    (function() {
                        var audios = document.querySelectorAll('audio');
                        for (var i = 0; i < audios.length; i++) { audios[i].play().catch(function(){}); }
                        var buttons = document.querySelectorAll('button, [role="button"], .play-btn, svg');
                        for (var i = 0; i < buttons.length; i++) { buttons[i].click(); }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !audioFound) {
                    audioFound = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.e(TAG, "Plaud 페이지 로드 실패: ${error?.description}")
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Plaud 페이지 로드 실패",
                        Toast.LENGTH_SHORT
                    ).show()
                    webView.destroy()
                    finish()
                }
            }
        }
        webView.loadUrl(plaudUrl)
    }

    /**
     * S3 presigned URL에서 OkHttp로 오디오를 다운로드하고 전사 파이프라인에 진입한다.
     * DashboardViewModel.downloadDirectUrl()과 동일 패턴.
     */
    private suspend fun downloadAndStartPipeline(audioUrl: String) {
        try {
            val now = System.currentTimeMillis()
            val audioDir = File(filesDir, "audio")
            audioDir.mkdirs()

            // OkHttp로 S3 URL 직접 다운로드
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder().url(audioUrl).build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("다운로드 실패: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("응답 본문이 비어있습니다")

                // 확장자 추출 (URL 또는 Content-Type 기반)
                val rawFileName = audioUrl.substringAfterLast("/").substringBefore("?")
                val extractedExt = rawFileName.substringAfterLast(".", "m4a")
                val extension = if (extractedExt in listOf("m4a", "mp4", "wav", "mp3", "ogg", "flac")) {
                    extractedExt
                } else {
                    val contentType = response.header("Content-Type") ?: ""
                    when {
                        contentType.contains("m4a") || contentType.contains("mp4") -> "m4a"
                        contentType.contains("wav") -> "wav"
                        contentType.contains("mp3") || contentType.contains("mpeg") -> "mp3"
                        else -> "m4a"
                    }
                }

                val audioFile = File(audioDir, "plaud_${now}.$extension")
                body.byteStream().use { input ->
                    audioFile.outputStream().use { output -> input.copyTo(output) }
                }
                response.close()

                Log.d(TAG, "Plaud 오디오 다운로드 완료: ${audioFile.absolutePath} (${audioFile.length()} bytes)")

                // MeetingEntity 생성 (AUDIO_RECEIVED)
                val instantNow = Instant.ofEpochMilli(now)
                val meeting = Meeting(
                    title = "Plaud 공유 회의",
                    recordedAt = instantNow,
                    audioFilePath = audioFile.absolutePath,
                    pipelineStatus = PipelineStatus.AUDIO_RECEIVED,
                    source = "PLAUD_SHARE",
                    createdAt = instantNow,
                    updatedAt = instantNow
                )
                val meetingId = meetingRepository.insertMeeting(meeting)

                // TranscriptionTriggerWorker enqueue
                // automationMode를 전달해야 HYBRID 모드에서 회의록 자동 생성이 방지됨
                val automationMode = userPreferencesRepository.getAutomationModeOnce()
                val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
                    .setInputData(
                        workDataOf(
                            TranscriptionTriggerWorker.KEY_AUDIO_FILE_PATH to audioFile.absolutePath,
                            TranscriptionTriggerWorker.KEY_MEETING_ID to meetingId,
                            TranscriptionTriggerWorker.KEY_AUTOMATION_MODE to automationMode.name
                        )
                    )
                    .build()
                WorkManager.getInstance(this@ShareReceiverActivity).enqueue(workRequest)

                Log.d(TAG, "Plaud 전사 파이프라인 진입: meetingId=$meetingId, automationMode=$automationMode")

                withContext(Dispatchers.Main) {
                    PipelineNotificationHelper.updateProgress(
                        this@ShareReceiverActivity,
                        "Plaud 음성 전사 중..."
                    )
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Plaud 음성 전사 중...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Plaud 오디오 다운로드/파이프라인 진입 실패", e)
            Toast.makeText(this, "Plaud 오디오 처리 실패: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    companion object {
        private const val TAG = "ShareReceiver"
        private const val DEFAULT_TITLE = "삼성 공유 회의록"
    }
}
