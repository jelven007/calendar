package com.tiandili.calendar.feature.calendar

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertThrows
import org.junit.Test

class MonthGridTest {

    @Test
    fun `january 2025 Monday-first grid starts on December 30 and has 42 cells`() {
        val cells = buildMonthGrid(
            yearMonth = YearMonth.of(2025, 1),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertThat(cells).hasSize(42)
        assertThat(cells.first().date).isEqualTo(LocalDate.of(2024, 12, 30))
        assertThat(cells.last().date).isEqualTo(LocalDate.of(2025, 2, 9))
    }

    @Test
    fun `rejects displayed months before the supported range`() {
        val error = assertThrows(CalendarRangeException::class.java) {
            buildMonthGrid(
                yearMonth = YearMonth.of(1900, 12),
                firstDayOfWeek = DayOfWeek.MONDAY,
            )
        }

        assertThat(error.message).isEqualTo("支持的日期范围为 1901 年至 2099 年")
    }

    @Test
    fun `rejects displayed months after the supported range`() {
        assertThrows(CalendarRangeException::class.java) {
            buildMonthGrid(
                yearMonth = YearMonth.of(2100, 1),
                firstDayOfWeek = DayOfWeek.MONDAY,
            )
        }
    }
}
