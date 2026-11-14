package com.tiandili.calendar.feature.schedule

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeScheduleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeScheduleRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repository emissions update the schedule list state`() {
        val viewModel = ScheduleListViewModel(repository)
        val schedule = Schedule(
            id = 7,
            title = "体检",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 2, 3),
                endDateExclusive = LocalDate.of(2025, 2, 4),
            ),
        )

        repository.emit(listOf(schedule))

        assertThat(viewModel.uiState.value.schedules).containsExactly(schedule)
    }

    @Test
    fun `delete delegates the schedule id to the repository`() {
        val viewModel = ScheduleListViewModel(repository)

        viewModel.deleteSchedule(9)
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.deletedIds).containsExactly(9L)
    }
}

private class FakeScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val deletedIds = mutableListOf<Long>()

    fun emit(value: List<Schedule>) {
        schedules.value = value
    }

    override suspend fun save(schedule: Schedule): Long = schedule.id

    override suspend fun get(scheduleId: Long): Schedule? =
        schedules.value.firstOrNull { it.id == scheduleId }

    override suspend fun delete(scheduleId: Long) {
        deletedIds += scheduleId
    }

    override fun observeAll(): Flow<List<Schedule>> = schedules
}
