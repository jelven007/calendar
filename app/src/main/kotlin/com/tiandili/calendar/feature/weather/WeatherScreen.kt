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

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("weather_content")
                .padding(horizontal = 16.dp),
        ) {
            item {
                WeatherHeader(
                    city = state.snapshot.city,
                    isRefreshing = state.isRefreshing,
                    onOpenCityPicker = onOpenCityPicker,
                    onRefresh = onRefresh,
                )
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
                modifier = Modifier.size(64.dp),
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
                fontSize = 58.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.semantics {
                    contentDescription =
                        "当前温度 ${current.temperatureC.roundToInt()}°"
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CurrentMetric("湿度", "${current.relativeHumidityPercent}%")
            CurrentMetric("风速", "${current.windSpeedKph.roundToInt()} km/h")
            CurrentMetric("降水", "${current.precipitationMm} mm")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        hours.forEachIndexed { index, hour ->
            val condition = WeatherCodeMapper.conditionFor(hour.weatherCode, isDay = true)
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (index == 0) {
                        "现在"
                    } else {
                        hour.time.format(DateTimeFormatter.ofPattern("HH:mm"))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeatherConditionIcon(
                    symbol = condition.symbol,
                    contentDescription = condition.description,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(26.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "${hour.temperatureC.roundToInt()}°",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${hour.precipitationProbabilityPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun DailySection(
    city: City,
    segment: CachedWeatherSegment<List<DailyForecast>>?,
    now: Instant,
) {
    SectionHeading("未来 7 日")
    if (segment == null) {
        MissingSectionText()
        return
    }
    FreshnessText(city, segment, WeatherSegmentType.DAILY, now)
    segment.value.take(7).forEach { day ->
        val condition = WeatherCodeMapper.conditionFor(day.weatherCode, isDay = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("M月d日 E")),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(96.dp),
            )
            WeatherConditionIcon(
                symbol = condition.symbol,
                contentDescription = condition.description,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = condition.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            )
            Text(
                text = "${day.minTemperatureC.roundToInt()}–" +
                    "${day.maxTemperatureC.roundToInt()}℃",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
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
private fun WeatherConditionIcon(
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
    in 0..50 -> com.tiandili.calendar.ui.theme.Jade
    in 51..100 -> MineralGold
    else -> Cinnabar
}
