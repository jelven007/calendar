package com.tiandili.calendar.domain.almanac

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

interface AlmanacEngine {
    fun day(date: LocalDate, zoneId: ZoneId): AlmanacDay

    fun month(yearMonth: YearMonth, zoneId: ZoneId): List<AlmanacDaySummary>
}
