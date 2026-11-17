package com.tiandili.calendar

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.backup.BackupCodec
import com.tiandili.calendar.data.backup.LocalDataManager
import com.tiandili.calendar.data.settings.DataStoreSettingsRepository
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.local.MIGRATION_1_2
import com.tiandili.calendar.data.local.MIGRATION_2_3
import com.tiandili.calendar.data.local.MIGRATION_3_4
import com.tiandili.calendar.data.reminder.ReminderSchedulingScheduleRepository
import com.tiandili.calendar.data.reminder.RoomReminderDeliveryStore
import com.tiandili.calendar.data.reminder.RoomReminderOutbox
import com.tiandili.calendar.data.schedule.RoomScheduleRepository
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.data.weather.CachingWeatherRepository
import com.tiandili.calendar.data.weather.RoomWeatherStore
import com.tiandili.calendar.data.weather.WeatherRepository
import com.tiandili.calendar.data.weather.remote.OpenMeteoClientFactory
import com.tiandili.calendar.data.widget.FileWidgetSnapshotStore
import com.tiandili.calendar.data.widget.WidgetSnapshotRepository
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.domain.widget.WidgetSnapshotComposer
import com.tiandili.calendar.platform.widget.CompactCalendarWidget
import com.tiandili.calendar.platform.widget.WideCalendarWidget
import com.tiandili.calendar.platform.widget.WidgetRefreshOwner
import com.tiandili.calendar.platform.background.AndroidBackgroundScheduler
import com.tiandili.calendar.platform.background.BackgroundTasks
import com.tiandili.calendar.platform.background.BackgroundTasksOwner
import androidx.glance.appwidget.updateAll
import androidx.work.Configuration
import java.io.File
import com.tiandili.calendar.domain.reminder.ReminderCoordinator
import com.tiandili.calendar.domain.reminder.ReminderDispatcher
import com.tiandili.calendar.platform.reminder.AndroidAlarmGateway
import com.tiandili.calendar.platform.reminder.AndroidReminderNotificationGateway
import com.tiandili.calendar.platform.reminder.CoroutineReminderRuntime
import com.tiandili.calendar.platform.reminder.ReminderRuntime
import com.tiandili.calendar.platform.reminder.ReminderRuntimeOwner
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException

private val Context.preferencesDataStore by preferencesDataStore(name = "settings")

class TiandiliApplication : Application(), ReminderRuntimeOwner, BackgroundTasksOwner, Configuration.Provider, WidgetRefreshOwner {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock: Clock by lazy { Clock.systemDefaultZone() }
    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    val backgroundScheduler by lazy { AndroidBackgroundScheduler(this) }
    override fun requestWidgetRefresh(resetMidnight: Boolean) {
        if (resetMidnight) backgroundScheduler.resetMidnight()
        backgroundScheduler.requestWidgetRefresh()
    }
    val widgetSnapshotStore by lazy {
        FileWidgetSnapshotStore(File(noBackupFilesDir, "widget_snapshot.json"), moshi)
    }
    private val weatherStore by lazy { RoomWeatherStore(database.weatherDao(), moshi) }
    private val widgetSnapshotRepository by lazy {
        WidgetSnapshotRepository(database, weatherStore, settingsRepository, widgetSnapshotStore,
            WidgetSnapshotComposer(LunarJavaAlmanacEngine()), clock)
    }
    private val widgetRefreshMutex = Mutex()

    override val backgroundTasks: BackgroundTasks by lazy {
        object : BackgroundTasks {
            override suspend fun refreshWeather() = weatherRepository.refresh(force = false)
            override suspend fun rebuildWidgets() {
                widgetRefreshMutex.withLock {
                    widgetSnapshotRepository.rebuild()
                    CompactCalendarWidget().updateAll(this@TiandiliApplication)
                    WideCalendarWidget().updateAll(this@TiandiliApplication)
                    backgroundScheduler.scheduleNextMidnight()
                }
            }
            override suspend fun recoverReminders() {
                localDataManager.recoverPendingPreferences()
                suspendCancellableCoroutine { continuation ->
                    reminderRuntime.reconcile(rebuildScheduled = true) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        backgroundScheduler.ensureScheduled()
        applicationScope.launch {
            try {
                combine(
                    database.invalidationTracker.createFlow("schedules", "recurrence_rules",
                        "schedule_exceptions", "weather_cities", "weather_cache_segments"),
                    settingsRepository.preferences,
                ) { _, _ -> Unit }.debounce(150).collect {
                    backgroundScheduler.requestWidgetRefresh()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Persisted periodic work and foreground recovery can rebuild later.
            }
        }
    }

    val settingsRepository by lazy { DataStoreSettingsRepository(preferencesDataStore) }
    val userPreferences by lazy {
        settingsRepository.preferences.stateIn(applicationScope, SharingStarted.Eagerly, UserPreferences())
    }
    val localDataManager by lazy {
        LocalDataManager(database, settingsRepository, BackupCodec(moshi), clock,
            onDataChanged = { reminderRuntime.reconcile() })
    }

    val database: TiandiliDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            TiandiliDatabase::class.java,
            "tiandili.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    private val roomScheduleRepository: ScheduleRepository by lazy {
        RoomScheduleRepository(
            dao = database.scheduleDao(),
            clock = clock,
            deviceZoneId = { ZoneId.systemDefault() },
        )
    }

    override val reminderRuntime: ReminderRuntime by lazy {
        val dao = database.scheduleDao()
        val coordinator = ReminderCoordinator(
            outbox = RoomReminderOutbox(dao),
            alarmGateway = AndroidAlarmGateway(
                context = applicationContext,
                clock = clock,
            ),
        )
        val dispatcher = ReminderDispatcher(
            store = RoomReminderDeliveryStore(dao, clock),
            notifications = AndroidReminderNotificationGateway(
                context = applicationContext,
                zoneId = ZoneId.systemDefault(),
                showTitleOnLockScreen = { userPreferences.value.showScheduleTitleOnLockScreen },
            ),
            clock = clock,
        )
        CoroutineReminderRuntime(
            scope = applicationScope,
            dispatchReminder = dispatcher::dispatch,
            reconcileReminders = {
                coordinator.reconcile()
                dao.purgeCancelledSchedules()
            },
            prepareForRecovery = {
                RoomScheduleRepository(dao, Clock.systemDefaultZone(),
                    deviceZoneId = { ZoneId.systemDefault() }).replanAlarmTimeZones()
                dao.prepareReminderJobsForRecovery()
            },
        )
    }

    val scheduleRepository: ScheduleRepository by lazy {
        ReminderSchedulingScheduleRepository(
            delegate = roomScheduleRepository,
            reminderRuntime = reminderRuntime,
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        CachingWeatherRepository(
            store = weatherStore,
            remote = OpenMeteoClientFactory.create(moshi),
            clock = clock,
        )
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
