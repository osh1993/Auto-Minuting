package com.autominuting.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API 키를 EncryptedSharedPreferences로 암호화하여 저장/조회하는 Repository.
 * 기존 DataStore와 별도 저장소로 분리하여 민감 데이터의 관심사를 분리한다.
 */
@Singleton
class SecureApiKeyRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureApiKeyRepository"
        private const val PREFS_FILE = "secure_api_keys"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_GOOGLE_OAUTH_CLIENT_ID = "google_oauth_client_id"
        private const val KEY_GROQ_API = "groq_api_key"
        private const val KEY_DEEPGRAM_API = "deepgram_api_key"
        private const val KEY_CLOVA_INVOKE_URL = "clova_invoke_url"
        private const val KEY_CLOVA_SECRET_KEY = "clova_secret_key"
        private const val KEY_CLOVA_SUMMARY_CLIENT_ID = "clova_summary_client_id"
        private const val KEY_CLOVA_SUMMARY_CLIENT_SECRET = "clova_summary_client_secret"

        /** Drive access token — 메모리 캐시 소실 대비 단기 저장 (Worker 실행 시 사용) */
        private const val KEY_DRIVE_ACCESS_TOKEN = "drive_access_token"

        // 다중 Gemini API 키 (GEMINI-01, GEMINI-04)
        private const val KEY_GEMINI_KEY_COUNT = "gemini_api_key_count"
        private fun keyGeminiLabel(i: Int) = "gemini_api_key_${i}_label"
        private fun keyGeminiValue(i: Int) = "gemini_api_key_${i}_value"

        /** 라운드로빈 인덱스 저장 키. EncryptedSharedPreferences String 타입으로 저장. */
        private const val KEY_RR_INDEX = "gemini_roundrobin_index"
    }

    @Suppress("DEPRECATION")
    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // OEM별 KeyStore 초기화 실패 방어 (Samsung/Xiaomi 일부 기기)
            Log.e(TAG, "EncryptedSharedPreferences 초기화 실패", e)
            null
        }
    }

    /** 저장된 Gemini API 키를 반환한다. 없거나 초기화 실패 시 null. */
    fun getGeminiApiKey(): String? =
        encryptedPrefs?.getString(KEY_GEMINI_API, null)

    /** Gemini API 키를 암호화하여 저장한다. */
    fun saveGeminiApiKey(apiKey: String) {
        encryptedPrefs?.edit()?.putString(KEY_GEMINI_API, apiKey)?.apply()
            ?: Log.w(TAG, "API 키 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 Gemini API 키를 삭제한다. */
    fun clearGeminiApiKey() {
        encryptedPrefs?.edit()?.remove(KEY_GEMINI_API)?.apply()
    }

    /** 저장된 Google OAuth Web Client ID를 반환한다. 없거나 초기화 실패 시 null. */
    fun getGoogleOAuthClientId(): String? =
        encryptedPrefs?.getString(KEY_GOOGLE_OAUTH_CLIENT_ID, null)

    /** Google OAuth Web Client ID를 암호화하여 저장한다. */
    fun saveGoogleOAuthClientId(clientId: String) {
        encryptedPrefs?.edit()?.putString(KEY_GOOGLE_OAUTH_CLIENT_ID, clientId)?.apply()
            ?: Log.w(TAG, "OAuth Client ID 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 Google OAuth Web Client ID를 삭제한다. */
    fun clearGoogleOAuthClientId() {
        encryptedPrefs?.edit()?.remove(KEY_GOOGLE_OAUTH_CLIENT_ID)?.apply()
    }

    /** 저장된 Groq API 키를 반환한다. 없거나 초기화 실패 시 null. */
    fun getGroqApiKey(): String? =
        encryptedPrefs?.getString(KEY_GROQ_API, null)

    /** Groq API 키를 암호화하여 저장한다. */
    fun saveGroqApiKey(apiKey: String) {
        encryptedPrefs?.edit()?.putString(KEY_GROQ_API, apiKey)?.apply()
            ?: Log.w(TAG, "Groq API 키 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 Groq API 키를 삭제한다. */
    fun clearGroqApiKey() {
        encryptedPrefs?.edit()?.remove(KEY_GROQ_API)?.apply()
    }

    /** 저장된 Deepgram API 키를 반환한다. */
    fun getDeepgramApiKey(): String? =
        encryptedPrefs?.getString(KEY_DEEPGRAM_API, null)

    /** Deepgram API 키를 암호화하여 저장한다. */
    fun saveDeepgramApiKey(apiKey: String) {
        encryptedPrefs?.edit()?.putString(KEY_DEEPGRAM_API, apiKey)?.apply()
            ?: Log.w(TAG, "Deepgram API 키 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 Deepgram API 키를 삭제한다. */
    fun clearDeepgramApiKey() {
        encryptedPrefs?.edit()?.remove(KEY_DEEPGRAM_API)?.apply()
    }

    /** 저장된 CLOVA Speech invoke URL을 반환한다. */
    fun getClovaInvokeUrl(): String? =
        encryptedPrefs?.getString(KEY_CLOVA_INVOKE_URL, null)

    /** CLOVA Speech invoke URL을 저장한다. */
    fun saveClovaInvokeUrl(url: String) {
        encryptedPrefs?.edit()?.putString(KEY_CLOVA_INVOKE_URL, url)?.apply()
            ?: Log.w(TAG, "CLOVA invoke URL 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 CLOVA Speech invoke URL을 삭제한다. */
    fun clearClovaInvokeUrl() {
        encryptedPrefs?.edit()?.remove(KEY_CLOVA_INVOKE_URL)?.apply()
    }

    /** 저장된 CLOVA Speech Secret Key를 반환한다. */
    fun getClovaSecretKey(): String? =
        encryptedPrefs?.getString(KEY_CLOVA_SECRET_KEY, null)

    /** CLOVA Speech Secret Key를 암호화하여 저장한다. */
    fun saveClovaSecretKey(secretKey: String) {
        encryptedPrefs?.edit()?.putString(KEY_CLOVA_SECRET_KEY, secretKey)?.apply()
            ?: Log.w(TAG, "CLOVA Secret Key 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 CLOVA Speech Secret Key를 삭제한다. */
    fun clearClovaSecretKey() {
        encryptedPrefs?.edit()?.remove(KEY_CLOVA_SECRET_KEY)?.apply()
    }

    /** 저장된 CLOVA Summary Client ID를 반환한다. */
    fun getClovaSummaryClientId(): String? =
        encryptedPrefs?.getString(KEY_CLOVA_SUMMARY_CLIENT_ID, null)

    /** CLOVA Summary Client ID를 암호화하여 저장한다. */
    fun saveClovaSummaryClientId(clientId: String) {
        encryptedPrefs?.edit()?.putString(KEY_CLOVA_SUMMARY_CLIENT_ID, clientId)?.apply()
            ?: Log.w(TAG, "CLOVA Summary Client ID 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 CLOVA Summary Client ID를 삭제한다. */
    fun clearClovaSummaryClientId() {
        encryptedPrefs?.edit()?.remove(KEY_CLOVA_SUMMARY_CLIENT_ID)?.apply()
    }

    /** 저장된 CLOVA Summary Client Secret을 반환한다. */
    fun getClovaSummaryClientSecret(): String? =
        encryptedPrefs?.getString(KEY_CLOVA_SUMMARY_CLIENT_SECRET, null)

    /** CLOVA Summary Client Secret을 암호화하여 저장한다. */
    fun saveClovaSummaryClientSecret(secret: String) {
        encryptedPrefs?.edit()?.putString(KEY_CLOVA_SUMMARY_CLIENT_SECRET, secret)?.apply()
            ?: Log.w(TAG, "CLOVA Summary Client Secret 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** 저장된 CLOVA Summary Client Secret을 삭제한다. */
    fun clearClovaSummaryClientSecret() {
        encryptedPrefs?.edit()?.remove(KEY_CLOVA_SUMMARY_CLIENT_SECRET)?.apply()
    }

    /** Drive access token을 반환한다. 없거나 초기화 실패 시 null. */
    fun getDriveAccessToken(): String? =
        encryptedPrefs?.getString(KEY_DRIVE_ACCESS_TOKEN, null)

    /** Drive access token을 암호화하여 저장한다. */
    fun saveDriveAccessToken(token: String) {
        encryptedPrefs?.edit()?.putString(KEY_DRIVE_ACCESS_TOKEN, token)?.apply()
            ?: Log.w(TAG, "Drive access token 저장 실패: EncryptedSharedPreferences 사용 불가")
    }

    /** Drive access token을 삭제한다. */
    fun clearDriveAccessToken() {
        encryptedPrefs?.edit()?.remove(KEY_DRIVE_ACCESS_TOKEN)?.apply()
    }

    /**
     * 기존 단일 gemini_api_key 값을 다중 키 구조의 index 0으로 마이그레이션한다.
     * 이미 마이그레이션되었거나 레거시 키가 없으면 아무것도 하지 않는다.
     * SettingsViewModel.init에서 호출한다.
     */
    fun migrateGeminiApiKeyIfNeeded() {
        val prefs = encryptedPrefs ?: return
        val legacyKey = prefs.getString(KEY_GEMINI_API, null) ?: return
        // 이미 다중 키 구조가 존재하면 마이그레이션 건너뜀
        val count = prefs.getString(KEY_GEMINI_KEY_COUNT, "0")?.toIntOrNull() ?: 0
        if (count > 0) {
            prefs.edit().remove(KEY_GEMINI_API).apply()
            return
        }
        // index 0으로 이동
        prefs.edit()
            .putString(keyGeminiLabel(0), "기본 키")
            .putString(keyGeminiValue(0), legacyKey)
            .putString(KEY_GEMINI_KEY_COUNT, "1")
            .remove(KEY_GEMINI_API)
            .apply()
        Log.i(TAG, "레거시 gemini_api_key → 다중 키 index 0으로 마이그레이션 완료")
    }

    /**
     * 등록된 Gemini API 키 목록을 반환한다. 키 값은 마스킹 처리된다.
     * 마스킹 형식: AIza****WXYZ (앞 4자 + **** + 뒤 4자; 총 길이 < 8이면 전체 마스킹)
     */
    fun getGeminiApiKeys(): List<GeminiApiKeyEntry> {
        val prefs = encryptedPrefs ?: return emptyList()
        val count = prefs.getString(KEY_GEMINI_KEY_COUNT, "0")?.toIntOrNull() ?: 0
        return (0 until count).mapNotNull { i ->
            val label = prefs.getString(keyGeminiLabel(i), null) ?: return@mapNotNull null
            val value = prefs.getString(keyGeminiValue(i), null) ?: return@mapNotNull null
            val masked = maskApiKey(value)
            GeminiApiKeyEntry(label = label, maskedKey = masked, index = i)
        }
    }

    /**
     * 새 Gemini API 키를 목록 끝에 추가한다.
     * 중복 키 값 검사: 이미 동일한 키 값이 존재하면 IllegalArgumentException 발생.
     */
    fun addGeminiApiKey(label: String, key: String) {
        val prefs = encryptedPrefs ?: run {
            Log.w(TAG, "addGeminiApiKey 실패: EncryptedSharedPreferences 사용 불가")
            return
        }
        val count = prefs.getString(KEY_GEMINI_KEY_COUNT, "0")?.toIntOrNull() ?: 0
        // 중복 검사
        for (i in 0 until count) {
            if (prefs.getString(keyGeminiValue(i), null) == key) {
                throw IllegalArgumentException("이미 등록된 API 키입니다.")
            }
        }
        prefs.edit()
            .putString(keyGeminiLabel(count), label)
            .putString(keyGeminiValue(count), key)
            .putString(KEY_GEMINI_KEY_COUNT, (count + 1).toString())
            .apply()
        Log.i(TAG, "Gemini API 키 추가: index=$count, label=$label")
    }

    /**
     * 지정 인덱스의 Gemini API 키를 삭제하고 인덱스를 재정렬한다.
     * @param index 삭제할 키의 인덱스 (0-based)
     */
    fun removeGeminiApiKey(index: Int) {
        val prefs = encryptedPrefs ?: run {
            Log.w(TAG, "removeGeminiApiKey 실패: EncryptedSharedPreferences 사용 불가")
            return
        }
        val count = prefs.getString(KEY_GEMINI_KEY_COUNT, "0")?.toIntOrNull() ?: 0
        if (index < 0 || index >= count) return
        // 삭제 후 이후 항목을 앞으로 당김 (인덱스 재정렬)
        val editor = prefs.edit()
        for (i in index until count - 1) {
            val nextLabel = prefs.getString(keyGeminiLabel(i + 1), "")!!
            val nextValue = prefs.getString(keyGeminiValue(i + 1), "")!!
            editor.putString(keyGeminiLabel(i), nextLabel)
            editor.putString(keyGeminiValue(i), nextValue)
        }
        // 마지막 항목 삭제
        editor.remove(keyGeminiLabel(count - 1))
        editor.remove(keyGeminiValue(count - 1))
        editor.putString(KEY_GEMINI_KEY_COUNT, (count - 1).toString())
        editor.apply()
        Log.i(TAG, "Gemini API 키 삭제: index=$index, 재정렬 후 count=${count - 1}")
    }

    /**
     * 등록된 모든 Gemini API 키의 복호화된 실제 값 목록을 반환한다.
     * Phase 52 라운드로빈 로직에서 사용한다.
     */
    fun getAllGeminiApiKeyValues(): List<String> {
        val prefs = encryptedPrefs ?: return emptyList()
        val count = prefs.getString(KEY_GEMINI_KEY_COUNT, "0")?.toIntOrNull() ?: 0
        return (0 until count).mapNotNull { i ->
            prefs.getString(keyGeminiValue(i), null)
        }
    }

    /** API 키를 마스킹한다. 형식: AIza****WXYZ (앞 4자 + **** + 뒤 4자). */
    private fun maskApiKey(key: String): String {
        return if (key.length >= 8) {
            "${key.take(4)}****${key.takeLast(4)}"
        } else {
            "****"
        }
    }

    /** EncryptedSharedPreferences가 정상 초기화되었는지 반환한다. */
    fun isAvailable(): Boolean = encryptedPrefs != null

    /** 라운드로빈 현재 인덱스를 반환한다. EncryptedSharedPreferences 미사용 시 0. */
    fun getRoundRobinIndex(): Int =
        encryptedPrefs?.getString(KEY_RR_INDEX, "0")?.toIntOrNull() ?: 0

    /** 라운드로빈 인덱스를 암호화 저장한다. */
    fun saveRoundRobinIndex(index: Int) {
        encryptedPrefs?.edit()?.putString(KEY_RR_INDEX, index.toString())?.apply()
    }
}
