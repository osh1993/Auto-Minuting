package com.autominuting.presentation.transcripts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.model.PromptTemplate
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.domain.repository.PromptTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 전사 목록 화면의 상태를 관리하는 ViewModel.
 * 전사가 진행 중이거나 완료된 회의 목록을 필터링하여 제공한다.
 * 수동 회의록 생성 기능을 포함한다.
 */
@HiltViewModel
class TranscriptsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val minutesRepository: MinutesRepository,
    private val meetingDao: MeetingDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "TranscriptsVM"

        /** 전사 목록에 표시할 파이프라인 상태 목록 */
        private val TRANSCRIPT_VISIBLE_STATUSES = setOf(
            PipelineStatus.TRANSCRIBING,
            PipelineStatus.TRANSCRIBED,
            PipelineStatus.GENERATING_MINUTES,
            PipelineStatus.COMPLETED,
            PipelineStatus.FAILED
        )
    }

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

    /** 수동 회의록 생성 진행 중 여부 */
    private val _isManualGenerating = MutableStateFlow(false)
    val isManualGenerating: StateFlow<Boolean> = _isManualGenerating.asStateFlow()

    /** 수동 회의록 생성 결과 이벤트 */
    private val _manualGenerationResult = MutableSharedFlow<ManualGenerationResult>()
    val manualGenerationResult: SharedFlow<ManualGenerationResult> = _manualGenerationResult.asSharedFlow()

    init {
        // 기본 템플릿이 없으면 자동 생성
        viewModelScope.launch {
            promptTemplateRepository.ensureDefaultTemplates()
        }
    }

    /**
     * 수동으로 회의록을 생성한다.
     *
     * @param meetingId 대상 회의 ID
     * @param templateId 선택된 템플릿 ID (null이면 customPrompt 사용)
     * @param customPrompt 직접 입력한 프롬프트 (null이면 templateId 사용)
     */
    fun generateManualMinutes(meetingId: Long, templateId: Long?, customPrompt: String?) {
        viewModelScope.launch {
            _isManualGenerating.value = true
            try {
                // 1. Meeting 조회, transcriptPath 가져오기
                val meeting = meetingDao.getMeetingByIdOnce(meetingId)
                    ?: throw IllegalStateException("회의를 찾을 수 없습니다: $meetingId")

                val transcriptPath = meeting.transcriptPath
                    ?: throw IllegalStateException("전사 파일이 없습니다")

                val transcriptText = File(transcriptPath).readText()
                Log.d(TAG, "수동 회의록 생성 시작: meetingId=$meetingId, 전사 텍스트 ${transcriptText.length}자")

                // 2. 프롬프트 결정
                val prompt = if (templateId != null) {
                    promptTemplateRepository.getById(templateId)?.promptText
                        ?: throw IllegalStateException("템플릿을 찾을 수 없습니다: $templateId")
                } else {
                    customPrompt ?: throw IllegalStateException("프롬프트가 지정되지 않았습니다")
                }

                // 3. 상태 업데이트: GENERATING_MINUTES
                val now = Instant.now().toEpochMilli()
                meetingDao.updatePipelineStatus(
                    meetingId,
                    PipelineStatus.GENERATING_MINUTES.name,
                    null,
                    now
                )

                // 4. 회의록 생성 (커스텀 프롬프트 사용)
                val result = minutesRepository.generateMinutes(
                    transcriptText = transcriptText,
                    customPrompt = prompt
                )

                // 5. 성공: 파일 저장 + DB 업데이트 (COMPLETED)
                val minutesText = result.getOrThrow()
                val minutesDir = File(context.filesDir, "minutes")
                minutesDir.mkdirs()
                val file = File(minutesDir, "${meetingId}.md")
                file.writeText(minutesText)

                val completedAt = Instant.now().toEpochMilli()
                meetingDao.updateMinutes(
                    meetingId,
                    file.absolutePath,
                    PipelineStatus.COMPLETED.name,
                    completedAt
                )

                Log.d(TAG, "수동 회의록 생성 성공: ${file.absolutePath}")
                _manualGenerationResult.emit(ManualGenerationResult.Success(meetingId))
            } catch (e: Exception) {
                Log.e(TAG, "수동 회의록 생성 실패: ${e.message}", e)
                val errorAt = Instant.now().toEpochMilli()
                meetingDao.updatePipelineStatus(
                    meetingId,
                    PipelineStatus.FAILED.name,
                    e.message,
                    errorAt
                )
                _manualGenerationResult.emit(
                    ManualGenerationResult.Error(e.message ?: "알 수 없는 오류")
                )
            } finally {
                _isManualGenerating.value = false
            }
        }
    }
}

/** 수동 회의록 생성 결과 */
sealed class ManualGenerationResult {
    /** 생성 성공 */
    data class Success(val meetingId: Long) : ManualGenerationResult()
    /** 생성 실패 */
    data class Error(val message: String) : ManualGenerationResult()
}
