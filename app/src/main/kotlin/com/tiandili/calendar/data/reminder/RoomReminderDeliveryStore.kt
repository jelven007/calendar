package com.tiandili.calendar.data.reminder

import com.tiandili.calendar.data.schedule.ReminderOutboxEntity
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.ScheduleDao
import com.tiandili.calendar.data.schedule.toDomainSchedule
import com.tiandili.calendar.domain.reminder.NextReminderPlanner
import com.tiandili.calendar.domain.reminder.ReminderDelivery
import com.tiandili.calendar.domain.reminder.ReminderDeliveryStore
import java.time.Clock

class RoomReminderDeliveryStore(
    private val dao: ScheduleDao,
    private val clock: Clock,
) : ReminderDeliveryStore {

    override suspend fun claim(jobId: Long): ReminderDelivery? =
        dao.claimReminderForDelivery(jobId)?.let { row ->
            ReminderDelivery(
                jobId = row.jobId,
                scheduleId = row.scheduleId,
                title = row.title,
                occurrenceEpochMillis = row.occurrenceEpochMillis,
                triggerAtEpochMillis = row.triggerAtEpochMillis,
                kind = row.kind,
            )
        }

    override suspend fun markFired(jobId: Long) {
        completeAndPlanNext(jobId, ReminderOutboxStatus.FIRED)
    }

    override suspend fun markBlocked(jobId: Long) {
        complete(jobId, ReminderOutboxStatus.BLOCKED)
    }

    override suspend fun markSkipped(jobId: Long) {
        completeAndPlanNext(jobId, ReminderOutboxStatus.SKIPPED)
    }

    override suspend fun releaseForRetry(
        jobId: Long,
        message: String,
    ) {
        dao.releaseReminderForRetry(jobId, message)
    }

    private suspend fun complete(
        jobId: Long,
        status: ReminderOutboxStatus,
    ) {
        dao.transitionReminderStatus(
            reminderId = jobId,
            expectedStatus = ReminderOutboxStatus.DISPATCHING,
            newStatus = status,
        )
    }

    private suspend fun completeAndPlanNext(
        jobId: Long,
        status: ReminderOutboxStatus,
    ) {
        val job = dao.getReminderJob(jobId) ?: return
        val scheduleEntity = dao.getActiveSchedule(job.scheduleId)
        val nextReminder = scheduleEntity?.let { activeSchedule ->
            val schedule = activeSchedule.toDomainSchedule(
                recurrenceRule = dao.getRecurrenceRule(job.scheduleId),
                reminder = job,
            )
            NextReminderPlanner.planAfter(
                schedule = schedule,
                currentOccurrenceEpochMillis = if (status == ReminderOutboxStatus.SKIPPED) {
                    maxOf(job.occurrenceEpochMillis, clock.millis())
                } else {
                    job.occurrenceEpochMillis
                },
                defaultZoneId = clock.zone,
            )?.let { plan ->
                ReminderOutboxEntity(
                    scheduleId = job.scheduleId,
                    occurrenceEpochMillis = plan.occurrenceEpochMillis,
                    triggerAtEpochMillis = plan.triggerAtEpochMillis,
                    status = ReminderOutboxStatus.PENDING,
                    createdAtEpochMillis = clock.millis(),
                )
            }
        }
        dao.completeReminderDelivery(
            reminderId = jobId,
            finalStatus = status,
            nextReminder = nextReminder,
        )
    }
}
