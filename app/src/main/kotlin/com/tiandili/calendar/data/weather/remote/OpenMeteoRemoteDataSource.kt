package com.tiandili.calendar.data.weather.remote

import com.tiandili.calendar.data.weather.WeatherRemoteDataSource
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.ForecastBundle
import java.io.IOException
import retrofit2.HttpException

class OpenMeteoRemoteDataSource(
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val forecastApi: OpenMeteoForecastApi,
    private val airQualityApi: OpenMeteoAirQualityApi,
) : WeatherRemoteDataSource {

    override suspend fun searchCities(query: String): List<City> = retryOnce {
        geocodingApi.searchCities(query)
            .results
            .orEmpty()
            .mapNotNull(OpenMeteoMapper::cityOrNull)
    }

    override suspend fun fetchForecast(city: City): ForecastBundle = retryOnce {
        OpenMeteoMapper.forecast(
            dto = forecastApi.forecast(
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = city.zoneId.id,
            ),
            city = city,
        )
    }

    override suspend fun fetchAirQuality(city: City): AirQuality = retryOnce {
        OpenMeteoMapper.airQuality(
            dto = airQualityApi.airQuality(
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = city.zoneId.id,
            ),
            city = city,
        )
    }

    private suspend fun <T> retryOnce(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: Throwable) {
            if (!error.isRetryable()) {
                throw error
            }
            block()
        }
    }

    private fun Throwable.isRetryable(): Boolean =
        this is IOException || (this is HttpException && code() in 500..599)
}
