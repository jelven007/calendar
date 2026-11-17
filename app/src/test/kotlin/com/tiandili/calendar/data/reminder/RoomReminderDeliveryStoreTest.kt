package com.tiandili.calendar.data.reminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.ReminderOutboxEntity
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.RecurrenceRuleEntity
import com.tiandili.calendar.data.schedule.ScheduleDao
import com.tiandili.calendar.data.schedule.ScheduleEntity
import com.tiandili.calendar.domain.reminder.ReminderDelivery
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomReminderDeliveryStoreTest {

    @Test
    fun `ringing authorization follows claim firing and cancellation state`() = runTest {
        val id = insertScheduledReminder()
        val entity = checkNotNull(dao.getSchedule(id))
        dao.updateSchedule(entity.copy(kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM))
        val jobId = dao.getReminderJobs(id).single().id
        assertThat(dao.observeRingingJobActive(jobId).first()).isFalse()
        checkNotNull(store.claim(jobId))
        assertThat(dao.observeRingingJobActive(jobId).first()).isTrue()
        store.markFired(jobId)
        assertThat(dao.observeRingingJobActive(jobId).first()).isTrue()
        dao.softDeleteScheduleDetails(id, clock.millis())
        assertThat(dao.observeRingingJobActive(jobId).first()).isFalse()
    }

    @Test
    fun `disabled or completed records cannot claim old scheduled broadcasts`() = runTest {
        for (completed in listOf(false, true)) {
            val id = insertScheduledReminder()
            val entity = checkNotNull(dao.getSchedule(id))
            dao.updateSchedule(entity.copy(enabled = completed, completed = completed))
            assertThat(store.claim(dao.getReminderJobs(id).single().id)).isNull()
        }
    }

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(
        Instant.parse("2025-01-01T00:00:00Z"),
        zoneId,
    )
    private lateinit var database: TiandiliDatabase
    private lateinit var dao: ScheduleDao
    private lateinit var store: RoomReminderDeliveryStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.scheduleDao()
        store = RoomReminderDeliveryStore(dao, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `claim atomically returns one scheduled reminder for an active schedule`() = runTest {
        val scheduleId = insertScheduledReminder()
        val job = dao.getReminderJobs(scheduleId).single()

        val firstClaim = store.claim(job.id)
        val duplicateClaim = store.claim(job.id)

        assertThat(firstClaim).isEqualTo(
            ReminderDelivery(
                jobId = job.id,
                scheduleId = scheduleId,
                title = "项目回顾",
                occurrenceEpochMillis = job.occurrenceEpochMillis,
                triggerAtEpochMillis = job.triggerAtEpochMillis,
            ),
        )
        assertThat(duplicateClaim).isNull()
        assertThat(dao.getReminderJob(job.id)?.status)
            .isEqualTo(ReminderOutboxStatus.DISPATCHING)
    }

    @Test
    fun `claim rejects a reminder whose schedule was deleted`() = runTest {
        val scheduleId = insertScheduledReminder()
        val job = dao.getReminderJobs(scheduleId).single()
        dao.softDeleteSchedule(
            scheduleId = scheduleId,
            deletedAtEpochMillis = 1_735_700_000_000,
        )

        assertThat(store.claim(job.id)).isNull()
    }

    @Test
    fun `delivery outcomes only transition a claimed reminder`() = runTest {
        val firedJob = claimNewReminder()
        val blockedJob = claimNewReminder()
        val skippedJob = claimNewReminder()

        store.markFired(firedJob)
        store.markBlocked(blockedJob)
        store.markSkipped(skippedJob)

        assertThat(dao.getReminderJob(firedJob)?.status)
            .isEqualTo(ReminderOutboxStatus.FIRED)
        assertThat(dao.getReminderJob(blockedJob)?.status)
            .isEqualTo(ReminderOutboxStatus.BLOCKED)
        assertThat(dao.getReminderJob(skippedJob)?.status)
            .isEqualTo(ReminderOutboxStatus.SKIPPED)
    }

    @Test
    fun `delivery failure releases a claimed reminder for retry`() = runTest {
        val jobId = claimNewReminder()

        store.releaseForRetry(jobId, "notification service unavailable")

        val stored = checkNotNull(dao.getReminderJob(jobId))
        assertThat(stored.status).isEqualTo(ReminderOutboxStatus.PENDING)
        assertThat(stored.attemptCount).isEqualTo(1)
        assertThat(stored.lastError).isEqualTo("notification service unavailable")
    }

    @Test
    fun `firing a recurring reminder queues its next occurrence`() = runTest {
        val currentOccurrence = LocalDateTime.of(2025, 1, 1, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val scheduleId = dao.insertScheduleDetails(
            schedule = ScheduleEntity(
                title = "晨会",
                allDay = false,
                startDateEpochDay = java.time.LocalDate.of(2025, 1, 1).toEpochDay(),
                endDateEpochDay = java.time.LocalDate.of(2025, 1, 1).toEpochDay(),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = zoneId.id,
                createdAtEpochMillis = clock.millis(),
                updatedAtEpochMillis = clock.millis(),
            ),
            recurrenceRule = RecurrenceRuleEntity(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
            ),
            reminder = ReminderOutboxEntity(
                occurrenceEpochMillis = currentOccurrence,
                triggerAtEpochMillis = currentOccurrence - 15 * 60 * 1000L,
                status = ReminderOutboxStatus.PENDING,
                createdAtEpochMillis = clock.millis(),
            ),
        )
        val currentJob = dao.getReminderJobs(scheduleId).single()
        dao.transitionReminderStatus(
            reminderId = currentJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.SCHEDULED_EXACT,
        )
        checkNotNull(store.claim(currentJob.id))

        store.markFired(currentJob.id)

        val jobs = dao.getReminderJobs(scheduleId)
        val nextOccurrence = LocalDateTime.of(2025, 1, 2, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        assertThat(jobs.map(ReminderOutboxEntity::status))
            .containsExactly(
                ReminderOutboxStatus.FIRED,
                ReminderOutboxStatus.PENDING,
            )
            .inOrder()
        assertThat(jobs.last().occurrenceEpochMillis).isEqualTo(nextOccurrence)
        assertThat(jobs.last().triggerAtEpochMillis)
            .isEqualTo(nextOccurrence - 15 * 60 * 1000L)
    }

    @Test
    fun `skipping a stale recurring reminder jumps to the next future occurrence`() = runTest {
        val recoveryClock = Clock.fixed(
            Instant.parse("2025-01-10T02:00:00Z"),
            zoneId,
        )
        store = RoomReminderDeliveryStore(dao, recoveryClock)
        val staleOccurrence = LocalDateTime.of(2025, 1, 1, 9, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val scheduleId = dao.insertScheduleDetails(
            schedule = ScheduleEntity(
                title = "晨会",
                allDay = false,
                startDateEpochDay = java.time.LocalDate.of(2025, 1, 1).toEpochDay(),
                endDateEpochDay = java.time.LocalDate.of(2025, 1, 1).toEpochDay(),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = zoneId.id,
                createdAtEpochMillis = clock.millis(),
                updatedAtEpochMillis = clock.millis(),
            ),
            recurrenceRule = RecurrenceRuleEntity(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
            ),
            reminder = ReminderOutboxEntity(
                occurrenceEpochMillis = staleOccurrence,
                triggerAtEpochMillis = staleOccurrence,
                status = ReminderOutboxStatus.PENDING,
                createdAtEpochMillis = clock.millis(),
            ),
        )
        val staleJob = dao.getReminderJobs(scheduleId).single()
        dao.transitionReminderStatus(
            reminderId = staleJob.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.SCHEDULED_EXACT,
        )
        checkNotNull(store.claim(staleJob.id))

        store.markSkipped(staleJob.id)

        val nextJob = dao.getReminderJobs(scheduleId).last()
        assertThat(nextJob.occurrenceEpochMillis).isEqualTo(
            LocalDateTime.of(2025, 1, 11, 9, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
        )
    }

    private suspend fun claimNewReminder(): Long {
        val scheduleId = insertScheduledReminder()
        val jobId = dao.getReminderJobs(scheduleId).single().id
        checkNotNull(store.claim(jobId))
        return jobId
    }

    private suspend fun insertScheduledReminder(): Long {
        val scheduleId = dao.insertScheduleDetails(
            schedule = ScheduleEntity(
                title = "项目回顾",
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
        val job = dao.getReminderJobs(scheduleId).single()
        dao.transitionReminderStatus(
            reminderId = job.id,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = ReminderOutboxStatus.SCHEDULED_EXACT,
        )
        return scheduleId
    }
}
