package com.tiandili.calendar.data.reminder

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.platform.reminder.ReminderRuntime
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderSchedulingScheduleRepositoryTest {

    @Test
    fun `successful save requests reminder reconciliation after persistence`() = runTest {
        val delegate = RecordingRepository()
        val runtime = RecordingRuntime()
        val repository = ReminderSchedulingScheduleRepository(delegate, runtime)
        val schedule = sampleSchedule()

        val id = repository.save(schedule)

        assertThat(id).isEqualTo(9)
        assertThat(delegate.events).containsExactly("save")
        assertThat(runtime.events).containsExactly("reconcile:false")
    }

    @Test
    fun `delete requests reminder reconciliation after soft deletion`() = runTest {
        val delegate = RecordingRepository()
        val runtime = RecordingRuntime()
        val repository = ReminderSchedulingScheduleRepository(delegate, runtime)

        repository.delete(9)

        assertThat(delegate.events).containsExactly("delete:9")
        assertThat(runtime.events).containsExactly("reconcile:false")
    }

    private fun sampleSchedule() = Schedule(
        title = "项目回顾",
        timing = ScheduleTiming.AllDay(
            startDate = LocalDate.of(2025, 1, 1),
            endDateExclusive = LocalDate.of(2025, 1, 2),
        ),
    )
}

private class RecordingRepository : ScheduleRepository {
    val events = mutableListOf<String>()
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())

    override suspend fun save(schedule: Schedule): Long {
        events += "save"
        return 9
    }

    override suspend fun get(scheduleId: Long): Schedule? = null

    override suspend fun delete(scheduleId: Long) {
        events += "delete:$scheduleId"
    }

    override fun observeAll(): Flow<List<Schedule>> = schedules
}

private class RecordingRuntime : ReminderRuntime {
    val events = mutableListOf<String>()

    override fun dispatch(
        jobId: Long,
        onComplete: () -> Unit,
    ) = onComplete()

    override fun reconcile(
        rebuildScheduled: Boolean,
        onComplete: () -> Unit,
    ) {
        events += "reconcile:$rebuildScheduled"
        onComplete()
    }
}
