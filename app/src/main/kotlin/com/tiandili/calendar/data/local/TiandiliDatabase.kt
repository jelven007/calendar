package com.tiandili.calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tiandili.calendar.data.backup.LocalDataDao
import com.tiandili.calendar.data.backup.PendingPreferencesEntity
import com.tiandili.calendar.data.schedule.RecurrenceRuleEntity
import com.tiandili.calendar.data.schedule.ReminderOutboxEntity
import com.tiandili.calendar.data.schedule.ScheduleConverters
import com.tiandili.calendar.data.schedule.ScheduleDao
import com.tiandili.calendar.data.schedule.ScheduleEntity
import com.tiandili.calendar.data.schedule.ScheduleExceptionEntity
import com.tiandili.calendar.data.weather.WeatherCacheSegmentEntity
import com.tiandili.calendar.data.weather.WeatherCityEntity
import com.tiandili.calendar.data.weather.WeatherConverters
import com.tiandili.calendar.data.weather.WeatherDao

@Database(
    entities = [
        ScheduleEntity::class,
        RecurrenceRuleEntity::class,
        ScheduleExceptionEntity::class,
        ReminderOutboxEntity::class,
        WeatherCityEntity::class,
        WeatherCacheSegmentEntity::class,
        PendingPreferencesEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ScheduleConverters::class, WeatherConverters::class)
abstract class TiandiliDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    abstract fun weatherDao(): WeatherDao

    abstract fun localDataDao(): LocalDataDao
}
