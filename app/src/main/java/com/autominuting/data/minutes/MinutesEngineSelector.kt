package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.data.auth.AuthMode
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.domain.model.MinutesEngineType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 인증 모드(API 키/OAuth) 및 회의록 엔진 유형(MinutesEngineType)에 따라
 * 적절한 MinutesEngine을 선택하는 위임 클래스.
 *
 * GEMINI 타입일 때는 기존 authMode 기반 서브셀렉션(API 키/OAuth)을 적용하고,
 * DEEPGRAM/NAVER_CLOVA 타입일 때는 해당 엔진을 직접 반환한다.
 */
@Singleton
class MinutesEngineSelector @Inject constructor(
    private val geminiEngineProvider: Provider<GeminiEngine>,
    private val geminiOAuthEngineProvider: Provider<GeminiOAuthEngine>,
    private val deepgramEngineProvider: Provider<DeepgramMinutesEngine>,
    private val clovaEngineProvider: Provider<NaverClovaMinutesEngine>,
    private val userPreferencesRepository: UserPreferencesRepository
) : MinutesEngine {

    companion object {
        private const val TAG = "MinutesEngineSelector"
    }

    /**
     * 현재 인증 모드에 따라 적절한 엔진을 선택하여 회의록을 생성한다.
     */
    override suspend fun generate(
        transcriptText: String,
        customPrompt: String?
    ): Result<String> {
        val engine = selectEngine()
        Log.d(TAG, "선택된 엔진: ${engine.engineName()}")
        return engine.generate(transcriptText, customPrompt)
    }

    override fun engineName(): String = "MinutesEngineSelector"

    override fun isAvailable(): Boolean {
        // Gemini 엔진 중 하나라도, 또는 Deepgram, 또는 CLOVA가 사용 가능하면 true
        return geminiEngineProvider.get().isAvailable() ||
                geminiOAuthEngineProvider.get().isAvailable() ||
                deepgramEngineProvider.get().isAvailable() ||
                clovaEngineProvider.get().isAvailable()
    }

    /**
     * 현재 설정된 회의록 엔진 유형에 따라 엔진을 선택한다.
     * GEMINI 타입일 때만 기존 authMode 기반 서브셀렉션을 적용한다.
     */
    private suspend fun selectEngine(): MinutesEngine {
        val engineType = userPreferencesRepository.getMinutesEngineTypeOnce()

        return when (engineType) {
            MinutesEngineType.GEMINI -> {
                // 기존 authMode 기반 Gemini 엔진 선택 로직 유지
                val authMode = userPreferencesRepository.authMode.first()
                when (authMode) {
                    AuthMode.OAUTH -> {
                        val oauthEngine = geminiOAuthEngineProvider.get()
                        if (oauthEngine.isAvailable()) {
                            oauthEngine
                        } else {
                            Log.w(TAG, "OAuth 엔진 사용 불가 — API 키 엔진으로 폴백")
                            geminiEngineProvider.get()
                        }
                    }
                    AuthMode.API_KEY -> geminiEngineProvider.get()
                }
            }
            MinutesEngineType.DEEPGRAM -> deepgramEngineProvider.get()
            MinutesEngineType.NAVER_CLOVA -> clovaEngineProvider.get()
        }
    }
}
