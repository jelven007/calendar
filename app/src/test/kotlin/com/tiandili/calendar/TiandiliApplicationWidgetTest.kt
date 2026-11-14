package com.tiandili.calendar

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.platform.background.BackgroundTasksOwner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TiandiliApplication::class)
class TiandiliApplicationWidgetTest {
    @Test fun `application initializes persisted periodic background work`() {
        val app = ApplicationProvider.getApplicationContext<TiandiliApplication>()
        assertThat(app).isInstanceOf(BackgroundTasksOwner::class.java)
        val manager = WorkManager.getInstance(app)
        assertThat(manager.getWorkInfosForUniqueWork("weather_refresh").get()).hasSize(1)
        assertThat(manager.getWorkInfosForUniqueWork("reminder_compensation").get()).hasSize(1)
        assertThat(manager.getWorkInfosForUniqueWork("widget_fallback").get()).hasSize(1)
    }

    @Test fun `manifest registers both desktop widgets with provider metadata`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        listOf("CompactCalendarWidgetReceiver", "WideCalendarWidgetReceiver").forEach { name ->
            val component = ComponentName(app.packageName, "${app.packageName}.platform.widget.$name")
            val provider = runCatching { app.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA) }
                .getOrNull()
            assertThat(provider).isNotNull()
            assertThat(provider?.metaData?.getInt("android.appwidget.provider")).isGreaterThan(0)
        }
    }
}
