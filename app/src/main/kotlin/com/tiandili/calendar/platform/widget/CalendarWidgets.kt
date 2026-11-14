package com.tiandili.calendar.platform.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.tiandili.calendar.TiandiliApplication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class CalendarWidget(private val wide: Boolean) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override val stateDefinition = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TiandiliApplication
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val (snapshot, preferences) = withContext(Dispatchers.IO) {
            app.widgetSnapshotStore.read()?.takeIf {
                it.date == LocalDate.now(zone).toString()
            } to app.settingsRepository.current()
        }
        if (snapshot == null) app.backgroundScheduler.requestWidgetRefresh()
        provideContent {
            CalendarWidgetContent(snapshot, wide, preferences.showNextScheduleInWidget, now, zone)
        }
    }
}

class CompactCalendarWidget : CalendarWidget(wide = false)
class WideCalendarWidget : CalendarWidget(wide = true)

abstract class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        (context.applicationContext as? WidgetRefreshOwner)?.requestWidgetRefresh()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

class CompactCalendarWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactCalendarWidget()
}

class WideCalendarWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideCalendarWidget()
}
