package com.tiandili.calendar.domain.reminder

import kotlinx.coroutines.CancellationException

data class ReminderAlarm(
    val jobId: Long,
    val triggerAtEpochMillis: Long,
)

enum class AlarmPrecision {
    EXACT,
    INEXACT,
}

sealed interface ReminderCommand {
    val jobId: Long

    data class Schedule(
        val alarm: ReminderAlarm,
    ) : ReminderCommand {
        override val jobId: Long = alarm.jobId
    }

    data class Cancel(
        override val jobId: Long,
    ) : ReminderCommand
}

interface ReminderOutbox {
    suspend fun getCommands(): List<ReminderCommand>

    suspend fun markScheduled(
        jobId: Long,
        precision: AlarmPrecision,
    ): Boolean

    suspend fun markCancelled(jobId: Long): Boolean

    suspend fun recordFailure(
        jobId: Long,
        message: String,
    )
}

interface AlarmGateway {
    fun schedule(alarm: ReminderAlarm): AlarmPrecision

    fun cancel(jobId: Long)
}

class ReminderCoordinator(
    private val outbox: ReminderOutbox,
    private val alarmGateway: AlarmGateway,
) {

    suspend fun reconcile() {
        outbox.getCommands().forEach { command ->
            try {
                when (command) {
                    is ReminderCommand.Schedule -> {
                        val precision = alarmGateway.schedule(command.alarm)
                        outbox.markScheduled(command.jobId, precision)
                    }
                    is ReminderCommand.Cancel -> {
                        alarmGateway.cancel(command.jobId)
                        outbox.markCancelled(command.jobId)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                outbox.recordFailure(
                    jobId = command.jobId,
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }
}
