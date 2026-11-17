package com.tiandili.calendar.feature.explore

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateTools {
    fun daysBetween(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end)
    fun offset(start: LocalDate, days: Long): LocalDate? =
        runCatching { start.plusDays(days) }.getOrNull()?.takeIf { it.year in 1901..2099 }
}
