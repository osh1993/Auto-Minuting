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

    /** EncryptedSharedPreferences가 정상 초기화되었는지 반환한다. */
    fun isAvailable(): Boolean = encryptedPrefs != null
}
