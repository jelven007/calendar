package com.tiandili.calendar.platform.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tiandili.calendar.data.weather.WeatherRefreshResult
import kotlinx.coroutines.CancellationException

interface BackgroundTasks {
    suspend fun refreshWeather(): WeatherRefreshResult
    suspend fun rebuildWidgets()
    suspend fun recoverReminders()
}

interface BackgroundTasksOwner { val backgroundTasks: BackgroundTasks }

suspend fun BackgroundTasks.onForeground() {
    recoverReminders()
    try {
        refreshWeather()
    } finally {
        rebuildWidgets()
    }
}

class WeatherRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = completeBackgroundWork {
        val tasks = (applicationContext as BackgroundTasksOwner).backgroundTasks
        val refreshed = tasks.refreshWeather()
        tasks.rebuildWidgets()
        refreshed.failedGroups.isEmpty()
    }
}

class ReminderCompensationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = completeBackgroundWork {
        (applicationContext as BackgroundTasksOwner).backgroundTasks.recoverReminders()
        true
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = completeBackgroundWork {
        (applicationContext as BackgroundTasksOwner).backgroundTasks.rebuildWidgets()
        true
    }
}

private suspend fun CoroutineWorker.completeBackgroundWork(block: suspend () -> Boolean): ListenableWorkerResult {
    val succeeded = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
    return if (succeeded) androidx.work.ListenableWorker.Result.success()
        else if (runAttemptCount < 3) androidx.work.ListenableWorker.Result.retry()
        else androidx.work.ListenableWorker.Result.failure()
}

private typealias ListenableWorkerResult = androidx.work.ListenableWorker.Result
