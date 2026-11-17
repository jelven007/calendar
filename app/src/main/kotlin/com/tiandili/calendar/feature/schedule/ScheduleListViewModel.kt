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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScheduleListUiState(
    val schedules: List<Schedule> = emptyList(),
    val error: String? = null,
)

class ScheduleListViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val actions = Mutex()

    val uiState: StateFlow<ScheduleListUiState> = combine(repository.observeAll(), message) { records, error ->
        ScheduleListUiState(records, error)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ScheduleListUiState(),
        )

    fun deleteSchedule(scheduleId: Long) {
        act {
            repository.delete(scheduleId)
        }
    }

    fun setCompleted(scheduleId: Long, completed: Boolean) {
        act {
            val record = repository.get(scheduleId) ?: return@act
            repository.save(record.copy(completed = completed,
                checklist = record.checklist.map { it.copy(completed = completed) }))
        }
    }
    fun setEnabled(scheduleId: Long, enabled: Boolean) {
        act {
            val record = repository.get(scheduleId) ?: return@act
            repository.save(record.copy(enabled = enabled))
        }
    }
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { actions.withLock { block() }; message.value = null }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { message.value = "操作失败，请重试" }
        }
    }
}
