package com.tiandili.calendar.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tiandili.calendar.domain.almanac.AlmanacDay
import com.tiandili.calendar.domain.weather.CalendarWeatherResult
import com.tiandili.calendar.ui.theme.Cinnabar
import com.tiandili.calendar.ui.theme.MineralGold
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    modifier: Modifier = Modifier,
    scheduleDates: Set<LocalDate> = emptySet(),
    weather: CalendarWeatherResult.Available? = null,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onReturnToToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("calendar_content")
            .padding(horizontal = 16.dp),
    ) {
        item {
            MonthHeader(
                year = state.displayedMonth.year,
                month = state.displayedMonth.monthValue,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onReturnToToday = onReturnToToday,
            )
        }
        item {
            CalendarGrid(
                state = state,
                scheduleDates = scheduleDates,
                onDateSelected = onDateSelected,
            )
        }
        item {
            SelectedDayDetail(
                day = state.selectedAlmanac,
                weather = weather,
                modifier = Modifier.padding(vertical = 22.dp),
            )
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onReturnToToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$year 年 $month 月",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
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
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "下个月",
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    state: CalendarUiState,
    scheduleDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
                (0..6).forEach { index ->
                    val weekday = weekdays[(state.firstDayOfWeek.value - 1 + index) % 7]
                    Text(
                        text = weekday,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            hasSchedule = cell.date in scheduleDates,
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
            .aspectRatio(0.82f)
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
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 18.sp,
            )
            Text(
                text = cell.label.orEmpty(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (cell.label in listOf("春节", "清明", "端午节", "中秋节")) {
                    Cinnabar
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasSchedule) {
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
private fun SelectedDayDetail(
    day: AlmanacDay,
    weather: CalendarWeatherResult.Available?,
    modifier: Modifier = Modifier,
) {
    val festivalSuffix = (day.solarTerm?.let(::listOf).orEmpty() + day.festivals)
        .distinct()
        .joinToString(" · ")
        .takeIf(String::isNotEmpty)
    Column(modifier = modifier.fillMaxWidth()) {
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
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "${day.yearGanzhi}年 · ${day.zodiac}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        weather?.let {
            val forecast = it.forecast
            Text(
                text = "${it.condition.description} " +
                    "${forecast.minTemperatureC.roundToInt()}–" +
                    "${forecast.maxTemperatureC.roundToInt()}℃",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
