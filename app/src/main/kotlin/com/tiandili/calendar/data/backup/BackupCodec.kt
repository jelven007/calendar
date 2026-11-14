package com.tiandili.calendar.data.backup

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.RecurrenceRule
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.schedule.ScheduleValidator
import com.tiandili.calendar.domain.weather.City
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import okio.Buffer

const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
private const val BACKUP_APP_ID = "com.tiandili.calendar"
private val MIN_BACKUP_DATE = LocalDate.of(1901, 1, 1)
private val MAX_BACKUP_DATE = LocalDate.of(2099, 12, 31)

data class BackupException(
    val scheduleId: Long,
    val occurrenceDate: LocalDate,
    val cancelled: Boolean,
)

data class BackupSnapshot(
    val exportedAt: Instant,
    val schedules: List<Schedule>,
    val exceptions: List<BackupException>,
    val preferences: UserPreferences,
    val cities: List<City>,
    val selectedCityId: String?,
)

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupCodec(moshi: Moshi) {
    private val adapter = moshi.adapter(BackupDocument::class.java).failOnUnknown()
    private val preferencesAdapter = moshi.adapter(BackupPreferences::class.java).failOnUnknown()

    fun encode(snapshot: BackupSnapshot): String = formatChecked {
        validate(snapshot)
        adapter.indent("  ").serializeNulls().toJson(snapshot.toDocument()).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "备份文件超过 5 MB" }
        }
    }

    fun decode(json: String): BackupSnapshot = formatChecked {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "备份文件超过 5 MB" }
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val document = requireNotNull(adapter.fromJson(reader)) { "备份内容为空" }
        require(reader.peek() == JsonReader.Token.END_DOCUMENT) { "备份文件包含多余内容" }
        require(document.appId == BACKUP_APP_ID) { "不是天地历备份文件" }
        require(document.formatVersion == 1) { "不支持的备份版本：${document.formatVersion}" }
        document.toSnapshot().also(::validate)
    }

    internal fun encodePreferences(preferences: UserPreferences): String =
        preferencesAdapter.serializeNulls().toJson(preferences.toBackupPreferences())

    internal fun decodePreferences(json: String): UserPreferences = formatChecked {
        requireNotNull(preferencesAdapter.fromJson(json)).toPreferences()
    }

    private fun validate(snapshot: BackupSnapshot) {
        require(snapshot.schedules.size <= 10_000) { "最多导入 10000 个日程" }
        require(snapshot.exceptions.size <= 50_000) { "取消记录数量过多" }
        val ids = snapshot.schedules.map(Schedule::id)
        require(ids.all { it > 0 } && ids.distinct().size == ids.size) { "日程 ID 无效或重复" }
        snapshot.schedules.forEach { schedule ->
            require(ScheduleValidator.validate(schedule).isEmpty()) { "日程标题、时间或备注无效" }
            val dates = when (val timing = schedule.timing) {
                is ScheduleTiming.AllDay -> timing.startDate to timing.endDateExclusive.minusDays(1)
                is ScheduleTiming.Timed -> {
                    require(
                        timing.startsAt.second == 0 && timing.startsAt.nano == 0 &&
                            timing.endsAt.second == 0 && timing.endsAt.nano == 0,
                    ) { "日程时间必须精确到分钟" }
                    timing.startsAt.toLocalDate() to timing.endsAt.toLocalDate()
                }
            }
            require(dates.first in MIN_BACKUP_DATE..MAX_BACKUP_DATE &&
                dates.second in MIN_BACKUP_DATE..MAX_BACKUP_DATE) { "日程日期超出 1901–2099 年" }
            require(schedule.reminderMinutesBefore == null ||
                schedule.reminderMinutesBefore in 0..525_600) { "提醒时间无效" }
            schedule.recurrence?.let { rule ->
                require(rule.interval in 1..1000 && (rule.count == null || rule.count in 1..100_000)) {
                    "重复规则无效"
                }
                require(rule.until == null || rule.until in dates.first..MAX_BACKUP_DATE) {
                    "重复结束日期无效"
                }
            }
        }
        val schedulesById = snapshot.schedules.associateBy(Schedule::id)
        require(snapshot.exceptions.map { it.scheduleId to it.occurrenceDate }.distinct().size ==
            snapshot.exceptions.size) { "取消记录重复" }
        snapshot.exceptions.forEach { exception ->
            require(schedulesById[exception.scheduleId]?.recurrence != null) { "取消记录缺少所属重复日程" }
            require(exception.occurrenceDate in MIN_BACKUP_DATE..MAX_BACKUP_DATE) { "取消日期无效" }
        }
        require(snapshot.preferences.firstDayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            "周起始日仅支持周一或周日"
        }
        require(snapshot.preferences.defaultReminderMinutes == null ||
            snapshot.preferences.defaultReminderMinutes in 0..525_600) { "默认提醒无效" }
        require(snapshot.cities.size <= 10) { "最多保存 10 个城市" }
        require(snapshot.cities.map(City::id).distinct().size == snapshot.cities.size) { "城市 ID 重复" }
        snapshot.cities.forEach { city ->
            require(city.id.isNotBlank() && city.id.length <= 200 &&
                city.name.isNotBlank() && city.name.length <= 200 &&
                city.country.isNotBlank() && city.country.length <= 200 &&
                (city.admin1 == null || city.admin1.length <= 200) &&
                city.latitude.isFinite() && city.latitude in -90.0..90.0 &&
                city.longitude.isFinite() && city.longitude in -180.0..180.0) { "城市信息无效" }
        }
        require(
            (snapshot.cities.isEmpty() && snapshot.selectedCityId == null) ||
                snapshot.cities.any { it.id == snapshot.selectedCityId },
        ) { "当前城市无效" }
    }
}

