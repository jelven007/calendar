package com.tiandili.calendar.domain.weather

import java.time.Clock
import java.time.LocalDate

sealed interface CalendarWeatherResult {
    data class Available(
        val forecast: DailyForecast,
        val condition: WeatherCondition,
    ) : CalendarWeatherResult

    data object NotAvailable : CalendarWeatherResult
}

object CalendarWeatherResolver {

    fun resolve(
        city: City,
        forecasts: List<DailyForecast>,
        date: LocalDate,
        clock: Clock,
    ): CalendarWeatherResult {
        val cityToday = LocalDate.now(clock.withZone(city.zoneId))
        if (date !in cityToday..cityToday.plusDays(6)) {
            return CalendarWeatherResult.NotAvailable
        }
        val forecast = forecasts.firstOrNull { it.date == date }
            ?: return CalendarWeatherResult.NotAvailable
        return CalendarWeatherResult.Available(
            forecast = forecast,
            condition = WeatherCodeMapper.conditionFor(
                code = forecast.weatherCode,
                isDay = true,
            ),
        )
    }
}
