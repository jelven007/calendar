package com.tiandili.calendar.data.reminder

import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.platform.reminder.ReminderRuntime
import kotlinx.coroutines.flow.Flow

class ReminderSchedulingScheduleRepository(
    private val delegate: ScheduleRepository,
    private val reminderRuntime: ReminderRuntime,
) : ScheduleRepository {

    override suspend fun save(schedule: Schedule): Long {
        val scheduleId = delegate.save(schedule)
        reminderRuntime.reconcile()
        return scheduleId
    }

    override suspend fun get(scheduleId: Long): Schedule? =
        delegate.get(scheduleId)

    override suspend fun delete(scheduleId: Long) {
        delegate.delete(scheduleId)
        reminderRuntime.reconcile()
    }

    override fun observeAll(): Flow<List<Schedule>> = delegate.observeAll()
    override fun observeCancelledOccurrences() = delegate.observeCancelledOccurrences()
}
