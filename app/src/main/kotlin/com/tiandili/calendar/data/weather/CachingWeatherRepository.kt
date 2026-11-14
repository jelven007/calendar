package com.tiandili.calendar.data.weather

import com.tiandili.calendar.domain.weather.CacheFreshness
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachingWeatherRepository(
    private val store: WeatherStore,
    private val remote: WeatherRemoteDataSource,
    private val clock: Clock,
) : WeatherRepository {
    private val refreshMutex = Mutex()
    private var inFlightRefresh: CompletableDeferred<WeatherRefreshResult>? = null

    override fun observeWeather(): Flow<WeatherSnapshot?> = store.observeWeather()

    override suspend fun searchCities(query: String): List<City> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            emptyList()
        } else {
            remote.searchCities(normalized)
        }
    }

    override suspend fun savedCities(): List<City> = store.savedCities()

    override suspend fun selectCity(city: City): WeatherRefreshResult {
        store.selectCity(city, clock.instant())
        return refresh(force = true)
    }

    override suspend fun refresh(force: Boolean): WeatherRefreshResult {
        var isLeader = false
        val deferred = refreshMutex.withLock {
            inFlightRefresh?.takeIf { !it.isCompleted }
                ?: CompletableDeferred<WeatherRefreshResult>().also {
                    inFlightRefresh = it
                    isLeader = true
                }
        }
        if (!isLeader) {
            return deferred.await()
        }

        return try {
            val result = performRefresh(force)
            deferred.complete(result)
            result
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            refreshMutex.withLock {
                if (inFlightRefresh === deferred) {
                    inFlightRefresh = null
                }
            }
        }
    }

    private suspend fun performRefresh(force: Boolean): WeatherRefreshResult {
        val city = store.selectedCity()
            ?: return WeatherRefreshResult(noSelectedCity = true)
        val snapshot = store.currentSnapshot(city.id)
        val now = clock.instant()
        val refreshForecast = force || forecastNeedsRefresh(snapshot, now)
        val refreshAirQuality = force || segmentNeedsRefresh(
            snapshot?.airQuality,
            WeatherSegmentType.AIR_QUALITY,
            now,
        )
        if (!refreshForecast && !refreshAirQuality) {
            return WeatherRefreshResult(skippedAsFresh = true)
        }

        return coroutineScope {
            val forecastResult = if (refreshForecast) {
                async {
                    runCatching {
                        val forecast = remote.fetchForecast(city)
                        store.saveForecast(city, forecast, now)
                    }
                }
            } else {
                null
            }
            val airQualityResult = if (refreshAirQuality) {
                async {
                    runCatching {
                        val airQuality = remote.fetchAirQuality(city)
                        store.saveAirQuality(city, airQuality, now)
                    }
                }
            } else {
                null
            }

            buildResult(
                forecastResult = forecastResult?.await(),
                airQualityResult = airQualityResult?.await(),
            )
        }
    }

    private fun forecastNeedsRefresh(
        snapshot: WeatherSnapshot?,
        now: Instant,
    ): Boolean =
        segmentNeedsRefresh(snapshot?.current, WeatherSegmentType.CURRENT, now) ||
            segmentNeedsRefresh(snapshot?.hourly, WeatherSegmentType.HOURLY, now) ||
            segmentNeedsRefresh(snapshot?.daily, WeatherSegmentType.DAILY, now)

    private fun segmentNeedsRefresh(
        segment: CachedWeatherSegment<*>?,
        type: WeatherSegmentType,
        now: Instant,
    ): Boolean = segment == null ||
        WeatherCachePolicy.freshness(type, segment.fetchedAt, now) != CacheFreshness.FRESH

    private fun buildResult(
        forecastResult: Result<Unit>?,
        airQualityResult: Result<Unit>?,
    ): WeatherRefreshResult {
        val updated = buildSet {
            if (forecastResult?.isSuccess == true) add(WeatherRefreshGroup.FORECAST)
            if (airQualityResult?.isSuccess == true) add(WeatherRefreshGroup.AIR_QUALITY)
        }
        val failed = buildSet {
            if (forecastResult?.isFailure == true) add(WeatherRefreshGroup.FORECAST)
            if (airQualityResult?.isFailure == true) add(WeatherRefreshGroup.AIR_QUALITY)
        }
        return WeatherRefreshResult(
            updatedGroups = updated,
            failedGroups = failed,
        )
    }
}
