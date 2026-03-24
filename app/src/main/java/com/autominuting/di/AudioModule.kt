package com.autominuting.di

import com.autominuting.data.audio.PlaudCloudApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Plaud SDK 및 Cloud API 관련 의존성을 Hilt DI 그래프에 제공하는 모듈.
 *
 * PlaudSdkManager, AudioFileManager는 @Singleton @Inject constructor로 자동 제공.
 * PlaudCloudApi는 Retrofit 빌더로 생성하여 제공한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    /** Plaud Cloud API base URL */
    private const val PLAUD_CLOUD_BASE_URL = "https://api.plaud.ai/"

    /**
     * PlaudCloudApi Retrofit 인터페이스를 제공한다.
     * OkHttp에 로깅 인터셉터를 추가하여 디버그 시 네트워크 통신을 확인할 수 있다.
     */
    @Provides
    @Singleton
    fun providePlaudCloudApi(): PlaudCloudApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(PLAUD_CLOUD_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlaudCloudApi::class.java)
    }
}
