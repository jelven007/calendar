package com.tiandili.calendar.data.almanac

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Test

class LunarJavaAlmanacEngineTest {

    private val engine = LunarJavaAlmanacEngine()

    @Test
    fun `maps 2025 spring festival to the first day of the first lunar month`() {
        val day = engine.day(
            date = LocalDate.of(2025, 1, 29),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertThat(day.lunarMonthText).isEqualTo("正月")
        assertThat(day.lunarDayText).isEqualTo("初一")
        assertThat(day.zodiac).isEqualTo("蛇")
        assertThat(day.festivals).contains("春节")
    }

    @Test
    fun `maps every day in a leap-year February in date order`() {
        val days = engine.month(
            yearMonth = YearMonth.of(2024, 2),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertThat(days).hasSize(29)
        assertThat(days.first().date).isEqualTo(LocalDate.of(2024, 2, 1))
        assertThat(days.last().date).isEqualTo(LocalDate.of(2024, 2, 29))
        val springFestival = days.single { it.date == LocalDate.of(2024, 2, 10) }
        assertThat(springFestival.lunarDayText).isEqualTo("初一")
        assertThat(springFestival.festivals).contains("春节")
        assertThat(springFestival.displayLabel).isEqualTo("春节")
    }

    @Test
    fun `maps Qingming detail fields without leaking provider types`() {
        val day = engine.day(
            date = LocalDate.of(2025, 4, 4),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertThat(day.weekdayText).isEqualTo("星期五")
        assertThat(day.solarTerm).isEqualTo("清明")
        assertThat(day.yearGanzhi).isEqualTo("乙巳")
        assertThat(day.dayGanzhi).isNotEmpty()
        assertThat(day.suitable).isNotEmpty()
        assertThat(day.avoid).isNotEmpty()
        assertThat(day.pengZu).hasSize(2)
        assertThat(day.deityDirections.map { it.name })
            .containsExactly("喜神", "福神", "财神")
    }

    @Test
    fun `prioritizes a solar term in the month cell label`() {
        val qingming = engine
            .month(YearMonth.of(2025, 4), ZoneId.of("Asia/Shanghai"))
            .single { it.date == LocalDate.of(2025, 4, 4) }

        assertThat(qingming.solarTerm).isEqualTo("清明")
        assertThat(qingming.displayLabel).isEqualTo("清明")
    }
}
