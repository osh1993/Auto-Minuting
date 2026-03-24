package com.autominuting.di

import com.autominuting.data.repository.MeetingRepositoryImpl
import com.autominuting.domain.repository.MeetingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈.
 *
 * AudioRepository, TranscriptionRepository, MinutesRepository는
 * Phase 3~5에서 구현체가 추가될 때 바인딩한다.
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
}
