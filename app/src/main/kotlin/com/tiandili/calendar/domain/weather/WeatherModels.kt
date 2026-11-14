package com.tiandili.calendar.domain.weather

import java.time.LocalDate
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class City(
    val id: String,
    val name: String,
    val admin1: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
)

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val precipitationProbabilityPercent: Int,
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
)

data class CurrentWeather(
    val observedAt: ZonedDateTime,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val relativeHumidityPercent: Int,
    val precipitationMm: Double,
    val weatherCode: Int,
    val windSpeedKph: Double,
    val isDay: Boolean,
)

data class HourlyForecast(
    val time: ZonedDateTime,
    val temperatureC: Double,
    val precipitationProbabilityPercent: Int,
    val weatherCode: Int,
    val windSpeedKph: Double,
)

data class AirQuality(
    val observedAt: ZonedDateTime,
    val usAqi: Int,
    val pm25MicrogramsPerCubicMeter: Double,
    val pm10MicrogramsPerCubicMeter: Double,
    val ozoneMicrogramsPerCubicMeter: Double,
    val nitrogenDioxideMicrogramsPerCubicMeter: Double,
)

data class ForecastBundle(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
)

data class CachedWeatherSegment<T>(
    val value: T,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)

data class WeatherSnapshot(
    val city: City,
    val current: CachedWeatherSegment<CurrentWeather>? = null,
    val hourly: CachedWeatherSegment<List<HourlyForecast>>? = null,
    val daily: CachedWeatherSegment<List<DailyForecast>>? = null,
    val airQuality: CachedWeatherSegment<AirQuality>? = null,
)

enum class WeatherSymbol {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
}

data class WeatherCondition(
    val description: String,
    val symbol: WeatherSymbol,
)
