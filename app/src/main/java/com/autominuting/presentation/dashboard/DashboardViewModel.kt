package com.autominuting.presentation.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autominuting.data.audio.BleConnectionState
import com.autominuting.domain.model.Meeting
import com.autominuting.domain.model.MinutesFormat
import com.autominuting.domain.model.PipelineStatus
import com.autominuting.domain.repository.AudioRepository
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.service.AudioCollectionService
import com.autominuting.data.repository.MinutesRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * BLE 디버그 로그 항목.
 *
 * @param timestamp 로그 발생 시각 (HH:mm:ss)
 * @param message 로그 메시지
 */
data class BleLogEntry(
    val timestamp: String,
    val message: String
)

/**
 * 대시보드 화면의 상태를 관리하는 ViewModel.
 * 현재 진행 중인 파이프라인이 있으면 해당 회의 정보를 제공한다.
 * BLE 연결 상태를 실시간으로 노출하고, 디버그 로그를 기록한다.
 * 디버그 모드에서 테스트 데이터 삽입 및 Gemini API 테스트를 지원한다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val minutesRepository: MinutesRepository,
    private val audioRepository: AudioRepository,
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

    /** 오디오 수집 서비스 실행 상태 */
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    /** BLE 연결 상태 (AudioRepository에서 위임) */
    val bleConnectionState: StateFlow<BleConnectionState> =
        audioRepository.getBleConnectionState()

    /** BLE 디버그 로그 목록 (최신 항목이 앞쪽, 최대 20건) */
    private val _bleLogEntries = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val bleLogEntries: StateFlow<List<BleLogEntry>> = _bleLogEntries.asStateFlow()

    /** 시간 포맷터 */
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        // BLE 연결 상태 변화를 감지하여 로그에 기록
        observeBleState()
    }

    /** BLE 상태 변화를 구독하여 디버그 로그에 타임스탬프와 함께 추가한다 */
    private fun observeBleState() {
        viewModelScope.launch {
            var previousState: BleConnectionState? = null
            bleConnectionState.collect { newState ->
                if (previousState != null && previousState != newState) {
                    val timestamp = LocalTime.now().format(timeFormatter)
                    val prevName = getStateName(previousState!!)
                    val newName = getStateName(newState)
                    val entry = BleLogEntry(
                        timestamp = timestamp,
                        message = "상태변경: $prevName -> $newName"
                    )
                    // 최신 항목을 앞에 추가, 최대 20건 유지
                    _bleLogEntries.value = (listOf(entry) + _bleLogEntries.value).take(20)
                }
                previousState = newState
            }
        }
    }

    /** BleConnectionState의 표시용 이름을 반환한다 */
    private fun getStateName(state: BleConnectionState): String = when (state) {
        is BleConnectionState.IDLE -> "IDLE"
        is BleConnectionState.SCANNING -> "SCANNING"
        is BleConnectionState.DEVICE_FOUND -> "DEVICE_FOUND"
        is BleConnectionState.CONNECTING -> "CONNECTING"
        is BleConnectionState.CONNECTED -> "CONNECTED"
        is BleConnectionState.DISCONNECTED -> "DISCONNECTED"
        is BleConnectionState.ERROR -> "ERROR"
    }

    /** BLE 디버그 로그를 지운다 */
    fun clearBleLog() {
        _bleLogEntries.value = emptyList()
    }

    /** AudioCollectionService를 시작/중지하는 토글 함수 */
    fun toggleCollection() {
        try {
            val intent = Intent(context, AudioCollectionService::class.java)
            if (_isCollecting.value) {
                intent.action = AudioCollectionService.ACTION_STOP
                context.startService(intent)
                _isCollecting.value = false
            } else {
                intent.action = AudioCollectionService.ACTION_START
                context.startForegroundService(intent)
                _isCollecting.value = true
            }
        } catch (e: Exception) {
            _testStatus.value = "✗ 서비스 시작 실패: ${e.message}"
            _isCollecting.value = false
        }
    }

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
}
