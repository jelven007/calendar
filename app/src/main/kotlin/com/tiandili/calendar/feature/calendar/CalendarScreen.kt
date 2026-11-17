package com.tiandili.calendar.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.tiandili.calendar.domain.almanac.AlmanacDay
import com.tiandili.calendar.domain.weather.CalendarWeatherResult
import com.tiandili.calendar.ui.theme.Cinnabar
import com.tiandili.calendar.ui.theme.MineralGold
import com.tiandili.calendar.ui.theme.Jade
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    modifier: Modifier = Modifier,
    scheduleDates: Set<LocalDate> = emptySet(),
    weather: CalendarWeatherResult.Available? = null,
    scheduleLabels: Map<LocalDate, List<String>> = emptyMap(),
    cityName: String? = null,
    onAdd: () -> Unit = {},
    onOpenAlmanac: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onReturnToToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    var selectingDate by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("calendar_content"),
    ) {
        item {
            MonthHeader(
                year = state.displayedMonth.year,
                month = state.displayedMonth.monthValue,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onReturnToToday = onReturnToToday,
                onSelectMonth = { selectingDate = true },
                onAdd = onAdd,
            )
        }
        item {
            CalendarGrid(
                state = state,
                scheduleDates = scheduleDates,
                onDateSelected = onDateSelected,
                labels = scheduleLabels,
                modifier = Modifier.pointerInput(onPreviousMonth, onNextMonth) {
                    detectHorizontalDragGestures(onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag > 90) onPreviousMonth()
                            if (drag < -90) onNextMonth()
                        }) { change, amount -> change.consume(); drag += amount }
                },
            )
        }
        item {
            SelectedDayDetail(
                day = state.selectedAlmanac,
                weather = weather,
                modifier = Modifier.padding(12.dp),
                cityName = cityName,
                onOpenWeather = onOpenWeather,
                onOpenAlmanac = onOpenAlmanac,
                fullAlmanac = false,
            )
        }
    }
    if (selectingDate) DateSelectionDialog(state.selectedDate, { selectingDate = false }, onDateSelected)
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onReturnToToday: () -> Unit,
    onSelectMonth: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onReturnToToday) {
            Icon(
                imageVector = Icons.Outlined.Today,
                contentDescription = "回到今天",
            )
        }
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "上个月",
            )
        }
        TextButton(onClick = onSelectMonth, modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Text("${year}年${month}月⌄", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "下个月",
            )
        }
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "新增事项") }
    }
    }
}

@Composable
private fun CalendarGrid(
    state: CalendarUiState,
    scheduleDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    labels: Map<LocalDate, List<String>>,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).forEach { index ->
                    val weekday = weekdays[(state.firstDayOfWeek.value - 1 + index) % 7]
                    Text(
                        text = weekday,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (weekday in listOf("六", "日")) Cinnabar else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weekday_$index")
                            .padding(vertical = 4.dp),
                    )
                }
            }
            state.cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            selected = cell.date == state.selectedDate,
                            hasSchedule = cell.date in scheduleDates || !labels[cell.date].isNullOrEmpty(),
                            labels = labels[cell.date].orEmpty(),
                            onClick = { onDateSelected(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    selected: Boolean,
    hasSchedule: Boolean,
    labels: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val borderModifier = if (selected) {
        Modifier.border(1.dp, selectedColor, RoundedCornerShape(6.dp))
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .heightIn(min = 86.dp)
            .then(borderModifier)
            .background(
                color = if (selected) selectedColor else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .alpha(if (cell.isInDisplayedMonth) 1f else 0.45f)
            .semantics {
                contentDescription = buildString {
                    append("${cell.date.year}年${cell.date.monthValue}月${cell.date.dayOfMonth}日")
                    cell.label?.let { append("，$it") }
                    if (hasSchedule) append("，有日程")
                    labels.forEach { append("，$it") }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    if (cell.date.dayOfWeek in listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY))
                        Cinnabar else MaterialTheme.colorScheme.onSurface
                },
                fontSize = 27.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 32.sp,
            )
            Text(
                text = cell.label.orEmpty(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (cell.isSolarTerm) {
                    Cinnabar
                } else if (cell.isFestival) {
                    Jade
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (labels.isNotEmpty()) {
                Text(labels.first() + if (labels.size > 1) " +${labels.size - 1}" else "",
                    Modifier.padding(top = 2.dp).background(Color(0xFFFFF2A9), RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp),
                    color = Color(0xFF544415), fontSize = 9.sp, lineHeight = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (hasSchedule) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MineralGold
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
fun SelectedDayDetail(
    day: AlmanacDay,
    weather: CalendarWeatherResult.Available?,
    modifier: Modifier = Modifier,
    cityName: String? = null,
    onOpenWeather: () -> Unit = {},
    onOpenAlmanac: () -> Unit = {},
    fullAlmanac: Boolean = true,
) {
    val festivalSuffix = (day.solarTerm?.let(::listOf).orEmpty() + day.festivals)
        .distinct()
        .joinToString(" · ")
        .takeIf(String::isNotEmpty)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
    Column(Modifier.padding(14.dp)) {
        Text(
            text = "${day.date.year}年${day.date.monthValue}月${day.date.dayOfMonth}日 ${day.weekdayText}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append(day.lunarMonthText)
                append(day.lunarDayText)
                festivalSuffix?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenAlmanac),
        )
        Text("第 ${day.date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())} 周 · " +
            "${day.weekdayText} · 宜忌 ›", modifier = Modifier.clickable(onClick = onOpenAlmanac)
                .padding(vertical = 6.dp))
        Text(
            text = "${day.yearGanzhi}年 ${day.monthGanzhi}月 ${day.dayGanzhi}日 [属${day.zodiac}]",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        weather?.let {
            val forecast = it.forecast
            Text(
                text = "${cityName.orEmpty()} · ${it.condition.description} " +
                    "${forecast.minTemperatureC.roundToInt()}–" +
                    "${forecast.maxTemperatureC.roundToInt()}℃",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onOpenWeather).padding(top = 6.dp),
            )
        }
        if (weather == null && !fullAlmanac) TextButton(onClick = onOpenWeather) { Text("查看城市天气 ›") }
        if (fullAlmanac) {
        Spacer(Modifier.height(16.dp))
        AlmanacPair(
            suitable = day.suitable,
            avoid = day.avoid,
        )
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        InfoLine(label = "日柱", value = day.dayGanzhi)
        InfoLine(label = "彭祖百忌", value = day.pengZu.joinToString("；"))
        InfoLine(
            label = "吉神方位",
            value = day.deityDirections.joinToString(" · ") { "${it.name}${it.direction}" },
        )
        Text("黄历内容来自传统历法，仅供文化参考", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}

@Composable
private fun AlmanacPair(
    suitable: List<String>,
    avoid: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AlmanacList(
            marker = "宜",
            items = suitable,
            markerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        AlmanacList(
            marker = "忌",
            items = avoid,
            markerColor = Cinnabar,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlmanacList(
    marker: String,
    items: List<String>,
    markerColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = markerColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = marker,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = items.take(8).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MineralGold,
            modifier = Modifier.weight(0.32f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.68f),
        )
    }
}
