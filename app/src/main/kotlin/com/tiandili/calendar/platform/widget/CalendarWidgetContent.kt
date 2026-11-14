package com.tiandili.calendar.platform.widget

import androidx.compose.runtime.Composable
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.tiandili.calendar.MainActivity
import com.tiandili.calendar.EXTRA_DESTINATION
import com.tiandili.calendar.platform.reminder.EXTRA_SCHEDULE_ID
import com.tiandili.calendar.domain.widget.WidgetSnapshot
import com.tiandili.calendar.domain.widget.WidgetWeatherSummary
import com.tiandili.calendar.domain.weather.CacheFreshness
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Ink = ColorProvider(Color(0xFF213C30))
private val Muted = ColorProvider(Color(0xFF53685D))
private val Accent = ColorProvider(Color(0xFF2F6B58))

@Composable
fun CalendarWidgetContent(
    snapshot: WidgetSnapshot?,
    wide: Boolean,
    showNextSchedule: Boolean,
    now: Instant,
    zoneId: ZoneId,
) {
    val context = LocalContext.current
    val date = snapshot?.date?.let(LocalDate::parse) ?: now.atZone(zoneId).toLocalDate()
    val weekday = snapshot?.weekday ?: "星期${"一二三四五六日"[date.dayOfWeek.value - 1]}"
    val dateAction = GlanceModifier.semantics { testTag = "date" }
        .clickable(actionStartActivity(widgetIntent(context, "calendar")))
    Column(GlanceModifier.fillMaxSize().background(Color(0xFFF5F6EF))
        .cornerRadius(16.dp).padding(8.dp)) {
        if (wide) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(dateAction.width(110.dp)) {
                    Text("${date.monthValue}月 · $weekday", style = TextStyle(color = Muted, fontSize = 12.sp))
                    Text(date.dayOfMonth.toString(),
                        style = TextStyle(color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                    Text(snapshot?.lunar ?: "天地历", style = TextStyle(color = Ink, fontSize = 13.sp), maxLines = 1)
                    snapshot?.occasion?.let {
                        Text(it, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
                    }
                }
                Column(GlanceModifier.defaultWeight()) {
                    snapshot?.weather?.let { WeatherContent(it, true, now, zoneId) }
                        ?: Text("离线日历", style = TextStyle(color = Muted, fontSize = 13.sp))
                }
            }
            val schedule = snapshot?.nextSchedule?.takeIf { showNextSchedule }
            if (schedule != null) {
                Column(GlanceModifier.fillMaxWidth().semantics { testTag = "schedule" }
                    .clickable(actionStartActivity(widgetIntent(context, "schedule", schedule.scheduleId)))) {
                    Text(schedule.title, style = TextStyle(color = Ink, fontSize = 13.sp), maxLines = 1)
                    Text(schedule.timeLabel, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
                }
            } else if (snapshot != null) {
                Text("宜 ${snapshot.suitable}", style = TextStyle(color = Accent, fontSize = 12.sp), maxLines = 1)
                Text("忌 ${snapshot.avoid}", style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
            }
        } else {
            Row(dateAction.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(date.dayOfMonth.toString(), GlanceModifier.padding(end = 10.dp),
                    style = TextStyle(color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                Column {
                    Text("${date.year}年${date.monthValue}月", style = TextStyle(color = Muted, fontSize = 11.sp))
                    Text(weekday, style = TextStyle(color = Ink, fontSize = 12.sp))
                }
            }
            Text(snapshot?.lunar ?: "天地历", style = TextStyle(color = Ink, fontSize = 13.sp), maxLines = 1)
            snapshot?.weather?.let { WeatherContent(it, false, now, zoneId) }
                ?: snapshot?.occasion?.let {
                    Text(it, style = TextStyle(color = Accent, fontSize = 12.sp), maxLines = 2)
                }
        }
        if (snapshot == null) {
            Text("打开应用", GlanceModifier.padding(top = 8.dp)
                .clickable(actionStartActivity(widgetIntent(context, "calendar"))),
                style = TextStyle(color = Accent, fontSize = 13.sp))
        }
    }
}

@Composable
private fun WeatherContent(weather: WidgetWeatherSummary, wide: Boolean, now: Instant, zoneId: ZoneId) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxWidth().semantics { testTag = "weather" }
        .clickable(actionStartActivity(widgetIntent(context, "weather")))) {
        Text("${weather.city} · ${weather.condition} ${weather.temperature}",
            style = TextStyle(color = Ink, fontSize = if (wide) 15.sp else 13.sp), maxLines = 1)
        if (wide) weather.range?.let {
            Text(it, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
        }
        val freshness = WeatherCachePolicy.freshness(WeatherSegmentType.CURRENT,
            Instant.ofEpochMilli(weather.updatedAtEpochMillis), now)
        val suffix = when (freshness) {
            CacheFreshness.FRESH -> ""
            CacheFreshness.STALE -> " · 缓存"
            CacheFreshness.EXPIRED -> " · 已过期"
        }
        val time = Instant.ofEpochMilli(weather.updatedAtEpochMillis).atZone(zoneId)
            .format(DateTimeFormatter.ofPattern("M-d HH:mm"))
        Text("更新于 $time$suffix", GlanceModifier.semantics { testTag = "weather_updated" },
            style = TextStyle(color = Muted, fontSize = 10.sp), maxLines = 1)
    }
    Text("Open-Meteo", GlanceModifier.padding(top = 2.dp, bottom = 2.dp)
        .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, "https://open-meteo.com/".toUri()))),
        style = TextStyle(color = Accent, fontSize = 12.sp), maxLines = 1)
}

private fun widgetIntent(context: Context, destination: String, scheduleId: Long? = null): Intent =
    Intent(context, MainActivity::class.java).putExtra(EXTRA_DESTINATION, destination)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .also { intent -> scheduleId?.let { intent.putExtra(EXTRA_SCHEDULE_ID, it) } }
