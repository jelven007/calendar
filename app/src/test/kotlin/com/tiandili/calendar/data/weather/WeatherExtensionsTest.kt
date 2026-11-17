package com.tiandili.calendar.data.weather

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.weather.remote.*
import com.tiandili.calendar.domain.weather.*
import com.tiandili.calendar.feature.weather.dailyAirAqi
import com.tiandili.calendar.feature.weather.windDescription
import java.time.*
import org.junit.Test

class WeatherExtensionsTest {
    private val moshi = Moshi.Builder().build()
    private val codec = WeatherCacheCodec(moshi)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val city = City("hz", "杭州", null, "中国", 30.0, 120.0, zone)
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText()

    @Test fun `optional wind direction survives cache while old payload remains readable`() {
        val dto = checkNotNull(moshi.adapter(ForecastResponseDto::class.java).fromJson(fixture("open_meteo_forecast.json")))
        val current = OpenMeteoMapper.forecast(dto.copy(current = dto.current.copy(windDirection = 315.0)), city).current
        assertThat(current.windDirectionDegrees).isEqualTo(315.0)
        assertThat(windDescription(current)).startsWith("西北风")
        assertThat(codec.decodeCurrent(codec.encodeCurrent(current), zone)).isEqualTo(current)
        val oldJson = codec.encodeCurrent(current).replace(",\"windDirectionDegrees\":315.0", "")
        assertThat(codec.decodeCurrent(oldJson, zone).windDirectionDegrees).isNull()
        assertThat(OpenMeteoMapper.forecast(dto.copy(current = dto.current.copy(windDirection = 721.0)), city)
            .current.windDirectionDegrees).isNull()
    }

    @Test fun `available hourly air samples are cached and daily mean never invents future data`() {
        val dto = checkNotNull(moshi.adapter(AirQualityResponseDto::class.java).fromJson(fixture("open_meteo_air_quality.json")))
        val extended = dto.copy(hourly = HourlyAirQualityDto(
            listOf(1735794000L, 1735797600L, 1735801200L), listOf(42, null, 82)))
        val air = OpenMeteoMapper.airQuality(extended, city)
        assertThat(air.hourlyUsAqi).hasSize(2)
        assertThat(codec.decodeAirQuality(codec.encodeAirQuality(air), zone)).isEqualTo(air)
        assertThat(dailyAirAqi(air, LocalDate.of(2025, 1, 2))).isEqualTo(62)
        assertThat(dailyAirAqi(air, LocalDate.of(2025, 1, 3))).isNull()
        val oldPayload = """{"observedAtEpochMillis":1735794000000,"usAqi":42,"pm25":12.3,
            "pm10":20.4,"ozone":55.1,"nitrogenDioxide":18.2}"""
        assertThat(codec.decodeAirQuality(oldPayload, zone).hourlyUsAqi).isEmpty()
        assertThat(runCatching { OpenMeteoMapper.airQuality(
            extended.copy(hourly = extended.hourly!!.copy(usAqi = listOf(10))), city) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
