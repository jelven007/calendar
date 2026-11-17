package com.tiandili.calendar

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
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

    @Test fun `cancelled instance has no label and almanac follows selected day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), zone)
        val vm = CalendarViewModel(clock, zone, DayOfWeek.SUNDAY, LunarJavaAlmanacEngine())
        composeRule.setContent { TiandiliTheme { TiandiliApp(vm, clock = clock,
            schedules = listOf(Schedule(id = 1, title = "已取消实例", timing = ScheduleTiming.AllDay(
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19)))),
            cancelledOccurrences = mapOf(1L to setOf(LocalDate.of(2026, 11, 18)))) } }
        composeRule.onNode(hasContentDescription("有日程", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("2026年11月19日", substring = true)).performScrollTo().performClick()
        composeRule.onNodeWithText("黄历").performClick()
        composeRule.onNodeWithText("2026-11-19 ⌄").assertIsDisplayed()
        assertThat(vm.uiState.value.selectedAlmanac.date).isEqualTo(LocalDate.of(2026, 11, 19))
    }

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

        listOf("日历", "黄历", "天气", "发现", "我的").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("我的").performClick()
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
        composeRule.onNodeWithText("有标记的日程", substring = true).assertIsDisplayed()
    }
}
