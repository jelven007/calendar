package com.tiandili.calendar.data.weather

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.ForecastBundle
import com.tiandili.calendar.domain.weather.HourlyForecast
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CachingWeatherRepositoryTest {

    private val now = Instant.parse("2025-01-02T05:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val city = City(
        id = "open-meteo:1808926",
        name = "杭州市",
        admin1 = "浙江",
        country = "中国",
        latitude = 30.29365,
        longitude = 120.16142,
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun `fresh cache skips automatic network refresh`() = runTest {
        val store = FakeWeatherStore(snapshot(fetchedAt = now.minusSeconds(10 * 60)))
        val remote = FakeWeatherRemote()
        val repository = CachingWeatherRepository(store, remote, clock)

        val result = repository.refresh(force = false)

        assertThat(result.skippedAsFresh).isTrue()
        assertThat(remote.forecastCalls).isEqualTo(0)
        assertThat(remote.airQualityCalls).isEqualTo(0)
    }

    @Test
    fun `stale forecast refreshes without replacing fresh air quality`() = runTest {
        val oldSnapshot = snapshot(
            fetchedAt = now.minusSeconds(7 * 60 * 60),
            airFetchedAt = now.minusSeconds(10 * 60),
        )
        val store = FakeWeatherStore(oldSnapshot)
        val remote = FakeWeatherRemote()
        val repository = CachingWeatherRepository(store, remote, clock)

        val result = repository.refresh(force = false)

        assertThat(result.updatedGroups).containsExactly(WeatherRefreshGroup.FORECAST)
        assertThat(remote.forecastCalls).isEqualTo(1)
        assertThat(remote.airQualityCalls).isEqualTo(0)
        assertThat(store.snapshot.value?.current?.fetchedAt).isEqualTo(now)
        assertThat(store.snapshot.value?.airQuality).isEqualTo(oldSnapshot.airQuality)
    }

    @Test
    fun `forecast failure keeps old cache while air quality succeeds`() = runTest {
        val oldSnapshot = snapshot(fetchedAt = now.minusSeconds(7 * 60 * 60))
        val store = FakeWeatherStore(oldSnapshot)
        val remote = FakeWeatherRemote(
            forecastFailure = IllegalStateException("forecast unavailable"),
        )
        val repository = CachingWeatherRepository(store, remote, clock)

        val result = repository.refresh(force = true)

        assertThat(result.updatedGroups).containsExactly(WeatherRefreshGroup.AIR_QUALITY)
        assertThat(result.failedGroups).containsExactly(WeatherRefreshGroup.FORECAST)
        assertThat(store.snapshot.value?.current).isEqualTo(oldSnapshot.current)
        assertThat(store.snapshot.value?.airQuality?.fetchedAt).isEqualTo(now)
    }

    @Test
    fun `selecting a city persists it before fetching weather`() = runTest {
        val store = FakeWeatherStore()
        val remote = FakeWeatherRemote()
        val repository = CachingWeatherRepository(store, remote, clock)

        repository.selectCity(city)

        assertThat(store.selectedCities).containsExactly(city)
        assertThat(store.operationOrder.first()).isEqualTo("select")
        assertThat(store.operationOrder).containsAtLeast("forecast", "air")
        assertThat(store.snapshot.value?.city).isEqualTo(city)
    }

    @Test
    fun `concurrent refreshes share one in flight request`() = runTest {
        val store = FakeWeatherStore(snapshot(fetchedAt = now.minusSeconds(7 * 60 * 60)))
        val forecastGate = CompletableDeferred<Unit>()
        val remote = FakeWeatherRemote(forecastGate = forecastGate)
        val repository = CachingWeatherRepository(store, remote, clock)

        val first = async { repository.refresh(force = true) }
        val second = async { repository.refresh(force = true) }
        testScheduler.runCurrent()
        assertThat(remote.forecastCalls).isEqualTo(1)

        forecastGate.complete(Unit)
        assertThat(first.await()).isEqualTo(second.await())
        assertThat(remote.forecastCalls).isEqualTo(1)
        assertThat(remote.airQualityCalls).isEqualTo(1)
    }

    @Test
    fun `city search delegates without changing the selected city`() = runTest {
        val store = FakeWeatherStore(snapshot(fetchedAt = now))
        val remote = FakeWeatherRemote(searchResults = listOf(city.copy(id = "search-result")))
        val repository = CachingWeatherRepository(store, remote, clock)

        val results = repository.searchCities("杭州")

        assertThat(results.map(City::id)).containsExactly("search-result")
        assertThat(store.selectedCities).isEmpty()
        assertThat(store.snapshot.value?.city).isEqualTo(city)
    }

    private fun snapshot(
        fetchedAt: Instant,
        airFetchedAt: Instant = fetchedAt,
    ) = WeatherSnapshot(
        city = city,
        current = segment(
            sampleForecast().current,
            WeatherSegmentType.CURRENT,
            fetchedAt,
        ),
        hourly = segment(
            sampleForecast().hourly,
            WeatherSegmentType.HOURLY,
            fetchedAt,
        ),
        daily = segment(
            sampleForecast().daily,
            WeatherSegmentType.DAILY,
            fetchedAt,
        ),
        airQuality = segment(
            sampleAirQuality(),
            WeatherSegmentType.AIR_QUALITY,
            airFetchedAt,
        ),
    )

    private fun <T> segment(
        value: T,
        type: WeatherSegmentType,
        fetchedAt: Instant,
    ) = CachedWeatherSegment(
        value = value,
        fetchedAt = fetchedAt,
        expiresAt = fetchedAt.plus(WeatherCachePolicy.freshnessDuration(type)),
    )

    private fun sampleForecast() = ForecastBundle(
        current = CurrentWeather(
            observedAt = now.atZone(city.zoneId),
            temperatureC = 8.4,
            apparentTemperatureC = 7.1,
            relativeHumidityPercent = 64,
            precipitationMm = 0.0,
            weatherCode = 2,
            windSpeedKph = 8.7,
            isDay = true,
        ),
        hourly = listOf(
            HourlyForecast(
                time = now.atZone(city.zoneId),
                temperatureC = 8.4,
                precipitationProbabilityPercent = 10,
                weatherCode = 2,
                windSpeedKph = 8.7,
            ),
        ),
        daily = listOf(
            DailyForecast(
                date = LocalDate.of(2025, 1, 2),
                weatherCode = 2,
                minTemperatureC = 3.0,
                maxTemperatureC = 10.0,
                precipitationProbabilityPercent = 20,
                sunrise = null,
                sunset = null,
            ),
        ),
    )

    private fun sampleAirQuality() = AirQuality(
        observedAt = now.atZone(city.zoneId),
        usAqi = 42,
        pm25MicrogramsPerCubicMeter = 12.3,
        pm10MicrogramsPerCubicMeter = 20.4,
        ozoneMicrogramsPerCubicMeter = 55.1,
        nitrogenDioxideMicrogramsPerCubicMeter = 18.2,
    )

    private inner class FakeWeatherStore(
        initialSnapshot: WeatherSnapshot? = null,
    ) : WeatherStore {
        val snapshot = MutableStateFlow(initialSnapshot)
        val selectedCities = mutableListOf<City>()
        val operationOrder = mutableListOf<String>()

        override fun observeWeather(): Flow<WeatherSnapshot?> = snapshot

        override suspend fun selectedCity(): City? = snapshot.value?.city

        override suspend fun currentSnapshot(cityId: String): WeatherSnapshot? =
            snapshot.value?.takeIf { it.city.id == cityId }

        override suspend fun selectCity(city: City, selectedAt: Instant) {
            selectedCities += city
            operationOrder += "select"
            snapshot.value = WeatherSnapshot(city = city)
        }

        override suspend fun savedCities(): List<City> = selectedCities

        override suspend fun saveForecast(
            city: City,
            forecast: ForecastBundle,
            fetchedAt: Instant,
        ) {
            operationOrder += "forecast"
            val previous = snapshot.value
            snapshot.value = WeatherSnapshot(
                city = city,
                current = segment(forecast.current, WeatherSegmentType.CURRENT, fetchedAt),
                hourly = segment(forecast.hourly, WeatherSegmentType.HOURLY, fetchedAt),
                daily = segment(forecast.daily, WeatherSegmentType.DAILY, fetchedAt),
                airQuality = previous?.airQuality,
            )
        }

        override suspend fun saveAirQuality(
            city: City,
            airQuality: AirQuality,
            fetchedAt: Instant,
        ) {
            operationOrder += "air"
            val previous = snapshot.value
            snapshot.value = WeatherSnapshot(
                city = city,
                current = previous?.current,
                hourly = previous?.hourly,
                daily = previous?.daily,
                airQuality = segment(
                    airQuality,
                    WeatherSegmentType.AIR_QUALITY,
                    fetchedAt,
                ),
            )
        }
    }

    private inner class FakeWeatherRemote(
        private val forecastFailure: Throwable? = null,
        private val forecastGate: CompletableDeferred<Unit>? = null,
        private val searchResults: List<City> = listOf(city),
    ) : WeatherRemoteDataSource {
        var forecastCalls = 0
        var airQualityCalls = 0

        override suspend fun searchCities(query: String): List<City> = searchResults

        override suspend fun fetchForecast(city: City): ForecastBundle {
            forecastCalls += 1
            forecastGate?.await()
            forecastFailure?.let { throw it }
            return sampleForecast()
        }

        override suspend fun fetchAirQuality(city: City): AirQuality {
            airQualityCalls += 1
            return sampleAirQuality()
        }
    }
}
