package com.autominuting.presentation.transcripts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.model.PromptTemplate
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.PromptTemplateRepository
import com.autominuting.worker.MinutesGenerationWorker
import com.autominuting.worker.TranscriptionTriggerWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 전사 목록 화면의 상태를 관리하는 ViewModel.
 * 전사가 진행 중이거나 완료된 회의 목록을 필터링하여 제공한다.
 */
@HiltViewModel
class TranscriptsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val promptTemplateRepository: PromptTemplateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 전사 관련 상태의 회의 목록 (전사 중, 전사 완료, 회의록 생성 중, 완료, 실패) */
    val meetings: StateFlow<List<Meeting>> = meetingRepository.getMeetings()
        .map { list ->
            list.filter { meeting ->
                meeting.pipelineStatus in TRANSCRIPT_VISIBLE_STATUSES
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 프롬프트 템플릿 목록 */
    val templates: StateFlow<List<PromptTemplate>> = promptTemplateRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 기본 프롬프트 템플릿 ID (0 = 미설정, 매번 선택) */
    val defaultTemplateId: StateFlow<Long> = userPreferencesRepository.defaultTemplateId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L
        )

    /** 전사 항목의 제목을 변경한다. */
    fun updateTitle(meetingId: Long, newTitle: String) {
        viewModelScope.launch {
            val meeting = meetingRepository.getMeetingById(meetingId).first()
            if (meeting != null) {
                meetingRepository.updateMeeting(
                    meeting.copy(title = newTitle, updatedAt = Instant.now())
                )
            }
        }
    }

    /** 전사 항목 전체(회의 + 전사 파일 + 회의록)를 삭제한다. */
    fun deleteTranscript(id: Long) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(id)
        }
    }

    /**
     * 수동 회의록 생성을 트리거한다.
     * 기본 템플릿이 설정되어 있으면(> 0) 바로 Worker를 enqueue하고,
     * 미설정(0)이면 호출측에서 다이얼로그를 표시해야 한다.
     *
     * @param meetingId 회의록을 생성할 Meeting의 ID
     * @return true이면 Worker가 enqueue됨, false이면 다이얼로그 표시 필요
     */
    fun generateMinutes(meetingId: Long) {
        viewModelScope.launch {
            val templateId = userPreferencesRepository.getDefaultTemplateIdOnce()
            if (templateId == UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID) {
                // 직접 입력 모드: 저장된 커스텀 프롬프트로 생성
                val customPrompt = userPreferencesRepository.getDefaultCustomPromptOnce()
                enqueueMinutesWorker(
                    meetingId,
                    customPrompt = customPrompt.ifBlank { null }
                )
            } else if (templateId > 0) {
                // 기본 템플릿이 설정되어 있으면 바로 생성
                enqueueMinutesWorker(meetingId, templateId = templateId)
            } else {
                // 매번 선택 모드 → 기본 폴백으로 바로 생성
                enqueueMinutesWorker(meetingId)
            }
        }
    }

    /**
     * ManualMinutesDialog에서 템플릿/커스텀 프롬프트를 선택한 후 호의록을 생성한다.
     *
     * @param meetingId 회의록을 생성할 Meeting의 ID
     * @param templateId 선택된 프롬프트 템플릿 ID (null이면 미선택)
     * @param customPrompt 직접 입력한 커스텀 프롬프트 (null이면 미입력)
     */
    fun generateMinutesWithTemplate(meetingId: Long, templateId: Long?, customPrompt: String?) {
        viewModelScope.launch {
            enqueueMinutesWorker(meetingId, templateId = templateId, customPrompt = customPrompt)
        }
    }

    /** MinutesGenerationWorker를 enqueue하는 공통 헬퍼 */
    private suspend fun enqueueMinutesWorker(
        meetingId: Long,
        templateId: Long? = null,
        customPrompt: String? = null
    ) {
        val meeting = meetingRepository.getMeetingById(meetingId).first()
        if (meeting == null) {
            Log.w(TAG, "회의를 찾을 수 없습니다: meetingId=$meetingId")
            return
        }
        if (meeting.transcriptPath == null) {
            Log.w(TAG, "전사 파일 경로가 없습니다: meetingId=$meetingId")
            return
        }

        val minutesFormat = userPreferencesRepository.getMinutesFormatOnce()

        val workRequest = OneTimeWorkRequestBuilder<MinutesGenerationWorker>()
            .setInputData(
                workDataOf(
                    MinutesGenerationWorker.KEY_MEETING_ID to meetingId,
                    MinutesGenerationWorker.KEY_TRANSCRIPT_PATH to meeting.transcriptPath,
                    MinutesGenerationWorker.KEY_MINUTES_FORMAT to minutesFormat.name,
                    MinutesGenerationWorker.KEY_TEMPLATE_ID to (templateId ?: 0L),
                    MinutesGenerationWorker.KEY_CUSTOM_PROMPT to customPrompt
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "회의록 생성 Worker enqueue 완료: meetingId=$meetingId, templateId=$templateId")
    }

    /**
     * 재전사를 트리거한다.
     * 기존 전사 파일을 삭제하고 TranscriptionTriggerWorker를 enqueue하여 다시 전사한다.
     *
     * @param meetingId 재전사할 Meeting의 ID
     */
    fun retranscribe(meetingId: Long) {
        viewModelScope.launch {
            val meeting = meetingRepository.getMeetingById(meetingId).first()
            if (meeting == null) {
                Log.w(TAG, "회의를 찾을 수 없습니다: meetingId=$meetingId")
                return@launch
            }
            if (meeting.audioFilePath.isBlank()) {
                Log.w(TAG, "오디오 파일 경로가 없습니다: meetingId=$meetingId")
                return@launch
            }

            // 기존 전사 파일은 보존 (재전사 실패 시 복구 가능하도록)
            // Worker 성공 후 새 전사 파일로 덮어쓰므로 별도 삭제 불필요

            // 즉시 TRANSCRIBING 상태로 변경 (목록에서 사라지지 않도록)
            meetingRepository.updatePipelineStatus(meetingId, PipelineStatus.TRANSCRIBING)

            // TranscriptionTriggerWorker enqueue
            val workRequest = OneTimeWorkRequestBuilder<TranscriptionTriggerWorker>()
                .setInputData(
                    workDataOf(
                        TranscriptionTriggerWorker.KEY_MEETING_ID to meetingId,
                        TranscriptionTriggerWorker.KEY_AUDIO_FILE_PATH to meeting.audioFilePath
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "재전사 Worker enqueue 완료: meetingId=$meetingId")
        }
    }

    /**
     * 전사 텍스트를 외부 앱으로 공유한다.
     * 전사 파일을 읽어 ACTION_SEND Intent로 공유 시트를 띄운다.
     *
     * @param meetingId 공유할 Meeting의 ID
     * @param activityContext Activity Context (startActivity 호출용)
     */
    fun shareTranscript(meetingId: Long, activityContext: Context) {
        viewModelScope.launch {
            val meeting = meetingRepository.getMeetingById(meetingId).first()
            if (meeting == null) {
                Log.w(TAG, "회의를 찾을 수 없습니다: meetingId=$meetingId")
                return@launch
            }
            if (meeting.transcriptPath == null) {
                Log.w(TAG, "전사 파일 경로가 없습니다: meetingId=$meetingId")
                return@launch
            }

            val transcriptText = try {
                File(meeting.transcriptPath).readText()
            } catch (e: Exception) {
                Log.w(TAG, "전사 파일 읽기 실패: meetingId=$meetingId", e)
                return@launch
            }

            if (transcriptText.isBlank()) {
                Log.w(TAG, "전사 텍스트가 비어있습니다: meetingId=$meetingId")
                return@launch
            }

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, meeting.title)
                putExtra(Intent.EXTRA_TEXT, transcriptText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            activityContext.startActivity(shareIntent)
            Log.d(TAG, "전사 텍스트 공유: meetingId=$meetingId")
        }
    }

    companion object {
        private const val TAG = "TranscriptsVM"

        /** 전사 목록에 표시할 파이프라인 상태 목록 */
        private val TRANSCRIPT_VISIBLE_STATUSES = setOf(
            PipelineStatus.AUDIO_RECEIVED,
            PipelineStatus.TRANSCRIBING,
            PipelineStatus.TRANSCRIBED,
            PipelineStatus.GENERATING_MINUTES,
            PipelineStatus.COMPLETED,
            PipelineStatus.FAILED
        )
    }
}
