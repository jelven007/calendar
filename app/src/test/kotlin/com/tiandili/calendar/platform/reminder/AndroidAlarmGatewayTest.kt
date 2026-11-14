package com.tiandili.calendar.platform.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.reminder.AlarmPrecision
import com.tiandili.calendar.domain.reminder.ReminderAlarm
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@Suppress("DEPRECATION")
class AndroidAlarmGatewayTest {

    @Test
    fun `cancelling a reminder also removes its posted notification`() {
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        notifications.createNotificationChannel(android.app.NotificationChannel(
            "test", "Test", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        notifications.notify(42, android.app.Notification.Builder(context, "test")
            .setSmallIcon(com.tiandili.calendar.R.drawable.ic_launcher_foreground).build())

        gateway.cancel(42)

        assertThat(shadowOf(notifications).getNotification(42)).isNull()
    }

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var gateway: AndroidAlarmGateway

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        gateway = AndroidAlarmGateway(
            context = context,
            clock = Clock.fixed(
                Instant.ofEpochMilli(NOW_EPOCH_MILLIS),
                ZoneOffset.UTC,
            ),
        )
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `schedule uses an exact idle alarm when capability is available`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val precision = gateway.schedule(
            ReminderAlarm(
                jobId = 42,
                triggerAtEpochMillis = FUTURE_TRIGGER_EPOCH_MILLIS,
            ),
        )

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val pendingIntent = shadowOf(alarm.operation)
        assertThat(precision).isEqualTo(AlarmPrecision.EXACT)
        assertThat(alarm.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.triggerAtTime).isEqualTo(FUTURE_TRIGGER_EPOCH_MILLIS)
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(alarm.allowWhileIdle).isTrue()
        assertThat(pendingIntent.savedIntent.getLongExtra(EXTRA_REMINDER_JOB_ID, -1))
            .isEqualTo(42)
    }

    @Test
    fun `schedule falls back to a window alarm when exact capability is unavailable`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val precision = gateway.schedule(
            ReminderAlarm(
                jobId = 42,
                triggerAtEpochMillis = FUTURE_TRIGGER_EPOCH_MILLIS,
            ),
        )

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(precision).isEqualTo(AlarmPrecision.INEXACT)
        assertThat(alarm.triggerAtTime).isEqualTo(FUTURE_TRIGGER_EPOCH_MILLIS)
        assertThat(alarm.windowLengthMs).isEqualTo(INEXACT_ALARM_WINDOW_MILLIS)
    }

    @Test
    fun `schedule moves a missed trigger to the current instant`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        gateway.schedule(
            ReminderAlarm(
                jobId = 42,
                triggerAtEpochMillis = NOW_EPOCH_MILLIS - 60_000,
            ),
        )

        assertThat(shadowOf(alarmManager).scheduledAlarms.single().triggerAtTime)
            .isEqualTo(NOW_EPOCH_MILLIS)
    }

    @Test
    fun `cancel removes the deterministic pending intent for a job`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        gateway.schedule(
            ReminderAlarm(
                jobId = 42,
                triggerAtEpochMillis = FUTURE_TRIGGER_EPOCH_MILLIS,
            ),
        )

        gateway.cancel(jobId = 42)

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    private companion object {
        const val NOW_EPOCH_MILLIS = 1_735_689_600_000L
        const val FUTURE_TRIGGER_EPOCH_MILLIS = NOW_EPOCH_MILLIS + 60_000L
    }
}
