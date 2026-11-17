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
    val kind: ScheduleKind = ScheduleKind.SCHEDULE,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val mediaUris: List<String> = emptyList(),
) {
    val calendarEligible: Boolean
        get() = enabled && !completed &&
            (kind in setOf(ScheduleKind.SCHEDULE, ScheduleKind.BIRTHDAY,
                ScheduleKind.ANNIVERSARY, ScheduleKind.ALARM) || reminderMinutesBefore != null)
}

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
