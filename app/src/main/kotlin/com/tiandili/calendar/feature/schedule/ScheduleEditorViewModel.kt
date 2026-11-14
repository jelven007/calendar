package com.tiandili.calendar.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.schedule.ScheduleValidationError
import com.tiandili.calendar.domain.schedule.ScheduleValidator
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleEditorUiState(
    val id: Long = 0,
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate = LocalDate.of(1970, 1, 1),
    val endDate: LocalDate = LocalDate.of(1970, 1, 1),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val reminderMinutesBefore: Int? = null,
    val note: String = "",
    val errors: List<ScheduleValidationError> = emptyList(),
    val savedScheduleId: Long? = null,
)

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val defaultReminderMinutes: () -> Int? = { null },
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onAllDayChanged(allDay: Boolean) {
        _uiState.update { it.copy(allDay = allDay) }
    }

    fun onStartDateChanged(startDate: LocalDate) {
        _uiState.update { it.copy(startDate = startDate) }
    }

    fun onEndDateChanged(endDate: LocalDate) {
        _uiState.update { it.copy(endDate = endDate) }
    }

    fun onStartTimeChanged(startTime: LocalTime) {
        _uiState.update { it.copy(startTime = startTime) }
    }

    fun onEndTimeChanged(endTime: LocalTime) {
        _uiState.update { it.copy(endTime = endTime) }
    }

    fun onRecurrenceChanged(frequency: RecurrenceFrequency?) {
        _uiState.update { it.copy(recurrenceFrequency = frequency) }
    }

    fun onReminderMinutesChanged(minutesBefore: Int?) {
        _uiState.update { it.copy(reminderMinutesBefore = minutesBefore) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun startNew() {
        _uiState.value = initialState()
    }

    fun load(scheduleId: Long) {
        viewModelScope.launch {
            val schedule = repository.get(scheduleId) ?: return@launch
            _uiState.value = schedule.toEditorState()
        }
    }

    fun save() {
        val state = _uiState.value
        val schedule = Schedule(
            id = state.id,
            title = state.title,
            timing = if (state.allDay) {
                ScheduleTiming.AllDay(
                    startDate = state.startDate,
                    endDateExclusive = state.endDate.plusDays(1),
                )
            } else {
                ScheduleTiming.Timed(
                    startsAt = LocalDateTime.of(state.startDate, state.startTime),
                    endsAt = LocalDateTime.of(state.endDate, state.endTime),
                    zoneId = zoneId,
                )
            },
            note = state.note,
            recurrence = state.recurrenceFrequency?.let(::RecurrenceRule),
            reminderMinutesBefore = state.reminderMinutesBefore,
        )
        val errors = ScheduleValidator.validate(schedule)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            val scheduleId = repository.save(schedule)
            _uiState.update {
                it.copy(
                    errors = emptyList(),
                    savedScheduleId = scheduleId,
                )
            }
        }
    }

    private fun initialState(): ScheduleEditorUiState {
        val date = LocalDate.now(clock)
        return ScheduleEditorUiState(
            startDate = date,
            endDate = date,
            reminderMinutesBefore = defaultReminderMinutes(),
        )
    }
}

private fun Schedule.toEditorState(): ScheduleEditorUiState =
    when (val scheduleTiming = timing) {
        is ScheduleTiming.AllDay -> ScheduleEditorUiState(
            id = id,
            title = title,
            allDay = true,
            startDate = scheduleTiming.startDate,
            endDate = scheduleTiming.endDateExclusive.minusDays(1),
            note = note,
            recurrenceFrequency = recurrence?.frequency,
            reminderMinutesBefore = reminderMinutesBefore,
        )
        is ScheduleTiming.Timed -> ScheduleEditorUiState(
            id = id,
            title = title,
            allDay = false,
            startDate = scheduleTiming.startsAt.toLocalDate(),
            endDate = scheduleTiming.endsAt.toLocalDate(),
            startTime = scheduleTiming.startsAt.toLocalTime(),
            endTime = scheduleTiming.endsAt.toLocalTime(),
            note = note,
            recurrenceFrequency = recurrence?.frequency,
            reminderMinutesBefore = reminderMinutesBefore,
        )
    }
