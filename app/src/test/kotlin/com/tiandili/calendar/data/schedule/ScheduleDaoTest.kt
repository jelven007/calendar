package com.tiandili.calendar.data.schedule

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScheduleDaoTest {

    private lateinit var database: TiandiliDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.scheduleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saving a series stores its rule and pending reminder in one transaction`() = runTest {
        val scheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = ScheduleEntity(
                title = "月末结账",
                note = "核对所有账户",
                allDay = false,
                startDateEpochDay = 20_119,
                endDateEpochDay = 20_119,
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = "Asia/Shanghai",
                createdAtEpochMillis = 1_735_689_600_000,
                updatedAtEpochMillis = 1_735_689_600_000,
            ),
            recurrenceRule = RecurrenceRuleEntity(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
            ),
            reminder = ReminderOutboxEntity(
                occurrenceEpochMillis = 1_735_722_000_000,
                triggerAtEpochMillis = 1_735_721_100_000,
                status = ReminderOutboxStatus.PENDING,
                createdAtEpochMillis = 1_735_689_600_000,
            ),
        )

        assertThat(scheduleId).isGreaterThan(0)
        assertThat(dao.getSchedule(scheduleId)?.title).isEqualTo("月末结账")
        assertThat(dao.getRecurrenceRule(scheduleId)?.scheduleId).isEqualTo(scheduleId)
        assertThat(dao.getReminderJobs(scheduleId).single().scheduleId).isEqualTo(scheduleId)
        assertThat(dao.getReminderJobs(scheduleId).single().status)
            .isEqualTo(ReminderOutboxStatus.PENDING)
    }

    @Test
    fun `updating a schedule replaces the stored values for the same id`() = runTest {
        val scheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule(),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val stored = checkNotNull(dao.getSchedule(scheduleId))

        dao.updateSchedule(
            stored.copy(
                title = "月末结账（已调整）",
                updatedAtEpochMillis = 1_735_776_000_000,
            ),
        )

        assertThat(dao.getSchedule(scheduleId)?.title).isEqualTo("月末结账（已调整）")
        assertThat(dao.getSchedule(scheduleId)?.updatedAtEpochMillis)
            .isEqualTo(1_735_776_000_000)
    }

    @Test
    fun `soft delete retains the row but excludes it from active lookup`() = runTest {
        val scheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule(),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )

        dao.softDeleteSchedule(
            scheduleId = scheduleId,
            deletedAtEpochMillis = 1_735_776_000_000,
        )

        assertThat(dao.getSchedule(scheduleId)?.deletedAtEpochMillis)
            .isEqualTo(1_735_776_000_000)
        assertThat(dao.getActiveSchedule(scheduleId)).isNull()
    }

    @Test
    fun `schedule exception cancels one occurrence without deleting its series`() = runTest {
        val scheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule(),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )

        dao.saveException(
            ScheduleExceptionEntity(
                scheduleId = scheduleId,
                occurrenceDateEpochDay = 20_150,
                cancelled = true,
            ),
        )

        assertThat(
            dao.getException(
                scheduleId = scheduleId,
                occurrenceDateEpochDay = 20_150,
            )?.cancelled,
        ).isTrue()
        assertThat(dao.getSchedule(scheduleId)).isNotNull()
    }

    @Test
    fun `date window query includes active recurring candidates only`() = runTest {
        val recurringId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule(),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(
                title = "未来系列",
                startDateEpochDay = 20_250,
                endDateEpochDay = 20_250,
            ),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val deletedId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(
                title = "已删除系列",
                startDateEpochDay = 20_120,
                endDateEpochDay = 20_120,
            ),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        dao.softDeleteSchedule(deletedId, 1_735_776_000_000)

        val candidates = dao.observeScheduleCandidates(
            startEpochDay = 20_150,
            endEpochDayInclusive = 20_180,
        ).first()

        assertThat(candidates.map(ScheduleEntity::id)).containsExactly(recurringId)
    }

    @Test
    fun `reconciliation query returns pending and cancel pending jobs only`() = runTest {
        val pendingScheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(title = "待安排"),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val cancelledScheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(title = "待取消"),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val firedScheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(title = "已触发"),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val cancelledJob = dao.getReminderJobs(cancelledScheduleId).single()
        val firedJob = dao.getReminderJobs(firedScheduleId).single()
        dao.transitionReminderStatus(
            reminderId = cancelledJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.CANCEL_PENDING,
        )
        dao.transitionReminderStatus(
            reminderId = firedJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.FIRED,
        )

        val jobs = dao.getReminderJobsForReconciliation()

        assertThat(jobs.map(ReminderOutboxEntity::scheduleId))
            .containsExactly(pendingScheduleId, cancelledScheduleId)
        assertThat(jobs.map(ReminderOutboxEntity::status))
            .containsExactly(
                ReminderOutboxStatus.PENDING,
                ReminderOutboxStatus.CANCEL_PENDING,
            )
    }

    @Test
    fun `reminder transition only succeeds from the expected status`() = runTest {
        val scheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule(),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val reminderId = dao.getReminderJobs(scheduleId).single().id

        val firstTransition = dao.transitionReminderStatus(
            reminderId = reminderId,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.SCHEDULED_EXACT,
        )
        val duplicateTransition = dao.transitionReminderStatus(
            reminderId = reminderId,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.SCHEDULED_INEXACT,
        )

        assertThat(firstTransition).isEqualTo(1)
        assertThat(duplicateTransition).isEqualTo(0)
        assertThat(dao.getReminderJob(reminderId)?.status)
            .isEqualTo(ReminderOutboxStatus.SCHEDULED_EXACT)
    }

    @Test
    fun `recovery resets recoverable jobs but preserves pending cancellation`() = runTest {
        val recoverableStatuses = listOf(
            ReminderOutboxStatus.SCHEDULED_EXACT,
            ReminderOutboxStatus.SCHEDULED_INEXACT,
            ReminderOutboxStatus.BLOCKED,
            ReminderOutboxStatus.DISPATCHING,
            ReminderOutboxStatus.FAILED,
        )
        val recoverableJobIds = recoverableStatuses.mapIndexed { index, status ->
            val scheduleId = dao.insertScheduleWithRuleAndReminder(
                schedule = sampleSchedule().copy(title = "恢复 $index"),
                recurrenceRule = sampleRule(),
                reminder = sampleReminder(),
            )
            val job = dao.getReminderJobs(scheduleId).single()
            dao.transitionReminderStatus(
                reminderId = job.id,
                expectedStatus = ReminderOutboxStatus.PENDING,
                newStatus = status,
            )
            job.id
        }
        val cancelScheduleId = dao.insertScheduleWithRuleAndReminder(
            schedule = sampleSchedule().copy(title = "待取消"),
            recurrenceRule = sampleRule(),
            reminder = sampleReminder(),
        )
        val cancelJob = dao.getReminderJobs(cancelScheduleId).single()
        dao.transitionReminderStatus(
            reminderId = cancelJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.CANCEL_PENDING,
        )

        dao.prepareReminderJobsForRecovery()

        assertThat(recoverableJobIds.map { dao.getReminderJob(it)?.status })
            .containsExactlyElementsIn(
                List(recoverableStatuses.size) { ReminderOutboxStatus.PENDING },
            )
        assertThat(dao.getReminderJob(cancelJob.id)?.status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
    }

    private fun sampleSchedule() = ScheduleEntity(
        title = "月末结账",
        note = "核对所有账户",
        allDay = false,
        startDateEpochDay = 20_119,
        endDateEpochDay = 20_119,
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 10 * 60,
        zoneId = "Asia/Shanghai",
        createdAtEpochMillis = 1_735_689_600_000,
        updatedAtEpochMillis = 1_735_689_600_000,
    )

    private fun sampleRule() = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 1,
    )

    private fun sampleReminder() = ReminderOutboxEntity(
        occurrenceEpochMillis = 1_735_722_000_000,
        triggerAtEpochMillis = 1_735_721_100_000,
        status = ReminderOutboxStatus.PENDING,
        createdAtEpochMillis = 1_735_689_600_000,
    )
}
