package com.tiandili.calendar.data.weather

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.HourlyForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeatherCacheCodec(moshi: Moshi) {
    private val currentAdapter = moshi.adapter(CurrentWeatherPayload::class.java)
    private val hourlyAdapter = moshi.adapter(HourlyForecastPayload::class.java)
    private val dailyAdapter = moshi.adapter(DailyForecastPayload::class.java)
    private val airQualityAdapter = moshi.adapter(AirQualityPayload::class.java)

    fun encodeCurrent(value: CurrentWeather): String = currentAdapter.toJson(
        CurrentWeatherPayload(
            observedAtEpochMillis = value.observedAt.toInstant().toEpochMilli(),
            temperatureC = value.temperatureC,
            apparentTemperatureC = value.apparentTemperatureC,
            relativeHumidityPercent = value.relativeHumidityPercent,
            precipitationMm = value.precipitationMm,
            weatherCode = value.weatherCode,
            windSpeedKph = value.windSpeedKph,
            isDay = value.isDay,
            windDirectionDegrees = value.windDirectionDegrees,
        ),
    )

    fun decodeCurrent(json: String, zoneId: ZoneId): CurrentWeather {
        val payload = requireNotNull(currentAdapter.fromJson(json))
        return CurrentWeather(
            observedAt = payload.observedAtEpochMillis.atZone(zoneId),
            temperatureC = payload.temperatureC,
            apparentTemperatureC = payload.apparentTemperatureC,
            relativeHumidityPercent = payload.relativeHumidityPercent,
            precipitationMm = payload.precipitationMm,
            weatherCode = payload.weatherCode,
            windSpeedKph = payload.windSpeedKph,
            isDay = payload.isDay,
            windDirectionDegrees = payload.windDirectionDegrees,
        )
    }

    fun encodeHourly(values: List<HourlyForecast>): String = hourlyAdapter.toJson(
        HourlyForecastPayload(
            items = values.map { value ->
                HourlyForecastItemPayload(
                    timeEpochMillis = value.time.toInstant().toEpochMilli(),
                    temperatureC = value.temperatureC,
                    precipitationProbabilityPercent =
                        value.precipitationProbabilityPercent,
                    weatherCode = value.weatherCode,
                    windSpeedKph = value.windSpeedKph,
                )
            },
        ),
    )

    fun decodeHourly(json: String, zoneId: ZoneId): List<HourlyForecast> =
        requireNotNull(hourlyAdapter.fromJson(json)).items.map { value ->
            HourlyForecast(
                time = value.timeEpochMillis.atZone(zoneId),
                temperatureC = value.temperatureC,
                precipitationProbabilityPercent =
                    value.precipitationProbabilityPercent,
                weatherCode = value.weatherCode,
                windSpeedKph = value.windSpeedKph,
            )
        }

    fun encodeDaily(values: List<DailyForecast>): String = dailyAdapter.toJson(
        DailyForecastPayload(
            items = values.map { value ->
                DailyForecastItemPayload(
                    dateEpochDay = value.date.toEpochDay(),
                    weatherCode = value.weatherCode,
                    minTemperatureC = value.minTemperatureC,
                    maxTemperatureC = value.maxTemperatureC,
                    precipitationProbabilityPercent =
                        value.precipitationProbabilityPercent,
                    sunriseEpochMillis = value.sunrise?.toInstant()?.toEpochMilli(),
                    sunsetEpochMillis = value.sunset?.toInstant()?.toEpochMilli(),
                )
            },
        ),
    )

    fun decodeDaily(json: String, zoneId: ZoneId): List<DailyForecast> =
        requireNotNull(dailyAdapter.fromJson(json)).items.map { value ->
            DailyForecast(
                date = LocalDate.ofEpochDay(value.dateEpochDay),
                weatherCode = value.weatherCode,
                minTemperatureC = value.minTemperatureC,
                maxTemperatureC = value.maxTemperatureC,
                precipitationProbabilityPercent =
                    value.precipitationProbabilityPercent,
                sunrise = value.sunriseEpochMillis?.atZone(zoneId),
                sunset = value.sunsetEpochMillis?.atZone(zoneId),
            )
        }

    fun encodeAirQuality(value: AirQuality): String = airQualityAdapter.toJson(
        AirQualityPayload(
            observedAtEpochMillis = value.observedAt.toInstant().toEpochMilli(),
            usAqi = value.usAqi,
            pm25 = value.pm25MicrogramsPerCubicMeter,
            pm10 = value.pm10MicrogramsPerCubicMeter,
            ozone = value.ozoneMicrogramsPerCubicMeter,
            nitrogenDioxide = value.nitrogenDioxideMicrogramsPerCubicMeter,
            hourlyUsAqi = value.hourlyUsAqi.map {
                HourlyAirQualityPayload(it.time.toInstant().toEpochMilli(), it.usAqi) },
        ),
    )

    fun decodeAirQuality(json: String, zoneId: ZoneId): AirQuality {
        val payload = requireNotNull(airQualityAdapter.fromJson(json))
        return AirQuality(
            observedAt = payload.observedAtEpochMillis.atZone(zoneId),
            usAqi = payload.usAqi,
            pm25MicrogramsPerCubicMeter = payload.pm25,
            pm10MicrogramsPerCubicMeter = payload.pm10,
            ozoneMicrogramsPerCubicMeter = payload.ozone,
            nitrogenDioxideMicrogramsPerCubicMeter = payload.nitrogenDioxide,
            hourlyUsAqi = payload.hourlyUsAqi.map {
                com.tiandili.calendar.domain.weather.HourlyAirQuality(it.timeEpochMillis.atZone(zoneId), it.usAqi) },
        )
    }

    private fun Long.atZone(zoneId: ZoneId) =
        Instant.ofEpochMilli(this).atZone(zoneId)
}

@JsonClass(generateAdapter = true)
data class CurrentWeatherPayload(
    val observedAtEpochMillis: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val relativeHumidityPercent: Int,
    val precipitationMm: Double,
    val weatherCode: Int,
    val windSpeedKph: Double,
    val isDay: Boolean,
    val windDirectionDegrees: Double? = null,
)

@JsonClass(generateAdapter = true)
data class HourlyForecastPayload(
    val items: List<HourlyForecastItemPayload>,
)

@JsonClass(generateAdapter = true)
data class HourlyForecastItemPayload(
    val timeEpochMillis: Long,
    val temperatureC: Double,
    val precipitationProbabilityPercent: Int,
    val weatherCode: Int,
    val windSpeedKph: Double,
)

@JsonClass(generateAdapter = true)
data class DailyForecastPayload(
    val items: List<DailyForecastItemPayload>,
)

@JsonClass(generateAdapter = true)
data class DailyForecastItemPayload(
    val dateEpochDay: Long,
    val weatherCode: Int,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val precipitationProbabilityPercent: Int,
    val sunriseEpochMillis: Long?,
    val sunsetEpochMillis: Long?,
)

@JsonClass(generateAdapter = true)
data class AirQualityPayload(
    val observedAtEpochMillis: Long,
    val usAqi: Int,
    val pm25: Double,
    val pm10: Double,
    val ozone: Double,
    val nitrogenDioxide: Double,
    val hourlyUsAqi: List<HourlyAirQualityPayload> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HourlyAirQualityPayload(val timeEpochMillis: Long, val usAqi: Int)
