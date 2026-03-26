package com.autominuting.presentation.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.service.PipelineNotificationHelper
import com.autominuting.worker.MinutesGenerationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 외부 앱(삼성 녹음앱 등)에서 ACTION_SEND intent로 공유된 텍스트를 수신하여
 * STT 단계를 건너뛰고 회의록 생성 파이프라인에 자동 진입시키는 Activity.
 *
 * UI 없이 투명하게 동작하며, 공유 텍스트를 파일로 저장하고
 * MeetingEntity(source=SAMSUNG_SHARE)를 생성한 뒤 MinutesGenerationWorker를 enqueue한다.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var meetingRepository: MeetingRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

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

        // 공유 텍스트 추출 (여러 경로 시도)
        var sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        // EXTRA_TEXT가 없으면 EXTRA_STREAM (단일 파일 URI)에서 텍스트 읽기
        if (sharedText.isNullOrBlank()) {
            val streamUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (streamUri != null) {
                Log.d(TAG, "EXTRA_STREAM(단일)에서 텍스트 읽기 시도: $streamUri")
                sharedText = try {
                    readTextAutoDetectEncoding(streamUri)
                } catch (e: Exception) {
                    Log.e(TAG, "EXTRA_STREAM 읽기 실패", e)
                    null
                }
            }
        }

        // SEND_MULTIPLE: 여러 파일 URI에서 텍스트 읽기
        if (sharedText.isNullOrBlank() && intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
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

        // 4. 회의록 자동 생성 시도 (실패해도 전사 데이터는 이미 DB에 보존됨)
        try {
            val minutesFormat = userPreferencesRepository.getMinutesFormatOnce()

            val workRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
                .setInputData(
                    workDataOf(
                        MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                        MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to transcriptPath,
                        MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat.name
                    )
                )
                .build()
            WorkManager.getInstance(this@ShareReceiverActivity).enqueue(workRequest)

            Log.d(TAG, "회의록 생성 파이프라인 진입: meetingId=$meetingId, source=SAMSUNG_SHARE")

            PipelineNotificationHelper.updateProgress(this@ShareReceiverActivity, "회의록 생성 중...")
            Toast.makeText(this@ShareReceiverActivity, "회의록 생성 중...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Worker enqueue 실패 시에도 전사 데이터(TRANSCRIBED 상태)는 DB에 보존됨
            Log.e(TAG, "회의록 생성 Worker enqueue 실패 (전사 데이터는 보존됨)", e)
            Toast.makeText(
                this@ShareReceiverActivity,
                "전사 데이터가 저장되었습니다. 수동으로 회의록을 생성할 수 있습니다.",
                Toast.LENGTH_LONG
            ).show()
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

    companion object {
        private const val TAG = "ShareReceiver"
        private const val DEFAULT_TITLE = "삼성 공유 회의록"
    }
}
