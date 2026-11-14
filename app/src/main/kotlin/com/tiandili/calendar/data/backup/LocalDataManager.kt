package com.tiandili.calendar.data.backup

import androidx.room.withTransaction
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.RoomScheduleRepository
import com.tiandili.calendar.data.schedule.ScheduleExceptionEntity
import com.tiandili.calendar.data.schedule.toDomainSchedule
import com.tiandili.calendar.data.settings.SettingsRepository
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.data.weather.WeatherCityEntity
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.weather.City
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ImportMode { MERGE, REPLACE }
data class ImportResult(val importedCount: Int, val skippedCount: Int)

class LocalDataManager(
    private val database: TiandiliDatabase,
    private val settings: SettingsRepository,
    private val codec: BackupCodec,
    private val clock: Clock,
    private val onDataChanged: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val schedules = RoomScheduleRepository(database.scheduleDao(), clock)

    fun preview(json: String): BackupSnapshot = codec.decode(json)

    suspend fun exportJson(): String = mutex.withLock {
        applyPendingPreferences()
        val preferences = settings.current()
        database.withTransaction { codec.encode(snapshot(preferences)) }
    }

    suspend fun importBackup(backup: BackupSnapshot, mode: ImportMode): ImportResult = mutex.withLock {
        // Revalidate even if a caller supplies a snapshot without going through preview.
        codec.encode(backup)
        applyPendingPreferences()
        val currentPreferences = settings.current()
        val result = database.withTransaction {
            val existing = snapshot(currentPreferences)
            val keys = if (mode == ImportMode.MERGE) {
                existing.schedules.mapTo(mutableSetOf()) { fingerprint(it, existing.exceptions) }
            } else {
                database.scheduleDao().softDeleteAllSchedules(clock.millis())
                database.localDataDao().savePendingPreferences(
                    PendingPreferencesEntity(payloadJson = codec.encodePreferences(backup.preferences)),
                )
                mutableSetOf()
            }
            var imported = 0
            backup.schedules.forEach { schedule ->
                if (keys.add(fingerprint(schedule, backup.exceptions))) {
                    val newId = schedules.save(schedule.copy(id = 0))
                    backup.exceptions.filter { it.scheduleId == schedule.id }.forEach { exception ->
                        database.scheduleDao().saveException(ScheduleExceptionEntity(
                            scheduleId = newId,
                            occurrenceDateEpochDay = exception.occurrenceDate.toEpochDay(),
                            cancelled = exception.cancelled,
                        ))
                    }
                    imported++
                }
            }
            if (mode == ImportMode.REPLACE) {
                database.weatherDao().restoreCities(backup.cities.map {
                    it.toEntity(backup.selectedCityId)
                })
            } else {
                backup.cities.filter { incoming -> existing.cities.none { it.id == incoming.id } }
                    .forEach { city ->
                        if (database.weatherDao().getSavedCities().size < 10) {
                            database.weatherDao().addSavedCity(
                                city.toEntity(existing.selectedCityId ?: backup.selectedCityId),
                            )
                        }
                    }
            }
            database.scheduleDao().purgeCancelledSchedules()
            ImportResult(imported, backup.schedules.size - imported)
        }
        onDataChanged()
        applyPendingPreferences()
        result
    }

    suspend fun clearWeatherCache() = mutex.withLock {
        database.weatherDao().clearCache()
        onDataChanged()
    }

    suspend fun clearAll() = mutex.withLock {
        database.withTransaction {
            database.scheduleDao().softDeleteAllSchedules(clock.millis())
            database.weatherDao().clearCities()
            database.localDataDao().savePendingPreferences(
                PendingPreferencesEntity(payloadJson = codec.encodePreferences(UserPreferences())),
            )
            database.scheduleDao().purgeCancelledSchedules()
        }
        onDataChanged()
        applyPendingPreferences()
    }

    suspend fun recoverPendingPreferences() = mutex.withLock { applyPendingPreferences() }

    private suspend fun applyPendingPreferences() {
        val pending = database.localDataDao().getPendingPreferences() ?: return
        settings.replace(codec.decodePreferences(pending.payloadJson))
        database.localDataDao().clearPendingPreferences()
    }

    private suspend fun snapshot(preferences: UserPreferences): BackupSnapshot {
        val details = database.scheduleDao().getAllScheduleDetails()
        val cities = database.weatherDao().getSavedCities()
        return BackupSnapshot(
            exportedAt = clock.instant(),
            schedules = details.map { row ->
                row.schedule.toDomainSchedule(
                    row.recurrenceRules.firstOrNull(),
                    row.reminderJobs.lastOrNull {
                        it.status != ReminderOutboxStatus.CANCELLED &&
                            it.status != ReminderOutboxStatus.CANCEL_PENDING
                    },
                )
            },
            exceptions = database.scheduleDao().getAllActiveExceptions().map {
                BackupException(it.scheduleId, LocalDate.ofEpochDay(it.occurrenceDateEpochDay), it.cancelled)
            },
            preferences = preferences,
            cities = cities.map { City(it.id, it.name, it.admin1, it.country, it.latitude, it.longitude,
                ZoneId.of(it.zoneId)) },
            selectedCityId = cities.firstOrNull { it.isSelected }?.id,
        )
    }

    private fun City.toEntity(selectedId: String?) = WeatherCityEntity(
        id, name, admin1, country, latitude, longitude, zoneId.id, id == selectedId, clock.millis(),
    )
}

private data class ScheduleFingerprint(
    val schedule: Schedule,
    val exceptions: List<Pair<LocalDate, Boolean>>,
)

private fun fingerprint(schedule: Schedule, exceptions: List<BackupException>) = ScheduleFingerprint(
    schedule.copy(id = 0),
    exceptions.filter { it.scheduleId == schedule.id }
        .sortedBy { it.occurrenceDate }.map { it.occurrenceDate to it.cancelled },
)
