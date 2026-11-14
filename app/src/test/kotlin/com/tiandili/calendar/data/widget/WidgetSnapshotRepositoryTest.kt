package com.tiandili.calendar.data.widget

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.RoomScheduleRepository
import com.tiandili.calendar.data.schedule.ScheduleExceptionEntity
import com.tiandili.calendar.data.settings.DataStoreSettingsRepository
import com.tiandili.calendar.data.weather.RoomWeatherStore
import com.tiandili.calendar.domain.schedule.*
import com.tiandili.calendar.domain.widget.WidgetSnapshotComposer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WidgetSnapshotRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var database: TiandiliDatabase
    private lateinit var settings: DataStoreSettingsRepository
    private lateinit var store: FileWidgetSnapshotStore
    private lateinit var repository: WidgetSnapshotRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zone)

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), TiandiliDatabase::class.java).build()
        val moshi = Moshi.Builder().build()
        settings = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "settings.preferences_pb")
        })
        store = FileWidgetSnapshotStore(File(folder.root, "widget.json"), moshi)
        repository = WidgetSnapshotRepository(database, RoomWeatherStore(database.weatherDao(), moshi),
            settings, store, WidgetSnapshotComposer(LunarJavaAlmanacEngine()), clock, { zone })
    }
    @After fun close() { scope.cancel(); database.close() }

    @Test fun `rebuild reads persisted recurrence exceptions and privacy preferences`() = runBlocking<Unit> {
        val date = LocalDate.of(2025, 2, 2)
        val id = RoomScheduleRepository(database.scheduleDao(), clock).save(Schedule(
            title = "每天读书", timing = ScheduleTiming.AllDay(date, date.plusDays(1)),
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)))
        database.scheduleDao().saveException(ScheduleExceptionEntity(
            scheduleId = id, occurrenceDateEpochDay = date.toEpochDay(), cancelled = true))
        settings.setShowNextScheduleInWidget(true)
        repository.rebuild()
        assertThat(store.read()?.nextSchedule?.timeLabel).isEqualTo("2月3日 · 全天")
        settings.setShowNextScheduleInWidget(false)
        repository.rebuild()
        assertThat(store.read()?.nextSchedule).isNull()
    }

    @Test fun `rebuild excludes soft deleted schedules immediately`() = runBlocking<Unit> {
        val schedules = RoomScheduleRepository(database.scheduleDao(), clock)
        val date = LocalDate.of(2025, 2, 2)
        val id = schedules.save(Schedule(title = "待删除", timing = ScheduleTiming.AllDay(date, date.plusDays(1))))
        settings.setShowNextScheduleInWidget(true)
        repository.rebuild()
        assertThat(store.read()?.nextSchedule?.scheduleId).isEqualTo(id)
        schedules.delete(id)
        repository.rebuild()
        assertThat(store.read()?.nextSchedule).isNull()
    }
}
