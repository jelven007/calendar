package com.tiandili.calendar.data.weather

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WeatherDao {

    @Upsert
    protected abstract suspend fun upsertCity(city: WeatherCityEntity)

    @Upsert
    abstract suspend fun addSavedCity(city: WeatherCityEntity)

    @Query("UPDATE weather_cities SET is_selected = 0 WHERE is_selected = 1")
    protected abstract suspend fun clearSelectedCity()

    @Query(
        """
        DELETE FROM weather_cities
        WHERE is_selected = 0
          AND id NOT IN (
            SELECT id
            FROM weather_cities
            WHERE is_selected = 0
            ORDER BY last_used_at_epoch_millis DESC
            LIMIT 9
          )
        """,
    )
    protected abstract suspend fun trimCityHistory()

    @Transaction
    open suspend fun selectCity(
        city: WeatherCityEntity,
        selectedAtEpochMillis: Long,
    ) {
        clearSelectedCity()
        upsertCity(
            city.copy(
                isSelected = true,
                lastUsedAtEpochMillis = selectedAtEpochMillis,
            ),
        )
        trimCityHistory()
    }

    @Query(
        """
        SELECT * FROM weather_cities
        WHERE is_selected = 1
        ORDER BY last_used_at_epoch_millis DESC
        LIMIT 1
        """,
    )
    abstract fun observeSelectedCity(): Flow<WeatherCityEntity?>

    @Query(
        """
        SELECT * FROM weather_cities
        WHERE is_selected = 1
        ORDER BY last_used_at_epoch_millis DESC
        LIMIT 1
        """,
    )
    abstract suspend fun getSelectedCity(): WeatherCityEntity?

    @Query(
        """
        SELECT * FROM weather_cities
        ORDER BY is_selected DESC, last_used_at_epoch_millis DESC
        """,
    )
    abstract suspend fun getSavedCities(): List<WeatherCityEntity>

    @Upsert
    protected abstract suspend fun upsertSegments(
        segments: List<WeatherCacheSegmentEntity>,
    )

    @Transaction
    open suspend fun replaceSegments(segments: List<WeatherCacheSegmentEntity>) {
        require(segments.map(WeatherCacheSegmentEntity::cityId).distinct().size <= 1) {
            "segments must belong to one city"
        }
        upsertSegments(segments)
    }

    @Query(
        """
        SELECT * FROM weather_cache_segments
        WHERE city_id = :cityId
        ORDER BY segment_type
        """,
    )
    abstract fun observeSegments(cityId: String): Flow<List<WeatherCacheSegmentEntity>>

    @Query(
        """
        SELECT * FROM weather_cache_segments
        WHERE city_id = :cityId
        ORDER BY segment_type
        """,
    )
    abstract suspend fun getSegments(cityId: String): List<WeatherCacheSegmentEntity>

    @Query("DELETE FROM weather_cache_segments")
    abstract suspend fun clearCache()

    @Query("DELETE FROM weather_cities")
    abstract suspend fun clearCities()

    @Transaction
    open suspend fun restoreCities(cities: List<WeatherCityEntity>) {
        require(cities.size <= 10 && cities.count { it.isSelected } <= 1)
        clearCities()
        cities.forEach { upsertCity(it) }
    }
}
