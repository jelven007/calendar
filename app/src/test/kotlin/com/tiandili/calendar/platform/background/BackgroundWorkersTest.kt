package com.tiandili.calendar.platform.background

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.weather.WeatherRefreshResult
import com.tiandili.calendar.data.weather.WeatherRefreshGroup
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = BackgroundTestApplication::class)
class BackgroundWorkersTest {
    private lateinit var app: BackgroundTestApplication
    @Before fun setup() { app = ApplicationProvider.getApplicationContext(); app.tasks = TestBackgroundTasks() }

    @Test fun `weather worker refreshes cache before widgets`() = runBlocking<Unit> {
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(app).build()
        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(app.tasks.calls).containsExactly("weather", "widget").inOrder()
    }

    @Test fun `partial network failure retains local widget and requests retry`() = runBlocking<Unit> {
        app.tasks.result = WeatherRefreshResult(failedGroups = setOf(WeatherRefreshGroup.FORECAST))
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(app).build()
        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(app.tasks.calls).containsExactly("weather", "widget").inOrder()
    }

    @Test fun `weather failure stops retrying after three retries`() = runBlocking<Unit> {
        app.tasks.result = WeatherRefreshResult(failedGroups = setOf(WeatherRefreshGroup.FORECAST))
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(app).setRunAttemptCount(3).build()
        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test fun `no city does not create a network retry loop`() = runBlocking<Unit> {
        app.tasks.result = WeatherRefreshResult(noSelectedCity = true)
        assertThat(TestListenableWorkerBuilder<WeatherRefreshWorker>(app).build().doWork())
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test fun `daily compensation waits for reminder reconciliation`() = runBlocking<Unit> {
        assertThat(TestListenableWorkerBuilder<ReminderCompensationWorker>(app).build().doWork())
            .isEqualTo(ListenableWorker.Result.success())
        assertThat(app.tasks.calls).containsExactly("reminder")
    }

    @Test fun `local widget rebuild never calls weather network`() = runBlocking<Unit> {
        assertThat(TestListenableWorkerBuilder<WidgetRefreshWorker>(app).build().doWork())
            .isEqualTo(ListenableWorker.Result.success())
        assertThat(app.tasks.calls).containsExactly("widget")
    }
}

class BackgroundTestApplication : Application(), BackgroundTasksOwner {
    var tasks = TestBackgroundTasks()
    override val backgroundTasks: BackgroundTasks get() = tasks
}

class TestBackgroundTasks : BackgroundTasks {
    val calls = mutableListOf<String>()
    var result = WeatherRefreshResult()
    override suspend fun refreshWeather(): WeatherRefreshResult { calls += "weather"; return result }
    override suspend fun rebuildWidgets() { calls += "widget" }
    override suspend fun recoverReminders() { calls += "reminder" }
}
