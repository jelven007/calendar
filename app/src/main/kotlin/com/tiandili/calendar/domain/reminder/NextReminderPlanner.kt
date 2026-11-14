package com.tiandili.calendar.domain.reminder

import com.tiandili.calendar.domain.schedule.OccurrenceGenerator
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val DEFAULT_ALL_DAY_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

data class ReminderPlan(
    val occurrenceEpochMillis: Long,
    val triggerAtEpochMillis: Long,
)

object NextReminderPlanner {

    fun planAfter(
        schedule: Schedule,
        currentOccurrenceEpochMillis: Long,
        defaultZoneId: ZoneId = ZoneId.systemDefault(),
    ): ReminderPlan? {
        val rule = schedule.recurrence ?: return null
        val minutesBefore = schedule.reminderMinutesBefore ?: return null
        val zoneId = when (val timing = schedule.timing) {
            is ScheduleTiming.AllDay -> defaultZoneId
            is ScheduleTiming.Timed -> timing.zoneId
        }
        val currentDate = Instant.ofEpochMilli(currentOccurrenceEpochMillis)
            .atZone(zoneId)
            .toLocalDate()
        val searchStart = currentDate
        val searchEnd = minOf(
            rule.until ?: LocalDate.MAX,
            searchEnd(currentDate, rule.frequency, rule.interval),
        )
        if (searchEnd < searchStart) {
            return null
        }
        val occurrenceEpochMillis = when (val timing = schedule.timing) {
            is ScheduleTiming.AllDay ->
                OccurrenceGenerator.generateDates(
                    startDate = timing.startDate,
                    rule = rule,
                    window = searchStart..searchEnd,
                ).asSequence()
                    .map {
                        it.atTime(DEFAULT_ALL_DAY_REMINDER_TIME)
                            .atZone(zoneId)
                            .toInstant()
                            .toEpochMilli()
                    }
                    .firstOrNull { it > currentOccurrenceEpochMillis }
            is ScheduleTiming.Timed ->
                OccurrenceGenerator.generateTimed(
                    timing = timing,
                    rule = rule,
                    window = searchStart..searchEnd,
                ).asSequence()
                    .map { it.startsAt.toInstant().toEpochMilli() }
                    .firstOrNull { it > currentOccurrenceEpochMillis }
        } ?: return null
        return ReminderPlan(
            occurrenceEpochMillis = occurrenceEpochMillis,
            triggerAtEpochMillis =
                occurrenceEpochMillis - minutesBefore * 60_000L,
        )
    }

    private fun searchEnd(
        currentDate: LocalDate,
        frequency: RecurrenceFrequency,
        interval: Int,
    ): LocalDate =
        when (frequency) {
            RecurrenceFrequency.DAILY -> currentDate.plusDays(interval.toLong())
            RecurrenceFrequency.WEEKLY ->
                currentDate.plusWeeks(interval.toLong()).plusDays(7)
            RecurrenceFrequency.MONTHLY ->
                currentDate.plusMonths(interval.toLong() + 1)
            RecurrenceFrequency.YEARLY ->
                currentDate.plusYears(interval.toLong() + 1)
        }
}
