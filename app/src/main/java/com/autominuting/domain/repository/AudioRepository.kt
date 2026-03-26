package com.autominuting.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 오디오 수집을 위한 Repository 인터페이스.
 *
 * 오디오 파일을 수신하고 로컬 저장소에 저장하는 역할을 담당한다.
 */
interface AudioRepository {

    /**
     * 오디오 수집을 시작하고, 수신된 오디오 파일 경로를 emit한다.
     * @return 저장된 오디오 파일의 절대 경로를 방출하는 Flow
     */
    suspend fun startAudioCollection(): Flow<String>

    /** 오디오 수집을 중지한다. */
    suspend fun stopAudioCollection()

    /** 현재 오디오 수집이 진행 중인지 여부를 관찰한다. */
    fun isCollecting(): Flow<Boolean>
}
