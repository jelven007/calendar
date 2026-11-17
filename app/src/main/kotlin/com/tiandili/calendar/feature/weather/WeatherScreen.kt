package com.tiandili.calendar.feature.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.CacheFreshness
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.CurrentWeather
import com.tiandili.calendar.domain.weather.DailyForecast
import com.tiandili.calendar.domain.weather.HourlyForecast
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherCodeMapper
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import com.tiandili.calendar.domain.weather.WeatherSymbol
import com.tiandili.calendar.ui.theme.Cinnabar
import com.tiandili.calendar.ui.theme.MineralGold
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    now: Instant,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOpenCityPicker: () -> Unit = {},
    onCloseCityPicker: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSelectCity: (City) -> Unit = {},
    onUseCurrentLocation: () -> Unit = {},
    onOpenAttribution: () -> Unit = {},
    onShareWeather: (String) -> Unit = {},
) {
    if (state.isCityPickerOpen || state.snapshot == null) {
        CityPicker(
            state = state,
            modifier = modifier,
            canClose = state.snapshot != null,
            onClose = onCloseCityPicker,
            onSearchQueryChange = onSearchQueryChange,
            onSearch = onSearch,
            onSelectCity = onSelectCity,
            onUseCurrentLocation = onUseCurrentLocation,
        )
        return
    }

    WeatherScene(isDay = state.snapshot.current?.value?.isDay ?: true) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .testTag("weather_content")
                .padding(horizontal = 16.dp),
        ) {
            item {
                WeatherHeader(
                    city = state.snapshot.city,
                    isRefreshing = state.isRefreshing,
                    onOpenCityPicker = onOpenCityPicker,
                    onRefresh = onRefresh,
                    onShare = { onShareWeather(weatherShareText(state.snapshot, now)) },
                )
            }
            if (state.savedCities.size > 1) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    state.savedCities.forEach { city ->
                        TextButton(onClick = { onSelectCity(city) }) {
                            Text(if (city.id == state.snapshot.city.id) "● ${city.name}" else city.name) }
                    }
                }
            }
            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            item {
                CurrentWeatherSection(
                    snapshot = state.snapshot,
                    now = now,
                )
            }
            item { TodayTomorrowSection(state.snapshot, now) }
            item {
                HourlySection(
                    city = state.snapshot.city,
                    segment = state.snapshot.hourly,
                    now = now,
                )
            }
            item {
                DailySection(
                    city = state.snapshot.city,
                    segment = state.snapshot.daily,
                    now = now,
                    air = state.snapshot.airQuality?.value,
                )
            }
            item {
                AirQualitySection(
                    city = state.snapshot.city,
                    segment = state.snapshot.airQuality,
                    now = now,
                )
            }
            item {
                TextButton(
                    onClick = onOpenAttribution,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                ) {
                    Text("Weather data by Open-Meteo.com")
                }
            }
        }
    }
    }
}

@Composable
private fun CityPicker(
    state: WeatherUiState,
    canClose: Boolean,
    onClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectCity: (City) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择城市",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (canClose) {
                    TextButton(onClick = onClose) {
                        Text("完成")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onUseCurrentLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text("使用当前位置")
            }
        }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("城市名称") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSearch()
                    },
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onSearch()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索城市",
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
        }
        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.contains("失败") || message.contains("无法")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        if (state.isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
        items(
            count = state.searchResults.size,
            key = { index -> state.searchResults[index].id },
        ) { index ->
            val city = state.searchResults[index]
            CityResultRow(
                city = city,
                onClick = { onSelectCity(city) },
            )
        }
    }
}

@Composable
private fun CityResultRow(
    city: City,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = city.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = listOfNotNull(city.admin1, city.country.takeIf(String::isNotBlank))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun WeatherHeader(
    city: City,
    isRefreshing: Boolean,
    onOpenCityPicker: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenCityPicker)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新天气",
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, contentDescription = "分享天气") }
    }
}

