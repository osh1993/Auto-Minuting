package com.autominuting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autominuting.data.local.converter.Converters
import com.autominuting.data.local.dao.MeetingDao
import com.autominuting.data.local.entity.MeetingEntity

/**
 * Auto Minuting 앱의 Room 데이터베이스.
 * 회의 메타데이터와 파이프라인 상태를 영속 저장한다.
 */
@Database(
    entities = [MeetingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** meetings 테이블에 접근하는 DAO를 반환한다. */
    abstract fun meetingDao(): MeetingDao
}
