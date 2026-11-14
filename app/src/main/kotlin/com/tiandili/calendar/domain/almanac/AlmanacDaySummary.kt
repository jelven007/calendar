package com.tiandili.calendar.domain.almanac

import java.time.LocalDate

data class AlmanacDaySummary(
    val date: LocalDate,
    val lunarDayText: String,
    val festivals: List<String>,
    val solarTerm: String?,
) {
    val displayLabel: String
        get() = solarTerm ?: festivals.firstOrNull() ?: lunarDayText
}
