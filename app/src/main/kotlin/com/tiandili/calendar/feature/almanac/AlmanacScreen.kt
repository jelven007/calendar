package com.tiandili.calendar.feature.almanac

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.domain.almanac.AlmanacDay
import com.tiandili.calendar.feature.calendar.DateSelectionDialog
import com.tiandili.calendar.feature.calendar.SelectedDayDetail
import com.tiandili.calendar.ui.PageHeader
import java.time.LocalDate

@Composable
fun AlmanacScreen(day: AlmanacDay, onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("黄历") {
            TextButton(onClick = onToday, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) { Text("今天") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelectDate(day.date.minusDays(1)) }) { Text("‹ 前一天") }
            TextButton(onClick = { selecting = true }) { Text(day.date.toString() + " ⌄") }
            TextButton(onClick = { onSelectDate(day.date.plusDays(1)) }) { Text("后一天 ›") }
        }
        LazyColumn { item { SelectedDayDetail(day, null, Modifier.padding(12.dp),
            onOpenAlmanac = { selecting = true }) } }
    }
    if (selecting) DateSelectionDialog(day.date, { selecting = false }, onSelectDate)
}
