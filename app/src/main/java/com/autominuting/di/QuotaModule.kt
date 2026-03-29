package com.autominuting.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Gemini 쿼터 관련 의존성 Hilt 모듈.
 *
 * GeminiQuotaTracker는 @Singleton @Inject constructor로 자동 주입되므로
 * 별도 @Provides가 불필요하다. 향후 쿼터 정책 확장 시 바인딩을 추가한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object QuotaModule
