package com.tiandili.calendar.feature.calendar

import androidx.lifecycle.ViewModel
import com.tiandili.calendar.domain.almanac.AlmanacEngine
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

class CalendarViewModel(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private var firstDayOfWeek: DayOfWeek,
    private val almanacEngine: AlmanacEngine,
) : ViewModel() {

    private val today = clock.instant().atZone(zoneId).toLocalDate()
    private val _uiState = MutableStateFlow(
        CalendarUiState(
            displayedMonth = YearMonth.from(today),
            selectedDate = today,
            cells = buildCalendarCells(YearMonth.from(today)),
            selectedAlmanac = almanacEngine.day(today, zoneId),
            firstDayOfWeek = firstDayOfWeek,
        ),
    )

    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        if (firstDayOfWeek == dayOfWeek) return
        firstDayOfWeek = dayOfWeek
        _uiState.value = _uiState.value.copy(
            firstDayOfWeek = dayOfWeek,
            cells = buildCalendarCells(_uiState.value.displayedMonth),
        )
    }

    fun selectDate(date: LocalDate) {
        if (date.year !in 1901..2099) return
        val displayedMonth = YearMonth.from(date)
        _uiState.value = _uiState.value.copy(
            displayedMonth = displayedMonth,
            selectedDate = date,
            cells = buildCalendarCells(displayedMonth),
            selectedAlmanac = almanacEngine.day(date, zoneId),
        )
    }

    fun nextMonth() {
        moveMonth(1)
    }

    fun previousMonth() {
        moveMonth(-1)
    }

    fun returnToToday() {
        selectDate(clock.instant().atZone(zoneId).toLocalDate())
    }

    private fun moveMonth(offset: Long) {
        val targetMonth = _uiState.value.displayedMonth.plusMonths(offset)
        if (targetMonth.year !in 1901..2099) return
        val targetDay = min(_uiState.value.selectedDate.dayOfMonth, targetMonth.lengthOfMonth())
        selectDate(targetMonth.atDay(targetDay))
    }

    private fun buildCalendarCells(yearMonth: YearMonth): List<MonthCell> {
        val summariesByDate = almanacEngine
            .month(yearMonth, zoneId)
            .associateBy { it.date }
        return buildMonthGrid(yearMonth, firstDayOfWeek).map { cell ->
            val summary = summariesByDate[cell.date]
            cell.copy(label = summary?.displayLabel, isFestival = summary?.festivals?.isNotEmpty() == true,
                isSolarTerm = summary?.solarTerm != null)
        }
    }
}
