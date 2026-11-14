package com.tiandili.calendar.data.reminder

import com.tiandili.calendar.data.schedule.ReminderOutboxEntity
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.ScheduleDao
import com.tiandili.calendar.domain.reminder.AlarmPrecision
import com.tiandili.calendar.domain.reminder.ReminderAlarm
import com.tiandili.calendar.domain.reminder.ReminderCommand
import com.tiandili.calendar.domain.reminder.ReminderOutbox

class RoomReminderOutbox(
    private val dao: ScheduleDao,
) : ReminderOutbox {

    override suspend fun getCommands(): List<ReminderCommand> =
        dao.getReminderJobsForReconciliation().map(ReminderOutboxEntity::toCommand)

    override suspend fun markScheduled(
        jobId: Long,
        precision: AlarmPrecision,
    ): Boolean =
        dao.transitionReminderStatus(
            reminderId = jobId,
            expectedStatus = ReminderOutboxStatus.PENDING,
            newStatus = when (precision) {
                AlarmPrecision.EXACT -> ReminderOutboxStatus.SCHEDULED_EXACT
                AlarmPrecision.INEXACT -> ReminderOutboxStatus.SCHEDULED_INEXACT
            },
        ) == 1

    override suspend fun markCancelled(jobId: Long): Boolean =
        dao.transitionReminderStatus(
            reminderId = jobId,
            expectedStatus = ReminderOutboxStatus.CANCEL_PENDING,
            newStatus = ReminderOutboxStatus.CANCELLED,
        ) == 1

    override suspend fun recordFailure(
        jobId: Long,
        message: String,
    ) {
        dao.recordReminderFailure(jobId, message)
    }
}

private fun ReminderOutboxEntity.toCommand(): ReminderCommand =
    when (status) {
        ReminderOutboxStatus.PENDING -> ReminderCommand.Schedule(
            ReminderAlarm(
                jobId = id,
                triggerAtEpochMillis = triggerAtEpochMillis,
            ),
        )
        ReminderOutboxStatus.CANCEL_PENDING -> ReminderCommand.Cancel(jobId = id)
        else -> error("Reminder job $id with status $status cannot be reconciled")
    }
