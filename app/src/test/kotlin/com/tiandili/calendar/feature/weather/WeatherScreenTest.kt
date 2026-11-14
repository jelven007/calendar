package com.tiandili.calendar.feature.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.HourlyForecast
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import com.tiandili.calendar.ui.theme.TiandiliTheme
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
class WeatherScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2025-01-02T05:00:00Z")
    private val city = City(
        id = "open-meteo:1808926",
        name = "杭州市",
        admin1 = "浙江",
        country = "中国",
        latitude = 30.29365,
        longitude = 120.16142,
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun `empty weather state offers location and manual city search`() {
        composeRule.setContent {
            TiandiliTheme {
                WeatherScreen(
                    state = WeatherUiState(isCityPickerOpen = true),
                    now = now,
                )
            }
        }

        composeRule.onNodeWithText("选择城市").assertIsDisplayed()
        composeRule.onNodeWithText("使用当前位置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索城市").assertIsDisplayed()
    }

    @Test
    fun `weather snapshot shows current hourly daily air quality and attribution`() {
        composeRule.setContent {
            TiandiliTheme {
                WeatherScreen(
                    state = WeatherUiState(snapshot = sampleSnapshot()),
                    now = now,
                )
            }
        }

        composeRule.onNodeWithText("杭州市").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("当前温度 8°").assertIsDisplayed()
        composeRule.onNodeWithText("多云").assertIsDisplayed()
        composeRule.onNodeWithText("未来 24 小时").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_content").performScrollToNode(hasText("未来 7 日"))
        composeRule.onNodeWithText("未来 7 日").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_content").performScrollToNode(hasText("US AQI 42"))
        composeRule.onNodeWithText("US AQI 42").assertIsDisplayed()
        composeRule.onNodeWithText("良好").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_content").performScrollToNode(
            hasText("Weather data by Open-Meteo.com"),
        )
        composeRule.onNodeWithText("Weather data by Open-Meteo.com").assertIsDisplayed()
    }

    @Test
    fun `expired cache remains visible with explicit freshness labels`() {
        val old = now.minus(Duration.ofHours(73))
        composeRule.setContent {
            TiandiliTheme {
                WeatherScreen(
                    state = WeatherUiState(snapshot = sampleSnapshot(fetchedAt = old)),
                    now = now,
                )
            }
        }

        composeRule.onNodeWithContentDescription("当前温度 8°").assertIsDisplayed()
        composeRule.onAllNodesWithText("已过期", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun `attribution invokes the external link action`() {
        var opened = false
        composeRule.setContent {
            TiandiliTheme {
                WeatherScreen(
                    state = WeatherUiState(snapshot = sampleSnapshot()),
                    now = now,
                    onOpenAttribution = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag("weather_content").performScrollToNode(
            hasText("Weather data by Open-Meteo.com"),
        )
        composeRule.onNodeWithText("Weather data by Open-Meteo.com").performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `same name search result includes region and country`() {
        composeRule.setContent {
            TiandiliTheme {
                WeatherScreen(
                    state = WeatherUiState(
                        isCityPickerOpen = true,
                        searchResults = listOf(city),
                    ),
                    now = now,
                )
            }
        }

        composeRule.onNodeWithText("浙江 · 中国").assertIsDisplayed()
    }

    private fun sampleSnapshot(
        fetchedAt: Instant = now.minus(Duration.ofMinutes(10)),
    ): WeatherSnapshot {
        val current = CurrentWeather(
            observedAt = now.atZone(city.zoneId),
            temperatureC = 8.4,
            apparentTemperatureC = 7.1,
            relativeHumidityPercent = 64,
            precipitationMm = 0.0,
            weatherCode = 2,
            windSpeedKph = 8.7,
            isDay = true,
        )
        val hourly = listOf(
            HourlyForecast(
                time = now.atZone(city.zoneId),
                temperatureC = 8.4,
                precipitationProbabilityPercent = 10,
                weatherCode = 2,
                windSpeedKph = 8.7,
            ),
        )
        val daily = listOf(
            DailyForecast(
                date = LocalDate.of(2025, 1, 2),
                weatherCode = 2,
                minTemperatureC = 3.0,
                maxTemperatureC = 10.0,
                precipitationProbabilityPercent = 20,
                sunrise = now.minus(Duration.ofHours(6)).atZone(city.zoneId),
                sunset = now.plus(Duration.ofHours(4)).atZone(city.zoneId),
            ),
        )
        val airQuality = AirQuality(
            observedAt = now.atZone(city.zoneId),
            usAqi = 42,
            pm25MicrogramsPerCubicMeter = 12.3,
            pm10MicrogramsPerCubicMeter = 20.4,
            ozoneMicrogramsPerCubicMeter = 55.1,
            nitrogenDioxideMicrogramsPerCubicMeter = 18.2,
        )
        return WeatherSnapshot(
            city = city,
            current = CachedWeatherSegment(
                current,
                fetchedAt,
                fetchedAt.plus(Duration.ofMinutes(30)),
            ),
            hourly = CachedWeatherSegment(
                hourly,
                fetchedAt,
                fetchedAt.plus(Duration.ofMinutes(30)),
            ),
            daily = CachedWeatherSegment(
                daily,
                fetchedAt,
                fetchedAt.plus(Duration.ofHours(6)),
            ),
            airQuality = CachedWeatherSegment(
                airQuality,
                fetchedAt,
                fetchedAt.plus(Duration.ofMinutes(60)),
            ),
        )
    }
}
