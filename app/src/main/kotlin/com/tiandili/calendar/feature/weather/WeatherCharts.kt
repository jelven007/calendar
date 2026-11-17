package com.tiandili.calendar.feature.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.domain.weather.*
import com.nlf.calendar.Solar
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HourlyTemperatureChart(hours: List<HourlyForecast>) {
    if (hours.isEmpty()) { Text("暂无未来小时预报"); return }
    var selection by rememberSaveable(hours) { mutableIntStateOf(0) }
    val selected = hours[selection]
    val description = WeatherCodeMapper.conditionFor(selected.weatherCode,
        selected.time.hour in 6..18).description
    Text("${selected.time.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${
        selected.temperatureC.roundToInt()}℃ · $description · 降水 ${selected.precipitationProbabilityPercent}%",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width((hours.size * 64).coerceAtLeast(320).dp)) {
            TemperatureLines(listOf(hours.map { it.temperatureC }), selection, hours.size,
                "24 小时温度趋势，点选时间可查看温度与降水概率")
            Row {
                hours.forEachIndexed { index, hour ->
                    TextButton(onClick = { selection = index }, modifier = Modifier.width(64.dp)
                        .semantics { contentDescription = "选择小时 ${hour.time.format(DateTimeFormatter.ofPattern("HH:mm"))}" }) {
                        Text(hour.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = if (selection == index) MaterialTheme.colorScheme.secondary else Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTemperatureChart(days: List<DailyForecast>, air: AirQuality?) {
    if (days.isEmpty()) { Text("暂无未来日期预报"); return }
    var selection by rememberSaveable(days) { mutableIntStateOf(0) }
    val selected = days[selection]
    val aqi = dailyAirAqi(air, selected.date)
    Text("${selected.date.format(DateTimeFormatter.ofPattern("M月d日"))} · ${
        WeatherCodeMapper.conditionFor(selected.weatherCode, true).description} · ${
        selected.minTemperatureC.roundToInt()}–${selected.maxTemperatureC.roundToInt()}℃",
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
    Text(aqi?.let { "可用小时均值 US AQI $it · 降水 ${selected.precipitationProbabilityPercent}%" }
        ?: "该日暂无空气预报", style = MaterialTheme.typography.bodySmall)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width((days.size * 76).coerceAtLeast(320).dp)) {
            Row {
                days.forEachIndexed { index, day ->
                    Column(Modifier.width(76.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        TextButton(onClick = { selection = index }, modifier = Modifier.semantics {
                            contentDescription = "选择预报 ${day.date}" }) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("M/d")),
                                color = if (selection == index) MaterialTheme.colorScheme.secondary else Color.White) }
                        val lunar = remember(day.date) { Solar.fromYmd(day.date.year,
                            day.date.monthValue, day.date.dayOfMonth).lunar }
                        Text(lunar.dayInChinese, style = MaterialTheme.typography.labelSmall)
                        WeatherConditionIcon(WeatherCodeMapper.conditionFor(day.weatherCode, true).symbol,
                            WeatherCodeMapper.conditionFor(day.weatherCode, true).description,
                            Modifier.padding(vertical = 8.dp).size(24.dp), MaterialTheme.colorScheme.secondary)
                        Text(dailyAirAqi(air, day.date)?.let { "AQI $it" } ?: "AQI —",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TemperatureLines(listOf(days.map { it.maxTemperatureC }, days.map { it.minTemperatureC }),
                selection, days.size, "高低温趋势，金色为最高温，白色为最低温", showLabels = true)
        }
    }
    Text("高温 / 低温 · ℃；空气预报仅显示接口提供的可用小时均值。",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun TemperatureLines(
    series: List<List<Double>>, selection: Int, count: Int, description: String,
    showLabels: Boolean = false,
) {
    val min = series.flatten().minOrNull()!! - 2
    val max = series.flatten().maxOrNull()!! + 2
    val colors = if (series.size == 1) listOf(Color.White) else listOf(Color(0xFFFFC170), Color.White)
    Box(Modifier.height(if (showLabels) 166.dp else 110.dp).fillMaxWidth()) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val step = size.width / count
            val top = 20.dp.toPx()
            val height = size.height - 40.dp.toPx()
            fun point(index: Int, value: Double) =
                Offset(step * (index + .5f), top + ((max - value) / (max - min)).toFloat() * height)
            drawLine(Color.White.copy(alpha = .12f), Offset(0f, top + height),
                Offset(size.width, top + height), 1.dp.toPx())
            series.forEachIndexed { lineIndex, values ->
                val path = Path()
                values.forEachIndexed { index, value ->
                    val p = point(index, value)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                if (series.size == 1) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(step * (count - .5f), top + height)
                        lineTo(step * .5f, top + height); close()
                    }
                    drawPath(area, Color.White.copy(alpha = .10f))
                }
                drawPath(path, colors[lineIndex], style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                values.forEachIndexed { index, value ->
                    drawCircle(colors[lineIndex], if (index == selection) 4.dp.toPx() else 2.5.dp.toPx(),
                        point(index, value))
                }
            }
            val x = step * (selection + .5f)
            drawLine(Color.White.copy(alpha = .2f), Offset(x, top), Offset(x, top + height), 1.dp.toPx())
        }
        if (showLabels) Row(Modifier.fillMaxWidth()) {
            series[0].indices.forEach { index ->
                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("${series[0][index].roundToInt()}°", style = MaterialTheme.typography.labelSmall,
                        color = colors[0])
                    Spacer(Modifier.height(128.dp))
                    Text("${series[1][index].roundToInt()}°", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
