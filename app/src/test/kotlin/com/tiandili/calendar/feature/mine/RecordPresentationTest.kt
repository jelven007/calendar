package com.tiandili.calendar.feature.mine

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.schedule.*
import java.time.*
import org.junit.Test

class RecordPresentationTest {
    private val today = LocalDate.of(2026, 11, 18)
    private fun record(id: Long, kind: ScheduleKind, date: LocalDate = today) =
        Schedule(id, "记录$id", ScheduleTiming.AllDay(date, date.plusDays(1)), kind = kind)

    @Test fun `next birthday uses lunar recurrence and ignores cancelled starts`() {
        val birthday = record(1, ScheduleKind.BIRTHDAY, LocalDate.of(2025, 1, 29))
            .copy(recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY, lunarMonth = 1, lunarDay = 1))
        assertThat(nextRecordDate(birthday, today)).isEqualTo(LocalDate.of(2027, 2, 6))
        assertThat(nextRecordDate(birthday, today, setOf(LocalDate.of(2027, 2, 6))))
            .isEqualTo(LocalDate.of(2028, 1, 26))
        assertThat(nextRecordDate(birthday.copy(enabled = false), today)).isNull()
    }

    @Test fun `search matches title and body and keeps completed records`() {
        val values = listOf(record(1, ScheduleKind.NOTE).copy(note = "杭州旅行", completed = true),
            record(2, ScheduleKind.SCHEDULE).copy(title = "旅行计划"), record(3, ScheduleKind.ALARM))
        assertThat(searchRecords(values, " 旅行 ").map { it.id }).containsExactly(1L, 2L)
        assertThat(searchRecords(values, "不存在")).isEmpty()
        assertThat(searchRecords(values, "  ")).isEmpty()
    }

    @Test fun `upcoming schedule skips passed time in record timezone`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val past = record(1, ScheduleKind.SCHEDULE).copy(timing = ScheduleTiming.Timed(
            today.atTime(8, 0), today.atTime(9, 0), zone))
        val future = record(2, ScheduleKind.SCHEDULE).copy(timing = ScheduleTiming.Timed(
            today.atTime(16, 0), today.atTime(17, 0), zone))
        val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
        assertThat(upcomingRecords(listOf(past, future), ScheduleKind.SCHEDULE, clock).map { it.first.id })
            .containsExactly(2L)
    }
}
