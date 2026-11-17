package com.tiandili.calendar.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.tiandili.calendar.feature.calendar.DateSelectionDialog
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.ui.PageHeader
import java.time.*

@Composable
fun ExploreScreen(
    selectedDate: LocalDate = LocalDate.now(),
    onJumpToCalendar: (LocalDate) -> Unit = {},
) {
    var startText by rememberSaveable { mutableStateOf(selectedDate.toString()) }
    var endText by rememberSaveable { mutableStateOf(selectedDate.toString()) }
    var days by rememberSaveable { mutableStateOf("30") }
    var picking by remember { mutableStateOf<Int?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val start = LocalDate.parse(startText)
    val end = LocalDate.parse(endText)
    val result = days.toLongOrNull()?.let { DateTools.offset(start, it) }
    val engine = remember { LunarJavaAlmanacEngine() }
    val festivals = remember(month) { engine.month(month, ZoneId.systemDefault())
        .filter { it.festivals.isNotEmpty() || it.solarTerm != null } }
    LazyColumn(Modifier.fillMaxSize().testTag("explore_content")) {
        item { PageHeader("发现") }
        item { Column(Modifier.padding(20.dp)) {
            Text("日期间隔", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { picking = 0 }) { Text("开始日期 · $start") }
            TextButton(onClick = { picking = 1 }) { Text("结束日期 · $end") }
            Text("相隔 ${DateTools.daysBetween(start, end)} 天", style = MaterialTheme.typography.headlineSmall)
            Text("按自然日计算，不包含开始日；逆序显示负数。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { val old = startText; startText = endText; endText = old }) { Text("交换日期") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("日期前后推算", style = MaterialTheme.typography.titleLarge)
            Text("以 $start 为起点", Modifier.padding(vertical = 8.dp))
            OutlinedTextField(days, { if (it.length <= 8) days = it }, label = { Text("天数（负数向前）") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth())
            Text(result?.toString() ?: "请输入天数，结果须在 1901–2099 年",
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = result != null, onClick = { result?.let(onJumpToCalendar) }) { Text("在日历中查看") }
        } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("节日与节气", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(enabled = month > YearMonth.of(1901, 1), onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text("$month")
            TextButton(enabled = month < YearMonth.of(2099, 12), onClick = { month = month.plusMonths(1) }) { Text("›") }
        } }
        if (festivals.isEmpty()) item { Text("本月暂无节日或节气", Modifier.padding(20.dp)) }
        items(festivals.size, key = { festivals[it].date.toEpochDay() }) { index ->
            val day = festivals[index]
            TextButton(onClick = { onJumpToCalendar(day.date) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("${day.date}  ${listOfNotNull(day.solarTerm).plus(day.festivals).distinct().joinToString(" · ")}",
                    Modifier.fillMaxWidth()) }
        }
        item { Text("传统节日按农历换算；黄历内容供民俗文化参考。",
            Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    picking?.let { target ->
        DateSelectionDialog(if (target == 0) start else end, { picking = null }) {
            if (target == 0) startText = it.toString() else endText = it.toString()
        }
    }
}
