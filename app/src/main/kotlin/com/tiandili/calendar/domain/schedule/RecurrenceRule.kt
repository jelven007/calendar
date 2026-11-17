package com.tiandili.calendar.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
) {
    init {
        require(interval > 0) { "重复间隔必须大于 0" }
        require(count == null || count > 0) { "重复次数必须大于 0" }
        require((lunarMonth == null) == (lunarDay == null)) { "农历月日必须同时设置" }
        require(lunarMonth == null || (frequency == RecurrenceFrequency.YEARLY &&
            lunarMonth in -12..12 && lunarMonth != 0 && lunarDay in 1..30)) {
            "农历年重复月日无效"
        }
    }
}
