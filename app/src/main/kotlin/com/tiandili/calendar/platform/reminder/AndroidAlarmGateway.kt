package com.tiandili.calendar.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import com.tiandili.calendar.domain.reminder.AlarmGateway
import com.tiandili.calendar.domain.reminder.AlarmPrecision
import com.tiandili.calendar.domain.reminder.ReminderAlarm
import java.time.Clock
import com.tiandili.calendar.platform.alarm.AlarmSoundService

const val EXTRA_REMINDER_JOB_ID = "com.tiandili.calendar.extra.REMINDER_JOB_ID"
const val INEXACT_ALARM_WINDOW_MILLIS = 10 * 60 * 1000L
const val REMINDER_ALARM_ACTION = "com.tiandili.calendar.action.REMINDER_ALARM"

class AndroidAlarmGateway(
    context: Context,
    private val clock: Clock,
) : AlarmGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = requireNotNull(
        applicationContext.getSystemService(AlarmManager::class.java),
    )

    override fun schedule(alarm: ReminderAlarm): AlarmPrecision {
        val triggerAtEpochMillis = maxOf(
            alarm.triggerAtEpochMillis,
            clock.millis(),
        )
        val operation = pendingIntent(alarm.jobId)
        return if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMillis,
                operation,
            )
            AlarmPrecision.EXACT
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMillis,
                INEXACT_ALARM_WINDOW_MILLIS,
                operation,
            )
            AlarmPrecision.INEXACT
        }
    }

    override fun cancel(jobId: Long) {
        if (AlarmSoundService.activeJobId == jobId) {
            applicationContext.startService(AlarmSoundService.stopIntent(applicationContext, jobId))
        }
        alarmManager.cancel(pendingIntent(jobId))
        applicationContext.getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(jobId.hashCode())
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(jobId: Long): PendingIntent {
        val intent = Intent(applicationContext, ReminderAlarmReceiver::class.java)
            .setAction(REMINDER_ALARM_ACTION)
            .setData("tiandili://reminder/$jobId".toUri())
            .putExtra(EXTRA_REMINDER_JOB_ID, jobId)
        return PendingIntent.getBroadcast(
            applicationContext,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
