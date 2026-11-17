package com.tiandili.calendar.feature.weather

import com.tiandili.calendar.domain.weather.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun dailyAirAqi(air: AirQuality?, date: LocalDate): Int? =
    air?.hourlyUsAqi?.filter { it.time.toLocalDate() == date }?.takeIf { it.isNotEmpty() }
        ?.map { it.usAqi }?.average()?.roundToInt()

fun windDescription(current: CurrentWeather): String {
    val direction = current.windDirectionDegrees?.let {
        listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")[((it + 22.5) / 45).toInt() % 8]
    }
    val metersPerSecond = current.windSpeedKph / 3.6
    val thresholds = listOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)
    val level = thresholds.indexOfFirst { metersPerSecond < it }.let { if (it < 0) 12 else it }
    return "${direction?.let { "${it}风" } ?: "风"} $level 级 · ${current.windSpeedKph.roundToInt()} km/h"
}

fun weatherShareText(snapshot: WeatherSnapshot, now: Instant): String {
    val current = snapshot.current?.value
    val date = now.atZone(snapshot.city.zoneId).toLocalDate()
    val day = snapshot.daily?.value?.firstOrNull { it.date == date }
    return buildString {
        append("${snapshot.city.name} · $date\n")
        current?.let { append("${it.temperatureC.roundToInt()}℃ · ${
            WeatherCodeMapper.conditionFor(it.weatherCode, it.isDay).description}\n")
            append("${windDescription(it)} · 湿度 ${it.relativeHumidityPercent}%\n") }
        day?.let { append("今日 ${it.minTemperatureC.roundToInt()}–${it.maxTemperatureC.roundToInt()}℃\n") }
        snapshot.current?.let { append("数据更新于 ${it.fetchedAt.atZone(snapshot.city.zoneId)
            .format(DateTimeFormatter.ofPattern("M-d HH:mm"))}${
            if (WeatherCachePolicy.freshness(WeatherSegmentType.CURRENT, it.fetchedAt, now) == CacheFreshness.FRESH)
                "" else "（缓存）"}\n") }
        append("天地历 · Weather data by Open-Meteo.com · CC BY 4.0")
    }
}
