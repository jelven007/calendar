package com.tiandili.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class TiandiliAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appShowsBrandAndFourTopLevelDestinations() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val viewModel = CalendarViewModel(
            clock = Clock.fixed(Instant.parse("2025-01-29T02:00:00Z"), zoneId),
            zoneId = zoneId,
            firstDayOfWeek = DayOfWeek.MONDAY,
            almanacEngine = LunarJavaAlmanacEngine(),
        )

        composeRule.setContent {
            TiandiliTheme {
                TiandiliApp(calendarViewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("天地历").assertIsDisplayed()
        composeRule.onNodeWithText("月历").assertIsDisplayed()
        composeRule.onNodeWithText("日程").assertIsDisplayed()
        composeRule.onNodeWithText("天气").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        composeRule.onNodeWithText("天气").performClick()
        composeRule.onNodeWithText("尚未选择城市").assertIsDisplayed()
    }
}
