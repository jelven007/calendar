package com.tiandili.calendar.data.backup

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.weather.City
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec(Moshi.Builder().build())

    @Test
    fun `version one round trip preserves schedules rules exceptions cities and settings`() {
        val expected = sampleBackup()

        val json = codec.encode(expected)
        val actual = codec.decode(json)

        assertThat(actual).isEqualTo(expected)
        assertThat(json).contains("\"formatVersion\": 1")
        assertThat(json).contains("com.tiandili.calendar")
        assertThat(json).doesNotContain("reminder_outbox")
    }

    @Test
    fun `future version is rejected before importing anything`() {
        val json = codec.encode(sampleBackup()).replace("\"formatVersion\": 1", "\"formatVersion\": 2")

        assertInvalid(json)
    }

    @Test
    fun `foreign application backup is rejected`() {
        assertInvalid(codec.encode(sampleBackup()).replace("com.tiandili.calendar", "other.application"))
    }

    @Test
    fun `duplicate ids cannot ambiguously associate exceptions`() {
        val backup = sampleBackup()
        val error = runCatching {
            codec.encode(backup.copy(schedules = backup.schedules + backup.schedules.first()))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupFormatException::class.java)
    }

    @Test
    fun `malformed dates are rejected with a readable format error`() {
        assertInvalid(codec.encode(sampleBackup()).replace("2026-11-08", "2026-02-30"))
    }

    @Test
    fun `invalid reminder offset is rejected`() {
        assertInvalid(codec.encode(sampleBackup()).replace("\"reminderMinutesBefore\": 15", "\"reminderMinutesBefore\": -1"))
    }

    @Test
    fun `out of range calendar dates are rejected`() {
        assertInvalid(codec.encode(sampleBackup()).replace("2026-11-08", "2200-11-08"))
    }

    @Test
    fun `exception must reference an included series`() {
        val error = runCatching {
            codec.encode(sampleBackup().copy(
                exceptions = listOf(BackupException(999, LocalDate.of(2026, 11, 15), true)),
            ))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupFormatException::class.java)
    }

    @Test
    fun `export refuses a backup too large for the importer`() {
        val backup = sampleBackup()
        val largeBackup = backup.copy(
            schedules = (1L..6000L).map {
                backup.schedules.last().copy(id = it, note = "x".repeat(1000))
            },
            exceptions = emptyList(),
        )

        assertThat(runCatching { codec.encode(largeBackup) }.exceptionOrNull())
            .isInstanceOf(BackupFormatException::class.java)
    }

    @Test
    fun `trailing JSON and unknown fields are rejected`() {
        assertInvalid(codec.encode(sampleBackup()) + "{}")
        assertInvalid(codec.encode(sampleBackup()).replace("\"formatVersion\": 1", "\"unexpected\": true, \"formatVersion\": 1"))
    }

    private fun assertInvalid(json: String) {
        assertThat(runCatching { codec.decode(json) }.exceptionOrNull())
            .isInstanceOf(BackupFormatException::class.java)
    }
}

internal fun sampleBackup() = BackupSnapshot(
    exportedAt = Instant.parse("2026-11-08T01:00:00Z"),
    schedules = listOf(
        Schedule(
            id = 41,
            title = "每周复盘",
            note = "中文备注",
            timing = ScheduleTiming.Timed(
                LocalDateTime.of(2026, 11, 8, 9, 0),
                LocalDateTime.of(2026, 11, 8, 10, 0),
                ZoneId.of("Asia/Shanghai"),
            ),
            recurrence = RecurrenceRule(
                RecurrenceFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.SUNDAY),
                count = 8,
            ),
            reminderMinutesBefore = 15,
        ),
        Schedule(
            id = 42,
            title = "纪念日",
            timing = ScheduleTiming.AllDay(
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 9),
            ),
        ),
    ),
    exceptions = listOf(BackupException(41, LocalDate.of(2026, 11, 15), true)),
    preferences = UserPreferences(
        firstDayOfWeek = DayOfWeek.SUNDAY,
        themeMode = ThemeMode.DARK,
        showNextScheduleInWidget = true,
    ),
    cities = listOf(
        City("hangzhou", "杭州", "浙江", "中国", 30.2, 120.1, ZoneId.of("Asia/Shanghai")),
    ),
    selectedCityId = "hangzhou",
)
