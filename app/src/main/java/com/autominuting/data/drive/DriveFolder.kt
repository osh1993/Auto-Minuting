package com.autominuting.data.drive

/**
 * Google Drive 폴더를 나타내는 데이터 클래스.
 *
 * @param id Drive 폴더 ID (Files API에서 반환되는 id 필드)
 * @param name 폴더 표시 이름
 */
data class DriveFolder(
    val id: String,
    val name: String
)
