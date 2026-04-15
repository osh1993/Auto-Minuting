package com.autominuting.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autominuting.data.auth.AuthMode
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesEngineType
import com.autominuting.domain.model.SttEngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 설정을 DataStore로 관리하는 Repository.
 *
 * 자동화 모드(AutomationMode), 인증 모드(AuthMode),
 * Google 계정 정보를 Flow로 관찰하거나 즉시 조회할 수 있다.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
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

        /** 직접 입력 모드를 나타내는 특수 템플릿 ID */
        const val CUSTOM_PROMPT_MODE_ID = -1L

        /** 직접 입력 기본 프롬프트 키 */
        val DEFAULT_CUSTOM_PROMPT_KEY = stringPreferencesKey("default_custom_prompt")

        /** 회의록 엔진 유형 설정 키 */
        val MINUTES_ENGINE_KEY = stringPreferencesKey("minutes_engine")

        /** Drive 인증 완료 여부 설정 키 (토큰은 저장하지 않음 — 민감 정보) */
        val DRIVE_AUTHORIZED_KEY = booleanPreferencesKey("drive_authorized")

        /** DRIVE-04: 전사 파일 Drive 업로드 폴더 ID. 빈 문자열 = 업로드 비활성 */
        val DRIVE_TRANSCRIPT_FOLDER_KEY = stringPreferencesKey("drive_transcript_folder_id")

        /** DRIVE-04: 회의록 Drive 업로드 폴더 ID. 빈 문자열 = 업로드 비활성 */
        val DRIVE_MINUTES_FOLDER_KEY = stringPreferencesKey("drive_minutes_folder_id")

        /** Drive 자동 업로드 활성화 여부 설정 키. 기본값: true */
        val DRIVE_AUTO_UPLOAD_ENABLED_KEY = booleanPreferencesKey("drive_auto_upload_enabled")

        /** GEMINI-02: 라운드로빈 현재 인덱스 설정 키. 실제 사용은 Phase 52. */
        val GEMINI_ROUNDROBIN_INDEX_KEY = intPreferencesKey("gemini_roundrobin_index")
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
        try {
            SttEngineType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            SttEngineType.GEMINI  // 알 수 없는 값 -> 기본값 폴백
        }
    }

    /** 기본 프롬프트 템플릿 ID를 관찰한다. 기본값: 0L (미설정, 매번 선택) */
    val defaultTemplateId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[DEFAULT_TEMPLATE_ID_KEY] ?: 0L
    }

    /** 기본 커스텀 프롬프트 텍스트를 관찰한다. 기본값: 빈 문자열 */
    val defaultCustomPrompt: Flow<String> = dataStore.data.map { prefs ->
        prefs[DEFAULT_CUSTOM_PROMPT_KEY] ?: ""
    }

    /** 현재 회의록 엔진 유형을 관찰한다. 기본값: GEMINI */
    val minutesEngineType: Flow<MinutesEngineType> = dataStore.data.map { prefs ->
        val name = prefs[MINUTES_ENGINE_KEY] ?: MinutesEngineType.GEMINI.name
        try {
            MinutesEngineType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            MinutesEngineType.GEMINI  // 알 수 없는 값 -> 기본값 폴백
        }
    }

    /** 저장된 Google 계정 이메일을 관찰한다. */
    val googleEmail: Flow<String?> = dataStore.data.map { prefs ->
        prefs[GOOGLE_EMAIL_KEY]
    }

    /** 저장된 Google 계정 표시 이름을 관찰한다. */
    val googleDisplayName: Flow<String?> = dataStore.data.map { prefs ->
        prefs[GOOGLE_DISPLAY_NAME_KEY]
    }

    /** Drive 인증 완료 여부를 관찰한다. 기본값: false */
    val driveAuthorized: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DRIVE_AUTHORIZED_KEY] ?: false
    }

    /** 전사 Drive 폴더 ID를 관찰한다. 빈 문자열 = 업로드 비활성 (per DRIVE-04) */
    val driveTranscriptFolderId: Flow<String> = dataStore.data.map { prefs ->
        prefs[DRIVE_TRANSCRIPT_FOLDER_KEY] ?: ""
    }

    /** 회의록 Drive 폴더 ID를 관찰한다. 빈 문자열 = 업로드 비활성 (per DRIVE-04) */
    val driveMinutesFolderId: Flow<String> = dataStore.data.map { prefs ->
        prefs[DRIVE_MINUTES_FOLDER_KEY] ?: ""
    }

    /** Drive 자동 업로드 활성화 여부를 관찰한다. 기본값: true */
    val driveAutoUploadEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DRIVE_AUTO_UPLOAD_ENABLED_KEY] ?: true
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
     * Drive 인증 완료 여부를 저장한다.
     * @param authorized true = 인증 완료, false = 인증 해제
     */
    suspend fun setDriveAuthorized(authorized: Boolean) {
        dataStore.edit { prefs ->
            prefs[DRIVE_AUTHORIZED_KEY] = authorized
        }
    }

    /**
     * Drive 인증 여부를 삭제한다.
     */
    suspend fun clearDriveAuthorized() {
        dataStore.edit { prefs ->
            prefs.remove(DRIVE_AUTHORIZED_KEY)
        }
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
            val name = prefs[STT_ENGINE_KEY] ?: SttEngineType.GEMINI.name
            try {
                SttEngineType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                SttEngineType.GEMINI  // 알 수 없는 값 -> 기본값 폴백
            }
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

    /** 기본 커스텀 프롬프트 텍스트를 저장한다. */
    suspend fun setDefaultCustomPrompt(prompt: String) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_CUSTOM_PROMPT_KEY] = prompt
        }
    }

    /** 현재 기본 커스텀 프롬프트를 즉시 조회한다. */
    suspend fun getDefaultCustomPromptOnce(): String =
        dataStore.data.first()[DEFAULT_CUSTOM_PROMPT_KEY] ?: ""

    /** 전사 Drive 폴더 ID를 저장한다. 빈 문자열로 저장하면 업로드 비활성 (per DRIVE-04) */
    suspend fun setDriveTranscriptFolderId(folderId: String) {
        dataStore.edit { prefs -> prefs[DRIVE_TRANSCRIPT_FOLDER_KEY] = folderId }
    }

    /** 회의록 Drive 폴더 ID를 저장한다. 빈 문자열로 저장하면 업로드 비활성 (per DRIVE-04) */
    suspend fun setDriveMinutesFolderId(folderId: String) {
        dataStore.edit { prefs -> prefs[DRIVE_MINUTES_FOLDER_KEY] = folderId }
    }

    /** 전사 Drive 폴더 ID를 즉시 조회한다 (Worker 컨텍스트용) */
    suspend fun getDriveTranscriptFolderIdOnce(): String =
        dataStore.data.first()[DRIVE_TRANSCRIPT_FOLDER_KEY] ?: ""

    /** 회의록 Drive 폴더 ID를 즉시 조회한다 (Worker 컨텍스트용) */
    suspend fun getDriveMinutesFolderIdOnce(): String =
        dataStore.data.first()[DRIVE_MINUTES_FOLDER_KEY] ?: ""

    /** Drive 자동 업로드 활성화 여부를 저장한다. */
    suspend fun setDriveAutoUploadEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DRIVE_AUTO_UPLOAD_ENABLED_KEY] = enabled }
    }

    /** Drive 자동 업로드 활성화 여부를 즉시 조회한다 (Worker 컨텍스트용). 기본값: true */
    suspend fun getDriveAutoUploadEnabledOnce(): Boolean =
        dataStore.data.first()[DRIVE_AUTO_UPLOAD_ENABLED_KEY] ?: true

    /** 회의록 엔진 유형을 변경한다. */
    suspend fun setMinutesEngineType(type: MinutesEngineType) {
        dataStore.edit { prefs ->
            prefs[MINUTES_ENGINE_KEY] = type.name
        }
    }

    /** 현재 회의록 엔진 유형을 즉시 조회한다. */
    suspend fun getMinutesEngineTypeOnce(): MinutesEngineType =
        dataStore.data.first().let { prefs ->
            val name = prefs[MINUTES_ENGINE_KEY] ?: MinutesEngineType.GEMINI.name
            try {
                MinutesEngineType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                MinutesEngineType.GEMINI
            }
        }
}
