package com.tiandili.calendar.data.widget

import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.settings.SettingsRepository
import com.tiandili.calendar.data.weather.WeatherStore
import androidx.room.withTransaction
import com.tiandili.calendar.data.schedule.toDomainSchedule
import com.tiandili.calendar.domain.widget.CancelledWidgetOccurrence
import com.tiandili.calendar.domain.widget.WidgetSnapshot
import com.tiandili.calendar.domain.widget.WidgetSnapshotComposer
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WidgetSnapshotRepository(
    private val database: TiandiliDatabase,
    private val weather: WeatherStore,
    private val settings: SettingsRepository,
    private val store: FileWidgetSnapshotStore,
    private val composer: WidgetSnapshotComposer,
    private val clock: Clock,
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()

    suspend fun rebuild(): WidgetSnapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (schedules, cancelled, cachedWeather) = database.withTransaction {
                val dao = database.scheduleDao()
                Triple(
                    dao.getAllScheduleDetails().map {
                        it.schedule.toDomainSchedule(it.recurrenceRules.firstOrNull(), null)
                    },
                    dao.getAllActiveExceptions().filter { it.cancelled }.mapTo(mutableSetOf()) {
                        CancelledWidgetOccurrence(it.scheduleId, it.occurrenceDateEpochDay)
                    },
                    weather.selectedCity()?.let { weather.currentSnapshot(it.id) },
                )
            }
            composer.compose(
                now = clock.instant(),
                zoneId = zoneId(),
                schedules = schedules,
                cancelled = cancelled,
                weather = cachedWeather,
                preferences = settings.current(),
            ).also(store::write)
        }
    }
}
