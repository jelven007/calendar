package com.tiandili.calendar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class CalendarAccent { RED, JADE, BLUE }

data class UserPreferences(
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val defaultReminderMinutes: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showScheduleTitleOnLockScreen: Boolean = false,
    val showNextScheduleInWidget: Boolean = false,
    val accent: CalendarAccent = CalendarAccent.RED,
    val nickname: String = "历友",
) {
    init {
        require(nickname.isNotBlank() && nickname.length <= 30) { "昵称需要 1–30 个字" }
        require(firstDayOfWeek == DayOfWeek.MONDAY || firstDayOfWeek == DayOfWeek.SUNDAY) {
            "Week must start on Monday or Sunday"
        }
        require(defaultReminderMinutes == null || defaultReminderMinutes in 0..525_600) {
            "Default reminder is out of range"
        }
    }
}

interface SettingsRepository {
    val preferences: Flow<UserPreferences>

    suspend fun current(): UserPreferences

    suspend fun replace(preferences: UserPreferences)

    suspend fun setFirstDayOfWeek(dayOfWeek: DayOfWeek)

    suspend fun setDefaultReminderMinutes(minutes: Int?)

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setShowScheduleTitleOnLockScreen(show: Boolean)

    suspend fun setShowNextScheduleInWidget(show: Boolean)

    suspend fun reset()

    suspend fun setAccent(accent: CalendarAccent) = replace(current().copy(accent = accent))

    suspend fun setNickname(nickname: String) = replace(current().copy(nickname = nickname))
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toUserPreferences() }

    override suspend fun current(): UserPreferences = preferences.first()

    override suspend fun replace(preferences: UserPreferences) {
        dataStore.edit { stored ->
            stored[FIRST_DAY_OF_WEEK] = preferences.firstDayOfWeek.name
            preferences.defaultReminderMinutes?.let {
                stored[DEFAULT_REMINDER_MINUTES] = it
            } ?: stored.remove(DEFAULT_REMINDER_MINUTES)
            stored[THEME_MODE] = preferences.themeMode.name
            stored[SHOW_SCHEDULE_TITLE_ON_LOCK_SCREEN] =
                preferences.showScheduleTitleOnLockScreen
            stored[SHOW_NEXT_SCHEDULE_IN_WIDGET] = preferences.showNextScheduleInWidget
            stored[ACCENT] = preferences.accent.name
            stored[NICKNAME] = preferences.nickname
        }
    }

    override suspend fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        require(dayOfWeek == DayOfWeek.MONDAY || dayOfWeek == DayOfWeek.SUNDAY)
        dataStore.edit { it[FIRST_DAY_OF_WEEK] = dayOfWeek.name }
    }

    override suspend fun setDefaultReminderMinutes(minutes: Int?) {
        require(minutes == null || minutes in 0..525_600) {
            "Default reminder is out of range"
        }
        dataStore.edit { stored ->
            minutes?.let {
                stored[DEFAULT_REMINDER_MINUTES] = it
            } ?: stored.remove(DEFAULT_REMINDER_MINUTES)
        }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    override suspend fun setShowScheduleTitleOnLockScreen(show: Boolean) {
        dataStore.edit { it[SHOW_SCHEDULE_TITLE_ON_LOCK_SCREEN] = show }
    }

    override suspend fun setShowNextScheduleInWidget(show: Boolean) {
        dataStore.edit { it[SHOW_NEXT_SCHEDULE_IN_WIDGET] = show }
    }

    override suspend fun setAccent(accent: CalendarAccent) {
        dataStore.edit { it[ACCENT] = accent.name }
    }

    override suspend fun setNickname(nickname: String) {
        require(nickname.isNotBlank() && nickname.length <= 30) { "昵称需要 1–30 个字" }
        dataStore.edit { it[NICKNAME] = nickname }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        firstDayOfWeek = this[FIRST_DAY_OF_WEEK]
            ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?.takeIf { it == DayOfWeek.MONDAY || it == DayOfWeek.SUNDAY }
            ?: DayOfWeek.SUNDAY,
        defaultReminderMinutes = this[DEFAULT_REMINDER_MINUTES]
            ?.takeIf { it in 0..525_600 },
        themeMode = this[THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        showScheduleTitleOnLockScreen =
            this[SHOW_SCHEDULE_TITLE_ON_LOCK_SCREEN] ?: false,
        showNextScheduleInWidget = this[SHOW_NEXT_SCHEDULE_IN_WIDGET] ?: false,
        accent = this[ACCENT]?.let { runCatching { CalendarAccent.valueOf(it) }.getOrNull() }
            ?: CalendarAccent.RED,
        nickname = this[NICKNAME]?.takeIf { it.isNotBlank() && it.length <= 30 } ?: "历友",
    )

    private companion object {
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val DEFAULT_REMINDER_MINUTES = intPreferencesKey("default_reminder_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("calendar_accent")
        val NICKNAME = stringPreferencesKey("nickname")
        val SHOW_SCHEDULE_TITLE_ON_LOCK_SCREEN =
            booleanPreferencesKey("show_schedule_title_on_lock_screen")
        val SHOW_NEXT_SCHEDULE_IN_WIDGET =
            booleanPreferencesKey("show_next_schedule_in_widget")
    }
}
