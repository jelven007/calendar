package com.tiandili.calendar.feature.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class MonthCell(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
    val label: String? = null,
)

class CalendarRangeException :
    IllegalArgumentException("支持的日期范围为 1901 年至 2099 年")

fun buildMonthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
): List<MonthCell> {
    if (yearMonth.year < 1901 || yearMonth.year > 2099) {
        throw CalendarRangeException()
    }

    val firstOfMonth = yearMonth.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())

    return List(42) { index ->
        val date = gridStart.plusDays(index.toLong())
        MonthCell(
            date = date,
            isInDisplayedMonth = YearMonth.from(date) == yearMonth,
        )
    }
}
