package com.autominuting.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * STT 엔진 관련 의존성 모듈.
 *
 * WhisperEngine, MlKitEngine, AudioConverter는 모두 @Inject constructor와
 * @Singleton 어노테이션을 통해 Hilt가 자동으로 생성/관리한다.
 *
 * 추가 바인딩(예: SttEngine 인터페이스 멀티바인딩)이 필요한 경우 이 모듈에 정의한다.
 *
 * 현재 DI 구성:
 * - AudioConverter: @Singleton @Inject constructor()
 * - WhisperEngine: @Singleton @Inject constructor(Context, AudioConverter)
 * - MlKitEngine: @Singleton @Inject constructor(Context)
 * - TranscriptionRepositoryImpl -> TranscriptionRepository: RepositoryModule에서 @Binds
 */
@Module
@InstallIn(SingletonComponent::class)
object SttModule
