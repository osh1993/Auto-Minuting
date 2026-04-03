package com.autominuting.data.quota

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 엔진별 API 호출 횟수를 DataStore에 누적 저장한다.
 * GeminiQuotaTracker와 달리 날짜 리셋 없이 총 누적 횟수를 관리한다.
 * DataStore 키: "api_usage_{engineKey}" (intPreferencesKey)
 */
@Singleton
class ApiUsageTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        // STT 엔진 키
        const val KEY_GEMINI_STT = "GEMINI_STT"
        const val KEY_WHISPER_STT = "WHISPER_STT"
        const val KEY_GROQ_STT = "GROQ_STT"
        const val KEY_DEEPGRAM_STT = "DEEPGRAM_STT"
        const val KEY_NAVER_STT = "NAVER_STT"

        // Minutes 엔진 키
        const val KEY_GEMINI_MINUTES = "GEMINI_MINUTES"
        const val KEY_DEEPGRAM_MINUTES = "DEEPGRAM_MINUTES"
        const val KEY_NAVER_MINUTES = "NAVER_MINUTES"

        /** 모든 엔진 키 목록 */
        val ALL_KEYS = listOf(
            KEY_GEMINI_STT, KEY_WHISPER_STT, KEY_GROQ_STT, KEY_DEEPGRAM_STT, KEY_NAVER_STT,
            KEY_GEMINI_MINUTES, KEY_DEEPGRAM_MINUTES, KEY_NAVER_MINUTES
        )

        /**
         * 엔진별 예상 단가 (USD/call).
         * 무료 엔진은 0.0, 유료 엔진은 추정 단가 사용.
         */
        val ESTIMATED_COST_PER_CALL: Map<String, Double> = mapOf(
            KEY_GEMINI_STT to 0.0,
            KEY_WHISPER_STT to 0.0,
            KEY_GROQ_STT to 0.001,
            KEY_DEEPGRAM_STT to 0.005,
            KEY_NAVER_STT to 0.003,
            KEY_GEMINI_MINUTES to 0.0,
            KEY_DEEPGRAM_MINUTES to 0.01,
            KEY_NAVER_MINUTES to 0.005
        )

        /** DataStore 키 생성 헬퍼 */
        internal fun prefKey(engineKey: String) =
            intPreferencesKey("api_usage_$engineKey")
    }

    /**
     * 모든 엔진 키 → 호출 횟수 Map을 Flow로 노출한다.
     * 저장되지 않은 엔진은 0으로 반환한다.
     */
    val usageMap: Flow<Map<String, Int>> = dataStore.data.map { prefs ->
        ALL_KEYS.associateWith { key -> prefs[prefKey(key)] ?: 0 }
    }

    /**
     * API 호출 성공 시 해당 엔진의 횟수를 1 증가시킨다.
     * @param engineKey ApiUsageTracker.KEY_* 상수 중 하나
     */
    suspend fun record(engineKey: String) {
        dataStore.edit { prefs ->
            val key = prefKey(engineKey)
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    /**
     * 모든 엔진의 호출 횟수를 0으로 초기화한다 (테스트/개발용).
     */
    suspend fun resetAll() {
        dataStore.edit { prefs ->
            ALL_KEYS.forEach { engineKey -> prefs[prefKey(engineKey)] = 0 }
        }
    }
}
