package com.tiandili.calendar.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.backup.BackupCodec
import com.tiandili.calendar.data.backup.ImportMode
import com.tiandili.calendar.data.backup.LocalDataManager
import com.tiandili.calendar.data.backup.MAX_BACKUP_BYTES
import com.tiandili.calendar.data.backup.sampleBackup
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.data.schedule.RoomScheduleRepository
import com.tiandili.calendar.data.settings.DataStoreSettingsRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: TiandiliDatabase
    private val codec = BackupCodec(Moshi.Builder().build())
    private val clock = Clock.systemUTC()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            TiandiliDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { database.close(); Dispatchers.resetMain() }

    @Test
    fun `reading a backup previews counts without writing schedules`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.readImport { ByteArrayInputStream(codec.encode(sampleBackup()).toByteArray()) }
        vm.uiState.first { !it.busy }

        assertThat(vm.uiState.value.preview?.schedules).hasSize(2)
        assertThat(RoomScheduleRepository(database.scheduleDao(), clock).observeAll().first()).isEmpty()
        vm.confirmImport(ImportMode.MERGE)
        vm.uiState.first { !it.busy }
        assertThat(vm.uiState.value.preview).isNull()
        assertThat(RoomScheduleRepository(database.scheduleDao(), clock).observeAll().first()).hasSize(2)
    }

    @Test
    fun `oversized file exposes an error and leaves no preview`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.readImport { ByteArrayInputStream(ByteArray(MAX_BACKUP_BYTES + 1) { ' '.code.toByte() }) }
        vm.uiState.first { !it.busy }

        assertThat(vm.uiState.value.preview).isNull()
        assertThat(vm.uiState.value.message).contains("5 MB")
        assertThat(vm.uiState.value.busy).isFalse()
    }

    @Test
    fun `export writes a readable JSON file to the selected stream`() = runTest(dispatcher) {
        val vm = viewModel()
        val output = ByteArrayOutputStream()

        vm.export { output }
        vm.uiState.first { !it.busy }

        assertThat(codec.decode(output.toString("UTF-8")).schedules).isEmpty()
        assertThat(vm.uiState.value.message).isEqualTo("备份已保存")
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(): SettingsViewModel {
        val settings = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(
            scope = backgroundScope, produceFile = { java.io.File(folder.root, "settings.preferences_pb") },
        ))
        return SettingsViewModel(settings, LocalDataManager(database, settings, codec, clock), dispatcher)
    }
}
