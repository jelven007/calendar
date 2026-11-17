package com.tiandili.calendar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.*
import com.tiandili.calendar.domain.weather.*
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.feature.schedule.ScheduleEditorScreen
import com.tiandili.calendar.feature.schedule.ScheduleEditorUiState
import com.tiandili.calendar.feature.settings.*
import com.tiandili.calendar.feature.weather.WeatherScreen
import com.tiandili.calendar.feature.weather.WeatherUiState
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.io.File
import java.time.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-18T01:00:00Z")
    private val date = LocalDate.of(2026, 11, 18)
    private val clock = Clock.fixed(now, zone)
    private var editorSaved = false

    @Test fun `calendar preview includes real month labels`() {
        render("calendar")
        rule.onNodeWithTag("weekday_0").assertTextEquals("日")
        rule.onNodeWithText("方案评审", substring = true).assertIsDisplayed()
        save("01-calendar")
    }

    @Test fun `almanac preview contains computed traditional data`() {
        render("almanac")
        rule.onNodeWithText("2026-11-18 ⌄").assertIsDisplayed()
        save("02-almanac")
    }

    @Test fun `weather preview renders current forecast`() {
        render("weather")
        rule.onNodeWithText("北京", substring = true).assertIsDisplayed()
        save("03-weather")
    }

    @Test fun `mine preview renders local record summaries`() {
        render("mine")
        rule.onNodeWithTag("mine_shortcut_TODO").assertIsDisplayed()
        save("04-mine")
    }

    @Test fun `todo editor preview keeps save action visible`() {
        render("schedule")
        rule.onNodeWithTag("category_TODO").assertIsDisplayed()
        assertThat(rule.onNodeWithTag("category_TODO").fetchSemanticsNode().boundsInRoot.width)
            .isAtLeast(48f)
        rule.onNodeWithContentDescription("保存待办").assertIsDisplayed().performClick()
        assertThat(editorSaved).isTrue()
        save("05-todo-editor")
    }

    @Test fun `settings preview shows grouped pages`() {
        render("settings")
        rule.onNodeWithText("日历设置").assertIsDisplayed()
        save("06-settings")
    }

    @Test fun `narrow calendar with enlarged font remains readable`() {
        render("calendar", width = 320, fontScale = 1.3f)
        rule.onNodeWithTag("weekday_0").assertIsDisplayed()
        save("07-calendar-320-font1.3")
    }

    @Test fun `dark mine with enlarged font preserves shortcuts`() {
        render("mine", width = 320, fontScale = 1.3f, dark = true)
        rule.onNodeWithTag("mine_shortcut_TODO").assertIsDisplayed()
        save("08-mine-dark-320-font1.3")
    }

    @Test fun `forecast can be swiped to and tapped on its last day`() {
        render("weather")
        repeat(2) { rule.onNodeWithTag("weather_content").performTouchInput {
            swipeUp(startY = height * .8f, endY = height * .4f, durationMillis = 600)
        } }
        val scroll = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
            hasAnyDescendant(hasContentDescription("选择预报 2026-11-18")))
        // Real touch gestures are dispatched inside the chart viewport.
        repeat(5) { scroll.performTouchInput { swipeLeft() } }
        rule.onNodeWithContentDescription("选择预报 2026-12-02").assertIsDisplayed()
            .performTouchInput { click() }
        rule.onNodeWithText("12月2日", substring = true).assertIsDisplayed()
        save("09-weather-last-day")
    }

    private fun render(route: String, width: Int = 393, fontScale: Float = 1f, dark: Boolean = false) {
        val vm = CalendarViewModel(clock, zone, DayOfWeek.SUNDAY, LunarJavaAlmanacEngine())
        val snapshot = weather()
        val records = listOf(
            Schedule(1, "方案评审", ScheduleTiming.Timed(date.atTime(14, 0), date.atTime(15, 0), zone)),
            Schedule(2, "小安生日", ScheduleTiming.AllDay(date.plusDays(3), date.plusDays(4)),
                kind = ScheduleKind.BIRTHDAY, recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY)),
            Schedule(3, "一起走过的日子", ScheduleTiming.AllDay(date.plusDays(9), date.plusDays(10)),
                kind = ScheduleKind.ANNIVERSARY, recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY)),
            Schedule(4, "秋日随记", ScheduleTiming.AllDay(date, date.plusDays(1)),
                note = "把今天的好心情记下来。", kind = ScheduleKind.NOTE),
            Schedule(5, "早起读书", ScheduleTiming.Timed(date.plusDays(1).atTime(7, 0),
                date.plusDays(1).atTime(7, 1), zone), kind = ScheduleKind.ALARM,
                recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)),
            Schedule(6, "今天的小目标", ScheduleTiming.AllDay(date, date.plusDays(1)),
                kind = ScheduleKind.TODO, checklist = listOf(ChecklistItem("读完一章", true), ChecklistItem("散步半小时"))),
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TiandiliTheme(darkTheme = dark) {
                    Surface(Modifier.requiredSize(width.dp, 800.dp).testTag("preview_frame")) {
                        TiandiliApp(vm, schedules = records, weatherSnapshot = snapshot, clock = clock,
                            scheduleEditorVisible = route == "schedule",
                            navigationRequest = AppNavigationRequest(route, sequence = 1),
                            weatherContent = { WeatherScreen(WeatherUiState(snapshot = snapshot), now) },
                            scheduleContent = {
                                var editor by remember { mutableStateOf(ScheduleEditorUiState(
                                    title = "今天的小目标", allDay = true, startDate = date, endDate = date,
                                    kind = ScheduleKind.TODO, checklist = records.last().checklist,
                                    note = "让每一天都留下一点进步。")) }
                                ScheduleEditorScreen(editor, {}, { editor = editor.copy(title = it) },
                                    {}, {}, {}, {}, {}, {}, {}, { editor = editor.copy(note = it) },
                                    { editorSaved = true })
                            },
                            settingsContent = { _, close ->
                                SettingsScreen(UserPreferences(), SettingsUiState(), "北京",
                                    ReminderPermissionState(true, true), {}, {}, {}, {}, {},
                                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onBack = close)
                            })
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val frame = rule.onNodeWithTag("preview_frame")
        val bounds = frame.fetchSemanticsNode().boundsInRoot
        rule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: ""
            if (node.boundsInRoot.top >= bounds.top && node.boundsInRoot.bottom <= bounds.bottom &&
                node.boundsInRoot.left >= bounds.left && node.boundsInRoot.right <= bounds.right) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.forEach { layout ->
                    val explicitEllipsis = (0 until layout.lineCount).any(layout::isLineEllipsized)
                    assertWithMessage("$name text height: $text")
                        .that(layout.didOverflowHeight && !explicitEllipsis).isFalse()
                }
            }
        }
        // PixelCopy requires a real display; native Robolectric renders the same View tree to Canvas.
        val image = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
        rule.runOnIdle {
            val canvas = Canvas(image)
            canvas.translate(-bounds.left, -bounds.top)
            rule.activity.findViewById<View>(android.R.id.content).draw(canvas)
        }
        val colors = HashSet<Int>()
        for (y in 0 until image.height step 10) for (x in 0 until image.width step 10)
            colors += image.getPixel(x, y)
        assertWithMessage("$name native capture must contain rendered content").that(colors.size).isGreaterThan(15)
        val output = File("build/outputs/ui-previews").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun weather(): WeatherSnapshot {
        fun <T> cache(value: T) = CachedWeatherSegment(value, now, now.plusSeconds(3600))
        val city = City("beijing", "北京", "北京", "中国", 39.9, 116.4, zone)
        val current = CurrentWeather(now.atZone(zone), 8.0, 6.0, 45, 0.0, 1, 12.0, true, 335.0)
        val hours = (0..23).map { index ->
            HourlyForecast(now.atZone(zone).plusHours(index.toLong()), 8.0 + 4 * kotlin.math.sin(index / 5.0),
                10, 1, 12.0)
        }
        val days = (0..14).map { index -> DailyForecast(date.plusDays(index.toLong()), if (index % 3 == 0) 3 else 1,
            2.0 + index % 4, 10.0 + index % 5, 10, null, null) }
        val air = AirQuality(now.atZone(zone), 48, 12.0, 24.0, 30.0, 11.0,
            (0..119).map { HourlyAirQuality(date.atStartOfDay(zone).plusHours(it.toLong()), 48 + it % 8) })
        return WeatherSnapshot(city, cache(current), cache(hours), cache(days), cache(air))
    }
}
