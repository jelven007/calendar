package com.tiandili.calendar.platform.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = WidgetRecoveryTestApplication::class)
class WidgetRecoveryReceiverTest {
    @Test fun `system date and reboot events rebuild widget and midnight plan`() {
        val app = ApplicationProvider.getApplicationContext<WidgetRecoveryTestApplication>()
        val receiver = WidgetRecoveryReceiver()
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach {
            receiver.onReceive(app, Intent(it))
        }
        assertThat(app.requests).containsExactly(true, true, true, true)
    }

    @Test fun `unrelated broadcast cannot trigger refresh`() {
        val app = ApplicationProvider.getApplicationContext<WidgetRecoveryTestApplication>()
        WidgetRecoveryReceiver().onReceive(app, Intent("unrelated"))
        assertThat(app.requests).isEmpty()
    }
}

class WidgetRecoveryTestApplication : Application(), WidgetRefreshOwner {
    val requests = mutableListOf<Boolean>()
    override fun requestWidgetRefresh(resetMidnight: Boolean) { requests += resetMidnight }
}
