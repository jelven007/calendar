package com.tiandili.calendar.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.ReminderOutboxStatus
import com.tiandili.calendar.data.schedule.RoomScheduleRepository
import com.tiandili.calendar.data.settings.DataStoreSettingsRepository
import com.tiandili.calendar.data.settings.UserPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalDataManagerTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var database: TiandiliDatabase
    private val codec = BackupCodec(Moshi.Builder().build())
    private val clock = Clock.fixed(Instant.parse("2026-11-08T01:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close() }

    @Test
    fun `merge allocates local ids preserves existing preferences and remaps exceptions`() = runTest {
        val settings = settings()
        val manager = LocalDataManager(database, settings, codec, clock)
        val repository = RoomScheduleRepository(database.scheduleDao(), clock)
        val existingId = repository.save(sampleBackup().schedules.first().copy(id = 0, title = "原有日程"))

        val result = manager.importBackup(sampleBackup(), ImportMode.MERGE)

        assertThat(result.importedCount).isEqualTo(2)
        assertThat(repository.get(existingId)?.title).isEqualTo("原有日程")
        assertThat(repository.observeAll().first()).hasSize(3)
        assertThat(settings.current()).isEqualTo(UserPreferences())
        val imported = repository.observeAll().first().first { it.title == "每周复盘" }
        assertThat(database.scheduleDao().getException(imported.id,
            sampleBackup().exceptions.single().occurrenceDate.toEpochDay())?.cancelled).isTrue()
    }

    @Test
    fun `merging the same backup twice skips identical schedules and exceptions`() = runTest {
        val manager = LocalDataManager(database, settings(), codec, clock)
        manager.importBackup(sampleBackup(), ImportMode.MERGE)

        val result = manager.importBackup(sampleBackup(), ImportMode.MERGE)

        assertThat(result.importedCount).isEqualTo(0)
        assertThat(result.skippedCount).isEqualTo(2)
        assertThat(RoomScheduleRepository(database.scheduleDao(), clock).observeAll().first()).hasSize(2)
    }

    @Test
    fun `replace hides old schedules and keeps cancellation tombstones until acknowledged`() = runTest {
        val settings = settings()
        val repository = RoomScheduleRepository(database.scheduleDao(), clock)
        val oldId = repository.save(sampleBackup().schedules.first().copy(id = 0, title = "待替换"))
        val oldJob = database.scheduleDao().getReminderJobs(oldId).single()
        database.scheduleDao().transitionReminderStatus(oldJob.id, ReminderOutboxStatus.PENDING,
            ReminderOutboxStatus.SCHEDULED_EXACT)
        val manager = LocalDataManager(database, settings, codec, clock)

        manager.importBackup(sampleBackup(), ImportMode.REPLACE)

        assertThat(repository.get(oldId)).isNull()
        assertThat(database.scheduleDao().getSchedule(oldId)).isNotNull()
        assertThat(database.scheduleDao().getReminderJob(oldJob.id)?.status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
        assertThat(settings.current()).isEqualTo(sampleBackup().preferences)
        assertThat(database.weatherDao().getSelectedCity()?.id).isEqualTo("hangzhou")
        database.scheduleDao().transitionReminderStatus(oldJob.id, ReminderOutboxStatus.CANCEL_PENDING,
            ReminderOutboxStatus.CANCELLED)
        database.scheduleDao().purgeCancelledSchedules()
        assertThat(database.scheduleDao().getSchedule(oldId)).isNull()
    }

    @Test
    fun `database failure rolls back replacement and creates no pending preferences`() = runTest {
        val settings = settings()
        val repository = RoomScheduleRepository(database.scheduleDao(), clock)
        val oldId = repository.save(sampleBackup().schedules.first().copy(id = 0, title = "保留"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_import BEFORE INSERT ON schedules BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )

        val error = runCatching {
            LocalDataManager(database, settings, codec, clock).importBackup(sampleBackup(), ImportMode.REPLACE)
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(repository.get(oldId)?.title).isEqualTo("保留")
        assertThat(settings.current()).isEqualTo(UserPreferences())
        assertThat(database.localDataDao().getPendingPreferences()).isNull()
        assertThat(database.weatherDao().getSavedCities()).isEmpty()
    }

    @Test
    fun `export preserves completed reminder offsets and cancellation exceptions`() = runTest {
        val manager = LocalDataManager(database, settings(), codec, clock)
        manager.importBackup(sampleBackup(), ImportMode.REPLACE)
        val repository = RoomScheduleRepository(database.scheduleDao(), clock)
        val recurring = repository.observeAll().first().first { it.recurrence != null }
        val job = database.scheduleDao().getReminderJobs(recurring.id).single()
        database.scheduleDao().transitionReminderStatus(job.id, ReminderOutboxStatus.PENDING, ReminderOutboxStatus.FIRED)

        val exported = codec.decode(manager.exportJson())

        assertThat(exported.schedules.first { it.recurrence != null }.reminderMinutesBefore).isEqualTo(15)
        assertThat(exported.exceptions.single().scheduleId).isEqualTo(recurring.id)
        assertThat(exported.preferences).isEqualTo(sampleBackup().preferences)
        assertThat(exported.cities).isEqualTo(sampleBackup().cities)
    }

    @Test
    fun `clear all resets preferences and cities while retaining uncancelled reminder records`() = runTest {
        val settings = settings()
        val manager = LocalDataManager(database, settings, codec, clock)
        manager.importBackup(sampleBackup(), ImportMode.REPLACE)
        val repository = RoomScheduleRepository(database.scheduleDao(), clock)
        val recurring = repository.observeAll().first().first { it.recurrence != null }

        manager.clearAll()

        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(database.weatherDao().getSavedCities()).isEmpty()
        assertThat(settings.current()).isEqualTo(UserPreferences())
        assertThat(database.scheduleDao().getReminderJobs(recurring.id).single().status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
    }

    @Test
    fun `clear keeps a fired job until its visible notification can be cancelled`() = runTest {
        val manager = LocalDataManager(database, settings(), codec, clock)
        manager.importBackup(sampleBackup(), ImportMode.REPLACE)
        val job = database.scheduleDao().getReminderJobsForReconciliation().single()
        database.scheduleDao().transitionReminderStatus(job.id, ReminderOutboxStatus.PENDING, ReminderOutboxStatus.FIRED)

        manager.clearAll()

        assertThat(database.scheduleDao().getReminderJob(job.id)?.status)
            .isEqualTo(ReminderOutboxStatus.CANCEL_PENDING)
    }

    @Test
    fun `pending preferences from an interrupted import are replayed on recovery`() = runTest {
        val settings = settings()
        database.localDataDao().savePendingPreferences(
            PendingPreferencesEntity(payloadJson = codec.encodePreferences(sampleBackup().preferences)),
        )
        val manager = LocalDataManager(database, settings, codec, clock)

        manager.recoverPendingPreferences()

        assertThat(settings.current()).isEqualTo(sampleBackup().preferences)
        assertThat(database.localDataDao().getPendingPreferences()).isNull()
    }

    private fun kotlinx.coroutines.test.TestScope.settings() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = {
            java.io.File(folder.root, "settings.preferences_pb")
        }),
    )
}
