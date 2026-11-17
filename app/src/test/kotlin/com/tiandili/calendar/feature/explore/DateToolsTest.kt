package com.tiandili.calendar.feature.explore

import com.google.common.truth.Truth.assertThat
import java.time.*
import org.junit.Test

class DateToolsTest {
    @Test fun `date interval handles leap day reverse dates and same day`() {
        val start = LocalDate.of(2024, 2, 28)
        val end = LocalDate.of(2024, 3, 1)
        assertThat(DateTools.daysBetween(start, end)).isEqualTo(2)
        assertThat(DateTools.daysBetween(end, start)).isEqualTo(-2)
        assertThat(DateTools.daysBetween(end, end)).isEqualTo(0)
        assertThat(DateTools.offset(start, 1)).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(DateTools.offset(start, -1)).isEqualTo(LocalDate.of(2024, 2, 27))
        assertThat(DateTools.offset(LocalDate.of(2099, 12, 31), 1)).isNull()
    }
}
