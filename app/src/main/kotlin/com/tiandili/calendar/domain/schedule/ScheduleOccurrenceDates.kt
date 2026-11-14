package com.tiandili.calendar.domain.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun Schedule.occurrenceDates(
    window: ClosedRange<LocalDate>,
): Set<LocalDate> {
    val startDate: LocalDate
    val durationDays: Long
    when (val scheduleTiming = timing) {
        is ScheduleTiming.AllDay -> {
            startDate = scheduleTiming.startDate
            durationDays = ChronoUnit.DAYS.between(
                scheduleTiming.startDate,
                scheduleTiming.endDateExclusive,
            ) - 1
        }
        is ScheduleTiming.Timed -> {
            startDate = scheduleTiming.startsAt.toLocalDate()
            durationDays = ChronoUnit.DAYS.between(
                scheduleTiming.startsAt.toLocalDate(),
                scheduleTiming.endsAt.toLocalDate(),
            )
        }
    }
    val occurrenceStarts = recurrence?.let { rule ->
        OccurrenceGenerator.generateDates(
            startDate = startDate,
            rule = rule,
            window = window.start.minusDays(durationDays)..window.endInclusive,
        )
    } ?: listOf(startDate)

    return occurrenceStarts
        .flatMap { occurrenceStart ->
            (0..durationDays).map(occurrenceStart::plusDays)
        }
        .filterTo(mutableSetOf()) { it in window }
}
