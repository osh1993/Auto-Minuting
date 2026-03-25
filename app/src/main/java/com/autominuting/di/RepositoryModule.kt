package com.autominuting.di

import com.autominuting.data.minutes.GeminiEngine
import com.autominuting.data.minutes.MinutesEngine
import com.autominuting.data.repository.AudioRepositoryImpl
import com.autominuting.data.repository.MeetingRepositoryImpl
import com.autominuting.data.repository.MinutesRepositoryImpl
import com.autominuting.data.repository.TranscriptionRepositoryImpl
import com.autominuting.domain.repository.AudioRepository
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.domain.repository.TranscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈.
 *
 * MinutesRepository는 Phase 5에서 Gemini API 기반 구현체로 바인딩한다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * MeetingRepository 인터페이스를 MeetingRepositoryImpl 구현체에 바인딩한다.
     */
    @Binds
    abstract fun bindMeetingRepository(
        impl: MeetingRepositoryImpl
    ): MeetingRepository

    /**
     * AudioRepository 인터페이스를 AudioRepositoryImpl 구현체에 바인딩한다.
     * SDK(1차) + Cloud API(2차) 이중 경로를 지원한다.
     */
    @Binds
    abstract fun bindAudioRepository(
        impl: AudioRepositoryImpl
    ): AudioRepository

    /**
     * TranscriptionRepository 인터페이스를 TranscriptionRepositoryImpl 구현체에 바인딩한다.
     * Whisper(1차) + ML Kit/SpeechRecognizer(2차) 이중 경로를 지원한다.
     */
    @Binds
    abstract fun bindTranscriptionRepository(
        impl: TranscriptionRepositoryImpl
    ): TranscriptionRepository

    /**
     * MinutesRepository 인터페이스를 MinutesRepositoryImpl 구현체에 바인딩한다.
     * Gemini API(1차) 경로를 지원하며, 향후 NotebookLM MCP 폴백 추가 가능.
     */
    @Binds
    abstract fun bindMinutesRepository(
        impl: MinutesRepositoryImpl
    ): MinutesRepository

    /**
     * MinutesEngine 인터페이스를 GeminiEngine 구현체에 바인딩한다.
     * 기본 바인딩은 API 키 모드. Plan 02에서 OAuth 엔진 추가 예정.
     */
    @Binds
    abstract fun bindMinutesEngine(
        impl: GeminiEngine
    ): MinutesEngine
}
