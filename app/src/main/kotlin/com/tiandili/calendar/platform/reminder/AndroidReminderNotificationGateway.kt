package com.tiandili.calendar.platform.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import com.tiandili.calendar.MainActivity
import com.tiandili.calendar.R
import com.tiandili.calendar.domain.reminder.ReminderNotification
import com.tiandili.calendar.domain.reminder.ReminderNotificationGateway
import com.tiandili.calendar.domain.schedule.ScheduleKind
import com.tiandili.calendar.platform.alarm.AlarmSoundService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val REMINDER_CHANNEL_ID = "schedule_reminders"
const val EXTRA_SCHEDULE_ID = "com.tiandili.calendar.extra.SCHEDULE_ID"

class AndroidReminderNotificationGateway(
    context: Context,
    private val zoneId: ZoneId,
    private val showTitleOnLockScreen: () -> Boolean = { false },
) : ReminderNotificationGateway {
    private val applicationContext = context.applicationContext
    private val notificationManager = requireNotNull(
        applicationContext.getSystemService(NotificationManager::class.java),
    )
    private val formatter = DateTimeFormatter
        .ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
        .withZone(zoneId)

    override fun post(notification: ReminderNotification): Boolean {
        if (!canPostNotifications()) {
            return false
        }
        if (notification.kind == ScheduleKind.ALARM) {
            return try {
                applicationContext.startForegroundService(AlarmSoundService.startIntent(
                    applicationContext, notification.jobId, notification.scheduleId, notification.title))
                true
            } catch (_: IllegalStateException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        ensureChannel()
        notificationManager.notify(
            notification.jobId.hashCode(),
            buildNotification(notification),
        )
        return true
    }

    private fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && notificationManager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "日程开始前的本地提醒"
            },
        )
    }

    private fun buildNotification(
        reminder: ReminderNotification,
    ): Notification {
        val occurrenceText = formatter.format(
            Instant.ofEpochMilli(reminder.occurrenceEpochMillis),
        )
        val title = if (reminder.missed) "已错过提醒" else reminder.title
        val text = if (reminder.missed) {
            "${reminder.title} · $occurrenceText"
        } else {
            occurrenceText
        }
        val showTitle = showTitleOnLockScreen()
        val publicNotification = Notification.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (showTitle) title else "天地历提醒")
            .setContentText(if (showTitle) text else "你有一项日程提醒")
            .build()
        return Notification.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(reminder.scheduleId))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(if (showTitle) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setWhen(reminder.occurrenceEpochMillis)
            .setShowWhen(true)
            .build()
    }

    private fun contentIntent(scheduleId: Long): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setData("tiandili://schedule/$scheduleId".toUri())
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            applicationContext,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
