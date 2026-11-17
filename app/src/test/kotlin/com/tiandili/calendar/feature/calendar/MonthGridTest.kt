package com.tiandili.calendar.feature.calendar

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertThrows
import org.junit.Test

class MonthGridTest {

    @Test
    fun `january 2025 Monday-first grid uses only five rows`() {
        val cells = buildMonthGrid(
            yearMonth = YearMonth.of(2025, 1),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertThat(cells).hasSize(35)
        assertThat(cells.first().date).isEqualTo(LocalDate.of(2024, 12, 30))
        assertThat(cells.last().date).isEqualTo(LocalDate.of(2025, 2, 2))
    }

    @Test fun `grid supports exact four and six week months`() {
        assertThat(buildMonthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)).hasSize(28)
        assertThat(buildMonthGrid(YearMonth.of(2026, 8), DayOfWeek.SUNDAY)).hasSize(42)
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
