package com.tiandili.calendar.data.schedule

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecurrenceRule(rule: RecurrenceRuleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReminder(reminder: ReminderOutboxEntity)

    @Update
    abstract suspend fun updateSchedule(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveException(exception: ScheduleExceptionEntity)

    @Transaction
    open suspend fun insertScheduleWithRuleAndReminder(
        schedule: ScheduleEntity,
        recurrenceRule: RecurrenceRuleEntity,
        reminder: ReminderOutboxEntity,
    ): Long {
        val scheduleId = insertSchedule(schedule)
        insertRecurrenceRule(recurrenceRule.copy(scheduleId = scheduleId))
        insertReminder(reminder.copy(scheduleId = scheduleId))
        return scheduleId
    }

    @Transaction
    open suspend fun insertScheduleDetails(
        schedule: ScheduleEntity,
        recurrenceRule: RecurrenceRuleEntity?,
        reminder: ReminderOutboxEntity?,
    ): Long {
        val scheduleId = insertSchedule(schedule)
        recurrenceRule?.let {
            insertRecurrenceRule(it.copy(scheduleId = scheduleId))
        }
        reminder?.let {
            insertReminder(it.copy(scheduleId = scheduleId))
        }
        return scheduleId
    }

    @Query("DELETE FROM recurrence_rules WHERE schedule_id = :scheduleId")
    protected abstract suspend fun deleteRecurrenceRule(scheduleId: Long)

    @Query(
        """
        UPDATE reminder_outbox
        SET status = 'CANCEL_PENDING'
        WHERE schedule_id = :scheduleId
          AND status NOT IN ('CANCELLED', 'SKIPPED')
        """,
    )
    protected abstract suspend fun cancelActiveReminderJobs(scheduleId: Long)

    @Query(
        """
        SELECT * FROM reminder_outbox
        WHERE schedule_id = :scheduleId
          AND status IN (
            'PENDING',
            'SCHEDULED_EXACT',
            'SCHEDULED_INEXACT',
            'BLOCKED'
          )
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun getActiveReminderJob(
        scheduleId: Long,
    ): ReminderOutboxEntity?

    @Transaction
    open suspend fun updateScheduleDetails(
        schedule: ScheduleEntity,
        recurrenceRule: RecurrenceRuleEntity?,
        reminder: ReminderOutboxEntity?,
    ) {
        updateSchedule(schedule)
        deleteRecurrenceRule(schedule.id)
        recurrenceRule?.let {
            insertRecurrenceRule(it.copy(scheduleId = schedule.id))
        }
        val activeReminder = getActiveReminderJob(schedule.id)
        val reminderIsUnchanged = reminder != null &&
            activeReminder?.occurrenceEpochMillis == reminder.occurrenceEpochMillis &&
            activeReminder.triggerAtEpochMillis == reminder.triggerAtEpochMillis
        if (!reminderIsUnchanged) {
            cancelActiveReminderJobs(schedule.id)
            reminder?.let {
                insertReminder(it.copy(scheduleId = schedule.id))
            }
        }
    }

    @Query("SELECT * FROM schedules WHERE id = :scheduleId")
    abstract suspend fun getSchedule(scheduleId: Long): ScheduleEntity?

    @Query(
        """
        SELECT * FROM schedules
        WHERE id = :scheduleId AND deleted_at_epoch_millis IS NULL
        """,
    )
    abstract suspend fun getActiveSchedule(scheduleId: Long): ScheduleEntity?

    @Query(
        """
        UPDATE schedules
        SET deleted_at_epoch_millis = :deletedAtEpochMillis,
            updated_at_epoch_millis = :deletedAtEpochMillis
        WHERE id = :scheduleId
        """,
    )
    abstract suspend fun softDeleteSchedule(
        scheduleId: Long,
        deletedAtEpochMillis: Long,
    )

    @Transaction
    open suspend fun softDeleteScheduleDetails(
        scheduleId: Long,
        deletedAtEpochMillis: Long,
    ) {
        softDeleteSchedule(scheduleId, deletedAtEpochMillis)
        cancelActiveReminderJobs(scheduleId)
    }

    @Query("SELECT * FROM recurrence_rules WHERE schedule_id = :scheduleId")
    abstract suspend fun getRecurrenceRule(scheduleId: Long): RecurrenceRuleEntity?

    @Query(
        """
        SELECT * FROM schedule_exceptions
        WHERE schedule_id = :scheduleId
          AND occurrence_date_epoch_day = :occurrenceDateEpochDay
        """,
    )
    abstract suspend fun getException(
        scheduleId: Long,
        occurrenceDateEpochDay: Long,
    ): ScheduleExceptionEntity?

    @Query(
        """
        SELECT DISTINCT schedules.*
        FROM schedules
        LEFT JOIN recurrence_rules
          ON recurrence_rules.schedule_id = schedules.id
        WHERE schedules.deleted_at_epoch_millis IS NULL
          AND schedules.start_date_epoch_day <= :endEpochDayInclusive
          AND (
            recurrence_rules.schedule_id IS NOT NULL
            OR schedules.end_date_epoch_day >= :startEpochDay
          )
          AND (
            recurrence_rules.until_epoch_day IS NULL
            OR recurrence_rules.until_epoch_day >= :startEpochDay
          )
        ORDER BY schedules.start_date_epoch_day, schedules.start_minute_of_day
        """,
    )
    abstract fun observeScheduleCandidates(
        startEpochDay: Long,
        endEpochDayInclusive: Long,
    ): Flow<List<ScheduleEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM schedules
        WHERE deleted_at_epoch_millis IS NULL
        ORDER BY start_date_epoch_day, start_minute_of_day
        """,
    )
    abstract fun observeAllScheduleDetails(): Flow<List<ScheduleDetails>>

    @Transaction
    @Query("SELECT * FROM schedules WHERE deleted_at_epoch_millis IS NULL ORDER BY id")
    abstract suspend fun getAllScheduleDetails(): List<ScheduleDetails>

    @Query(
        """SELECT schedule_exceptions.* FROM schedule_exceptions
        INNER JOIN schedules ON schedules.id = schedule_exceptions.schedule_id
        WHERE schedules.deleted_at_epoch_millis IS NULL
        ORDER BY schedule_id, occurrence_date_epoch_day""",
    )
    abstract suspend fun getAllActiveExceptions(): List<ScheduleExceptionEntity>

    @Transaction
    open suspend fun softDeleteAllSchedules(deletedAtEpochMillis: Long) {
        getAllScheduleDetails().forEach {
            softDeleteScheduleDetails(it.schedule.id, deletedAtEpochMillis)
        }
    }

    @Query(
        """DELETE FROM schedules
        WHERE deleted_at_epoch_millis IS NOT NULL
          AND NOT EXISTS (
            SELECT 1 FROM reminder_outbox WHERE schedule_id = schedules.id
              AND status NOT IN ('CANCELLED', 'FIRED', 'SKIPPED')
          )""",
    )
    abstract suspend fun purgeCancelledSchedules()

    @Query(
        """
        SELECT * FROM reminder_outbox
        WHERE status IN ('PENDING', 'CANCEL_PENDING')
        ORDER BY id
        """,
    )
    abstract suspend fun getReminderJobsForReconciliation(): List<ReminderOutboxEntity>

    @Query("SELECT * FROM reminder_outbox WHERE id = :reminderId")
    abstract suspend fun getReminderJob(reminderId: Long): ReminderOutboxEntity?

    @Query(
        """
        UPDATE reminder_outbox
        SET status = :newStatus,
            last_error = NULL
        WHERE id = :reminderId
          AND status = :expectedStatus
        """,
    )
    abstract suspend fun transitionReminderStatus(
        reminderId: Long,
        expectedStatus: ReminderOutboxStatus,
        newStatus: ReminderOutboxStatus,
    ): Int

    @Transaction
    open suspend fun completeReminderDelivery(
        reminderId: Long,
        finalStatus: ReminderOutboxStatus,
        nextReminder: ReminderOutboxEntity?,
    ): Boolean {
        val completed = transitionReminderStatus(
            reminderId = reminderId,
            expectedStatus = ReminderOutboxStatus.DISPATCHING,
            newStatus = finalStatus,
        ) == 1
        if (completed) {
            nextReminder?.let { insertReminder(it) }
        }
        return completed
    }

    @Query(
        """
        UPDATE reminder_outbox
        SET attempt_count = attempt_count + 1,
            last_error = :message
        WHERE id = :reminderId
          AND status IN ('PENDING', 'CANCEL_PENDING')
        """,
    )
    abstract suspend fun recordReminderFailure(
        reminderId: Long,
        message: String,
    )

    @Query(
        """
        UPDATE reminder_outbox
        SET status = 'DISPATCHING',
            last_error = NULL
        WHERE id = :reminderId
          AND status IN ('SCHEDULED_EXACT', 'SCHEDULED_INEXACT')
          AND EXISTS (
            SELECT 1 FROM schedules
            WHERE schedules.id = reminder_outbox.schedule_id
              AND schedules.deleted_at_epoch_millis IS NULL
          )
        """,
    )
    protected abstract suspend fun claimReminder(reminderId: Long): Int

    @Query(
        """
        SELECT reminder_outbox.id AS jobId,
               reminder_outbox.schedule_id AS scheduleId,
               schedules.title AS title,
               reminder_outbox.occurrence_epoch_millis AS occurrenceEpochMillis,
               reminder_outbox.trigger_at_epoch_millis AS triggerAtEpochMillis
        FROM reminder_outbox
        INNER JOIN schedules
          ON schedules.id = reminder_outbox.schedule_id
        WHERE reminder_outbox.id = :reminderId
          AND reminder_outbox.status = 'DISPATCHING'
          AND schedules.deleted_at_epoch_millis IS NULL
        """,
    )
    protected abstract suspend fun getClaimedReminder(
        reminderId: Long,
    ): ReminderDeliveryRow?

    @Transaction
    open suspend fun claimReminderForDelivery(
        reminderId: Long,
    ): ReminderDeliveryRow? {
        if (claimReminder(reminderId) != 1) {
            return null
        }
        return getClaimedReminder(reminderId)
    }

    @Query(
        """
        UPDATE reminder_outbox
        SET status = 'PENDING',
            attempt_count = attempt_count + 1,
            last_error = :message
        WHERE id = :reminderId
          AND status = 'DISPATCHING'
        """,
    )
    abstract suspend fun releaseReminderForRetry(
        reminderId: Long,
        message: String,
    )

    @Query(
        """
        UPDATE reminder_outbox
        SET status = 'PENDING'
        WHERE status IN (
          'SCHEDULED_EXACT',
          'SCHEDULED_INEXACT',
          'DISPATCHING',
          'BLOCKED',
          'FAILED'
        )
        """,
    )
    abstract suspend fun prepareReminderJobsForRecovery()

    @Query(
        """
        SELECT * FROM reminder_outbox
        WHERE schedule_id = :scheduleId
        ORDER BY trigger_at_epoch_millis
        """,
    )
    abstract suspend fun getReminderJobs(scheduleId: Long): List<ReminderOutboxEntity>
}
