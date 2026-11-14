package com.tiandili.calendar.data.schedule

import com.tiandili.calendar.domain.schedule.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {

    suspend fun save(schedule: Schedule): Long

    suspend fun get(scheduleId: Long): Schedule?

    suspend fun delete(scheduleId: Long)

    fun observeAll(): Flow<List<Schedule>>
}
