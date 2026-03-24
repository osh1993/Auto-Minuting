package com.autominuting.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 설정을 DataStore로 관리하는 Repository.
 *
 * 회의록 형식(MinutesFormat)과 자동화 모드(AutomationMode) 설정을
 * Flow로 관찰하거나 즉시 조회할 수 있다.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        /** 회의록 형식 설정 키 */
        val MINUTES_FORMAT_KEY = stringPreferencesKey("minutes_format")

        /** 자동화 모드 설정 키 */
        val AUTOMATION_MODE_KEY = stringPreferencesKey("automation_mode")
    }

    /** 현재 회의록 형식 설정을 관찰한다. 기본값: STRUCTURED */
    val minutesFormat: Flow<MinutesFormat> = dataStore.data.map { prefs ->
        val name = prefs[MINUTES_FORMAT_KEY] ?: MinutesFormat.STRUCTURED.name
        MinutesFormat.valueOf(name)
    }

    /** 현재 자동화 모드 설정을 관찰한다. 기본값: FULL_AUTO */
    val automationMode: Flow<AutomationMode> = dataStore.data.map { prefs ->
        val name = prefs[AUTOMATION_MODE_KEY] ?: AutomationMode.FULL_AUTO.name
        AutomationMode.valueOf(name)
    }

    /**
     * 회의록 형식을 변경한다.
     * @param format 새로운 회의록 형식
     */
    suspend fun setMinutesFormat(format: MinutesFormat) {
        dataStore.edit { prefs ->
            prefs[MINUTES_FORMAT_KEY] = format.name
        }
    }

    /**
     * 자동화 모드를 변경한다.
     * @param mode 새로운 자동화 모드
     */
    suspend fun setAutomationMode(mode: AutomationMode) {
        dataStore.edit { prefs ->
            prefs[AUTOMATION_MODE_KEY] = mode.name
        }
    }

    /**
     * 현재 회의록 형식을 즉시 조회한다 (Worker enqueue 시점 등에서 사용).
     * @return 현재 저장된 회의록 형식. 기본값: STRUCTURED
     */
    suspend fun getMinutesFormatOnce(): MinutesFormat =
        dataStore.data.first().let { prefs ->
            MinutesFormat.valueOf(prefs[MINUTES_FORMAT_KEY] ?: MinutesFormat.STRUCTURED.name)
        }

    /**
     * 현재 자동화 모드를 즉시 조회한다 (Worker enqueue 시점 등에서 사용).
     * @return 현재 저장된 자동화 모드. 기본값: FULL_AUTO
     */
    suspend fun getAutomationModeOnce(): AutomationMode =
        dataStore.data.first().let { prefs ->
            AutomationMode.valueOf(prefs[AUTOMATION_MODE_KEY] ?: AutomationMode.FULL_AUTO.name)
        }
}
