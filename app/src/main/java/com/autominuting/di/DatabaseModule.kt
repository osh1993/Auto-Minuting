package com.autominuting.di

import android.content.Context
import androidx.room.Room
import com.autominuting.data.local.AppDatabase
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.dao.PromptTemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 데이터베이스 및 DAO를 Hilt DI 그래프에 제공하는 모듈.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * AppDatabase 싱글톤 인스턴스를 제공한다.
     * 데이터베이스 파일명: auto_minuting.db
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "auto_minuting.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
    .build()

    /**
     * MeetingDao를 제공한다.
     */
    @Provides
    fun provideMeetingDao(db: AppDatabase): MeetingDao = db.meetingDao()

    /**
     * PromptTemplateDao를 제공한다.
     */
    @Provides
    fun providePromptTemplateDao(db: AppDatabase): PromptTemplateDao = db.promptTemplateDao()
}
