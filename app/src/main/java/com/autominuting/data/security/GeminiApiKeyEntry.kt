package com.autominuting.data.security

/**
 * Gemini API 키 목록의 단일 항목. UI 표시용 (복호화 키 값 미포함).
 *
 * @param label 사용자가 입력한 별명 (예: 회사용, 개인용)
 * @param maskedKey 마스킹된 키 표시 문자열. 형식: AIza****WXYZ (앞 4자 + **** + 뒤 4자)
 * @param index EncryptedSharedPreferences 내 저장 인덱스 (0-based)
 */
data class GeminiApiKeyEntry(
    val label: String,
    val maskedKey: String,
    val index: Int
)
