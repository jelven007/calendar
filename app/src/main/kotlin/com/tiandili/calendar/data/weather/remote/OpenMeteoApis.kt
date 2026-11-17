package com.tiandili.calendar.data.weather.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun searchCities(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json",
    ): GeocodingResponseDto
}

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String,
        @Query("current") current: String = FORECAST_CURRENT_FIELDS,
        @Query("hourly") hourly: String = FORECAST_HOURLY_FIELDS,
        @Query("daily") daily: String = FORECAST_DAILY_FIELDS,
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("forecast_days") forecastDays: Int = 15,
    ): ForecastResponseDto
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String,
        @Query("current") current: String = AIR_QUALITY_CURRENT_FIELDS,
        @Query("hourly") hourly: String = "us_aqi",
        @Query("forecast_days") forecastDays: Int = 5,
        @Query("timeformat") timeFormat: String = "unixtime",
    ): AirQualityResponseDto
}

private const val FORECAST_CURRENT_FIELDS =
    "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation," +
        "weather_code,wind_speed_10m,wind_direction_10m,is_day"
private const val FORECAST_HOURLY_FIELDS =
    "temperature_2m,precipitation_probability,weather_code,wind_speed_10m"
private const val FORECAST_DAILY_FIELDS =
    "weather_code,temperature_2m_max,temperature_2m_min," +
        "precipitation_probability_max,sunrise,sunset"
private const val AIR_QUALITY_CURRENT_FIELDS =
    "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide"
