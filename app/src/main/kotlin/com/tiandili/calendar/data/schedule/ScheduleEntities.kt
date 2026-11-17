package com.tiandili.calendar.data.schedule

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.ScheduleKind

@Entity(
    tableName = "schedules",
    indices = [
        Index(value = ["start_date_epoch_day", "end_date_epoch_day"]),
        Index(value = ["deleted_at_epoch_millis"]),
    ],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    @ColumnInfo(name = "all_day")
    val allDay: Boolean,
    @ColumnInfo(name = "start_date_epoch_day")
    val startDateEpochDay: Long,
    @ColumnInfo(name = "end_date_epoch_day")
    val endDateEpochDay: Long,
    @ColumnInfo(name = "start_minute_of_day")
    val startMinuteOfDay: Int?,
    @ColumnInfo(name = "end_minute_of_day")
    val endMinuteOfDay: Int?,
    @ColumnInfo(name = "zone_id")
    val zoneId: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis")
    val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "'SCHEDULE'")
    val kind: ScheduleKind = ScheduleKind.SCHEDULE,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val completed: Boolean = false,
    @ColumnInfo(name = "checklist_json", defaultValue = "'[]'")
    val checklistJson: String = "[]",
    @ColumnInfo(name = "media_uris_json", defaultValue = "'[]'")
    val mediaUrisJson: String = "[]",
    @ColumnInfo(name = "reminder_minutes_before")
    val reminderMinutesBefore: Int? = null,
)

@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecurrenceRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "schedule_id")
    val scheduleId: Long = 0,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    @ColumnInfo(name = "weekdays_mask")
    val weekdaysMask: Int = 0,
    @ColumnInfo(name = "until_epoch_day")
    val untilEpochDay: Long? = null,
    val count: Int? = null,
    @ColumnInfo(name = "lunar_month")
    val lunarMonth: Int? = null,
    @ColumnInfo(name = "lunar_day")
    val lunarDay: Int? = null,
)

@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["schedule_id", "occurrence_date_epoch_day"],
            unique = true,
        ),
    ],
)
data class ScheduleExceptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "schedule_id")
    val scheduleId: Long,
    @ColumnInfo(name = "occurrence_date_epoch_day")
    val occurrenceDateEpochDay: Long,
    val cancelled: Boolean,
)

enum class ReminderOutboxStatus {
    PENDING,
    SCHEDULED_EXACT,
    SCHEDULED_INEXACT,
    DISPATCHING,
    BLOCKED,
    CANCEL_PENDING,
    FIRED,
    CANCELLED,
    FAILED,
    SKIPPED,
}

@Entity(
    tableName = "reminder_outbox",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["schedule_id"]),
        Index(value = ["status", "trigger_at_epoch_millis"]),
        Index(
            value = [
                "schedule_id",
                "occurrence_epoch_millis",
                "trigger_at_epoch_millis",
            ],
            unique = true,
        ),
    ],
)
data class ReminderOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "schedule_id")
    val scheduleId: Long = 0,
    @ColumnInfo(name = "occurrence_epoch_millis")
    val occurrenceEpochMillis: Long,
    @ColumnInfo(name = "trigger_at_epoch_millis")
    val triggerAtEpochMillis: Long,
    val status: ReminderOutboxStatus,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

data class ReminderDeliveryRow(
    val jobId: Long,
    val scheduleId: Long,
    val title: String,
    val occurrenceEpochMillis: Long,
    val triggerAtEpochMillis: Long,
    val kind: ScheduleKind = ScheduleKind.SCHEDULE,
)

data class ScheduleDetails(
    @Embedded
    val schedule: ScheduleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "schedule_id",
    )
    val recurrenceRules: List<RecurrenceRuleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "schedule_id",
    )
    val reminderJobs: List<ReminderOutboxEntity>,
)

class ScheduleConverters {

    @TypeConverter
    fun scheduleKindToString(value: ScheduleKind): String = value.name

    @TypeConverter
    fun stringToScheduleKind(value: String): ScheduleKind = ScheduleKind.valueOf(value)

    @TypeConverter
    fun recurrenceFrequencyToString(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun stringToRecurrenceFrequency(value: String): RecurrenceFrequency =
        RecurrenceFrequency.valueOf(value)

    @TypeConverter
    fun reminderStatusToString(value: ReminderOutboxStatus): String = value.name

    @TypeConverter
    fun stringToReminderStatus(value: String): ReminderOutboxStatus =
        ReminderOutboxStatus.valueOf(value)
}
