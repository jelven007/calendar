package com.tiandili.calendar.data.weather

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverter

@Entity(
    tableName = "weather_cities",
    indices = [
        Index(value = ["is_selected"]),
        Index(value = ["last_used_at_epoch_millis"]),
    ],
)
data class WeatherCityEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val name: String,
    val admin1: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean,
    @ColumnInfo(name = "last_used_at_epoch_millis")
    val lastUsedAtEpochMillis: Long,
)

enum class WeatherSegmentTypeEntity {
    CURRENT,
    HOURLY,
    DAILY,
    AIR_QUALITY,
}

enum class WeatherCacheStatusEntity {
    SUCCESS,
}

@Entity(
    tableName = "weather_cache_segments",
    primaryKeys = ["city_id", "segment_type"],
    foreignKeys = [
        ForeignKey(
            entity = WeatherCityEntity::class,
            parentColumns = ["id"],
            childColumns = ["city_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["expires_at_epoch_millis"]),
    ],
)
data class WeatherCacheSegmentEntity(
    @ColumnInfo(name = "city_id")
    val cityId: String,
    @ColumnInfo(name = "segment_type")
    val segmentType: WeatherSegmentTypeEntity,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "fetched_at_epoch_millis")
    val fetchedAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_millis")
    val expiresAtEpochMillis: Long,
    val status: WeatherCacheStatusEntity,
)

class WeatherConverters {

    @TypeConverter
    fun segmentTypeToString(value: WeatherSegmentTypeEntity): String = value.name

    @TypeConverter
    fun stringToSegmentType(value: String): WeatherSegmentTypeEntity =
        WeatherSegmentTypeEntity.valueOf(value)

    @TypeConverter
    fun cacheStatusToString(value: WeatherCacheStatusEntity): String = value.name

    @TypeConverter
    fun stringToCacheStatus(value: String): WeatherCacheStatusEntity =
        WeatherCacheStatusEntity.valueOf(value)
}
