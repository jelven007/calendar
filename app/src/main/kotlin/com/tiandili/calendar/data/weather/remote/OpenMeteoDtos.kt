package com.tiandili.calendar.data.weather.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto>?,
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val id: Long?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val country: String?,
    val admin1: String?,
)

@JsonClass(generateAdapter = true)
data class ForecastResponseDto(
    val timezone: String,
    @param:Json(name = "utc_offset_seconds")
    val utcOffsetSeconds: Int,
    val current: CurrentWeatherDto,
    val hourly: HourlyWeatherDto,
    val daily: DailyWeatherDto,
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    val time: Long,
    @param:Json(name = "temperature_2m")
    val temperature: Double,
    @param:Json(name = "apparent_temperature")
    val apparentTemperature: Double,
    @param:Json(name = "relative_humidity_2m")
    val relativeHumidity: Int,
    val precipitation: Double,
    @param:Json(name = "weather_code")
    val weatherCode: Int,
    @param:Json(name = "wind_speed_10m")
    val windSpeed: Double,
    @param:Json(name = "is_day")
    val isDay: Int,
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    val time: List<Long>,
    @param:Json(name = "temperature_2m")
    val temperatures: List<Double>,
    @param:Json(name = "precipitation_probability")
    val precipitationProbabilities: List<Int>,
    @param:Json(name = "weather_code")
    val weatherCodes: List<Int>,
    @param:Json(name = "wind_speed_10m")
    val windSpeeds: List<Double>,
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<Long>,
    @param:Json(name = "weather_code")
    val weatherCodes: List<Int>,
    @param:Json(name = "temperature_2m_max")
    val maximumTemperatures: List<Double>,
    @param:Json(name = "temperature_2m_min")
    val minimumTemperatures: List<Double>,
    @param:Json(name = "precipitation_probability_max")
    val precipitationProbabilities: List<Int>,
    val sunrise: List<Long>,
    val sunset: List<Long>,
)

@JsonClass(generateAdapter = true)
data class AirQualityResponseDto(
    val timezone: String,
    @param:Json(name = "utc_offset_seconds")
    val utcOffsetSeconds: Int,
    val current: CurrentAirQualityDto,
)

@JsonClass(generateAdapter = true)
data class CurrentAirQualityDto(
    val time: Long,
    @param:Json(name = "us_aqi")
    val usAqi: Int,
    @param:Json(name = "pm2_5")
    val pm25: Double,
    val pm10: Double,
    val ozone: Double,
    @param:Json(name = "nitrogen_dioxide")
    val nitrogenDioxide: Double,
)