@Composable
private fun CurrentWeatherSection(
    snapshot: WeatherSnapshot,
    now: Instant,
) {
    val segment = snapshot.current
    if (segment == null) {
        Text(
            text = "暂无当前天气，点击刷新",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 28.dp),
        )
        return
    }
    val current = segment.value
    val condition = WeatherCodeMapper.conditionFor(
        code = current.weatherCode,
        isDay = current.isDay,
    )
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)) {
        FreshnessText(
            city = snapshot.city,
            segment = segment,
            type = WeatherSegmentType.CURRENT,
            now = now,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherConditionIcon(
                symbol = condition.symbol,
                contentDescription = condition.description,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = condition.description,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "体感 ${current.apparentTemperatureC.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${current.temperatureC.roundToInt()}°",
                fontSize = 84.sp,
                lineHeight = 90.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.semantics {
                    contentDescription =
                        "当前温度 ${current.temperatureC.roundToInt()}°"
                },
            )
        }
        Text("${windDescription(current)} · 湿度 ${current.relativeHumidityPercent}%",
            modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
        val air = snapshot.airQuality?.value
        Text(air?.let { "${usAqiLabel(it.usAqi)} · US AQI ${it.usAqi}" } ?: "暂无当前空气质量",
            modifier = Modifier.padding(top = 8.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun TodayTomorrowSection(snapshot: WeatherSnapshot, now: Instant) {
    val today = now.atZone(snapshot.city.zoneId).toLocalDate()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        (0L..1L).forEach { offset ->
            val day = snapshot.daily?.value?.firstOrNull { it.date == today.plusDays(offset) }
            Column(Modifier.weight(1f)) {
                Text("${if (offset == 0L) "今天" else "明天"} · ${
                    day?.let { WeatherCodeMapper.conditionFor(it.weatherCode, true).description } ?: "暂无预报"}")
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(day?.let { "${it.maxTemperatureC.roundToInt()}/${it.minTemperatureC.roundToInt()}℃" } ?: "—",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    day?.let { WeatherConditionIcon(WeatherCodeMapper.conditionFor(it.weatherCode, true).symbol,
                        "天气图标", Modifier.size(28.dp), MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun CurrentMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun HourlySection(
    city: City,
    segment: CachedWeatherSegment<List<HourlyForecast>>?,
    now: Instant,
) {
    SectionHeading("未来 24 小时")
    if (segment == null) {
        MissingSectionText()
        return
    }
    FreshnessText(city, segment, WeatherSegmentType.HOURLY, now)
    val hours = segment.value
        .filterNot { it.time.toInstant().isBefore(now) }
        .take(24)
    HourlyTemperatureChart(hours)
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun DailySection(
    city: City,
    segment: CachedWeatherSegment<List<DailyForecast>>?,
    now: Instant,
    air: AirQuality?,
) {
    val today = now.atZone(city.zoneId).toLocalDate()
    val days = segment?.value.orEmpty().filter { it.date in today..today.plusDays(14) }.take(15)
    SectionHeading("未来 ${if (segment == null) 15 else days.size} 日")
    if (segment == null) {
        MissingSectionText()
        return
    }
    FreshnessText(city, segment, WeatherSegmentType.DAILY, now)
    DailyTemperatureChart(days, air)
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun AirQualitySection(
    city: City,
    segment: CachedWeatherSegment<AirQuality>?,
    now: Instant,
) {
    SectionHeading("空气质量")
    if (segment == null) {
        MissingSectionText()
        return
    }
    FreshnessText(city, segment, WeatherSegmentType.AIR_QUALITY, now)
    val air = segment.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "US AQI ${air.usAqi}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = usAqiLabel(air.usAqi),
                style = MaterialTheme.typography.bodyLarge,
                color = usAqiColor(air.usAqi),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "PM2.5 ${air.pm25MicrogramsPerCubicMeter} μg/m³",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "PM10 ${air.pm10MicrogramsPerCubicMeter} μg/m³",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "O₃ ${air.ozoneMicrogramsPerCubicMeter} · " +
                    "NO₂ ${air.nitrogenDioxideMicrogramsPerCubicMeter}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
    )
}

@Composable
private fun MissingSectionText() {
    Text(
        text = "暂无数据",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 14.dp),
    )
}

@Composable
private fun FreshnessText(
    city: City,
    segment: CachedWeatherSegment<*>,
    type: WeatherSegmentType,
    now: Instant,
) {
    val prefix = when (WeatherCachePolicy.freshness(type, segment.fetchedAt, now)) {
        CacheFreshness.FRESH -> "更新于"
        CacheFreshness.STALE -> "缓存 · 更新于"
        CacheFreshness.EXPIRED -> "已过期 · 更新于"
    }
    val localTime = segment.fetchedAt.atZone(city.zoneId)
        .format(DateTimeFormatter.ofPattern("M-d HH:mm"))
    Text(
        text = "$prefix $localTime",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun WeatherConditionIcon(
    symbol: WeatherSymbol,
    contentDescription: String,
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = symbol.icon(),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

private fun WeatherSymbol.icon(): ImageVector = when (this) {
    WeatherSymbol.CLEAR_DAY -> Icons.Outlined.WbSunny
    WeatherSymbol.CLEAR_NIGHT -> Icons.Outlined.NightsStay
    WeatherSymbol.PARTLY_CLOUDY_DAY,
    WeatherSymbol.PARTLY_CLOUDY_NIGHT,
    WeatherSymbol.CLOUDY,
    -> Icons.Outlined.Cloud
    WeatherSymbol.FOG -> Icons.Outlined.BlurOn
    WeatherSymbol.DRIZZLE,
    WeatherSymbol.RAIN,
    WeatherSymbol.SHOWERS,
    -> Icons.Outlined.WaterDrop
    WeatherSymbol.SNOW -> Icons.Outlined.AcUnit
    WeatherSymbol.THUNDERSTORM -> Icons.Outlined.Thunderstorm
    WeatherSymbol.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
}

private fun usAqiLabel(value: Int): String = when (value) {
    in 0..50 -> "良好"
    in 51..100 -> "中等"
    in 101..150 -> "对敏感人群不健康"
    in 151..200 -> "不健康"
    in 201..300 -> "非常不健康"
    else -> "危险"
}

private fun usAqiColor(value: Int): Color = when (value) {
    in 0..50 -> Color(0xFFB1EA98)
    in 51..100 -> Color(0xFFFFDB86)
    else -> Color(0xFFFFB5AE)
}
