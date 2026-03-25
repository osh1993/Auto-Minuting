package com.autominuting.di

import com.autominuting.data.auth.GoogleAuthRepository
import com.autominuting.data.minutes.BearerTokenInterceptor
import com.autominuting.data.minutes.GeminiRestApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 인증 관련 Hilt 모듈.
 *
 * OAuth 인증 모드에서 사용하는 OkHttpClient(Bearer 토큰 인터셉터 포함)와
 * Gemini REST API용 Retrofit 서비스를 제공한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /** Gemini REST API 베이스 URL */
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    /**
     * OAuth용 OkHttpClient를 제공한다.
     * Bearer 토큰 인터셉터와 로깅 인터셉터를 포함한다.
     */
    @Provides
    @Singleton
    @Named("oauth")
    fun provideOAuthOkHttpClient(
        googleAuthRepository: GoogleAuthRepository
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val bearerInterceptor = BearerTokenInterceptor {
            googleAuthRepository.getAccessToken()
        }

        return OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * OAuth용 Gemini REST API 서비스를 제공한다.
     * Bearer 토큰을 사용하여 API 키 없이 Gemini API를 호출한다.
     */
    @Provides
    @Singleton
    @Named("oauth")
    fun provideOAuthGeminiRestApiService(
        @Named("oauth") okHttpClient: OkHttpClient
    ): GeminiRestApiService {
        return Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiRestApiService::class.java)
    }
}
