package com.tiandili.calendar.platform.reminder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ReminderRuntime {
    fun dispatch(
        jobId: Long,
        onComplete: () -> Unit = {},
    )

    fun reconcile(
        rebuildScheduled: Boolean = false,
        onComplete: () -> Unit = {},
    )
}

interface ReminderRuntimeOwner {
    val reminderRuntime: ReminderRuntime
}

class CoroutineReminderRuntime(
    private val scope: CoroutineScope,
    private val dispatchReminder: suspend (Long) -> Unit,
    private val reconcileReminders: suspend () -> Unit,
    private val prepareForRecovery: suspend () -> Unit,
) : ReminderRuntime {
    private val reconciliationMutex = Mutex()

    override fun dispatch(
        jobId: Long,
        onComplete: () -> Unit,
    ) {
        launchWork(onComplete) {
            reconciliationMutex.withLock {
                dispatchReminder(jobId)
                reconcileReminders()
            }
        }
    }

    override fun reconcile(
        rebuildScheduled: Boolean,
        onComplete: () -> Unit,
    ) {
        launchWork(onComplete) {
            reconciliationMutex.withLock {
                if (rebuildScheduled) {
                    prepareForRecovery()
                }
                reconcileReminders()
            }
        }
    }

    private fun launchWork(
        onComplete: () -> Unit,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A later lifecycle or system event retries persisted outbox work.
            } finally {
                onComplete()
            }
        }
    }
}
