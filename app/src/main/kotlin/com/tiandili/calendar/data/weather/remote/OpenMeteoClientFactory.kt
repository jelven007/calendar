package com.tiandili.calendar.data.weather.remote

import com.squareup.moshi.Moshi
import java.time.Duration
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object OpenMeteoClientFactory {

    fun create(moshi: Moshi): OpenMeteoRemoteDataSource {
        val client = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .callTimeout(Duration.ofSeconds(20))
            .build()
        val converter = MoshiConverterFactory.create(moshi)
        return OpenMeteoRemoteDataSource(
            geocodingApi = Retrofit.Builder()
                .baseUrl(GEOCODING_BASE_URL)
                .client(client)
                .addConverterFactory(converter)
                .build()
                .create(OpenMeteoGeocodingApi::class.java),
            forecastApi = Retrofit.Builder()
                .baseUrl(FORECAST_BASE_URL)
                .client(client)
                .addConverterFactory(converter)
                .build()
                .create(OpenMeteoForecastApi::class.java),
            airQualityApi = Retrofit.Builder()
                .baseUrl(AIR_QUALITY_BASE_URL)
                .client(client)
                .addConverterFactory(converter)
                .build()
                .create(OpenMeteoAirQualityApi::class.java),
        )
    }

    private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"
    private const val FORECAST_BASE_URL = "https://api.open-meteo.com/"
    private const val AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
}
