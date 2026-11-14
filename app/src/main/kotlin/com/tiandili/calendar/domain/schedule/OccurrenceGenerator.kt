package com.tiandili.calendar.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

object OccurrenceGenerator {

    fun generateTimed(
        timing: ScheduleTiming.Timed,
        rule: RecurrenceRule,
        window: ClosedRange<LocalDate>,
    ): List<TimedOccurrence> {
        val endDayOffset = ChronoUnit.DAYS.between(
            timing.startsAt.toLocalDate(),
            timing.endsAt.toLocalDate(),
        )
        return generateDates(timing.startsAt.toLocalDate(), rule, window).map { date ->
            TimedOccurrence(
                startsAt = resolveLocalDateTime(
                    date.atTime(timing.startsAt.toLocalTime()),
                    timing.zoneId,
                ),
                endsAt = resolveLocalDateTime(
                    date.plusDays(endDayOffset).atTime(timing.endsAt.toLocalTime()),
                    timing.zoneId,
                ),
            )
        }
    }

    fun generateDates(
        startDate: LocalDate,
        rule: RecurrenceRule,
        window: ClosedRange<LocalDate>,
    ): List<LocalDate> {
        val effectiveEnd = minOf(window.endInclusive, rule.until ?: window.endInclusive)
        val generationWindow = startDate..effectiveEnd
        val occurrences = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> generateDaily(startDate, rule.interval, generationWindow)
            RecurrenceFrequency.WEEKLY -> generateWeekly(startDate, rule, generationWindow)
            RecurrenceFrequency.MONTHLY -> generateMonthly(startDate, rule.interval, generationWindow)
            RecurrenceFrequency.YEARLY -> generateYearly(startDate, rule.interval, generationWindow)
        }
        val limited = rule.count?.let(occurrences::take) ?: occurrences
        return limited.filter { it in window }
    }

    private fun generateDaily(
        startDate: LocalDate,
        interval: Int,
        window: ClosedRange<LocalDate>,
    ): List<LocalDate> {
        val occurrences = mutableListOf<LocalDate>()
        var occurrence = startDate

        while (occurrence <= window.endInclusive) {
            if (occurrence in window) {
                occurrences += occurrence
            }
            occurrence = occurrence.plusDays(interval.toLong())
        }

        return occurrences
    }

    private fun generateWeekly(
        startDate: LocalDate,
        rule: RecurrenceRule,
        window: ClosedRange<LocalDate>,
    ): List<LocalDate> {
        val selectedDays = rule.weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
        val startWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val occurrences = mutableListOf<LocalDate>()
        var date = startDate

        while (date <= window.endInclusive) {
            val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekOffset = ChronoUnit.WEEKS.between(startWeek, dateWeek)
            if (weekOffset % rule.interval == 0L && date.dayOfWeek in selectedDays && date in window) {
                occurrences += date
            }
            date = date.plusDays(1)
        }

        return occurrences
    }

    private fun generateMonthly(
        startDate: LocalDate,
        interval: Int,
        window: ClosedRange<LocalDate>,
    ): List<LocalDate> {
        val anchorDay = startDate.dayOfMonth
        val occurrences = mutableListOf<LocalDate>()
        var month = YearMonth.from(startDate)

        while (month.atDay(1) <= window.endInclusive) {
            val occurrence = month.atDay(min(anchorDay, month.lengthOfMonth()))
            if (occurrence >= startDate && occurrence in window) {
                occurrences += occurrence
            }
            month = month.plusMonths(interval.toLong())
        }

        return occurrences
    }

    private fun generateYearly(
        startDate: LocalDate,
        interval: Int,
        window: ClosedRange<LocalDate>,
    ): List<LocalDate> {
        val occurrences = mutableListOf<LocalDate>()
        var year = startDate.year

        while (LocalDate.of(year, 1, 1) <= window.endInclusive) {
            val month = YearMonth.of(year, startDate.month)
            val occurrence = month.atDay(min(startDate.dayOfMonth, month.lengthOfMonth()))
            if (occurrence >= startDate && occurrence in window) {
                occurrences += occurrence
            }
            year += interval
        }

        return occurrences
    }

    private fun resolveLocalDateTime(
        localDateTime: LocalDateTime,
        zoneId: ZoneId,
    ): ZonedDateTime {
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(localDateTime)
        return when (validOffsets.size) {
            1 -> ZonedDateTime.ofStrict(localDateTime, validOffsets.single(), zoneId)
            0 -> {
                val transition = requireNotNull(rules.getTransition(localDateTime))
                ZonedDateTime.ofStrict(
                    localDateTime.plusSeconds(transition.duration.seconds),
                    transition.offsetAfter,
                    zoneId,
                )
            }
            else -> ZonedDateTime.ofStrict(localDateTime, validOffsets.first(), zoneId)
        }
    }
}
