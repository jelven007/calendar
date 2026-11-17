package com.tiandili.calendar.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TiandiliDatabaseMigrationTest {

    @Test
    fun `full migration chain opens schema one as schema four`() {
        helper.createDatabase(TEST_DATABASE, 1).close()
        helper.runMigrationsAndValidate(TEST_DATABASE, 4, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).close()
    }

    @Test
    fun `migration three to four preserves legacy ids and backfills reminder offset`() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL("""INSERT INTO schedules VALUES
                (41, '保留的日程', '', 1, 20000, 20000, NULL, NULL, NULL, 1000, 1000, NULL)""")
            execSQL("""INSERT INTO reminder_outbox VALUES
                (1, 41, 2000000, 1100000, 'FIRED', 0, NULL, 1000)""")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4)
        db.query("""SELECT title, kind, enabled, completed, checklist_json, media_uris_json,
            reminder_minutes_before FROM schedules WHERE id = 41""").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("保留的日程")
            assertThat(it.getString(1)).isEqualTo("SCHEDULE")
            assertThat(it.getInt(2)).isEqualTo(1)
            assertThat(it.getInt(3)).isEqualTo(0)
            assertThat(it.getString(4)).isEqualTo("[]")
            assertThat(it.getString(5)).isEqualTo("[]")
            assertThat(it.getInt(6)).isEqualTo(15)
        }
        db.close()
    }

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation(),
        databaseClass = TiandiliDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun `migration one to two preserves schedules and creates weather cache`() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO schedules (
                    id, title, note, all_day, start_date_epoch_day,
                    end_date_epoch_day, start_minute_of_day, end_minute_of_day,
                    zone_id, created_at_epoch_millis, updated_at_epoch_millis,
                    deleted_at_epoch_millis
                ) VALUES (
                    41, '保留的日程', '', 1, 20000, 20000, NULL, NULL,
                    NULL, 1000, 1000, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query("SELECT title FROM schedules WHERE id = 41").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("保留的日程")
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'weather_cities'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>()
            .deleteDatabase(TEST_DATABASE)
    }

    private companion object {
        const val TEST_DATABASE = "tiandili-migration-test"
    }

    @Test
    fun `migration two to three preserves cities and adds recoverable settings journal`() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """INSERT INTO weather_cities VALUES (
                    'hangzhou', '杭州', '浙江', '中国', 30.2, 120.1, 'Asia/Shanghai', 1, 1000
                )""",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 3, true, MIGRATION_2_3)

        migrated.query("SELECT name FROM weather_cities WHERE id = 'hangzhou'").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("杭州")
        }
        migrated.execSQL("""INSERT INTO pending_preferences VALUES (1, '{}')""")
        migrated.query("SELECT payload_json FROM pending_preferences WHERE id = 1").use {
            assertThat(it.moveToFirst()).isTrue()
        }
        migrated.close()
    }
}
