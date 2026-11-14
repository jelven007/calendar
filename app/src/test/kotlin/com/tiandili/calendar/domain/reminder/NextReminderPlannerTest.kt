package com.tiandili.calendar.domain.reminder

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class NextReminderPlannerTest {

    @Test
    fun `daily timed series plans the next occurrence with the same lead time`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val schedule = Schedule(
            title = "晨会",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 1, 1, 9, 0),
                endsAt = LocalDateTime.of(2025, 1, 1, 10, 0),
                zoneId = zoneId,
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminderMinutesBefore = 15,
        )
        val currentOccurrence = LocalDateTime.of(2025, 1, 1, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val plan = NextReminderPlanner.planAfter(schedule, currentOccurrence)

        val expectedOccurrence = LocalDateTime.of(2025, 1, 2, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        assertThat(plan).isEqualTo(
            ReminderPlan(
                occurrenceEpochMillis = expectedOccurrence,
                triggerAtEpochMillis = expectedOccurrence - 15 * 60 * 1000L,
            ),
        )
    }

    @Test
    fun `monthly series keeps its original month end anchor`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val schedule = Schedule(
            title = "月末结账",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 1, 31, 9, 0),
                endsAt = LocalDateTime.of(2025, 1, 31, 10, 0),
                zoneId = zoneId,
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY),
            reminderMinutesBefore = 0,
        )
        val currentOccurrence = LocalDateTime.of(2025, 1, 31, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val plan = NextReminderPlanner.planAfter(schedule, currentOccurrence)

        assertThat(plan?.occurrenceEpochMillis).isEqualTo(
            LocalDateTime.of(2025, 2, 28, 9, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
        )
    }

    @Test
    fun `series with exhausted count has no next reminder`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val schedule = Schedule(
            title = "仅一次",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 1, 1),
                endDateExclusive = LocalDate.of(2025, 1, 2),
            ),
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                count = 1,
            ),
            reminderMinutesBefore = 0,
        )
        val currentOccurrence = LocalDate.of(2025, 1, 1)
            .atTime(9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val plan = NextReminderPlanner.planAfter(
            schedule = schedule,
            currentOccurrenceEpochMillis = currentOccurrence,
            defaultZoneId = zoneId,
        )

        assertThat(plan).isNull()
    }

    @Test
    fun `all day series plans its next reminder at nine in the morning`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val schedule = Schedule(
            title = "晨间事项",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 1, 1),
                endDateExclusive = LocalDate.of(2025, 1, 2),
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
            reminderMinutesBefore = 0,
        )
        val currentOccurrence = LocalDate.of(2025, 1, 1)
            .atTime(9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val plan = NextReminderPlanner.planAfter(
            schedule = schedule,
            currentOccurrenceEpochMillis = currentOccurrence,
            defaultZoneId = zoneId,
        )

        assertThat(plan?.occurrenceEpochMillis).isEqualTo(
            LocalDate.of(2025, 1, 2)
                .atTime(9, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
        )
    }
}
