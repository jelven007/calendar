package com.tiandili.calendar.data.schedule

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
class RoomScheduleRepositoryTest {

    private lateinit var database: TiandiliDatabase
    private lateinit var dao: ScheduleDao
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.scheduleDao()
        repository = RoomScheduleRepository(
            dao = dao,
            clock = Clock.fixed(
                Instant.parse("2025-01-01T00:00:00Z"),
                ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun `saving a recurring timed schedule persists its rule and pending reminder`() = runTest {
        val schedule = Schedule(
            title = "月末结账",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 1, 31, 9, 0),
                endsAt = LocalDateTime.of(2025, 1, 31, 10, 0),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
            ),
            reminderMinutesBefore = 15,
        )

        val scheduleId = repository.save(schedule)

        assertThat(repository.get(scheduleId)).isEqualTo(
            schedule.copy(id = scheduleId),
        )
        val reminder = dao.getReminderJobs(scheduleId).single()
        assertThat(reminder.status).isEqualTo(ReminderOutboxStatus.PENDING)
        assertThat(reminder.occurrenceEpochMillis - reminder.triggerAtEpochMillis)
            .isEqualTo(15 * 60 * 1000)
    }

    @Test
    fun `all day reminder uses nine in the morning as its local anchor`() = runTest {
        val scheduleId = repository.save(
            Schedule(
                title = "全天安排",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 1),
                    endDateExclusive = LocalDate.of(2025, 2, 2),
                ),
                reminderMinutesBefore = 0,
            ),
        )

        val reminder = dao.getReminderJobs(scheduleId).single()

        assertThat(reminder.occurrenceEpochMillis).isEqualTo(
            LocalDateTime.of(2025, 2, 1, 9, 0)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli(),
        )
    }

    @Test
    fun `saving an existing schedule updates the same row`() = runTest {
        val original = Schedule(
            title = "原日程",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 2, 1),
                endDateExclusive = LocalDate.of(2025, 2, 2),
            ),
        )
        val scheduleId = repository.save(original)

        val returnedId = repository.save(
            original.copy(
                id = scheduleId,
                title = "已修改日程",
            ),
        )

        assertThat(returnedId).isEqualTo(scheduleId)
        assertThat(repository.get(scheduleId)?.title).isEqualTo("已修改日程")
    }

    @Test
    fun `observing schedules returns domain models instead of Room entities`() = runTest {
        repository.save(
            Schedule(
                title = "全天安排",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 1),
                    endDateExclusive = LocalDate.of(2025, 2, 2),
                ),
            ),
        )
        repository.save(
            Schedule(
                title = "定时安排",
                timing = ScheduleTiming.Timed(
                    startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                    endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                    zoneId = ZoneId.of("Asia/Shanghai"),
                ),
            ),
        )

        assertThat(repository.observeAll().first().map(Schedule::title))
            .containsExactly("全天安排", "定时安排")
            .inOrder()
    }

    @Test
    fun `deleting a schedule hides it from repository reads`() = runTest {
        val scheduleId = repository.save(
            Schedule(
                title = "待删除日程",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 1),
                    endDateExclusive = LocalDate.of(2025, 2, 2),
                ),
            ),
        )

        repository.delete(scheduleId)

        assertThat(repository.get(scheduleId)).isNull()
        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(dao.getSchedule(scheduleId)).isNotNull()
    }

    @Test
    fun `deleting a schedule queues its active reminder for cancellation`() = runTest {
        val scheduleId = repository.save(
            Schedule(
                title = "带提醒日程",
                timing = ScheduleTiming.Timed(
                    startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                    endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                    zoneId = ZoneId.of("Asia/Shanghai"),
                ),
                reminderMinutesBefore = 15,
            ),
        )

        repository.delete(scheduleId)

        assertThat(dao.getReminderJobs(scheduleId).single().status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
    }

    @Test
    fun `updating a schedule replaces recurrence and queues its old reminder cancellation`() =
        runTest {
        val original = Schedule(
            title = "重复日程",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY),
            reminderMinutesBefore = 15,
        )
        val scheduleId = repository.save(original)

        repository.save(
            original.copy(
                id = scheduleId,
                recurrence = null,
                reminderMinutesBefore = null,
            ),
        )

        assertThat(repository.get(scheduleId)).isEqualTo(
            original.copy(
                id = scheduleId,
                recurrence = null,
                reminderMinutesBefore = null,
            ),
        )
        assertThat(dao.getRecurrenceRule(scheduleId)).isNull()
        assertThat(dao.getReminderJobs(scheduleId).single().status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
    }

    @Test
    fun `editing a recurring schedule keeps an unchanged reminder without conflict`() = runTest {
        val original = Schedule(
            title = "重复日程",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
            recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY),
            reminderMinutesBefore = 15,
        )
        val scheduleId = repository.save(original)

        repository.save(
            original.copy(
                id = scheduleId,
                title = "重复日程（已改名）",
            ),
        )

        assertThat(repository.get(scheduleId)?.title).isEqualTo("重复日程（已改名）")
        assertThat(repository.get(scheduleId)?.reminderMinutesBefore).isEqualTo(15)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saving and loading an all day schedule preserves its domain values`() = runTest {
        val scheduleId = repository.save(
            Schedule(
                title = "春节假期",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 1, 28),
                    endDateExclusive = LocalDate.of(2025, 2, 5),
                ),
                note = "返乡",
            ),
        )

        assertThat(repository.get(scheduleId)).isEqualTo(
            Schedule(
                id = scheduleId,
                title = "春节假期",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 1, 28),
                    endDateExclusive = LocalDate.of(2025, 2, 5),
                ),
                note = "返乡",
            ),
        )
    }
}
