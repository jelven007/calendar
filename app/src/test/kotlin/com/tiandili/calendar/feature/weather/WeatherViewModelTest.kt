package com.tiandili.calendar.feature.weather

import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.weather.WeatherRefreshGroup
import com.tiandili.calendar.data.weather.WeatherRefreshResult
import com.tiandili.calendar.data.weather.WeatherRepository
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.GeoPoint
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeWeatherRepository
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
        Dispatchers.setMain(dispatcher)
        repository = FakeWeatherRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached snapshot is exposed while automatic refresh runs`() {
        val viewModel = WeatherViewModel(repository)
        val snapshot = WeatherSnapshot(city = city)

        repository.weather.value = snapshot

        assertThat(viewModel.uiState.value.snapshot).isEqualTo(snapshot)
        assertThat(repository.refreshForces).containsExactly(false)
    }

    @Test
    fun `refresh failure keeps cached snapshot and shows an offline message`() {
        val snapshot = WeatherSnapshot(city = city)
        repository.weather.value = snapshot
        repository.refreshResult = WeatherRefreshResult(
            failedGroups = setOf(
                WeatherRefreshGroup.FORECAST,
                WeatherRefreshGroup.AIR_QUALITY,
            ),
        )
        val viewModel = WeatherViewModel(repository)

        viewModel.refresh()

        assertThat(viewModel.uiState.value.snapshot).isEqualTo(snapshot)
        assertThat(viewModel.uiState.value.message).contains("显示缓存")
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(repository.refreshForces).containsExactly(false, true).inOrder()
    }

    @Test
    fun `city search reports disambiguated results without selecting one`() {
        repository.searchResults = listOf(city)
        val viewModel = WeatherViewModel(repository)

        viewModel.updateSearchQuery("杭州")
        viewModel.search()

        assertThat(viewModel.uiState.value.searchResults).containsExactly(city)
        assertThat(viewModel.uiState.value.isSearching).isFalse()
        assertThat(repository.selectedCities).isEmpty()
    }

    @Test
    fun `selecting a city closes the picker after repository selection`() {
        val viewModel = WeatherViewModel(repository)
        viewModel.openCityPicker()

        viewModel.selectCity(city)

        assertThat(repository.selectedCities).containsExactly(city)
        assertThat(viewModel.uiState.value.isCityPickerOpen).isFalse()
    }

    @Test
    fun `current coordinates become a selected local timezone city`() {
        val viewModel = WeatherViewModel(repository)

        viewModel.selectCurrentLocation(
            point = GeoPoint(latitude = 30.2, longitude = 120.1),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        val selected = repository.selectedCities.single()
        assertThat(selected.name).isEqualTo("当前位置")
        assertThat(selected.zoneId).isEqualTo(ZoneId.of("Asia/Shanghai"))
        assertThat(selected.latitude).isWithin(0.001).of(30.2)
    }

    @Test
    fun `unavailable location keeps manual city search visible`() {
        val viewModel = WeatherViewModel(repository)

        viewModel.locationUnavailable()

        assertThat(viewModel.uiState.value.isCityPickerOpen).isTrue()
        assertThat(viewModel.uiState.value.message).contains("搜索城市")
    }

    private class FakeWeatherRepository : WeatherRepository {
        val weather = MutableStateFlow<WeatherSnapshot?>(null)
        val refreshForces = mutableListOf<Boolean>()
        val selectedCities = mutableListOf<City>()
        var refreshResult = WeatherRefreshResult(noSelectedCity = true)
        var searchResults = emptyList<City>()

        override fun observeWeather(): Flow<WeatherSnapshot?> = weather

        override suspend fun searchCities(query: String): List<City> = searchResults

        override suspend fun savedCities(): List<City> = selectedCities

        override suspend fun selectCity(city: City): WeatherRefreshResult {
            selectedCities += city
            weather.value = WeatherSnapshot(city)
            return WeatherRefreshResult()
        }

        override suspend fun refresh(force: Boolean): WeatherRefreshResult {
            refreshForces += force
            return refreshResult
        }
    }
}
