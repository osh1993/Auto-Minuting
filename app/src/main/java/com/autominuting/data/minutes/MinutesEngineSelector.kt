package com.autominuting.data.minutes

import android.util.Log
import com.autominuting.data.auth.AuthMode
import com.autominuting.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 인증 모드(API 키/OAuth)에 따라 적절한 MinutesEngine을 선택하는 위임 클래스.
 *
 * DataStore의 authMode 설정을 기반으로 GeminiEngine 또는 GeminiOAuthEngine을 사용한다.
 * OAuth 엔진이 사용 불가능한 경우 API 키 엔진으로 자동 폴백한다.
 */
@Singleton
class MinutesEngineSelector @Inject constructor(
    private val geminiEngineProvider: Provider<GeminiEngine>,
    private val geminiOAuthEngineProvider: Provider<GeminiOAuthEngine>,
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
        // 두 엔진 중 하나라도 사용 가능하면 true
        return geminiEngineProvider.get().isAvailable() ||
                geminiOAuthEngineProvider.get().isAvailable()
    }

    /**
     * 현재 설정된 인증 모드에 따라 엔진을 선택한다.
     * OAuth 모드이지만 로그인되지 않은 경우 API 키 엔진으로 폴백한다.
     */
    private suspend fun selectEngine(): MinutesEngine {
        val authMode = userPreferencesRepository.authMode.first()

        return when (authMode) {
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
}
