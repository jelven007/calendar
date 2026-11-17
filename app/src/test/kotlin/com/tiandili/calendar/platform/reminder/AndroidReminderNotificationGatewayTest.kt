package com.tiandili.calendar.platform.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.reminder.ReminderNotification
import java.time.ZoneOffset
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidReminderNotificationGatewayTest {

    @Test
    fun `alarm notification starts the ringing foreground service`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(gateway.post(sampleNotification().copy(
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM))).isTrue()
        val started = shadowOf(application).nextStartedService
        assertThat(started?.component?.className)
            .isEqualTo("com.tiandili.calendar.platform.alarm.AlarmSoundService")
        assertThat(started?.getLongExtra(EXTRA_REMINDER_JOB_ID, 0)).isEqualTo(JOB_ID)
    }

    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager
    private lateinit var gateway: AndroidReminderNotificationGateway

    @Test
    fun `explicit lock screen preference exposes the reminder title`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val publicGateway = AndroidReminderNotificationGateway(application, ZoneOffset.UTC,
            showTitleOnLockScreen = { true })

        publicGateway.post(sampleNotification())

        val notification = checkNotNull(shadowOf(notificationManager).getNotification(JOB_ID.hashCode()))
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("项目回顾")
    }

    @Test
    fun `default notification hides the title on the lock screen`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        gateway.post(sampleNotification())

        val notification = checkNotNull(shadowOf(notificationManager).getNotification(JOB_ID.hashCode()))
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(notification.publicVersion.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo("你有一项日程提醒")
    }

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        notificationManager = application.getSystemService(NotificationManager::class.java)
        gateway = AndroidReminderNotificationGateway(
            context = application,
            zoneId = ZoneOffset.UTC,
        )
    }

    @After
    fun tearDown() {
        ShadowNotificationManager.reset()
    }

    @Test
    fun `post returns blocked without notification permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val posted = gateway.post(sampleNotification())

        assertThat(posted).isFalse()
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `post creates the reminder channel and a deterministic notification`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val posted = gateway.post(sampleNotification())

        val channel = notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID)
        val notification = checkNotNull(
            shadowOf(notificationManager).getNotification(JOB_ID.hashCode()),
        )
        assertThat(posted).isTrue()
        assertThat(channel.name.toString()).isEqualTo("日程提醒")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("项目回顾")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
            .contains("1月1日")
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
    }

    @Test
    fun `missed reminder uses recovery wording`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        gateway.post(sampleNotification().copy(missed = true))

        val notification = checkNotNull(
            shadowOf(notificationManager).getNotification(JOB_ID.hashCode()),
        )
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("已错过提醒")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
            .contains("项目回顾")
    }

    private fun sampleNotification() = ReminderNotification(
        jobId = JOB_ID,
        scheduleId = 3,
        title = "项目回顾",
        occurrenceEpochMillis = 1_735_689_600_000,
        missed = false,
    )

    private companion object {
        const val JOB_ID = 7L
    }
}
