package com.autominuting.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autominuting.data.auth.AuthMode
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat
import com.autominuting.domain.model.SttEngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 설정을 DataStore로 관리하는 Repository.
 *
 * 회의록 형식(MinutesFormat), 자동화 모드(AutomationMode),
 * 인증 모드(AuthMode), Google 계정 정보를 Flow로 관찰하거나 즉시 조회할 수 있다.
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

        /** 인증 모드 설정 키 */
        val AUTH_MODE_KEY = stringPreferencesKey("auth_mode")

        /** Google 계정 이메일 */
        val GOOGLE_EMAIL_KEY = stringPreferencesKey("google_email")

        /** Google 계정 표시 이름 */
        val GOOGLE_DISPLAY_NAME_KEY = stringPreferencesKey("google_display_name")

        /** STT 엔진 유형 설정 키 */
        val STT_ENGINE_KEY = stringPreferencesKey("stt_engine")

        /** 기본 프롬프트 템플릿 ID 설정 키 (0 = 미설정, 매번 선택) */
        val DEFAULT_TEMPLATE_ID_KEY = longPreferencesKey("default_template_id")
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

    /** 현재 인증 모드 설정을 관찰한다. 기본값: API_KEY */
    val authMode: Flow<AuthMode> = dataStore.data.map { prefs ->
        val name = prefs[AUTH_MODE_KEY] ?: AuthMode.API_KEY.name
        AuthMode.valueOf(name)
    }

    /** 현재 STT 엔진 유형을 관찰한다. 기본값: GEMINI */
    val sttEngineType: Flow<SttEngineType> = dataStore.data.map { prefs ->
        val name = prefs[STT_ENGINE_KEY] ?: SttEngineType.GEMINI.name
        SttEngineType.valueOf(name)
    }

    /** 기본 프롬프트 템플릿 ID를 관찰한다. 기본값: 0L (미설정, 매번 선택) */
    val defaultTemplateId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[DEFAULT_TEMPLATE_ID_KEY] ?: 0L
    }

    /** 저장된 Google 계정 이메일을 관찰한다. */
    val googleEmail: Flow<String?> = dataStore.data.map { prefs ->
        prefs[GOOGLE_EMAIL_KEY]
    }

    /** 저장된 Google 계정 표시 이름을 관찰한다. */
    val googleDisplayName: Flow<String?> = dataStore.data.map { prefs ->
        prefs[GOOGLE_DISPLAY_NAME_KEY]
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
     * 인증 모드를 변경한다.
     * @param mode 새로운 인증 모드
     */
    suspend fun setAuthMode(mode: AuthMode) {
        dataStore.edit { prefs ->
            prefs[AUTH_MODE_KEY] = mode.name
        }
    }

    /**
     * STT 엔진 유형을 변경한다.
     * @param type 새로운 STT 엔진 유형
     */
    suspend fun setSttEngineType(type: SttEngineType) {
        dataStore.edit { prefs ->
            prefs[STT_ENGINE_KEY] = type.name
        }
    }

    /**
     * Google 계정 정보를 저장한다.
     * @param displayName 표시 이름
     * @param email 이메일 주소
     */
    suspend fun setGoogleAccount(displayName: String, email: String) {
        dataStore.edit { prefs ->
            prefs[GOOGLE_DISPLAY_NAME_KEY] = displayName
            prefs[GOOGLE_EMAIL_KEY] = email
        }
    }

    /**
     * Google 계정 정보를 삭제한다.
     */
    suspend fun clearGoogleAccount() {
        dataStore.edit { prefs ->
            prefs.remove(GOOGLE_DISPLAY_NAME_KEY)
            prefs.remove(GOOGLE_EMAIL_KEY)
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

    /**
     * 현재 STT 엔진 유형을 즉시 조회한다 (Worker 컨텍스트 등에서 사용).
     * @return 현재 저장된 STT 엔진 유형. 기본값: GEMINI
     */
    suspend fun getSttEngineTypeOnce(): SttEngineType =
        dataStore.data.first().let { prefs ->
            SttEngineType.valueOf(prefs[STT_ENGINE_KEY] ?: SttEngineType.GEMINI.name)
        }

    /**
     * 기본 프롬프트 템플릿 ID를 변경한다.
     * @param id 템플릿 ID (0이면 미설정 = 매번 선택)
     */
    suspend fun setDefaultTemplateId(id: Long) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_TEMPLATE_ID_KEY] = id
        }
    }

    /**
     * 현재 기본 프롬프트 템플릿 ID를 즉시 조회한다.
     * @return 저장된 기본 템플릿 ID. 기본값: 0L (미설정)
     */
    suspend fun getDefaultTemplateIdOnce(): Long =
        dataStore.data.first()[DEFAULT_TEMPLATE_ID_KEY] ?: 0L
}
