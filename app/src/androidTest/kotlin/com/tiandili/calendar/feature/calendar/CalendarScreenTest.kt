package com.tiandili.calendar.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class CalendarScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarShowsMonthAndSelectedSpringFestival() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        composeRule.setContent {
            TiandiliTheme {
                val state by viewModel.uiState.collectAsState()
                CalendarScreen(
                    state = state,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onReturnToToday = viewModel::returnToToday,
                    onDateSelected = viewModel::selectDate,
                )
            }
        }

        composeRule.onNodeWithText("2025 年 1 月").assertIsDisplayed()
        composeRule.onNodeWithText("正月初一 · 春节").assertIsDisplayed()
        composeRule.onNodeWithText("乙巳年 · 蛇").assertIsDisplayed()
    }
}
