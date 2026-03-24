package com.autominuting.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 앱 전역 의존성을 제공하는 Hilt 모듈.
 * Application Context 등 공통 의존성을 여기서 관리한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Application Context를 싱글톤으로 제공한다.
     * 다른 모듈에서 Context가 필요할 때 @ApplicationContext 대신 이 바인딩을 사용할 수 있다.
     */
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context
}
