package com.autominuting.presentation.minutes

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autominuting.domain.model.Minutes
import com.autominuting.domain.repository.MinutesDataRepository
import com.autominuting.worker.DriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 회의록 목록 UI에 표시할 모델. Minutes + 출처 전사명 포함. */
data class MinutesUiModel(
    val minutes: Minutes,
    val meetingTitle: String?  // null이면 "삭제된 전사" 표시
)

/**
 * 회의록 목록 화면의 상태를 관리하는 ViewModel.
 * Minutes 테이블 기반으로 회의록 목록을 제공하며, 검색/삭제/제목 수정/공유를 지원한다.
 */
@HiltViewModel
class MinutesViewModel @Inject constructor(
    private val minutesDataRepository: MinutesDataRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 검색어 내부 상태. */
    private val _searchQuery = MutableStateFlow("")

    /** 검색어 공개 상태. UI에서 양방향 바인딩에 사용한다. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 검색어를 변경한다. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * 회의록 목록 (검색어 기반 클라이언트 필터링, 출처 전사명 포함).
     * 검색어가 비어있으면 전체 목록을, 있으면 minutesTitle LIKE 검색 결과를 표시한다.
     * 300ms debounce로 불필요한 쿼리를 방지한다.
     */
    @OptIn(FlowPreview::class)
    val minutesUiModels: StateFlow<List<MinutesUiModel>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            minutesDataRepository.getAllMinutesWithMeetingTitle().map { list ->
                val filtered = if (query.isBlank()) list
                else list.filter { (minutes, _) ->
                    (minutes.minutesTitle ?: "").contains(query, ignoreCase = true)
                }
                filtered.map { (minutes, meetingTitle) ->
                    MinutesUiModel(minutes = minutes, meetingTitle = meetingTitle)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 하위 호환용 회의록 목록 (Minutes만 추출).
     * Plan 02에서 MinutesScreen이 minutesUiModels로 전환되면 제거 예정.
     */
    val minutes: StateFlow<List<Minutes>> = minutesUiModels
        .map { list -> list.map { it.minutes } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 회의록을 삭제한다 (Minutes ID 기반). */
    fun deleteMinutes(id: Long) {
        viewModelScope.launch {
            minutesDataRepository.deleteMinutes(id)
        }
    }

    /** 선택된 회의록을 일괄 삭제한다 (Minutes ID 기반). */
    fun deleteSelectedMinutes(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { minutesDataRepository.deleteMinutes(it) }
        }
    }

    /** 회의록 제목(minutesTitle)을 변경한다 (Minutes ID 기반). */
    fun updateMinutesTitle(minutesId: Long, newTitle: String) {
        viewModelScope.launch {
            minutesDataRepository.updateMinutesTitle(minutesId, newTitle)
        }
    }

    /**
     * 회의록 텍스트를 외부 앱으로 공유한다.
     * Minutes 엔티티에서 파일 경로를 읽어 ACTION_SEND Intent로 공유 시트를 띄운다.
     *
     * @param minutesId 공유할 Minutes의 ID
     * @param activityContext Activity Context (startActivity 호출용)
     */
    fun shareMinutes(minutesId: Long, activityContext: Context) {
        viewModelScope.launch {
            val minutesEntity = minutesDataRepository.getMinutesByIdOnce(minutesId)
                ?: return@launch

            val minutesText = try {
                File(minutesEntity.minutesPath).readText()
            } catch (e: Exception) {
                return@launch
            }
            if (minutesText.isBlank()) return@launch

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, minutesEntity.minutesTitle ?: "회의록")
                putExtra(Intent.EXTRA_TEXT, minutesText)
                type = "text/plain"
            }
            activityContext.startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    /**
     * 회의록 파일을 수동으로 Drive에 업로드한다.
     * DriveUploadWorker를 독립 enqueue한다.
     *
     * @param minutesId 업로드할 Minutes의 ID
     */
    fun uploadMinutesToDrive(minutesId: Long) {
        viewModelScope.launch {
            val minutes = minutesDataRepository.getMinutesByIdOnce(minutesId)
            if (minutes == null) {
                Log.w(TAG, "회의록 없음, Drive 업로드 불가: minutesId=$minutesId")
                return@launch
            }
            val workRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                .setInputData(workDataOf(
                    DriveUploadWorker.KEY_FILE_PATH to minutes.minutesPath,
                    DriveUploadWorker.KEY_FILE_TYPE to DriveUploadWorker.TYPE_MINUTES,
                    DriveUploadWorker.KEY_MEETING_ID to (minutes.meetingId ?: 0L)
                ))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "회의록 Drive 수동 업로드 enqueue: minutesId=$minutesId")
        }
    }

    /**
     * 회의록 파일의 내용을 읽어 반환한다.
     * 파일이 존재하지 않거나 읽기 실패 시 null을 반환한다.
     *
     * @param minutesPath 회의록 파일 절대 경로
     * @return 파일 내용 문자열 또는 null
     */
    fun getMinutesContent(minutesPath: String): String? {
        return try {
            val file = File(minutesPath)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "MinutesVM"
    }
}
