package com.autominuting.data.quota

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Gemini API 호출 종류 구분 */
enum class QuotaCategory { STT, MINUTES }

/**
 * 날짜별 Gemini API 사용량 데이터.
 *
 * @param sttCount STT 전사 API 호출 횟수
 * @param minutesCount 회의록 생성 API 호출 횟수
 * @param dailyLimit 일일 RPD 한도
 * @param date 카운트 날짜 (yyyy-MM-dd)
 */
data class QuotaUsage(
    val sttCount: Int,
    val minutesCount: Int,
    val dailyLimit: Int,
    val date: String
) {
    /** 총 API 호출 횟수 */
    val totalCount: Int get() = sttCount + minutesCount

    /** 사용률 (0.0 ~ 1.0+) */
    val usagePercent: Float get() = totalCount.toFloat() / dailyLimit

    /** 90% 초과 여부 */
    val isOverThreshold: Boolean get() = usagePercent >= 0.9f

    /** 잔여 호출 가능 횟수 */
    val remaining: Int get() = (dailyLimit - totalCount).coerceAtLeast(0)
}

/**
 * Gemini Free 티어 일일 API 호출 쿼터를 추적한다.
 *
 * DataStore에 날짜별 STT/Minutes 호출 카운트를 저장하고,
 * 날짜가 바뀌면 자동으로 리셋한다.
 */
@Singleton
class GeminiQuotaTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        /** 카운트 날짜 (yyyy-MM-dd) */
        val QUOTA_DATE_KEY = stringPreferencesKey("gemini_quota_date")

        /** STT 호출 카운트 */
        val QUOTA_STT_COUNT_KEY = intPreferencesKey("gemini_quota_stt_count")

        /** 회의록 생성 호출 카운트 */
        val QUOTA_MINUTES_COUNT_KEY = intPreferencesKey("gemini_quota_minutes_count")

        /** Gemini Free 일일 RPD 한도 */
        const val DAILY_LIMIT = 1500
    }

    /**
     * 현재 쿼터 사용량을 관찰한다.
     * 저장된 날짜가 오늘과 다르면 카운트 0으로 반환 (자동 리셋 효과).
     */
    val usage: Flow<QuotaUsage> = dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val savedDate = prefs[QUOTA_DATE_KEY] ?: ""

        if (savedDate == today) {
            QuotaUsage(
                sttCount = prefs[QUOTA_STT_COUNT_KEY] ?: 0,
                minutesCount = prefs[QUOTA_MINUTES_COUNT_KEY] ?: 0,
                dailyLimit = DAILY_LIMIT,
                date = today
            )
        } else {
            // 날짜가 다르면 리셋된 값 반환
            QuotaUsage(
                sttCount = 0,
                minutesCount = 0,
                dailyLimit = DAILY_LIMIT,
                date = today
            )
        }
    }

    /**
     * API 호출 성공 시 사용량을 기록한다.
     * 날짜가 오늘과 다르면 먼저 리셋 후 1로 설정한다.
     *
     * @param category API 호출 종류 (STT 또는 MINUTES)
     */
    suspend fun recordUsage(category: QuotaCategory) {
        dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val savedDate = prefs[QUOTA_DATE_KEY] ?: ""

            // 날짜가 바뀌었으면 리셋
            if (savedDate != today) {
                prefs[QUOTA_DATE_KEY] = today
                prefs[QUOTA_STT_COUNT_KEY] = 0
                prefs[QUOTA_MINUTES_COUNT_KEY] = 0
            }

            // 해당 카테고리 카운트 증가
            when (category) {
                QuotaCategory.STT -> {
                    val current = prefs[QUOTA_STT_COUNT_KEY] ?: 0
                    prefs[QUOTA_STT_COUNT_KEY] = current + 1
                }
                QuotaCategory.MINUTES -> {
                    val current = prefs[QUOTA_MINUTES_COUNT_KEY] ?: 0
                    prefs[QUOTA_MINUTES_COUNT_KEY] = current + 1
                }
            }
        }
    }
}
