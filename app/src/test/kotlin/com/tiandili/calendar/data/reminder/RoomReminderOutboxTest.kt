package com.tiandili.calendar.data.reminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.ReminderOutboxEntity
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.ScheduleDao
import com.tiandili.calendar.data.schedule.ScheduleEntity
import com.tiandili.calendar.domain.reminder.AlarmPrecision
import com.tiandili.calendar.domain.reminder.ReminderAlarm
import com.tiandili.calendar.domain.reminder.ReminderCommand
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomReminderOutboxTest {

    private lateinit var database: TiandiliDatabase
    private lateinit var dao: ScheduleDao
    private lateinit var outbox: RoomReminderOutbox

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.scheduleDao()
        outbox = RoomReminderOutbox(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `pending and cancel pending rows map to coordinator commands`() = runTest {
        val pendingScheduleId = insertReminder("待安排")
        val cancelledScheduleId = insertReminder("待取消")
        val pendingJob = dao.getReminderJobs(pendingScheduleId).single()
        val cancelledJob = dao.getReminderJobs(cancelledScheduleId).single()
        dao.transitionReminderStatus(
            reminderId = cancelledJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.CANCEL_PENDING,
        )

        assertThat(outbox.getCommands()).containsExactly(
            ReminderCommand.Schedule(
                ReminderAlarm(
                    jobId = pendingJob.id,
                    triggerAtEpochMillis = pendingJob.triggerAtEpochMillis,
                ),
            ),
            ReminderCommand.Cancel(jobId = cancelledJob.id),
        ).inOrder()
    }

    @Test
    fun `confirmed commands update rows with compare and set`() = runTest {
        val exactScheduleId = insertReminder("精确提醒")
        val cancelScheduleId = insertReminder("取消提醒")
        val exactJob = dao.getReminderJobs(exactScheduleId).single()
        val cancelJob = dao.getReminderJobs(cancelScheduleId).single()
        dao.transitionReminderStatus(
            reminderId = cancelJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.CANCEL_PENDING,
        )

        val scheduled = outbox.markScheduled(exactJob.id, AlarmPrecision.EXACT)
        val cancelled = outbox.markCancelled(cancelJob.id)

        assertThat(scheduled).isTrue()
        assertThat(cancelled).isTrue()
        assertThat(dao.getReminderJob(exactJob.id)?.status)
            .isEqualTo(ReminderOutboxStatus.SCHEDULED_EXACT)
        assertThat(dao.getReminderJob(cancelJob.id)?.status)
            .isEqualTo(ReminderOutboxStatus.CANCELLED)
    }

    @Test
    fun `gateway failure increments attempt count and keeps command pending`() = runTest {
        val scheduleId = insertReminder("失败重试")
        val job = dao.getReminderJobs(scheduleId).single()

        outbox.recordFailure(job.id, "alarm service unavailable")

        val stored = checkNotNull(dao.getReminderJob(job.id))
        assertThat(stored.status).isEqualTo(ReminderOutboxStatus.PENDING)
        assertThat(stored.attemptCount).isEqualTo(1)
        assertThat(stored.lastError).isEqualTo("alarm service unavailable")
    }

    private suspend fun insertReminder(title: String): Long =
        dao.insertScheduleDetails(
            schedule = ScheduleEntity(
                title = title,
                allDay = false,
                startDateEpochDay = 20_119,
                endDateEpochDay = 20_119,
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = "Asia/Shanghai",
                createdAtEpochMillis = 1_735_689_600_000,
                updatedAtEpochMillis = 1_735_689_600_000,
            ),
            recurrenceRule = null,
            reminder = ReminderOutboxEntity(
                occurrenceEpochMillis = 1_735_722_000_000,
                triggerAtEpochMillis = 1_735_721_100_000,
                status = ReminderOutboxStatus.PENDING,
                createdAtEpochMillis = 1_735_689_600_000,
            ),
        )
}
