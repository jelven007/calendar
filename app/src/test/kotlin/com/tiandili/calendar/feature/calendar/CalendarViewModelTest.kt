package com.tiandili.calendar.feature.calendar

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Test

class CalendarViewModelTest {

    @Test
    fun `week start updates the grid without resetting the selected date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zone), zone,
            DayOfWeek.MONDAY, LunarJavaAlmanacEngine(),
        )

        viewModel.setFirstDayOfWeek(DayOfWeek.SUNDAY)

        assertThat(viewModel.uiState.value.firstDayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(viewModel.uiState.value.cells.first().date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(LocalDate.of(2025, 1, 29))
    }

    @Test
    fun `initial state displays the current month and selects today`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        val state = viewModel.uiState.value

        assertThat(state.displayedMonth).isEqualTo(YearMonth.of(2025, 1))
        assertThat(state.selectedDate).isEqualTo(LocalDate.of(2025, 1, 29))
        assertThat(state.cells).hasSize(35)
        assertThat(state.cells.single { it.date == LocalDate.of(2025, 1, 29) }.label)
            .isEqualTo("春节")
        assertThat(state.selectedAlmanac.lunarMonthText).isEqualTo("正月")
        assertThat(state.selectedAlmanac.lunarDayText).isEqualTo("初一")
    }

    @Test
    fun `selecting an adjacent-month date moves the displayed month`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        viewModel.selectDate(LocalDate.of(2025, 2, 2))

        assertThat(viewModel.uiState.value.selectedDate)
            .isEqualTo(LocalDate.of(2025, 2, 2))
        assertThat(viewModel.uiState.value.displayedMonth)
            .isEqualTo(YearMonth.of(2025, 2))
        assertThat(viewModel.uiState.value.selectedAlmanac.date)
            .isEqualTo(LocalDate.of(2025, 2, 2))
    }

    @Test
    fun `moving to a shorter next month clamps the selected day`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        viewModel.nextMonth()

        assertThat(viewModel.uiState.value.displayedMonth)
            .isEqualTo(YearMonth.of(2025, 2))
        assertThat(viewModel.uiState.value.selectedDate)
            .isEqualTo(LocalDate.of(2025, 2, 28))
    }

    @Test
    fun `moving to the previous month keeps the selected day when available`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        viewModel.previousMonth()

        assertThat(viewModel.uiState.value.selectedDate)
            .isEqualTo(LocalDate.of(2024, 12, 29))
    }

    @Test
    fun `return to today restores the clock date`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )
        viewModel.nextMonth()

        viewModel.returnToToday()

        assertThat(viewModel.uiState.value.selectedDate)
            .isEqualTo(LocalDate.of(2025, 1, 29))
    }

    @Test fun `today reads the clock again after midnight and range edges stay usable`() {
        val zone = ZoneId.of("Asia/Shanghai")
        var now = Instant.parse("2026-11-18T15:59:00Z")
        val clock = object : Clock() {
            override fun getZone() = zone
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant() = now
        }
        val vm = CalendarViewModel(clock, zone, DayOfWeek.SUNDAY, LunarJavaAlmanacEngine())
        now = Instant.parse("2026-11-18T16:01:00Z")
        vm.returnToToday()
        assertThat(vm.uiState.value.selectedDate).isEqualTo(LocalDate.of(2026, 11, 19))
        vm.selectDate(LocalDate.of(1901, 1, 1))
        vm.previousMonth()
        assertThat(vm.uiState.value.selectedDate).isEqualTo(LocalDate.of(1901, 1, 1))
    }
}
