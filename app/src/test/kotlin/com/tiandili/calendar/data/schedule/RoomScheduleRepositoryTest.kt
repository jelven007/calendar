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

    @Test
    fun `time zone recovery replans alarms at device wall time and leaves schedules unchanged`() = runTest {
        val instant = Instant.parse("2025-01-01T00:00:00Z")
        var deviceZone = ZoneId.of("Asia/Shanghai")
        val repo = RoomScheduleRepository(dao, Clock.fixed(instant, deviceZone),
            deviceZoneId = { deviceZone })
        val original = Schedule(title = "七点闹钟",
            timing = ScheduleTiming.Timed(LocalDateTime.of(2025, 1, 2, 7, 0),
                LocalDateTime.of(2025, 1, 2, 7, 0), deviceZone),
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM, reminderMinutesBefore = 0,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))
        val alarmId = repo.save(original)
        val scheduleId = repo.save(original.copy(kind = com.tiandili.calendar.domain.schedule.ScheduleKind.SCHEDULE))
        deviceZone = ZoneId.of("Europe/London")
        repo.replanAlarmTimeZones()
        assertThat((repo.get(alarmId)?.timing as ScheduleTiming.Timed).zoneId).isEqualTo(deviceZone)
        val jobs = dao.getReminderJobs(alarmId)
        assertThat(jobs.map { it.status }).containsExactly(
            ReminderOutboxStatus.CANCEL_PENDING, ReminderOutboxStatus.PENDING)
        assertThat(jobs.last().triggerAtEpochMillis).isEqualTo(
            LocalDateTime.of(2025, 1, 2, 7, 0).atZone(deviceZone).toInstant().toEpochMilli())
        assertThat(repo.get(scheduleId)?.timing).isEqualTo(original.timing)
    }

    @Test
    fun `completed todo retains metadata and reminder preference while cancelling jobs`() = runTest {
        val todo = Schedule(title = "读书",
            timing = ScheduleTiming.AllDay(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2)),
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.TODO,
            checklist = listOf(com.tiandili.calendar.domain.schedule.ChecklistItem("第一章", true)),
            mediaUris = listOf("content://media/images/1"),
            reminderMinutesBefore = 30)
        val id = repository.save(todo)
        repository.save(todo.copy(id = id, completed = true))
        assertThat(repository.get(id)).isEqualTo(todo.copy(id = id, completed = true))
        assertThat(dao.getReminderJobs(id).map { it.status })
            .containsExactly(ReminderOutboxStatus.CANCEL_PENDING)
        val reopened = RoomScheduleRepository(dao, Clock.systemUTC())
        assertThat(reopened.get(id)).isEqualTo(todo.copy(id = id, completed = true))
    }

    @Test
    fun `re enabling the same alarm revives its unique reminder and preserves offset after firing`() = runTest {
        val alarm = Schedule(title = "起床",
            timing = ScheduleTiming.Timed(LocalDateTime.of(2025, 2, 1, 7, 0),
                LocalDateTime.of(2025, 2, 1, 7, 0), ZoneId.of("Asia/Shanghai")),
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM,
            reminderMinutesBefore = 0)
        val id = repository.save(alarm)
        repository.save(alarm.copy(id = id, enabled = false))
        repository.save(alarm.copy(id = id))
        val job = dao.getReminderJobs(id).single()
        assertThat(job.status).isEqualTo(ReminderOutboxStatus.PENDING)
        dao.transitionReminderStatus(job.id, ReminderOutboxStatus.PENDING, ReminderOutboxStatus.FIRED)
        assertThat(repository.get(id)?.reminderMinutesBefore).isEqualTo(0)
    }

    @Test
    fun `saving a historical lunar birthday schedules the first future reminder at nine`() = runTest {
        val birthday = Schedule(title = "春节生日",
            timing = ScheduleTiming.AllDay(LocalDate.of(2025, 1, 29), LocalDate.of(2025, 1, 30)),
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.BIRTHDAY,
            recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY, lunarMonth = 1, lunarDay = 1),
            reminderMinutesBefore = 1440)
        val repo = RoomScheduleRepository(dao, Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Shanghai")))
        val id = repo.save(birthday)
        val job = dao.getReminderJobs(id).single()
        assertThat(job.triggerAtEpochMillis).isEqualTo(
            LocalDate.of(2026, 2, 16).atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant().toEpochMilli())
    }

    @Test
    fun `all record metadata survive closing and opening an on disk database`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "record-roundtrip-test.db"
        context.deleteDatabase(name)
        val expected = Schedule(title = "旅行影集",
            timing = ScheduleTiming.AllDay(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2)),
            kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ARTICLE,
            note = "记".repeat(1500), enabled = false,
            checklist = listOf(com.tiandili.calendar.domain.schedule.ChecklistItem("整理照片", true)),
            mediaUris = listOf("content://media/images/1"),
            recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY, lunarMonth = -6, lunarDay = 30),
            reminderMinutesBefore = 30)
        var fileDb = Room.databaseBuilder(context, TiandiliDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            val repo = RoomScheduleRepository(fileDb.scheduleDao(), Clock.systemUTC())
            val id = repo.save(expected)
            fileDb.close()
            fileDb = Room.databaseBuilder(context, TiandiliDatabase::class.java, name)
                .allowMainThreadQueries().build()
            assertThat(RoomScheduleRepository(fileDb.scheduleDao(), Clock.systemUTC()).get(id))
                .isEqualTo(expected.copy(id = id))
        } finally {
            fileDb.close()
            context.deleteDatabase(name)
        }
    }

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
