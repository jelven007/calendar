package com.tiandili.calendar.domain.almanac

import java.time.LocalDate

data class AlmanacDay(
    val date: LocalDate,
    val weekdayText: String,
    val lunarMonthText: String,
    val lunarDayText: String,
    val zodiac: String,
    val festivals: List<String>,
    val solarTerm: String?,
    val yearGanzhi: String,
    val dayGanzhi: String,
    val suitable: List<String>,
    val avoid: List<String>,
    val pengZu: List<String>,
    val deityDirections: List<DeityDirection>,
    val monthGanzhi: String = "",
)

data class DeityDirection(
    val name: String,
    val direction: String,
)
