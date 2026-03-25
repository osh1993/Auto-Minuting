package com.autominuting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autominuting.data.local.converter.Converters
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.entity.MeetingEntity

/**
 * Auto Minuting 앱의 Room 데이터베이스.
 * 회의 메타데이터와 파이프라인 상태를 영속 저장한다.
 */
@Database(
    entities = [MeetingEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** meetings 테이블에 접근하는 DAO를 반환한다. */
    abstract fun meetingDao(): MeetingDao

    companion object {
        /** v1 -> v2: meetings 테이블에 source 컬럼 추가 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN source TEXT NOT NULL DEFAULT 'PLAUD_BLE'")
            }
        }
    }
}
