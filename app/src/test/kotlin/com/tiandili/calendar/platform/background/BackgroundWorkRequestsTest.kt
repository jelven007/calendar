package com.tiandili.calendar.platform.background

import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = BackgroundTestApplication::class)
class BackgroundWorkRequestsTest {
    @Test fun `weather repeats every three hours with connected network and exponential retry`() {
        val work = BackgroundWorkRequests.weather().workSpec
        assertThat(work.intervalDuration).isEqualTo(3 * 3_600_000L)
        assertThat(work.initialDelay).isEqualTo(3 * 3_600_000L)
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(work.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
    }

    @Test fun `reminder compensation is daily and can run offline`() {
        val work = BackgroundWorkRequests.reminder().workSpec
        assertThat(work.intervalDuration).isEqualTo(24 * 3_600_000L)
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
    }

    @Test fun `local widget fallback runs offline hourly`() {
        val work = BackgroundWorkRequests.widgetFallback().workSpec
        assertThat(work.intervalDuration).isEqualTo(3_600_000L)
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
    }

    @Test fun `midnight delay follows twenty three and twenty five hour DST days`() {
        val zone = ZoneId.of("America/New_York")
        assertThat(BackgroundWorkRequests.midnight(Instant.parse("2025-03-09T05:00:00Z"), zone)
            .workSpec.initialDelay).isEqualTo(23 * 3_600_000L)
        assertThat(BackgroundWorkRequests.midnight(Instant.parse("2025-11-02T04:00:00Z"), zone)
            .workSpec.initialDelay).isEqualTo(25 * 3_600_000L)
    }
}
