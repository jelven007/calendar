package com.tiandili.calendar.platform.background

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ForegroundMaintenanceTest {
    @Test fun `foreground recovery refreshes weather and widget after reminders`() = runBlocking<Unit> {
        val tasks = TestBackgroundTasks()
        tasks.onForeground()
        assertThat(tasks.calls).containsExactly("reminder", "weather", "widget").inOrder()
    }
}
