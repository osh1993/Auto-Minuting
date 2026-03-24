package com.autominuting.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager 의존성을 제공하는 Hilt 모듈.
 * WorkManager 인스턴스를 싱글톤으로 관리하여 앱 전역에서 백그라운드 작업을 예약할 수 있다.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    /**
     * WorkManager 싱글톤 인스턴스를 제공한다.
     * Phase 6에서 실제 파이프라인 Worker를 예약할 때 사용된다.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
}
