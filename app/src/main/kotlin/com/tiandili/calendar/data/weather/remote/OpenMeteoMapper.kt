package com.tiandili.calendar.data.weather.remote

import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.ForecastBundle
import com.tiandili.calendar.domain.weather.HourlyForecast
import java.time.Instant
import java.time.ZoneId

object OpenMeteoMapper {

    fun cityOrNull(dto: GeocodingResultDto): City? {
        val id = dto.id ?: return null
        val name = dto.name?.takeIf(String::isNotBlank) ?: return null
        val latitude = dto.latitude?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = dto.longitude?.takeIf { it in -180.0..180.0 } ?: return null
        val country = dto.country?.takeIf(String::isNotBlank) ?: return null
        val zoneId = dto.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: return null
        return City(
            id = "open-meteo:$id",
            name = name,
            admin1 = dto.admin1?.takeIf(String::isNotBlank),
            country = country,
            latitude = latitude,
            longitude = longitude,
            zoneId = zoneId,
        )
    }

    fun forecast(dto: ForecastResponseDto, city: City): ForecastBundle {
        require(dto.timezone == city.zoneId.id) {
            "forecast timezone ${dto.timezone} does not match ${city.zoneId.id}"
        }
        requireParallelSizes(
            label = "hourly",
            expected = dto.hourly.time.size,
            dto.hourly.temperatures.size,
            dto.hourly.precipitationProbabilities.size,
            dto.hourly.weatherCodes.size,
            dto.hourly.windSpeeds.size,
        )
        requireParallelSizes(
            label = "daily",
            expected = dto.daily.time.size,
            dto.daily.weatherCodes.size,
            dto.daily.maximumTemperatures.size,
            dto.daily.minimumTemperatures.size,
            dto.daily.precipitationProbabilities.size,
            dto.daily.sunrise.size,
            dto.daily.sunset.size,
        )
        return ForecastBundle(
            current = CurrentWeather(
                observedAt = dto.current.time.atZone(city.zoneId),
                temperatureC = dto.current.temperature,
                apparentTemperatureC = dto.current.apparentTemperature,
                relativeHumidityPercent = dto.current.relativeHumidity,
                precipitationMm = dto.current.precipitation,
                weatherCode = dto.current.weatherCode,
                windSpeedKph = dto.current.windSpeed,
                isDay = dto.current.isDay == 1,
            ),
            hourly = dto.hourly.time.indices.map { index ->
                HourlyForecast(
                    time = dto.hourly.time[index].atZone(city.zoneId),
                    temperatureC = dto.hourly.temperatures[index],
                    precipitationProbabilityPercent =
                        dto.hourly.precipitationProbabilities[index],
                    weatherCode = dto.hourly.weatherCodes[index],
                    windSpeedKph = dto.hourly.windSpeeds[index],
                )
            },
            daily = dto.daily.time.indices.map { index ->
                DailyForecast(
                    date = dto.daily.time[index].atZone(city.zoneId).toLocalDate(),
                    weatherCode = dto.daily.weatherCodes[index],
                    minTemperatureC = dto.daily.minimumTemperatures[index],
                    maxTemperatureC = dto.daily.maximumTemperatures[index],
                    precipitationProbabilityPercent =
                        dto.daily.precipitationProbabilities[index],
                    sunrise = dto.daily.sunrise[index].atZone(city.zoneId),
                    sunset = dto.daily.sunset[index].atZone(city.zoneId),
                )
            },
        )
    }

    fun airQuality(dto: AirQualityResponseDto, city: City): AirQuality {
        require(dto.timezone == city.zoneId.id) {
            "air quality timezone ${dto.timezone} does not match ${city.zoneId.id}"
        }
        return AirQuality(
            observedAt = dto.current.time.atZone(city.zoneId),
            usAqi = dto.current.usAqi,
            pm25MicrogramsPerCubicMeter = dto.current.pm25,
            pm10MicrogramsPerCubicMeter = dto.current.pm10,
            ozoneMicrogramsPerCubicMeter = dto.current.ozone,
            nitrogenDioxideMicrogramsPerCubicMeter = dto.current.nitrogenDioxide,
        )
    }

    private fun Long.atZone(zoneId: ZoneId) =
        Instant.ofEpochSecond(this).atZone(zoneId)

    private fun requireParallelSizes(
        label: String,
        expected: Int,
        vararg actual: Int,
    ) {
        require(actual.all { it == expected }) {
            "$label arrays must all contain $expected values"
        }
    }
}
