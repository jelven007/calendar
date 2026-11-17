package com.tiandili.calendar.feature.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarScheduleMarkerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `calendar date with a schedule exposes its marker semantics`() {
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
                    scheduleDates = setOf(LocalDate.of(2025, 1, 29)),
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onReturnToToday = viewModel::returnToToday,
                    onDateSelected = viewModel::selectDate,
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("有日程", substring = true),
        ).performScrollTo().assertIsDisplayed()
    }
}
