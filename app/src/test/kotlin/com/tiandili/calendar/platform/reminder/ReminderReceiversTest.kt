package com.tiandili.calendar.platform.reminder

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [31],
    application = FakeReminderApplication::class,
)
class ReminderReceiversTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeReminderApplication.runtime = RecordingReminderRuntime()
    }

    @Test
    fun `alarm receiver dispatches a valid reminder job`() {
        ReminderAlarmReceiver().onReceive(
            context,
            Intent(REMINDER_ALARM_ACTION)
                .putExtra(EXTRA_REMINDER_JOB_ID, 42L),
        )

        assertThat(FakeReminderApplication.runtime.dispatchedJobIds)
            .containsExactly(42L)
    }

    @Test
    fun `alarm receiver ignores an invalid reminder intent`() {
        ReminderAlarmReceiver().onReceive(
            context,
            Intent(REMINDER_ALARM_ACTION),
        )
        ReminderAlarmReceiver().onReceive(
            context,
            Intent("unrelated.action")
                .putExtra(EXTRA_REMINDER_JOB_ID, 42L),
        )

        assertThat(FakeReminderApplication.runtime.dispatchedJobIds).isEmpty()
    }

    @Test
    fun `recovery receiver rebuilds alarms for supported system events`() {
        val receiver = ReminderRecoveryReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        receiver.onReceive(
            context,
            Intent(ALARM_MANAGER_EXACT_PERMISSION_ACTION),
        )

        assertThat(FakeReminderApplication.runtime.rebuildRequests)
            .containsExactly(true, true, true, true)
    }

    @Test
    fun `recovery receiver ignores unrelated broadcasts`() {
        ReminderRecoveryReceiver().onReceive(
            context,
            Intent("unrelated.action"),
        )

        assertThat(FakeReminderApplication.runtime.rebuildRequests).isEmpty()
    }
}

class FakeReminderApplication : Application(), ReminderRuntimeOwner {
    override val reminderRuntime: ReminderRuntime
        get() = runtime

    companion object {
        lateinit var runtime: RecordingReminderRuntime
    }
}

class RecordingReminderRuntime : ReminderRuntime {
    val dispatchedJobIds = mutableListOf<Long>()
    val rebuildRequests = mutableListOf<Boolean>()

    override fun dispatch(
        jobId: Long,
        onComplete: () -> Unit,
    ) {
        dispatchedJobIds += jobId
        onComplete()
    }

    override fun reconcile(
        rebuildScheduled: Boolean,
        onComplete: () -> Unit,
    ) {
        rebuildRequests += rebuildScheduled
        onComplete()
    }
}
