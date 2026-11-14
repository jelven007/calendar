package com.tiandili.calendar.platform.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.testing.unit.assertHasText
import com.tiandili.calendar.MainActivity
import com.tiandili.calendar.EXTRA_DESTINATION
import com.tiandili.calendar.platform.reminder.EXTRA_SCHEDULE_ID
import com.tiandili.calendar.domain.widget.*
import com.tiandili.calendar.domain.weather.CacheFreshness
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarWidgetContentTest {
    private val now = Instant.parse("2025-02-02T01:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private val snapshot = WidgetSnapshot(now.toEpochMilli(), "2025-02-02", "星期日",
        "正月初五", null, "出行", "动土",
        weather = WidgetWeatherSummary("北京", "晴", "6°", "2° / 9°",
            now.minusSeconds(4 * 3600).toEpochMilli(), CacheFreshness.STALE),
        nextSchedule = WidgetScheduleSummary(7, "私人日程", now.toEpochMilli(), "今天 10:00"))

    @Test fun `compact widget has separate calendar weather and attribution actions`() =
        runGlanceAppWidgetUnitTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            setContext(context)
            provideComposable { CalendarWidgetContent(snapshot, false, false, now, zone) }
            onNode(hasTestTag("date")).assertHasStartActivityClickAction(
                Intent(context, MainActivity::class.java).putExtra(EXTRA_DESTINATION, "calendar")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            onNode(hasTestTag("weather")).assertHasStartActivityClickAction(
                Intent(context, MainActivity::class.java).putExtra(EXTRA_DESTINATION, "weather")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            onNode(hasText("Open-Meteo")).assertHasStartActivityClickAction(
                Intent(Intent.ACTION_VIEW, "https://open-meteo.com/".toUri()))
            onNode(hasText("私人日程")).assertDoesNotExist()
            onNode(hasTestTag("weather_updated")).assertHasText("更新于")
        }

    @Test fun `large widget still masks a previously visible cached title`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext<Context>())
            provideComposable { CalendarWidgetContent(snapshot, true, false, now, zone) }
            onNode(hasText("私人日程")).assertDoesNotExist()
            onNode(hasText("宜 出行")).assertExists()
            onNode(hasText("2° / 9°")).assertExists()
        }

    @Test fun `explicitly enabled large widget links to the actual schedule`() =
        runGlanceAppWidgetUnitTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            setContext(context)
            provideComposable { CalendarWidgetContent(snapshot, true, true, now, zone) }
            onNode(hasTestTag("schedule")).assertHasStartActivityClickAction(
                Intent(context, MainActivity::class.java).putExtra(EXTRA_DESTINATION, "schedule")
                    .putExtra(EXTRA_SCHEDULE_ID, 7L)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            onNode(hasText("私人日程")).assertExists()
        }

    @Test fun `offline widget hides source and retains lunar date`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext<Context>())
            provideComposable { CalendarWidgetContent(snapshot.copy(weather = null), false, false, now, zone) }
            onNode(hasText("正月初五")).assertExists()
            onNode(hasText("Open-Meteo")).assertDoesNotExist()
        }

    @Test fun `missing snapshot renders real date and an open app action`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext<Context>())
            provideComposable { CalendarWidgetContent(null, false, false, now, zone) }
            onNode(hasTextEqualTo("2")).assertExists()
            onNode(hasText("打开应用")).assertExists()
        }
}
