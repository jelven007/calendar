package com.tiandili.calendar.data.schedule

import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.reminder.DEFAULT_ALL_DAY_REMINDER_TIME
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScheduleRepository(
    private val dao: ScheduleDao,
    private val clock: Clock,
) : ScheduleRepository {

    override suspend fun save(schedule: Schedule): Long {
        val now = clock.millis()
        if (schedule.id != 0L) {
            val stored = requireNotNull(dao.getSchedule(schedule.id)) {
                "Schedule ${schedule.id} does not exist"
            }
            dao.updateScheduleDetails(
                schedule = schedule.toEntity(now).copy(
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                ),
                recurrenceRule = schedule.recurrence?.toEntity(),
                reminder = schedule.reminderMinutesBefore?.let { minutesBefore ->
                    schedule.toReminderEntity(minutesBefore, now, clock.zone)
                },
            )
            return schedule.id
        }
        return dao.insertScheduleDetails(
            schedule = schedule.toEntity(now),
            recurrenceRule = schedule.recurrence?.toEntity(),
            reminder = schedule.reminderMinutesBefore?.let { minutesBefore ->
                schedule.toReminderEntity(minutesBefore, now, clock.zone)
            },
        )
    }

    override suspend fun get(scheduleId: Long): Schedule? {
        val schedule = dao.getActiveSchedule(scheduleId) ?: return null
        return schedule.toDomainSchedule(
            recurrenceRule = dao.getRecurrenceRule(scheduleId),
            reminder = dao.getReminderJobs(scheduleId).firstOrNull(ReminderOutboxEntity::isActive),
        )
    }

    override suspend fun delete(scheduleId: Long) {
        dao.softDeleteScheduleDetails(
            scheduleId = scheduleId,
            deletedAtEpochMillis = clock.millis(),
        )
    }

    override fun observeAll(): Flow<List<Schedule>> =
        dao.observeAllScheduleDetails().map { schedules ->
            schedules.map { details ->
                details.schedule.toDomainSchedule(
                    recurrenceRule = details.recurrenceRules.firstOrNull(),
                    reminder = details.reminderJobs.firstOrNull(ReminderOutboxEntity::isActive),
                )
            }
        }
}

private fun Schedule.toEntity(now: Long): ScheduleEntity =
    when (val scheduleTiming = timing) {
        is ScheduleTiming.AllDay -> ScheduleEntity(
            id = id,
            title = title,
            note = note,
            allDay = true,
            startDateEpochDay = scheduleTiming.startDate.toEpochDay(),
            endDateEpochDay = scheduleTiming.endDateExclusive.minusDays(1).toEpochDay(),
            startMinuteOfDay = null,
            endMinuteOfDay = null,
            zoneId = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        is ScheduleTiming.Timed -> ScheduleEntity(
            id = id,
            title = title,
            note = note,
            allDay = false,
            startDateEpochDay = scheduleTiming.startsAt.toLocalDate().toEpochDay(),
            endDateEpochDay = scheduleTiming.endsAt.toLocalDate().toEpochDay(),
            startMinuteOfDay = scheduleTiming.startsAt.toLocalTime().toMinuteOfDay(),
            endMinuteOfDay = scheduleTiming.endsAt.toLocalTime().toMinuteOfDay(),
            zoneId = scheduleTiming.zoneId.id,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

internal fun ScheduleEntity.toDomainSchedule(
    recurrenceRule: RecurrenceRuleEntity?,
    reminder: ReminderOutboxEntity?,
): Schedule {
    val timing = if (allDay) {
        ScheduleTiming.AllDay(
            startDate = LocalDate.ofEpochDay(startDateEpochDay),
            endDateExclusive = LocalDate.ofEpochDay(endDateEpochDay).plusDays(1),
        )
    } else {
        val startMinute = requireNotNull(startMinuteOfDay)
        val endMinute = requireNotNull(endMinuteOfDay)
        ScheduleTiming.Timed(
            startsAt = LocalDateTime.of(
                LocalDate.ofEpochDay(startDateEpochDay),
                LocalTime.ofSecondOfDay(startMinute * 60L),
            ),
            endsAt = LocalDateTime.of(
                LocalDate.ofEpochDay(endDateEpochDay),
                LocalTime.ofSecondOfDay(endMinute * 60L),
            ),
            zoneId = ZoneId.of(requireNotNull(zoneId)),
        )
    }
    return Schedule(
        id = id,
        title = title,
        timing = timing,
        note = note,
        recurrence = recurrenceRule?.toDomainRule(),
        reminderMinutesBefore = reminder?.let {
            ((it.occurrenceEpochMillis - it.triggerAtEpochMillis) / 60_000).toInt()
        },
    )
}

private fun com.tiandili.calendar.domain.schedule.RecurrenceRule.toEntity() =
    RecurrenceRuleEntity(
        frequency = frequency,
        interval = interval,
        weekdaysMask = weekdays.fold(0) { mask, day ->
            mask or (1 shl (day.value - 1))
        },
        untilEpochDay = until?.toEpochDay(),
        count = count,
    )

internal fun RecurrenceRuleEntity.toDomainRule() =
    com.tiandili.calendar.domain.schedule.RecurrenceRule(
        frequency = frequency,
        interval = interval,
        weekdays = DayOfWeek.values().filterTo(mutableSetOf()) { day ->
            weekdaysMask and (1 shl (day.value - 1)) != 0
        },
        until = untilEpochDay?.let(LocalDate::ofEpochDay),
        count = count,
    )

private fun Schedule.toReminderEntity(
    minutesBefore: Int,
    now: Long,
    defaultZoneId: ZoneId,
): ReminderOutboxEntity {
    val occurrenceEpochMillis = when (val scheduleTiming = timing) {
        is ScheduleTiming.AllDay ->
            scheduleTiming.startDate
                .atTime(DEFAULT_ALL_DAY_REMINDER_TIME)
                .atZone(defaultZoneId)
                .toInstant()
                .toEpochMilli()
        is ScheduleTiming.Timed ->
            scheduleTiming.startsAt.atZone(scheduleTiming.zoneId).toInstant().toEpochMilli()
    }
    return ReminderOutboxEntity(
        occurrenceEpochMillis = occurrenceEpochMillis,
        triggerAtEpochMillis = occurrenceEpochMillis - minutesBefore * 60_000L,
        status = ReminderOutboxStatus.PENDING,
        createdAtEpochMillis = now,
    )
}

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

private fun ReminderOutboxEntity.isActive(): Boolean =
    status == ReminderOutboxStatus.PENDING ||
        status == ReminderOutboxStatus.SCHEDULED_EXACT ||
        status == ReminderOutboxStatus.SCHEDULED_INEXACT ||
        status == ReminderOutboxStatus.BLOCKED
