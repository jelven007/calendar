package com.tiandili.calendar.domain.reminder

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderCoordinatorTest {

    @Test
    fun `reconcile schedules and cancels every pending command`() = runTest {
        val outbox = FakeReminderOutbox(
            commands = listOf(
                ReminderCommand.Schedule(
                    alarm = ReminderAlarm(
                        jobId = 7,
                        triggerAtEpochMillis = 1_735_721_100_000,
                    ),
                ),
                ReminderCommand.Cancel(jobId = 8),
            ),
        )
        val gateway = FakeAlarmGateway(
            precision = AlarmPrecision.INEXACT,
        )

        ReminderCoordinator(outbox, gateway).reconcile()

        assertThat(gateway.scheduled).containsExactly(
            ReminderAlarm(
                jobId = 7,
                triggerAtEpochMillis = 1_735_721_100_000,
            ),
        )
        assertThat(gateway.cancelled).containsExactly(8L)
        assertThat(outbox.scheduled).containsExactly(7L to AlarmPrecision.INEXACT)
        assertThat(outbox.cancelled).containsExactly(8L)
    }

    @Test
    fun `reconcile does not repeat commands that were already confirmed`() = runTest {
        val outbox = FakeReminderOutbox(
            commands = listOf(
                ReminderCommand.Schedule(
                    alarm = ReminderAlarm(
                        jobId = 7,
                        triggerAtEpochMillis = 1_735_721_100_000,
                    ),
                ),
            ),
        )
        val gateway = FakeAlarmGateway()
        val coordinator = ReminderCoordinator(outbox, gateway)

        coordinator.reconcile()
        coordinator.reconcile()

        assertThat(gateway.scheduled).hasSize(1)
        assertThat(outbox.scheduled).containsExactly(7L to AlarmPrecision.EXACT)
    }

    @Test
    fun `reconcile records a gateway failure without confirming the command`() = runTest {
        val outbox = FakeReminderOutbox(
            commands = listOf(
                ReminderCommand.Schedule(
                    alarm = ReminderAlarm(
                        jobId = 7,
                        triggerAtEpochMillis = 1_735_721_100_000,
                    ),
                ),
            ),
        )
        val gateway = FakeAlarmGateway(
            failure = IllegalStateException("alarm service unavailable"),
        )

        ReminderCoordinator(outbox, gateway).reconcile()

        assertThat(outbox.scheduled).isEmpty()
        assertThat(outbox.failures).containsExactly(
            7L to "alarm service unavailable",
        )
    }
}

private class FakeReminderOutbox(
    commands: List<ReminderCommand>,
) : ReminderOutbox {
    private val remainingCommands = commands.toMutableList()
    val scheduled = mutableListOf<Pair<Long, AlarmPrecision>>()
    val cancelled = mutableListOf<Long>()
    val failures = mutableListOf<Pair<Long, String>>()

    override suspend fun getCommands(): List<ReminderCommand> = remainingCommands.toList()

    override suspend fun markScheduled(
        jobId: Long,
        precision: AlarmPrecision,
    ): Boolean {
        scheduled += jobId to precision
        remainingCommands.removeAll { it.jobId == jobId }
        return true
    }

    override suspend fun markCancelled(jobId: Long): Boolean {
        cancelled += jobId
        remainingCommands.removeAll { it.jobId == jobId }
        return true
    }

    override suspend fun recordFailure(
        jobId: Long,
        message: String,
    ) {
        failures += jobId to message
    }
}

private class FakeAlarmGateway(
    private val precision: AlarmPrecision = AlarmPrecision.EXACT,
    private val failure: RuntimeException? = null,
) : AlarmGateway {
    val scheduled = mutableListOf<ReminderAlarm>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(alarm: ReminderAlarm): AlarmPrecision {
        failure?.let { throw it }
        scheduled += alarm
        return precision
    }

    override fun cancel(jobId: Long) {
        failure?.let { throw it }
        cancelled += jobId
    }
}
