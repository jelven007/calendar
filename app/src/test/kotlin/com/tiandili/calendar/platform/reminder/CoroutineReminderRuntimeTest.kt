package com.tiandili.calendar.platform.reminder

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineReminderRuntimeTest {

    @Test
    fun `cancellation reconciliation waits for an in progress notification dispatch`() {
        val scope = TestScope(StandardTestDispatcher())
        val releaseDispatch = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val runtime = CoroutineReminderRuntime(
            scope,
            dispatchReminder = {
                events += "dispatch-start"
                releaseDispatch.await()
                events += "notification-posted"
            },
            reconcileReminders = { events += "cancel-notification" },
            prepareForRecovery = {},
        )
        runtime.dispatch(1)
        scope.advanceUntilIdle()

        runtime.reconcile()
        scope.advanceUntilIdle()
        assertThat(events).containsExactly("dispatch-start")
        releaseDispatch.complete(Unit)
        scope.advanceUntilIdle()
        assertThat(events).containsExactly(
            "dispatch-start", "notification-posted", "cancel-notification", "cancel-notification",
        ).inOrder()
    }

    @Test
    fun `dispatch runs in the application scope and completes afterward`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val events = mutableListOf<String>()
        val runtime = CoroutineReminderRuntime(
            scope = scope,
            dispatchReminder = { events += "dispatch:$it" },
            reconcileReminders = { events += "reconcile" },
            prepareForRecovery = { events += "prepare" },
        )

        runtime.dispatch(42) {
            events += "complete"
        }
        assertThat(events).isEmpty()

        scope.advanceUntilIdle()

        assertThat(events)
            .containsExactly("dispatch:42", "reconcile", "complete")
            .inOrder()
    }

    @Test
    fun `recovery prepares persisted jobs before reconciliation`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val events = mutableListOf<String>()
        val runtime = CoroutineReminderRuntime(
            scope = scope,
            dispatchReminder = {},
            reconcileReminders = { events += "reconcile" },
            prepareForRecovery = { events += "prepare" },
        )

        runtime.reconcile(rebuildScheduled = true) {
            events += "complete"
        }
        scope.advanceUntilIdle()

        assertThat(events)
            .containsExactly("prepare", "reconcile", "complete")
            .inOrder()
    }

    @Test
    fun `ordinary reconciliation keeps scheduled jobs intact`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val events = mutableListOf<String>()
        val runtime = CoroutineReminderRuntime(
            scope = scope,
            dispatchReminder = {},
            reconcileReminders = { events += "reconcile" },
            prepareForRecovery = { events += "prepare" },
        )

        runtime.reconcile(rebuildScheduled = false)
        scope.advanceUntilIdle()

        assertThat(events).containsExactly("reconcile")
    }

    @Test
    fun `runtime completes a broadcast when work fails`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        var completed = false
        val runtime = CoroutineReminderRuntime(
            scope = scope,
            dispatchReminder = { error("database unavailable") },
            reconcileReminders = {},
            prepareForRecovery = {},
        )

        runtime.dispatch(42) {
            completed = true
        }
        scope.advanceUntilIdle()

        assertThat(completed).isTrue()
    }
}
