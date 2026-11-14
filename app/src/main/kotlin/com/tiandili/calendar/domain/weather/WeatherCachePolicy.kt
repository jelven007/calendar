package com.tiandili.calendar.domain.weather

import java.time.Duration
import java.time.Instant

enum class WeatherSegmentType {
    CURRENT,
    HOURLY,
    DAILY,
    AIR_QUALITY,
}

enum class CacheFreshness {
    FRESH,
    STALE,
    EXPIRED,
}

object WeatherCachePolicy {
    private val maximumOfflineAge = Duration.ofHours(72)

    fun freshness(
        type: WeatherSegmentType,
        fetchedAt: Instant,
        now: Instant,
    ): CacheFreshness {
        val age = Duration.between(fetchedAt, now)
        if (age >= maximumOfflineAge) {
            return CacheFreshness.EXPIRED
        }
        return if (age < freshnessDuration(type)) {
            CacheFreshness.FRESH
        } else {
            CacheFreshness.STALE
        }
    }

    fun freshnessDuration(type: WeatherSegmentType): Duration = when (type) {
        WeatherSegmentType.CURRENT,
        WeatherSegmentType.HOURLY,
        -> Duration.ofMinutes(30)
        WeatherSegmentType.DAILY -> Duration.ofHours(6)
        WeatherSegmentType.AIR_QUALITY -> Duration.ofMinutes(60)
    }
}
