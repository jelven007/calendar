package com.tiandili.calendar.feature.schedule

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.schedule.ScheduleValidationError
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
class ScheduleEditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId)
    private lateinit var repository: RecordingScheduleRepository

    @Test
    fun `new schedules use the current default reminder`() {
        var defaultMinutes: Int? = 15
        val viewModel = ScheduleEditorViewModel(repository, clock, zoneId,
            defaultReminderMinutes = { defaultMinutes })
        assertThat(viewModel.uiState.value.reminderMinutesBefore).isEqualTo(15)

        defaultMinutes = 30
        viewModel.startNew()

        assertThat(viewModel.uiState.value.reminderMinutesBefore).isEqualTo(30)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = RecordingScheduleRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving a blank title exposes the required title error without persisting`() {
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )

        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.errors)
            .containsExactly(ScheduleValidationError.TITLE_REQUIRED)
        assertThat(repository.savedSchedules).isEmpty()
    }

    @Test
    fun `valid title saves the default timed schedule and exposes its id`() {
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )

        viewModel.onTitleChanged("看牙")
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.savedSchedules).containsExactly(
            Schedule(
                title = "看牙",
                timing = ScheduleTiming.Timed(
                    startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                    endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                    zoneId = zoneId,
                ),
            ),
        )
        assertThat(viewModel.uiState.value.savedScheduleId).isEqualTo(11)
    }

    @Test
    fun `configured all day schedule saves dates recurrence reminder and note`() {
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )

        viewModel.onTitleChanged("出差")
        viewModel.onAllDayChanged(true)
        viewModel.onStartDateChanged(LocalDate.of(2025, 2, 10))
        viewModel.onEndDateChanged(LocalDate.of(2025, 2, 12))
        viewModel.onRecurrenceChanged(RecurrenceFrequency.WEEKLY)
        viewModel.onReminderMinutesChanged(30)
        viewModel.onNoteChanged("携带证件")
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.savedSchedules.single()).isEqualTo(
            Schedule(
                title = "出差",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 10),
                    endDateExclusive = LocalDate.of(2025, 2, 13),
                ),
                note = "携带证件",
                recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
                reminderMinutesBefore = 30,
            ),
        )
    }

    @Test
    fun `configured timed schedule saves selected start and end times`() {
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )

        viewModel.onTitleChanged("客户会议")
        viewModel.onStartTimeChanged(LocalTime.of(14, 30))
        viewModel.onEndTimeChanged(LocalTime.of(15, 45))
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.savedSchedules.single().timing).isEqualTo(
            ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 2, 2, 14, 30),
                endsAt = LocalDateTime.of(2025, 2, 2, 15, 45),
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun `loading then saving a schedule preserves its id for editing`() {
        val existing = Schedule(
            id = 5,
            title = "原标题",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 3, 1),
                endDateExclusive = LocalDate.of(2025, 3, 3),
            ),
            note = "原备注",
            recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY),
            reminderMinutesBefore = 60,
        )
        repository.schedulesById[5] = existing
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )

        viewModel.load(5)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.title).isEqualTo("原标题")
        assertThat(viewModel.uiState.value.endDate).isEqualTo(LocalDate.of(2025, 3, 2))

        viewModel.onTitleChanged("新标题")
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repository.savedSchedules.single().id).isEqualTo(5)
        assertThat(repository.savedSchedules.single().title).isEqualTo("新标题")
    }

    @Test
    fun `starting a new schedule clears previously loaded values`() {
        repository.schedulesById[5] = Schedule(
            id = 5,
            title = "旧日程",
            timing = ScheduleTiming.AllDay(
                startDate = LocalDate.of(2025, 3, 1),
                endDateExclusive = LocalDate.of(2025, 3, 2),
            ),
            note = "旧备注",
        )
        val viewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = clock,
            zoneId = zoneId,
        )
        viewModel.load(5)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startNew()

        assertThat(viewModel.uiState.value.id).isEqualTo(0)
        assertThat(viewModel.uiState.value.title).isEmpty()
        assertThat(viewModel.uiState.value.startDate).isEqualTo(LocalDate.of(2025, 2, 2))
        assertThat(viewModel.uiState.value.note).isEmpty()
    }
}

private class RecordingScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val savedSchedules = mutableListOf<Schedule>()
    val schedulesById = mutableMapOf<Long, Schedule>()

    override suspend fun save(schedule: Schedule): Long {
        savedSchedules += schedule
        return 11
    }

    override suspend fun get(scheduleId: Long): Schedule? = schedulesById[scheduleId]

    override suspend fun delete(scheduleId: Long) = Unit

    override fun observeAll(): Flow<List<Schedule>> = schedules
}
