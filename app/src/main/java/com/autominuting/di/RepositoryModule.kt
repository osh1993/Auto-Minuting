package com.autominuting.di

import com.autominuting.data.minutes.MinutesEngine
import com.autominuting.data.minutes.MinutesEngineSelector
import com.autominuting.data.repository.AudioRepositoryImpl
import com.autominuting.data.repository.MeetingRepositoryImpl
import com.autominuting.data.repository.MinutesDataRepositoryImpl
import com.autominuting.data.repository.MinutesRepositoryImpl
import com.autominuting.data.repository.PromptTemplateRepositoryImpl
import com.autominuting.data.repository.TranscriptionRepositoryImpl
import com.autominuting.domain.repository.AudioRepository
import com.autominuting.domain.repository.MeetingRepository
import com.autominuting.domain.repository.MinutesDataRepository
import com.autominuting.domain.repository.MinutesRepository
import com.autominuting.domain.repository.PromptTemplateRepository
import com.autominuting.domain.repository.TranscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈.
 *
 * MinutesEngine은 MinutesEngineSelector를 통해 인증 모드에 따라
 * GeminiEngine(API 키) 또는 GeminiOAuthEngine(OAuth)을 선택한다.
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
     * MinutesEngine 인터페이스를 MinutesEngineSelector에 바인딩한다.
     * 인증 모드(API 키/OAuth)에 따라 적절한 엔진을 동적 선택한다.
     */
    @Binds
    abstract fun bindMinutesEngine(
        impl: MinutesEngineSelector
    ): MinutesEngine

    /**
     * MinutesDataRepository 인터페이스를 MinutesDataRepositoryImpl 구현체에 바인딩한다.
     * 회의록 데이터 CRUD 전용 (생성 API 호출은 MinutesRepository가 담당).
     */
    @Binds
    abstract fun bindMinutesDataRepository(
        impl: MinutesDataRepositoryImpl
    ): MinutesDataRepository

    /**
     * PromptTemplateRepository 인터페이스를 PromptTemplateRepositoryImpl 구현체에 바인딩한다.
     */
    @Binds
    abstract fun bindPromptTemplateRepository(
        impl: PromptTemplateRepositoryImpl
    ): PromptTemplateRepository
}
