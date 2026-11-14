package com.tiandili.calendar.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class Schedule(
    val id: Long = 0,
    val title: String,
    val timing: ScheduleTiming,
    val note: String = "",
    val recurrence: RecurrenceRule? = null,
    val reminderMinutesBefore: Int? = null,
)

sealed interface ScheduleTiming {

    data class AllDay(
        val startDate: LocalDate,
        val endDateExclusive: LocalDate,
    ) : ScheduleTiming

    data class Timed(
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val zoneId: ZoneId,
    ) : ScheduleTiming
}

data class TimedOccurrence(
    val startsAt: ZonedDateTime,
    val endsAt: ZonedDateTime,
)
