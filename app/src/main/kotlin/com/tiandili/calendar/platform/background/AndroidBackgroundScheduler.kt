package com.tiandili.calendar.platform.background

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.util.concurrent.TimeUnit

object BackgroundWorkRequests {
    fun weather(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(3, TimeUnit.HOURS)
        .setInitialDelay(3, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()

    fun reminder(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<ReminderCompensationWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(24, TimeUnit.HOURS).build()

    fun widgetFallback(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS)
        .setInitialDelay(1, TimeUnit.HOURS).build()

    fun midnight(now: Instant, zoneId: ZoneId): OneTimeWorkRequest {
        val midnight = now.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        return OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(Duration.between(now, midnight).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(MIDNIGHT_WORK_TAG).build()
    }
}

const val MIDNIGHT_WORK_TAG = "widget_midnight"

class AndroidBackgroundScheduler(context: Context) {
    private val manager = WorkManager.getInstance(context.applicationContext)

    fun ensureScheduled() {
        manager.enqueueUniquePeriodicWork("weather_refresh", ExistingPeriodicWorkPolicy.KEEP,
            BackgroundWorkRequests.weather())
        manager.enqueueUniquePeriodicWork("reminder_compensation", ExistingPeriodicWorkPolicy.KEEP,
            BackgroundWorkRequests.reminder())
        manager.enqueueUniquePeriodicWork("widget_fallback", ExistingPeriodicWorkPolicy.KEEP,
            BackgroundWorkRequests.widgetFallback())
        scheduleNextMidnight()
    }

    fun requestWidgetRefresh() {
        manager.enqueueUniqueWork("widget_refresh", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }

    fun scheduleNextMidnight() {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val target = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        manager.enqueueUniqueWork("$MIDNIGHT_WORK_TAG:${target.toEpochMilli()}", ExistingWorkPolicy.KEEP,
            BackgroundWorkRequests.midnight(now, zone))
    }

    fun resetMidnight() {
        manager.cancelAllWorkByTag(MIDNIGHT_WORK_TAG)
        scheduleNextMidnight()
    }
}
