package com.tiandili.calendar.domain.weather

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class CalendarWeatherResolverTest {

    private val city = City(
        id = "hangzhou-cn",
        name = "杭州",
        admin1 = "浙江",
        country = "中国",
        latitude = 30.2741,
        longitude = 120.1551,
        zoneId = ZoneId.of("Asia/Shanghai"),
    )
    private val clock = Clock.fixed(
        Instant.parse("2025-01-01T16:30:00Z"),
        ZoneId.of("UTC"),
    )
    private val forecasts = (1L..9L).map { day ->
        DailyForecast(
            date = LocalDate.of(2025, 1, 1).plusDays(day - 1),
            weatherCode = 2,
            minTemperatureC = 3.0,
            maxTemperatureC = 10.0,
            precipitationProbabilityPercent = 20,
            sunrise = null,
            sunset = null,
        )
    }

    @Test
    fun `weather window starts on the city local date`() {
        assertThat(
            CalendarWeatherResolver.resolve(
                city = city,
                forecasts = forecasts,
                date = LocalDate.of(2025, 1, 1),
                clock = clock,
            ),
        ).isEqualTo(CalendarWeatherResult.NotAvailable)
        assertThat(
            CalendarWeatherResolver.resolve(
                city = city,
                forecasts = forecasts,
                date = LocalDate.of(2025, 1, 2),
                clock = clock,
            ),
        ).isInstanceOf(CalendarWeatherResult.Available::class.java)
    }

    @Test
    fun `weather window includes exactly seven city local dates`() {
        assertThat(
            CalendarWeatherResolver.resolve(
                city = city,
                forecasts = forecasts,
                date = LocalDate.of(2025, 1, 8),
                clock = clock,
            ),
        ).isInstanceOf(CalendarWeatherResult.Available::class.java)
        assertThat(
            CalendarWeatherResolver.resolve(
                city = city,
                forecasts = forecasts,
                date = LocalDate.of(2025, 1, 9),
                clock = clock,
            ),
        ).isEqualTo(CalendarWeatherResult.NotAvailable)
    }

    @Test
    fun `missing daily response is not synthesized`() {
        assertThat(
            CalendarWeatherResolver.resolve(
                city = city,
                forecasts = forecasts.filterNot {
                    it.date == LocalDate.of(2025, 1, 5)
                },
                date = LocalDate.of(2025, 1, 5),
                clock = clock,
            ),
        ).isEqualTo(CalendarWeatherResult.NotAvailable)
    }
}
