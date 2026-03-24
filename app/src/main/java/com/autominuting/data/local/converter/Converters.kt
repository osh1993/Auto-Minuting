package com.autominuting.data.local.converter

import androidx.room.TypeConverter
import com.autominuting.domain.model.PipelineStatus
import java.time.Instant

/**
 * Room 데이터베이스용 타입 변환기.
 * PipelineStatus와 Instant를 Room이 저장할 수 있는 기본 타입으로 변환한다.
 */
class Converters {

    /** PipelineStatus -> String 변환 */
    @TypeConverter
    fun fromPipelineStatus(status: PipelineStatus): String = status.name

    /** String -> PipelineStatus 변환 */
    @TypeConverter
    fun toPipelineStatus(value: String): PipelineStatus = PipelineStatus.valueOf(value)

    /** Instant -> Long (epoch millis) 변환 */
    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    /** Long (epoch millis) -> Instant 변환 */
    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
