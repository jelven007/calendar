package com.tiandili.calendar.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.Schedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleListUiState(
    val schedules: List<Schedule> = emptyList(),
)

class ScheduleListViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {

    val uiState: StateFlow<ScheduleListUiState> = repository.observeAll()
        .map(::ScheduleListUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ScheduleListUiState(),
        )

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.delete(scheduleId)
        }
    }
}
