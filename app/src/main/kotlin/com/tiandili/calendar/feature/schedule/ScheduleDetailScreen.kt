package com.tiandili.calendar.feature.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleDetailScreen(schedule: Schedule, onBack: () -> Unit, onEdit: () -> Unit) {
    val format = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("${schedule.kind.label}详情", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onEdit) { Text("编辑") }
        }
        Text(schedule.title, style = MaterialTheme.typography.headlineMedium)
        Text(when (val timing = schedule.timing) {
            is ScheduleTiming.AllDay ->
                "${timing.startDate} 至 ${timing.endDateExclusive.minusDays(1)} · 全天"
            is ScheduleTiming.Timed ->
                "${timing.startsAt.format(format)}\n至 ${timing.endsAt.format(format)}\n时区：${timing.zoneId.id}"
        })
        schedule.recurrence?.let { Text("重复日程") }
        schedule.reminderMinutesBefore?.let { Text("提前 $it 分钟提醒") }
        if (schedule.note.isNotBlank()) Text(schedule.note)
        schedule.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.completed, null)
                Text(item.title)
            }
        }
        if (schedule.mediaUris.isNotEmpty()) RecordImages(schedule.mediaUris, onReselect = { onEdit() })
    }
}
