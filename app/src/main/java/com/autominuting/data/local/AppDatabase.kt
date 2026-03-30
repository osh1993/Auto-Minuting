package com.autominuting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autominuting.data.local.converter.Converters
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.dao.MinutesDao
import com.autominuting.data.local.dao.PromptTemplateDao
import com.autominuting.data.local.entity.MeetingEntity
import com.autominuting.data.local.entity.MinutesEntity
import com.autominuting.data.local.entity.PromptTemplateEntity

/**
 * Auto Minuting 앱의 Room 데이터베이스.
 * 회의 메타데이터와 파이프라인 상태를 영속 저장한다.
 */
@Database(
    entities = [MeetingEntity::class, PromptTemplateEntity::class, MinutesEntity::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** meetings 테이블에 접근하는 DAO를 반환한다. */
    abstract fun meetingDao(): MeetingDao

    /** minutes 테이블에 접근하는 DAO를 반환한다. */
    abstract fun minutesDao(): MinutesDao

    /** prompt_templates 테이블에 접근하는 DAO를 반환한다. */
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        /** v1 -> v2: meetings 테이블에 source 컬럼 추가 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN source TEXT NOT NULL DEFAULT 'PLAUD_BLE'")
            }
        }

        /** v3 -> v4: meetings 테이블에 minutesTitle 컬럼 추가 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN minutesTitle TEXT DEFAULT NULL")
            }
        }

        /** v4 -> v5: minutes 테이블 신설 + meetings에서 minutesPath/minutesTitle 제거 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1단계: minutes 테이블 생성
                db.execSQL("""
                    CREATE TABLE minutes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        meetingId INTEGER,
                        minutesPath TEXT NOT NULL,
                        minutesTitle TEXT,
                        templateId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (meetingId) REFERENCES meetings(id) ON DELETE SET NULL
                    )
                """)
                db.execSQL("CREATE INDEX index_minutes_meetingId ON minutes(meetingId)")

                // 2단계: 기존 minutesPath 데이터를 minutes 테이블로 이관
                db.execSQL("""
                    INSERT INTO minutes (meetingId, minutesPath, minutesTitle, templateId, createdAt, updatedAt)
                    SELECT id, minutesPath, minutesTitle, NULL, updatedAt, updatedAt
                    FROM meetings
                    WHERE minutesPath IS NOT NULL
                """)

                // 3단계: meetings 테이블 재생성 (minutesPath, minutesTitle 제거)
                db.execSQL("""
                    CREATE TABLE meetings_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        audioFilePath TEXT NOT NULL,
                        transcriptPath TEXT,
                        pipelineStatus TEXT NOT NULL,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL
                    )
                """)

                // 4단계: 기존 데이터 복사 (MINUTES_ONLY -> COMPLETED 상태 변환)
                db.execSQL("""
                    INSERT INTO meetings_new (id, title, recordedAt, audioFilePath, transcriptPath,
                                              pipelineStatus, errorMessage, createdAt, updatedAt, source)
                    SELECT id, title, recordedAt, audioFilePath, transcriptPath,
                           CASE WHEN pipelineStatus = 'MINUTES_ONLY' THEN 'COMPLETED' ELSE pipelineStatus END,
                           errorMessage, createdAt, updatedAt, source
                    FROM meetings
                """)

                // 5단계: 기존 테이블 제거 및 새 테이블로 교체
                db.execSQL("DROP TABLE meetings")
                db.execSQL("ALTER TABLE meetings_new RENAME TO meetings")
            }
        }

        /** v2 -> v3: prompt_templates 테이블 생성 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE prompt_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        promptText TEXT NOT NULL,
                        isBuiltIn INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
    }
}
