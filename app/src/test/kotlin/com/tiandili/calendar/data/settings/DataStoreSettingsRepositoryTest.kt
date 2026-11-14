package com.tiandili.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `new settings store exposes privacy preserving defaults`() = runTest {
        val repository = repository()

        assertThat(repository.current()).isEqualTo(
            UserPreferences(
                firstDayOfWeek = DayOfWeek.MONDAY,
                defaultReminderMinutes = null,
                themeMode = ThemeMode.SYSTEM,
                showScheduleTitleOnLockScreen = false,
                showNextScheduleInWidget = false,
            ),
        )
    }

    @Test
    fun `all user preferences survive a repository restart`() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val storeJob = SupervisorJob()
        val firstRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(storeJob + Dispatchers.IO),
                produceFile = { file },
            ),
        )
        val expected = UserPreferences(
            firstDayOfWeek = DayOfWeek.SUNDAY,
            defaultReminderMinutes = 30,
            themeMode = ThemeMode.DARK,
            showScheduleTitleOnLockScreen = true,
            showNextScheduleInWidget = true,
        )

        firstRepository.replace(expected)
        storeJob.cancelAndJoin()
        val reopenedRepository = repository(file)

        assertThat(reopenedRepository.current()).isEqualTo(expected)
    }

    @Test
    fun `negative default reminder is rejected without changing settings`() = runTest {
        val repository = repository()

        val error = runCatching {
            repository.setDefaultReminderMinutes(-1)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.current().defaultReminderMinutes).isNull()
    }

    @Test
    fun `unsupported week start is rejected without changing preferences`() = runTest {
        val repository = repository()

        val error = runCatching { repository.setFirstDayOfWeek(DayOfWeek.TUESDAY) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.current().firstDayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `reset emits defaults after saved preferences`() = runTest {
        val repository = repository()
        repository.replace(UserPreferences(themeMode = ThemeMode.DARK))

        repository.reset()

        assertThat(repository.preferences.first()).isEqualTo(UserPreferences())
    }

    private fun TestScope.repository(
        file: java.io.File = temporaryFolder.newFile("settings.preferences_pb"),
    ): DataStoreSettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        return DataStoreSettingsRepository(dataStore)
    }
}
