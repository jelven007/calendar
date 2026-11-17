package com.tiandili.calendar.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ScheduleOccurrenceDatesTest {

    @Test fun `cancelling a multi day occurrence hides all its dates but keeps later repeats`() {
        val record = Schedule(title = "出行", timing = ScheduleTiming.AllDay(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)),
            recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY))
        assertThat(record.occurrenceDates(LocalDate.of(2026, 11, 2)..LocalDate.of(2026, 11, 10),
            setOf(LocalDate.of(2026, 11, 1)))).containsExactly(
            LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10))
    }

    @Test
    fun `monthly schedule contributes its clamped date to a calendar window`() {
        val schedule = Schedule(
            title = "月末结账",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 1, 31, 9, 0),
                endsAt = LocalDateTime.of(2025, 1, 31, 10, 0),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY),
        )

        val dates = schedule.occurrenceDates(
            window = LocalDate.of(2025, 2, 1)..LocalDate.of(2025, 2, 28),
        )

        assertThat(dates).containsExactly(LocalDate.of(2025, 2, 28))
    }
}
