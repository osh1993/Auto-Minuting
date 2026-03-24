package com.autominuting.di

import com.autominuting.data.repository.AudioRepositoryImpl
import com.autominuting.data.repository.MeetingRepositoryImpl
import com.autominuting.domain.repository.AudioRepository
import com.autominuting.domain.repository.MeetingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈.
 *
 * TranscriptionRepository, MinutesRepository는
 * Phase 4~5에서 구현체가 추가될 때 바인딩한다.
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
}
