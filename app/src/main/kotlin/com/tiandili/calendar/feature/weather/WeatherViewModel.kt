package com.tiandili.calendar.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiandili.calendar.data.weather.WeatherRefreshResult
import com.tiandili.calendar.data.weather.WeatherRepository
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.GeoPoint
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val isRefreshing: Boolean = false,
    val isCityPickerOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val message: String? = null,
    val savedCities: List<City> = emptyList(),
)

class WeatherViewModel(
    private val repository: WeatherRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeWeather().collectLatest { snapshot ->
                val cities = try { repository.savedCities() }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (_: Exception) { emptyList() }
                mutableUiState.update {
                    it.copy(
                        snapshot = snapshot,
                        isCityPickerOpen = snapshot == null || it.isCityPickerOpen,
                        savedCities = cities,
                    )
                }
            }
        }
        viewModelScope.launch {
            refreshInternal(force = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshInternal(force = true)
        }
    }

    fun openCityPicker() {
        mutableUiState.update { it.copy(isCityPickerOpen = true, message = null) }
    }

    fun closeCityPicker() {
        mutableUiState.update {
            it.copy(isCityPickerOpen = it.snapshot == null)
        }
    }

    fun updateSearchQuery(query: String) {
        mutableUiState.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
                message = null,
            )
        }
    }

    fun search() {
        val query = mutableUiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            mutableUiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, message = null) }
            runCatching { repository.searchCities(query) }
                .onSuccess { cities ->
                    mutableUiState.update {
                        it.copy(
                            searchResults = cities,
                            isSearching = false,
                            message = if (cities.isEmpty()) "未找到匹配城市" else null,
                        )
                    }
                }
                .onFailure {
                    mutableUiState.update {
                        it.copy(
                            isSearching = false,
                            message = "城市搜索失败，请重试",
                        )
                    }
                }
        }
    }

    fun selectCity(city: City) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isRefreshing = true, message = null)
            }
            val result = runCatching { repository.selectCity(city) }
                .getOrElse {
                    WeatherRefreshResult(
                        failedGroups = com.tiandili.calendar.data.weather
                            .WeatherRefreshGroup.entries.toSet(),
                    )
                }
            mutableUiState.update {
                it.copy(
                    isCityPickerOpen = false,
                    searchResults = emptyList(),
                    isRefreshing = false,
                )
            }
            applyRefreshResult(result)
        }
    }

    fun selectCurrentLocation(
        point: GeoPoint,
        zoneId: ZoneId,
    ) {
        val city = City(
            id = String.format(
                Locale.ROOT,
                "location:%.4f:%.4f",
                point.latitude,
                point.longitude,
            ),
            name = "当前位置",
            admin1 = null,
            country = "",
            latitude = point.latitude,
            longitude = point.longitude,
            zoneId = zoneId,
        )
        selectCity(city)
    }

    fun locationUnavailable() {
        mutableUiState.update {
            it.copy(
                isCityPickerOpen = true,
                message = "无法获取位置，请搜索城市",
            )
        }
    }

    private suspend fun refreshInternal(force: Boolean) {
        mutableUiState.update { it.copy(isRefreshing = true, message = null) }
        val result = runCatching { repository.refresh(force) }
            .getOrElse {
                WeatherRefreshResult(
                    failedGroups = com.tiandili.calendar.data.weather
                        .WeatherRefreshGroup.entries.toSet(),
                )
            }
        mutableUiState.update { it.copy(isRefreshing = false) }
        applyRefreshResult(result)
    }

    private fun applyRefreshResult(result: WeatherRefreshResult) {
        mutableUiState.update { state ->
            when {
                result.noSelectedCity -> state.copy(isCityPickerOpen = true)
                result.failedGroups.isNotEmpty() && state.snapshot != null ->
                    state.copy(message = "天气更新失败，正在显示缓存")
                result.failedGroups.isNotEmpty() ->
                    state.copy(message = "天气更新失败，请重试")
                else -> state.copy(message = null)
            }
        }
    }
}
