package com.tiandili.calendar.domain.widget

import com.squareup.moshi.JsonClass
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.almanac.AlmanacEngine
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.schedule.OccurrenceGenerator
import com.tiandili.calendar.domain.weather.CacheFreshness
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherCodeMapper
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class WidgetSnapshot(
    val generatedAtEpochMillis: Long,
    val date: String,
    val weekday: String,
    val lunar: String,
    val occasion: String?,
    val suitable: String,
    val avoid: String,
    val weather: WidgetWeatherSummary? = null,
    val nextSchedule: WidgetScheduleSummary? = null,
)

@JsonClass(generateAdapter = true)
data class WidgetWeatherSummary(
    val city: String,
    val condition: String,
    val temperature: String,
    val range: String?,
    val updatedAtEpochMillis: Long,
    val freshness: CacheFreshness,
    val attribution: String = "Open-Meteo",
)

@JsonClass(generateAdapter = true)
data class WidgetScheduleSummary(
    val scheduleId: Long,
    val title: String,
    val startsAtEpochMillis: Long,
    val timeLabel: String,
)

data class CancelledWidgetOccurrence(val scheduleId: Long, val anchorEpochDay: Long)

class WidgetSnapshotComposer(private val almanac: AlmanacEngine) {
    fun compose(
        now: Instant,
        zoneId: ZoneId,
        schedules: List<Schedule> = emptyList(),
        cancelled: Set<CancelledWidgetOccurrence> = emptySet(),
        weather: WeatherSnapshot? = null,
        preferences: UserPreferences = UserPreferences(),
    ): WidgetSnapshot {
        val date = now.atZone(zoneId).toLocalDate()
        val day = if (date.year in 1901..2099) almanac.day(date, zoneId) else null
        return WidgetSnapshot(
            generatedAtEpochMillis = now.toEpochMilli(),
            date = date.toString(),
            weekday = day?.weekdayText ?: "星期${"一二三四五六日"[date.dayOfWeek.value - 1]}",
            lunar = day?.let { it.lunarMonthText + it.lunarDayText } ?: "日期超出农历支持范围",
            occasion = day?.solarTerm ?: day?.festivals?.firstOrNull(),
            suitable = day?.suitable?.take(3)?.joinToString(" · ").orEmpty(),
            avoid = day?.avoid?.take(3)?.joinToString(" · ").orEmpty(),
            weather = weather?.summary(now),
            nextSchedule = if (preferences.showNextScheduleInWidget) {
                schedules.mapNotNull { nextOccurrence(it, cancelled, now, zoneId) }
                    .minWithOrNull(compareBy<WidgetScheduleSummary>(
                        { maxOf(it.startsAtEpochMillis, now.toEpochMilli()) }, { it.scheduleId },
                    ))
            } else null,
        )
    }

    private fun WeatherSnapshot.summary(now: Instant): WidgetWeatherSummary? {
        val current = current ?: return null
        val today = now.atZone(city.zoneId).toLocalDate()
        val forecast = daily?.value?.firstOrNull { it.date == today }
        return WidgetWeatherSummary(
            city = city.name,
            condition = WeatherCodeMapper.conditionFor(
                current.value.weatherCode, current.value.isDay,
            ).description,
            temperature = "${current.value.temperatureC.roundToInt()}°",
            range = forecast?.let {
                "${it.minTemperatureC.roundToInt()}° / ${it.maxTemperatureC.roundToInt()}°"
            },
            updatedAtEpochMillis = current.fetchedAt.toEpochMilli(),
            freshness = WeatherCachePolicy.freshness(WeatherSegmentType.CURRENT, current.fetchedAt, now),
        )
    }

    private fun nextOccurrence(
        schedule: Schedule,
        cancelled: Set<CancelledWidgetOccurrence>,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetScheduleSummary? {
        if (!schedule.calendarEligible) return null
        val timing = schedule.timing
        val eventZone = (timing as? ScheduleTiming.Timed)?.zoneId ?: deviceZone
        val today = now.atZone(eventZone).toLocalDate()
        val anchor = when (timing) {
            is ScheduleTiming.AllDay -> timing.startDate
            is ScheduleTiming.Timed -> timing.startsAt.toLocalDate()
        }
        val durationDays = when (timing) {
            is ScheduleTiming.AllDay -> ChronoUnit.DAYS.between(anchor, timing.endDateExclusive)
            is ScheduleTiming.Timed -> ChronoUnit.DAYS.between(anchor, timing.endsAt.toLocalDate())
        }
        val windowStart = today.minusDays(durationDays)
        val lastDate = LocalDate.of(2099, 12, 31)
        if (windowStart > lastDate) return null
        var windowEnd = minOf(today.plusYears(1), lastDate)
        while (true) {
            val anchors = schedule.recurrence?.let {
                OccurrenceGenerator.generateDates(anchor, it, windowStart..windowEnd)
            } ?: listOf(anchor)
            for (date in anchors) {
                if (CancelledWidgetOccurrence(schedule.id, date.toEpochDay()) in cancelled) continue
                val start = when (timing) {
                    is ScheduleTiming.AllDay -> date.atStartOfDay(deviceZone)
                    is ScheduleTiming.Timed -> date.atTime(timing.startsAt.toLocalTime()).atZone(eventZone)
                }
                val end = when (timing) {
                    is ScheduleTiming.AllDay -> date.plusDays(durationDays).atStartOfDay(deviceZone)
                    is ScheduleTiming.Timed ->
                        date.plusDays(durationDays).atTime(timing.endsAt.toLocalTime()).atZone(eventZone)
                }
                if (end.toInstant() <= now) continue
                val localStart = start.withZoneSameInstant(deviceZone)
                val deviceToday = now.atZone(deviceZone).toLocalDate()
                val ongoing = start.toInstant() <= now
                val dayLabel = if (ongoing || localStart.toLocalDate() == deviceToday) "今天"
                    else localStart.format(DateTimeFormatter.ofPattern("M月d日"))
                return WidgetScheduleSummary(
                    scheduleId = schedule.id,
                    title = schedule.title,
                    startsAtEpochMillis = start.toInstant().toEpochMilli(),
                    timeLabel = if (timing is ScheduleTiming.AllDay) "$dayLabel · 全天"
                        else "$dayLabel ${localStart.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                )
            }
            if (schedule.recurrence == null || windowEnd == lastDate) return null
            windowEnd = minOf(windowEnd.plusYears(2), lastDate)
        }
    }
}
