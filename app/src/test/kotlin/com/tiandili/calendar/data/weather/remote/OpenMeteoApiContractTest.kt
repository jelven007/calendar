package com.tiandili.calendar.data.weather.remote

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class OpenMeteoApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder().build(),
                ),
            )
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `city search requests Chinese results and maps disambiguating fields`() = runTest {
        server.enqueue(jsonResponse(fixture("open_meteo_geocoding.json")))
        val api = retrofit.create(OpenMeteoGeocodingApi::class.java)

        val response = api.searchCities(name = "杭州")
        val cities = response.results.orEmpty().mapNotNull(OpenMeteoMapper::cityOrNull)

        assertThat(cities).hasSize(2)
        assertThat(cities.first().name).isEqualTo("杭州市")
        assertThat(cities.first().admin1).isEqualTo("浙江")
        assertThat(cities.first().country).isEqualTo("中国")
        assertThat(cities.first().zoneId).isEqualTo(ZoneId.of("Asia/Shanghai"))
        assertThat(server.takeRequest().requestUrl?.queryParameter("language")).isEqualTo("zh")
    }

    @Test
    fun `forecast request carries timezone and maps city local timestamps`() = runTest {
        server.enqueue(jsonResponse(fixture("open_meteo_forecast.json")))
        val api = retrofit.create(OpenMeteoForecastApi::class.java)
        val city = sampleCity()

        val response = api.forecast(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = city.zoneId.id,
        )
        val forecast = OpenMeteoMapper.forecast(response, city)
        val requestUrl = server.takeRequest().requestUrl

        assertThat(requestUrl?.queryParameter("timezone")).isEqualTo("Asia/Shanghai")
        assertThat(requestUrl?.queryParameter("timeformat")).isEqualTo("unixtime")
        assertThat(requestUrl?.queryParameter("forecast_days")).isEqualTo("7")
        assertThat(forecast.current.observedAt.toLocalTime()).isEqualTo(LocalTime.of(13, 0))
        assertThat(forecast.hourly).hasSize(3)
        assertThat(forecast.daily.first().date).isEqualTo(LocalDate.of(2025, 1, 2))
        assertThat(forecast.daily.first().sunrise?.toLocalTime()).isEqualTo(LocalTime.of(7, 0))
    }

    @Test
    fun `air quality response is labeled as US AQI data`() = runTest {
        server.enqueue(jsonResponse(fixture("open_meteo_air_quality.json")))
        val api = retrofit.create(OpenMeteoAirQualityApi::class.java)
        val city = sampleCity()

        val response = api.airQuality(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = city.zoneId.id,
        )
        val airQuality = OpenMeteoMapper.airQuality(response, city)

        assertThat(airQuality.usAqi).isEqualTo(42)
        assertThat(airQuality.pm25MicrogramsPerCubicMeter).isWithin(0.01).of(12.3)
        assertThat(server.takeRequest().requestUrl?.queryParameter("current"))
            .contains("us_aqi")
    }

    @Test
    fun `forecast mapper rejects mismatched parallel arrays`() = runTest {
        server.enqueue(jsonResponse(fixture("open_meteo_forecast.json")))
        val response = retrofit.create(OpenMeteoForecastApi::class.java).forecast(
            latitude = sampleCity().latitude,
            longitude = sampleCity().longitude,
            timezone = sampleCity().zoneId.id,
        )

        val malformed = response.copy(
            hourly = response.hourly.copy(
                temperatures = response.hourly.temperatures.dropLast(1),
            ),
        )

        val failure = runCatching {
            OpenMeteoMapper.forecast(malformed, sampleCity())
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failure).hasMessageThat().contains("hourly")
    }

    @Test
    fun `remote data source retries one server failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(jsonResponse(fixture("open_meteo_forecast.json")))
        val dataSource = dataSource()

        val result = dataSource.fetchForecast(sampleCity())

        assertThat(result.current.temperatureC).isWithin(0.01).of(8.4)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `remote data source does not retry rate limiting`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(jsonResponse(fixture("open_meteo_forecast.json")))
        val dataSource = dataSource()

        val failure = runCatching {
            dataSource.fetchForecast(sampleCity())
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(retrofit2.HttpException::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    private fun dataSource() = OpenMeteoRemoteDataSource(
        geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
        forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
        airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
    )

    private fun sampleCity() = com.tiandili.calendar.domain.weather.City(
        id = "open-meteo:1808926",
        name = "杭州市",
        admin1 = "浙江",
        country = "中国",
        latitude = 30.29365,
        longitude = 120.16142,
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText()

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
