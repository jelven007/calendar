package com.tiandili.calendar

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
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
class TiandiliScheduleNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `schedule destination renders the provided schedule feature`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val calendarViewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )
        composeRule.setContent {
            TiandiliTheme {
                TiandiliApp(
                    calendarViewModel = calendarViewModel,
                    scheduleContent = { Text("日程功能已连接") },
                )
            }
        }

        composeRule.onNodeWithText("日程").performClick()

        composeRule.onNodeWithText("日程功能已连接").assertIsDisplayed()
    }

    @Test
    fun `provided schedules mark their dates on the calendar`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val calendarViewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )
        composeRule.setContent {
            TiandiliTheme {
                TiandiliApp(
                    calendarViewModel = calendarViewModel,
                    schedules = listOf(
                        Schedule(
                            id = 1,
                            title = "有标记的日程",
                            timing = ScheduleTiming.AllDay(
                                startDate = LocalDate.of(2025, 2, 2),
                                endDateExclusive = LocalDate.of(2025, 2, 3),
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("有日程", substring = true),
        ).assertIsDisplayed()
    }
}
