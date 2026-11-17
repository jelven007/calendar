package com.tiandili.calendar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedules ADD COLUMN kind TEXT NOT NULL DEFAULT 'SCHEDULE'")
        db.execSQL("ALTER TABLE schedules ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE schedules ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE schedules ADD COLUMN checklist_json TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE schedules ADD COLUMN media_uris_json TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE schedules ADD COLUMN reminder_minutes_before INTEGER")
        db.execSQL("""UPDATE schedules SET reminder_minutes_before = (
            SELECT CAST((occurrence_epoch_millis - trigger_at_epoch_millis) / 60000 AS INTEGER)
            FROM reminder_outbox WHERE schedule_id = schedules.id
              AND status NOT IN ('CANCELLED', 'CANCEL_PENDING')
            ORDER BY id DESC LIMIT 1)""")
        db.execSQL("ALTER TABLE recurrence_rules ADD COLUMN lunar_month INTEGER")
        db.execSQL("ALTER TABLE recurrence_rules ADD COLUMN lunar_day INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `pending_preferences` (
                `id` INTEGER NOT NULL,
                `payload_json` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `weather_cities` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `admin1` TEXT,
                `country` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `zone_id` TEXT NOT NULL,
                `is_selected` INTEGER NOT NULL,
                `last_used_at_epoch_millis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weather_cities_is_selected`
            ON `weather_cities` (`is_selected`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weather_cities_last_used_at_epoch_millis`
            ON `weather_cities` (`last_used_at_epoch_millis`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `weather_cache_segments` (
                `city_id` TEXT NOT NULL,
                `segment_type` TEXT NOT NULL,
                `payload_json` TEXT NOT NULL,
                `fetched_at_epoch_millis` INTEGER NOT NULL,
                `expires_at_epoch_millis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`city_id`, `segment_type`),
                FOREIGN KEY(`city_id`) REFERENCES `weather_cities`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weather_cache_segments_expires_at_epoch_millis`
            ON `weather_cache_segments` (`expires_at_epoch_millis`)
            """.trimIndent(),
        )
    }
}
