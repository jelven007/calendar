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
import com.tiandili.calendar.domain.schedule.ScheduleKind
import com.tiandili.calendar.domain.schedule.ChecklistItem
import java.time.DayOfWeek
import com.nlf.calendar.Solar
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
    val kind: ScheduleKind = ScheduleKind.SCHEDULE,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val mediaUris: List<String> = emptyList(),
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: Set<DayOfWeek> = emptySet(),
    val recurrenceUntil: LocalDate? = null,
    val recurrenceCount: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val timingZoneId: ZoneId = ZoneId.systemDefault(),
    val saving: Boolean = false,
    val saveError: String? = null,
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
        _uiState.update { it.copy(startDate = startDate,
            endDate = if (it.kind != ScheduleKind.SCHEDULE || it.endDate < startDate) startDate else it.endDate) }
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
        _uiState.update { it.copy(recurrenceFrequency = frequency,
            lunarMonth = if (frequency == RecurrenceFrequency.YEARLY) it.lunarMonth else null,
            lunarDay = if (frequency == RecurrenceFrequency.YEARLY) it.lunarDay else null) }
    }

    fun onKindChanged(kind: ScheduleKind) {
        _uiState.update { state ->
            if (state.kind == kind) return@update state
            val annual = kind == ScheduleKind.BIRTHDAY || kind == ScheduleKind.ANNIVERSARY
            state.copy(kind = kind, allDay = kind !in setOf(ScheduleKind.ALARM, ScheduleKind.SCHEDULE),
                recurrenceFrequency = if (annual) RecurrenceFrequency.YEARLY
                    else if (kind == ScheduleKind.ALARM) RecurrenceFrequency.DAILY else null,
                recurrenceInterval = 1, recurrenceWeekdays = emptySet(), recurrenceUntil = null,
                recurrenceCount = null, lunarMonth = null, lunarDay = null,
                endDate = if (kind == ScheduleKind.SCHEDULE) state.endDate else state.startDate,
                reminderMinutesBefore = if (kind == ScheduleKind.ALARM) 0
                    else if (state.kind == ScheduleKind.ALARM) defaultReminderMinutes() else state.reminderMinutesBefore)
        }
    }

    fun onEnabledChanged(enabled: Boolean) { _uiState.update { it.copy(enabled = enabled) } }
    fun onCompletedChanged(completed: Boolean) {
        _uiState.update { it.copy(completed = completed, checklist = it.checklist.map { item -> item.copy(completed = completed) }) }
    }
    fun addChecklistItem(title: String) {
        if (title.isBlank() || title.trim().length > 200 || _uiState.value.checklist.size >= 50) return
        _uiState.update { it.copy(checklist = it.checklist + ChecklistItem(title.trim()), completed = false) }
    }
    fun toggleChecklistItem(index: Int, completed: Boolean) {
        _uiState.update { state ->
            val list = state.checklist.mapIndexed { i, item -> if (i == index) item.copy(completed = completed) else item }
            state.copy(checklist = list, completed = list.isNotEmpty() && list.all { it.completed })
        }
    }
    fun removeChecklistItem(index: Int) {
        _uiState.update { state ->
            val list = state.checklist.filterIndexed { i, _ -> i != index }
            state.copy(checklist = list, completed = list.isNotEmpty() && list.all { it.completed })
        }
    }
    fun onMediaChanged(uris: List<String>) { _uiState.update { it.copy(mediaUris = uris.distinct().take(9)) } }
    fun onRecurrenceIntervalChanged(interval: Int) {
        if (interval > 0) _uiState.update { it.copy(recurrenceInterval = interval) }
    }
    fun onWeekdayChanged(day: DayOfWeek, selected: Boolean) {
        _uiState.update { it.copy(recurrenceWeekdays = if (selected) it.recurrenceWeekdays + day else it.recurrenceWeekdays - day) }
    }
    fun onRecurrenceUntilChanged(date: LocalDate?) { _uiState.update { it.copy(recurrenceUntil = date) } }
    fun onRecurrenceCountChanged(count: Int?) {
        if (count == null || count > 0) _uiState.update { it.copy(recurrenceCount = count) }
    }
    fun onLunarChanged(enabled: Boolean) {
        _uiState.update { state ->
            val lunar = Solar.fromYmd(state.startDate.year, state.startDate.monthValue, state.startDate.dayOfMonth).lunar
            state.copy(recurrenceFrequency = RecurrenceFrequency.YEARLY,
                lunarMonth = if (enabled) lunar.month else null, lunarDay = if (enabled) lunar.day else null)
        }
    }
    fun onLunarDateChanged(month: Int, day: Int) {
        if (month in -12..12 && month != 0 && day in 1..30) _uiState.update {
            it.copy(lunarMonth = month, lunarDay = day, recurrenceFrequency = RecurrenceFrequency.YEARLY)
        }
    }

    fun onReminderMinutesChanged(minutesBefore: Int?) {
        _uiState.update { it.copy(reminderMinutesBefore = minutesBefore) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun startNew(kind: ScheduleKind = ScheduleKind.SCHEDULE, date: LocalDate? = null) {
        _uiState.value = initialState().let { state ->
            if (date == null) state else state.copy(startDate = date, endDate = date)
        }
        if (kind != ScheduleKind.SCHEDULE) onKindChanged(kind)
    }

    fun load(scheduleId: Long) {
        viewModelScope.launch {
            val schedule = repository.get(scheduleId) ?: return@launch
            _uiState.value = schedule.toEditorState()
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return
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
                    endsAt = if (state.kind == ScheduleKind.ALARM)
                        LocalDateTime.of(state.startDate, state.startTime).plusMinutes(1)
                        else LocalDateTime.of(state.endDate, state.endTime),
                    zoneId = if (state.kind == ScheduleKind.ALARM) zoneId else state.timingZoneId,
                )
            },
            note = state.note,
            recurrence = state.recurrenceFrequency?.let {
                RecurrenceRule(it, state.recurrenceInterval, state.recurrenceWeekdays,
                    state.recurrenceUntil, state.recurrenceCount, state.lunarMonth, state.lunarDay)
            },
            reminderMinutesBefore = state.reminderMinutesBefore,
            kind = state.kind, enabled = state.enabled, completed = state.completed,
            checklist = state.checklist, mediaUris = state.mediaUris,
        )
        val errors = ScheduleValidator.validate(schedule)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val scheduleId = repository.save(schedule)
                _uiState.update { it.copy(errors = emptyList(), savedScheduleId = scheduleId, saving = false) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(saving = false, saveError = "保存失败，请重试") }
            }
        }
    }

    private fun initialState(): ScheduleEditorUiState {
        val date = LocalDate.now(clock)
        return ScheduleEditorUiState(
            startDate = date,
            endDate = date,
            reminderMinutesBefore = defaultReminderMinutes(),
            timingZoneId = zoneId,
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
    }.copy(kind = kind, enabled = enabled, completed = completed, checklist = checklist,
        mediaUris = mediaUris, recurrenceInterval = recurrence?.interval ?: 1,
        recurrenceWeekdays = recurrence?.weekdays.orEmpty(), recurrenceUntil = recurrence?.until,
        recurrenceCount = recurrence?.count, lunarMonth = recurrence?.lunarMonth, lunarDay = recurrence?.lunarDay,
        timingZoneId = (timing as? ScheduleTiming.Timed)?.zoneId ?: ZoneId.systemDefault())
