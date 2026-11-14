package com.tiandili.calendar

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.reminder.ReminderSchedulingScheduleRepository
import com.tiandili.calendar.platform.reminder.ReminderAlarmReceiver
import com.tiandili.calendar.platform.reminder.ReminderRecoveryReceiver
import com.tiandili.calendar.platform.reminder.ReminderRuntimeOwner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    application = TiandiliApplication::class,
)
class TiandiliApplicationReminderTest {

    @Test
    fun `application exposes the reminder runtime and coordinating repository`() {
        val application =
            ApplicationProvider.getApplicationContext<TiandiliApplication>()

        assertThat(application).isInstanceOf(ReminderRuntimeOwner::class.java)
        assertThat(application.reminderRuntime).isNotNull()
        assertThat(application.scheduleRepository)
            .isInstanceOf(ReminderSchedulingScheduleRepository::class.java)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `manifest declares reminder permissions and receivers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertThat(packageInfo.requestedPermissions?.toList()).containsAtLeast(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SCHEDULE_EXACT_ALARM,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
        )
        assertThat(
            packageManager.getReceiverInfo(
                ComponentName(context, ReminderAlarmReceiver::class.java),
                0,
            ),
        ).isNotNull()
        assertThat(
            packageManager.getReceiverInfo(
                ComponentName(context, ReminderRecoveryReceiver::class.java),
                0,
            ),
        ).isNotNull()
    }
}
