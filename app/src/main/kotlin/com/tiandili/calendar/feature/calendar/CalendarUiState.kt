package com.tiandili.calendar.feature.calendar

import com.tiandili.calendar.domain.almanac.AlmanacDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

data class CalendarUiState(
    val displayedMonth: YearMonth,
    val selectedDate: LocalDate,
    val cells: List<MonthCell>,
    val selectedAlmanac: AlmanacDay,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)
