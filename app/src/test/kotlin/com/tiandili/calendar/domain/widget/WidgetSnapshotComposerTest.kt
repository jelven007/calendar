package com.tiandili.calendar.domain.widget

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.weather.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class WidgetSnapshotComposerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2025-02-02T01:00:00Z")
    private val date = LocalDate.of(2025, 2, 2)
    private val composer = WidgetSnapshotComposer(LunarJavaAlmanacEngine())
    private val visible = UserPreferences(showNextScheduleInWidget = true)

    @Test fun `offline snapshot keeps date lunar and almanac`() {
        val snapshot = composer.compose(now, zone)
        assertThat(snapshot.date).isEqualTo("2025-02-02")
        assertThat(snapshot.weekday).isEqualTo("星期日")
        assertThat(snapshot.lunar).contains("初五")
        assertThat(snapshot.suitable).isNotEmpty()
        assertThat(snapshot.weather).isNull()
        assertThat(snapshot.nextSchedule).isNull()
    }

    @Test fun `private default excludes title and schedule identifier`() {
        val snapshot = composer.compose(now, zone, schedules = listOf(allDay(1, date)))
        assertThat(snapshot.nextSchedule).isNull()
        val enabled = composer.compose(now, zone, schedules = listOf(allDay(1, date)), preferences = visible)
        assertThat(enabled.nextSchedule?.title).isEqualTo("私人日程")
        assertThat(enabled.nextSchedule?.scheduleId).isEqualTo(1)
        assertThat(enabled.nextSchedule?.timeLabel).isEqualTo("今天 · 全天")
    }

    @Test fun `next recurrence skips cancelled anchor and honors count`() {
        val recurring = allDay(8, date.minusDays(2)).copy(
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY, count = 4),
        )
        val cancelled = setOf(CancelledWidgetOccurrence(8, date.toEpochDay()))
        val snapshot = composer.compose(now, zone, listOf(recurring), cancelled, preferences = visible)
        assertThat(snapshot.nextSchedule?.timeLabel).isEqualTo("2月3日 · 全天")
        val exhausted = composer.compose(now, zone, listOf(recurring),
            cancelled + CancelledWidgetOccurrence(8, date.plusDays(1).toEpochDay()), preferences = visible)
        assertThat(exhausted.nextSchedule).isNull()
    }

    @Test fun `next schedule compares timed instants across zones and excludes ended items`() {
        val foreignZone = ZoneId.of("America/New_York")
        val earlier = Schedule(2, "纽约事项", ScheduleTiming.Timed(
            date.minusDays(1).atTime(20, 30), date.minusDays(1).atTime(21, 0), foreignZone,
        ))
        val later = Schedule(3, "上海事项", ScheduleTiming.Timed(
            date.atTime(10, 0), date.atTime(11, 0), zone,
        ))
        val ended = Schedule(4, "已结束", ScheduleTiming.Timed(
            date.atTime(8, 0), date.atTime(9, 0), zone,
        ))
        val snapshot = composer.compose(now, zone, listOf(later, ended, earlier), preferences = visible)
        assertThat(snapshot.nextSchedule?.scheduleId).isEqualTo(2)
        assertThat(snapshot.nextSchedule?.timeLabel).isEqualTo("今天 09:30")
    }

    @Test fun `ongoing multi day item remains visible`() {
        val schedule = allDay(5, date.minusDays(3)).copy(
            timing = ScheduleTiming.AllDay(date.minusDays(3), date.plusDays(1)),
        )
        assertThat(composer.compose(now, zone, listOf(schedule), preferences = visible)
            .nextSchedule?.scheduleId).isEqualTo(5)
    }

    @Test fun `weather preserves stale age source and today high low`() {
        val weather = weather(now.minusSeconds(4 * 3600))
        val summary = composer.compose(now, zone, weather = weather).weather
        assertThat(summary?.temperature).isEqualTo("6°")
        assertThat(summary?.range).isEqualTo("2° / 9°")
        assertThat(summary?.freshness).isEqualTo(CacheFreshness.STALE)
        assertThat(summary?.updatedAtEpochMillis).isEqualTo(weather.current!!.fetchedAt.toEpochMilli())
        assertThat(summary?.attribution).isEqualTo("Open-Meteo")
    }

    @Test fun `expired weather is labelled without inventing missing daily range`() {
        val weather = weather(now.minusSeconds(73 * 3600)).copy(daily = null)
        val summary = composer.compose(now, zone, weather = weather).weather
        assertThat(summary?.freshness).isEqualTo(CacheFreshness.EXPIRED)
        assertThat(summary?.range).isNull()
    }

    @Test fun `city without current cache has no weather or attribution`() {
        assertThat(composer.compose(now, zone, weather = weather(now).copy(current = null)).weather).isNull()
    }

    @Test fun `timezone change composes new local calendar date`() {
        val snapshot = composer.compose(now, ZoneId.of("America/Los_Angeles"))
        assertThat(snapshot.date).isEqualTo("2025-02-01")
        assertThat(snapshot.weekday).isEqualTo("星期六")
    }

    private fun allDay(id: Long, date: LocalDate) = Schedule(
        id, "私人日程", ScheduleTiming.AllDay(date, date.plusDays(1)),
    )

    private fun weather(fetchedAt: Instant): WeatherSnapshot {
        val city = City("beijing", "北京", null, "中国", 39.9, 116.4, zone)
        val current = CurrentWeather(now.atZone(zone), 6.0, 4.0, 40, 0.0, 0, 8.0, true)
        val daily = DailyForecast(date, 0, 2.0, 9.0, 0, null, null)
        return WeatherSnapshot(city,
            current = CachedWeatherSegment(current, fetchedAt, fetchedAt.plusSeconds(1800)),
            daily = CachedWeatherSegment(listOf(daily), fetchedAt, fetchedAt.plusSeconds(21600)))
    }
}
