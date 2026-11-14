package com.tiandili.calendar.data.almanac

import com.nlf.calendar.Solar
import com.tiandili.calendar.domain.almanac.AlmanacDay
import com.tiandili.calendar.domain.almanac.AlmanacDaySummary
import com.tiandili.calendar.domain.almanac.AlmanacEngine
import com.tiandili.calendar.domain.almanac.DeityDirection
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LunarJavaAlmanacEngine : AlmanacEngine {

    override fun day(date: LocalDate, zoneId: ZoneId): AlmanacDay {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar

        return AlmanacDay(
            date = date,
            weekdayText = "星期${solar.weekInChinese}",
            lunarMonthText = "${lunar.monthInChinese}月",
            lunarDayText = lunar.dayInChinese,
            zodiac = lunar.yearShengXiao,
            festivals = (lunar.festivals + solar.festivals).distinct(),
            solarTerm = lunar.jieQi.takeIf(String::isNotBlank),
            yearGanzhi = lunar.yearInGanZhi,
            dayGanzhi = lunar.dayInGanZhi,
            suitable = lunar.dayYi,
            avoid = lunar.dayJi,
            pengZu = listOf(lunar.pengZuGan, lunar.pengZuZhi),
            deityDirections = listOf(
                DeityDirection("喜神", lunar.dayPositionXiDesc),
                DeityDirection("福神", lunar.dayPositionFuDesc),
                DeityDirection("财神", lunar.dayPositionCaiDesc),
            ),
        )
    }

    override fun month(
        yearMonth: YearMonth,
        zoneId: ZoneId,
    ): List<AlmanacDaySummary> =
        (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            val day = day(date, zoneId)
            AlmanacDaySummary(
                date = date,
                lunarDayText = day.lunarDayText,
                festivals = day.festivals,
                solarTerm = day.solarTerm,
            )
        }
}
