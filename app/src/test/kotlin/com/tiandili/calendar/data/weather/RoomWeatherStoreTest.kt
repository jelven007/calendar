package com.tiandili.calendar.data.weather

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.data.local.TiandiliDatabase
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.ForecastBundle
import com.tiandili.calendar.domain.weather.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomWeatherStoreTest {

    private lateinit var database: TiandiliDatabase
    private lateinit var dao: WeatherDao
    private lateinit var store: RoomWeatherStore
    private val fetchedAt = Instant.parse("2025-01-02T05:00:00Z")
    private val city = City(
        id = "open-meteo:1808926",
        name = "杭州市",
        admin1 = "浙江",
        country = "中国",
        latitude = 30.29365,
        longitude = 120.16142,
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.weatherDao()
        store = RoomWeatherStore(
            dao = dao,
            moshi = Moshi.Builder().build(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `selected city and all weather segments round trip through Room`() = runTest {
        store.selectCity(city, fetchedAt)
        store.saveForecast(city, sampleForecast(), fetchedAt)
        store.saveAirQuality(city, sampleAirQuality(), fetchedAt)

        val snapshot = store.observeWeather().filterNotNull().first {
            it.current != null && it.airQuality != null
        }

        assertThat(snapshot.city).isEqualTo(city)
        assertThat(snapshot.current?.value?.temperatureC).isWithin(0.01).of(8.4)
        assertThat(snapshot.hourly?.value).hasSize(1)
        assertThat(snapshot.daily?.value?.single()?.date)
            .isEqualTo(LocalDate.of(2025, 1, 2))
        assertThat(snapshot.airQuality?.value?.usAqi).isEqualTo(42)
    }

    @Test
    fun `each cache segment stores its own expiry`() = runTest {
        store.selectCity(city, fetchedAt)
        store.saveForecast(city, sampleForecast(), fetchedAt)
        store.saveAirQuality(city, sampleAirQuality(), fetchedAt)

        val segments = dao.getSegments(city.id).associateBy(
            WeatherCacheSegmentEntity::segmentType,
        )

        assertThat(segments.getValue(WeatherSegmentTypeEntity.CURRENT).expiresAtEpochMillis)
            .isEqualTo(fetchedAt.plus(Duration.ofMinutes(30)).toEpochMilli())
        assertThat(segments.getValue(WeatherSegmentTypeEntity.HOURLY).expiresAtEpochMillis)
            .isEqualTo(fetchedAt.plus(Duration.ofMinutes(30)).toEpochMilli())
        assertThat(segments.getValue(WeatherSegmentTypeEntity.DAILY).expiresAtEpochMillis)
            .isEqualTo(fetchedAt.plus(Duration.ofHours(6)).toEpochMilli())
        assertThat(
            segments.getValue(WeatherSegmentTypeEntity.AIR_QUALITY)
                .expiresAtEpochMillis,
        ).isEqualTo(fetchedAt.plus(Duration.ofMinutes(60)).toEpochMilli())
    }

    @Test
    fun `one corrupt segment is ignored without hiding valid cache`() = runTest {
        store.selectCity(city, fetchedAt)
        store.saveForecast(city, sampleForecast(), fetchedAt)
        val current = dao.getSegments(city.id).first {
            it.segmentType == WeatherSegmentTypeEntity.CURRENT
        }
        dao.replaceSegments(listOf(current.copy(payloadJson = """{"broken":true}""")))

        val snapshot = store.observeWeather().filterNotNull().first()

        assertThat(snapshot.current).isNull()
        assertThat(snapshot.hourly).isNotNull()
        assertThat(snapshot.daily).isNotNull()
    }

    private fun sampleForecast() = ForecastBundle(
        current = CurrentWeather(
            observedAt = fetchedAt.atZone(city.zoneId),
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
                time = fetchedAt.atZone(city.zoneId),
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
                sunrise = fetchedAt.minusSeconds(6 * 60 * 60).atZone(city.zoneId),
                sunset = fetchedAt.plusSeconds(4 * 60 * 60).atZone(city.zoneId),
            ),
        ),
    )

    private fun sampleAirQuality() = AirQuality(
        observedAt = fetchedAt.atZone(city.zoneId),
        usAqi = 42,
        pm25MicrogramsPerCubicMeter = 12.3,
        pm10MicrogramsPerCubicMeter = 20.4,
        ozoneMicrogramsPerCubicMeter = 55.1,
        nitrogenDioxideMicrogramsPerCubicMeter = 18.2,
    )
}
