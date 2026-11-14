package com.tiandili.calendar.data.weather

import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.ForecastBundle
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observeWeather(): Flow<WeatherSnapshot?>

    suspend fun searchCities(query: String): List<City>

    suspend fun savedCities(): List<City>

    suspend fun selectCity(city: City): WeatherRefreshResult

    suspend fun refresh(force: Boolean): WeatherRefreshResult
}

interface WeatherStore {
    fun observeWeather(): Flow<WeatherSnapshot?>

    suspend fun selectedCity(): City?

    suspend fun currentSnapshot(cityId: String): WeatherSnapshot?

    suspend fun selectCity(city: City, selectedAt: Instant)

    suspend fun savedCities(): List<City>

    suspend fun saveForecast(
        city: City,
        forecast: ForecastBundle,
        fetchedAt: Instant,
    )

    suspend fun saveAirQuality(
        city: City,
        airQuality: AirQuality,
        fetchedAt: Instant,
    )
}

interface WeatherRemoteDataSource {
    suspend fun searchCities(query: String): List<City>

    suspend fun fetchForecast(city: City): ForecastBundle

    suspend fun fetchAirQuality(city: City): AirQuality
}

enum class WeatherRefreshGroup {
    FORECAST,
    AIR_QUALITY,
}

data class WeatherRefreshResult(
    val updatedGroups: Set<WeatherRefreshGroup> = emptySet(),
    val failedGroups: Set<WeatherRefreshGroup> = emptySet(),
    val skippedAsFresh: Boolean = false,
    val noSelectedCity: Boolean = false,
)