private inline fun <T> formatChecked(block: () -> T): T = try {
    block()
} catch (error: BackupFormatException) {
    throw error
} catch (error: Exception) {
    throw BackupFormatException("备份格式无效：${error.message ?: "无法读取内容"}", error)
}

@JsonClass(generateAdapter = true)
internal data class BackupDocument(
    val appId: String,
    val formatVersion: Int,
    val exportedAt: String,
    val schedules: List<BackupSchedule>,
    val exceptions: List<BackupExceptionRecord>,
    val preferences: BackupPreferences,
    val cities: List<BackupCity>,
    val selectedCityId: String?,
)

@JsonClass(generateAdapter = true)
internal data class BackupSchedule(
    val id: Long,
    val title: String,
    val note: String,
    val allDay: Boolean,
    val start: String,
    val end: String,
    val zoneId: String?,
    val recurrence: BackupRecurrence?,
    val reminderMinutesBefore: Int?,
)

@JsonClass(generateAdapter = true)
internal data class BackupRecurrence(
    val frequency: String,
    val interval: Int,
    val weekdays: List<Int>,
    val until: String?,
    val count: Int?,
)

@JsonClass(generateAdapter = true)
internal data class BackupExceptionRecord(
    val scheduleId: Long,
    val occurrenceDate: String,
    val cancelled: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class BackupPreferences(
    val firstDayOfWeek: String,
    val defaultReminderMinutes: Int?,
    val themeMode: String,
    val showScheduleTitleOnLockScreen: Boolean,
    val showNextScheduleInWidget: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class BackupCity(
    val id: String,
    val name: String,
    val admin1: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
)

private fun BackupSnapshot.toDocument() = BackupDocument(
    appId = BACKUP_APP_ID,
    formatVersion = 1,
    exportedAt = exportedAt.toString(),
    schedules = schedules.map { schedule ->
        val timing = schedule.timing
        BackupSchedule(
            id = schedule.id,
            title = schedule.title,
            note = schedule.note,
            allDay = timing is ScheduleTiming.AllDay,
            start = when (timing) {
                is ScheduleTiming.AllDay -> timing.startDate.toString()
                is ScheduleTiming.Timed -> timing.startsAt.toString()
            },
            end = when (timing) {
                is ScheduleTiming.AllDay -> timing.endDateExclusive.toString()
                is ScheduleTiming.Timed -> timing.endsAt.toString()
            },
            zoneId = (timing as? ScheduleTiming.Timed)?.zoneId?.id,
            recurrence = schedule.recurrence?.let {
                BackupRecurrence(it.frequency.name, it.interval, it.weekdays.map(DayOfWeek::getValue).sorted(),
                    it.until?.toString(), it.count)
            },
            reminderMinutesBefore = schedule.reminderMinutesBefore,
        )
    },
    exceptions = exceptions.map { BackupExceptionRecord(it.scheduleId, it.occurrenceDate.toString(), it.cancelled) },
    preferences = preferences.toBackupPreferences(),
    cities = cities.map { BackupCity(it.id, it.name, it.admin1, it.country, it.latitude, it.longitude, it.zoneId.id) },
    selectedCityId = selectedCityId,
)

private fun BackupDocument.toSnapshot() = BackupSnapshot(
    exportedAt = Instant.parse(exportedAt),
    schedules = schedules.map { schedule ->
        Schedule(
            id = schedule.id,
            title = schedule.title,
            note = schedule.note,
            timing = if (schedule.allDay) {
                require(schedule.zoneId == null) { "全天日程不能包含时区" }
                ScheduleTiming.AllDay(LocalDate.parse(schedule.start), LocalDate.parse(schedule.end))
            } else {
                ScheduleTiming.Timed(LocalDateTime.parse(schedule.start), LocalDateTime.parse(schedule.end),
                    ZoneId.of(requireNotNull(schedule.zoneId)))
            },
            recurrence = schedule.recurrence?.let {
                require(it.weekdays.distinct().size == it.weekdays.size) { "重复星期无效" }
                RecurrenceRule(RecurrenceFrequency.valueOf(it.frequency), it.interval,
                    it.weekdays.mapTo(mutableSetOf(), DayOfWeek::of), it.until?.let(LocalDate::parse), it.count)
            },
            reminderMinutesBefore = schedule.reminderMinutesBefore,
        )
    },
    exceptions = exceptions.map { BackupException(it.scheduleId, LocalDate.parse(it.occurrenceDate), it.cancelled) },
    preferences = preferences.toPreferences(),
    cities = cities.map { City(it.id, it.name, it.admin1, it.country, it.latitude, it.longitude, ZoneId.of(it.zoneId)) },
    selectedCityId = selectedCityId,
)

private fun UserPreferences.toBackupPreferences() = BackupPreferences(
    firstDayOfWeek.name, defaultReminderMinutes, themeMode.name,
    showScheduleTitleOnLockScreen, showNextScheduleInWidget,
)

private fun BackupPreferences.toPreferences() = UserPreferences(
    DayOfWeek.valueOf(firstDayOfWeek), defaultReminderMinutes, ThemeMode.valueOf(themeMode),
    showScheduleTitleOnLockScreen, showNextScheduleInWidget,
)
