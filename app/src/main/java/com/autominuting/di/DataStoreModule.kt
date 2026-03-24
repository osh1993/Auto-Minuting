package com.autominuting.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DataStore 인스턴스를 위한 확장 프로퍼티 (파일 최상위에 선언해야 함) */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore 의존성을 제공하는 Hilt 모듈.
 * UserPreferencesRepository에서 사용자 설정(회의록 형식, 자동화 모드)을 저장/읽기 위해 사용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * DataStore<Preferences> 싱글톤 인스턴스를 제공한다.
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
