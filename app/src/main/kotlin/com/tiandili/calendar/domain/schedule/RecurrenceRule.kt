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
) {
    init {
        require(interval > 0) { "重复间隔必须大于 0" }
        require(count == null || count > 0) { "重复次数必须大于 0" }
    }
}
