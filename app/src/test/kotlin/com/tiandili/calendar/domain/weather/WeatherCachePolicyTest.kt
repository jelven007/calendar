package com.tiandili.calendar.domain.weather

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

class WeatherCachePolicyTest {

    private val fetchedAt = Instant.parse("2025-01-02T00:00:00Z")

    @Test
    fun `current and hourly segments are fresh for thirty minutes`() {
        val justBeforeExpiry = fetchedAt.plus(Duration.ofMinutes(30)).minusMillis(1)

        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.CURRENT,
                fetchedAt,
                justBeforeExpiry,
            ),
        ).isEqualTo(CacheFreshness.FRESH)
        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.HOURLY,
                fetchedAt,
                fetchedAt.plus(Duration.ofMinutes(30)),
            ),
        ).isEqualTo(CacheFreshness.STALE)
    }

    @Test
    fun `daily and air quality segments use their own freshness windows`() {
        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.DAILY,
                fetchedAt,
                fetchedAt.plus(Duration.ofHours(5)),
            ),
        ).isEqualTo(CacheFreshness.FRESH)
        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.DAILY,
                fetchedAt,
                fetchedAt.plus(Duration.ofHours(6)),
            ),
        ).isEqualTo(CacheFreshness.STALE)
        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.AIR_QUALITY,
                fetchedAt,
                fetchedAt.plus(Duration.ofMinutes(60)),
            ),
        ).isEqualTo(CacheFreshness.STALE)
    }

    @Test
    fun `cache older than seventy two hours is expired`() {
        assertThat(
            WeatherCachePolicy.freshness(
                WeatherSegmentType.CURRENT,
                fetchedAt,
                fetchedAt.plus(Duration.ofHours(72)),
            ),
        ).isEqualTo(CacheFreshness.EXPIRED)
    }
}
