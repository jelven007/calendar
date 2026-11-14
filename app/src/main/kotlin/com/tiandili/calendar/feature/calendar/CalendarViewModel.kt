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
    clock: Clock,
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
        selectDate(today)
    }

    private fun moveMonth(offset: Long) {
        val targetMonth = _uiState.value.displayedMonth.plusMonths(offset)
        val targetDay = min(_uiState.value.selectedDate.dayOfMonth, targetMonth.lengthOfMonth())
        selectDate(targetMonth.atDay(targetDay))
    }

    private fun buildCalendarCells(yearMonth: YearMonth): List<MonthCell> {
        val summariesByDate = almanacEngine
            .month(yearMonth, zoneId)
            .associateBy { it.date }
        return buildMonthGrid(yearMonth, firstDayOfWeek).map { cell ->
            cell.copy(label = summariesByDate[cell.date]?.displayLabel)
        }
    }
}
