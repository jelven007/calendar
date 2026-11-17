package com.tiandili.calendar.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.domain.schedule.ScheduleKind
import com.tiandili.calendar.domain.schedule.occurrenceDates
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.testTag
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleListScreen(
    state: ScheduleListUiState,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    category: ScheduleKind? = null,
    onCompletedChanged: (Long, Boolean) -> Unit = { _, _ -> },
    onEnabledChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category?.label ?: "日程",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddSchedule) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "添加${category?.label ?: "日程"}",
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.schedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "暂无${category?.label ?: "日程"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.schedules,
                    key = Schedule::id,
                ) { schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        onEdit = { onEditSchedule(schedule.id) },
                        onDelete = { onDeleteSchedule(schedule) },
                        onCompletedChanged = { onCompletedChanged(schedule.id, it) },
                        onEnabledChanged = { onEnabledChanged(schedule.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCompletedChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = schedule.recordSummary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (schedule.kind == ScheduleKind.TODO) Checkbox(schedule.completed, onCompletedChanged,
                Modifier.testTag("record_complete_${schedule.id}"))
            if (schedule.kind == ScheduleKind.ALARM) Switch(schedule.enabled, onEnabledChanged,
                Modifier.testTag("record_enabled_${schedule.id}"))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除${schedule.kind.label}：${schedule.title}",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun Schedule.recordSummary(): String {
    val today = LocalDate.now()
    return when (kind) {
        ScheduleKind.TODO -> if (completed) "已完成" else "${checklist.count { it.completed }} / ${checklist.size} 项完成"
        ScheduleKind.ALARM -> "${(timing as? ScheduleTiming.Timed)?.startsAt?.format(TimeFormatter).orEmpty()} · ${if (enabled) "已启用" else "已关闭"}"
        ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY -> {
            val next = occurrenceDates(today..today.plusYears(2)).minOrNull()
            val since = when (val time = timing) {
                is ScheduleTiming.AllDay -> time.startDate
                is ScheduleTiming.Timed -> time.startsAt.toLocalDate()
            }
            val countdown = next?.let { "距下次${kind.label} ${ChronoUnit.DAYS.between(today, it)} 天" } ?: "暂无下一次"
            if (kind == ScheduleKind.ANNIVERSARY) "$countdown · 已过 ${ChronoUnit.DAYS.between(since, today).coerceAtLeast(0)} 天"
            else "$countdown · ${if (recurrence?.lunarMonth != null) "农历" else "公历"}"
        }
        ScheduleKind.ALBUM -> "${mediaUris.size} 张图片"
        ScheduleKind.NOTE, ScheduleKind.ARTICLE -> note.take(60).ifBlank { timing.summary() }
        else -> timing.summary()
    }
}

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun ScheduleTiming.summary(): String =
    when (this) {
        is ScheduleTiming.AllDay -> {
            val lastDate = endDateExclusive.minusDays(1)
            if (startDate == lastDate) {
                "${startDate.monthValue}月${startDate.dayOfMonth}日 · 全天"
            } else {
                "${startDate.monthValue}月${startDate.dayOfMonth}日–" +
                    "${lastDate.monthValue}月${lastDate.dayOfMonth}日 · 全天"
            }
        }
        is ScheduleTiming.Timed ->
            "${startsAt.monthValue}月${startsAt.dayOfMonth}日 · " +
                "${startsAt.format(TimeFormatter)}–${endsAt.format(TimeFormatter)}"
    }
