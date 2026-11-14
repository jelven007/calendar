package com.tiandili.calendar

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
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
class TiandiliWeatherNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val instant = Instant.parse("2025-02-02T01:00:00Z")
    private val clock = Clock.fixed(instant, zoneId)

    @Test
    fun `weather destination renders the provided weather feature`() {
        composeRule.setContent {
            TiandiliTheme {
                TiandiliApp(
                    calendarViewModel = calendarViewModel(),
                    weatherContent = { Text("天气功能已连接") },
                )
            }
        }

        composeRule.onNodeWithText("天气").performClick()

        composeRule.onNodeWithText("天气功能已连接").assertIsDisplayed()
    }

    @Test
    fun `selected calendar day shows current city daily weather summary`() {
        val city = City(
            id = "hangzhou",
            name = "杭州",
            admin1 = "浙江",
            country = "中国",
            latitude = 30.2,
            longitude = 120.1,
            zoneId = zoneId,
        )
        val fetchedAt = instant.minus(Duration.ofMinutes(10))
        val snapshot = WeatherSnapshot(
            city = city,
            daily = CachedWeatherSegment(
                value = listOf(
                    DailyForecast(
                        date = LocalDate.of(2025, 2, 2),
                        weatherCode = 2,
                        minTemperatureC = 3.0,
                        maxTemperatureC = 10.0,
                        precipitationProbabilityPercent = 20,
                        sunrise = null,
                        sunset = null,
                    ),
                ),
                fetchedAt = fetchedAt,
                expiresAt = fetchedAt.plus(Duration.ofHours(6)),
            ),
        )
        composeRule.setContent {
            TiandiliTheme {
                TiandiliApp(
                    calendarViewModel = calendarViewModel(),
                    weatherSnapshot = snapshot,
                    clock = clock,
                )
            }
        }

        composeRule.onNodeWithTag("calendar_content").performScrollToNode(
            hasText("多云 3–10℃"),
        )
        composeRule.onNodeWithText("多云 3–10℃").assertIsDisplayed()
    }

    private fun calendarViewModel() = CalendarViewModel(
        clock = clock,
        zoneId = zoneId,
        firstDayOfWeek = DayOfWeek.MONDAY,
        almanacEngine = LunarJavaAlmanacEngine(),
    )
}
