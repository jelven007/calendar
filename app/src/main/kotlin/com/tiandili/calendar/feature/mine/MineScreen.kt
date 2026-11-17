package com.tiandili.calendar.feature.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleKind
import com.tiandili.calendar.ui.PageHeader
import java.time.Clock
import java.time.LocalDate

@Composable
fun MineScreen(
    schedules: List<Schedule>,
    preferences: UserPreferences,
    onOpenKind: (ScheduleKind) -> Unit,
    onSettings: () -> Unit,
    onExplore: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
    cancelledOccurrences: Map<Long, Set<LocalDate>> = emptyMap(),
    onCreateKind: (ScheduleKind) -> Unit = {},
    onOpenRecord: (Long) -> Unit = {},
    onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    onSetCompleted: (Long, Boolean) -> Unit = { _, _ -> },
    error: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchResults = remember(schedules, query) { searchRecords(schedules, query) }
    val today = LocalDate.now(clock)
    val summaries = listOf(ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY,
        ScheduleKind.NOTE, ScheduleKind.SCHEDULE, ScheduleKind.ALARM, ScheduleKind.TODO)
    val upcoming = remember(schedules, today, cancelledOccurrences) {
        summaries.associateWith { upcomingRecords(schedules, it, clock, cancelledOccurrences) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mine_content")) {
        item { PageHeader("我的") {
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "设置") }
        } }
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) { Text(preferences.nickname.take(1), style = MaterialTheme.typography.headlineMedium) }
                Column { Text(preferences.nickname, style = MaterialTheme.typography.titleLarge)
                    Text("天地历 · 记录每一个日子", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("搜索标题、正文或子事项") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .testTag("mine_search")) }
        if (query.isNotBlank()) {
            item { Text("搜索结果 · ${searchResults.size} 条", Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleMedium) }
            if (searchResults.isEmpty()) item { Text("没有找到记录", Modifier.padding(20.dp)) }
            items(searchResults.size, key = { "search_${searchResults[it].id}" }) { index ->
                val record = searchResults[index]
                Column(Modifier.fillMaxWidth().clickable { onOpenRecord(record.id) }.padding(20.dp)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                    Text("${record.kind.label} · ${record.note.take(60)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        } else {
        item {
            val kinds = ScheduleKind.entries
            val icons = listOf(Icons.Outlined.EditNote, Icons.Outlined.Event, Icons.Outlined.Cake,
                Icons.Outlined.FavoriteBorder, Icons.Outlined.Alarm, Icons.Outlined.CheckCircleOutline,
                Icons.Outlined.PhotoLibrary, Icons.AutoMirrored.Outlined.Article)
            val colors = listOf(0xFF629388, 0xFFC45B4C, 0xFF699D68, 0xFFC77BA6,
                0xFF9273B6, 0xFF6098B9, 0xFFD6A554, 0xFF798DAD)
            (0..1).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    (0..4).forEach { column ->
                        val index = row * 5 + column
                        val label = if (index < 8) kinds[index].label else if (index == 8) "日期工具" else "系统设置"
                        Column(Modifier.weight(1f).testTag(if (index < 8) "mine_shortcut_${kinds[index].name}" else "mine_shortcut_$index")
                            .clickable { if (index < 8) onOpenKind(kinds[index]) else if (index == 8) onExplore() else onSettings() }
                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (index < 8) icons[index] else if (index == 8) Icons.Outlined.Calculate else Icons.Outlined.Settings,
                                contentDescription = null, tint = if (index < 8) Color(colors[index]) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                            val compactLabel = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
                            Text(if (compactLabel && index == 8) "日期\n工具"
                                else if (compactLabel && index == 9) "系统\n设置" else label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp, start = 3.dp, end = 3.dp))
                        }
                    }
                }
            }
        }
        item { Text("我的记录 · 共 ${schedules.size} 条", Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleMedium) }
        error?.let { item { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) } }
        summaries.forEachIndexed { index, kind ->
            item(key = "summary_${kind.name}") {
                val values = schedules.filter { it.kind == kind }
                val upcomingValues = upcoming[kind].orEmpty()
                val visible = when (kind) {
                    ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY, ScheduleKind.SCHEDULE ->
                        upcomingValues.map { it.first }.take(3)
                    ScheduleKind.NOTE -> values.sortedByDescending { it.id }.take(3)
                    ScheduleKind.TODO -> values.sortedBy { it.completed }.take(3)
                    else -> values.take(3)
                }
                val tones = listOf(0xFFEAF3E3, 0xFFF9E7ED, 0xFFFFF1D3,
                    0xFFE4EFF9, 0xFFEDE7F8, 0xFFE0F2EE)
                val dark = androidx.compose.foundation.isSystemInDarkTheme() ||
                    MaterialTheme.colorScheme.background.red < 0.3f
                Surface(color = if (dark) MaterialTheme.colorScheme.surfaceContainer else Color(tones[index]),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp).testTag("mine_summary_${kind.name}")) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onOpenKind(kind) }, modifier = Modifier.weight(1f)) {
                                Text("${kind.label} · ${values.size}", Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.titleMedium) }
                            IconButton(onClick = { onCreateKind(kind) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "新增${kind.label}") }
                        }
                        if (visible.isEmpty()) Text(
                            if (values.isEmpty()) "还没有${kind.label}，点 + 记录" else "暂无即将到来的${kind.label}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                        visible.forEach { record ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { onOpenRecord(record.id) }.padding(8.dp)) {
                                    Text(record.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                                    val date = upcomingValues.firstOrNull { it.first.id == record.id }?.second
                                    val detail = when (kind) {
                                        ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY -> date?.let {
                                            "$it · ${countdownText(today, it)}" }.orEmpty()
                                        ScheduleKind.NOTE -> record.note.take(80).ifBlank { record.recordDate().toString() }
                                        ScheduleKind.TODO -> if (record.checklist.isEmpty())
                                            if (record.completed) "已完成" else "未完成"
                                            else "${record.checklist.count { it.completed }}/${record.checklist.size} 项完成"
                                        ScheduleKind.ALARM -> (record.timing as? com.tiandili.calendar.domain.schedule.ScheduleTiming.Timed)
                                            ?.startsAt?.toLocalTime()?.toString() ?: record.recordDate().toString()
                                        else -> date?.toString().orEmpty()
                                    }
                                    Text(detail, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                if (kind == ScheduleKind.ALARM) Switch(record.enabled,
                                    { onSetEnabled(record.id, it) }, Modifier.testTag("mine_alarm_${record.id}"))
                                if (kind == ScheduleKind.TODO) Checkbox(record.completed,
                                    { onSetCompleted(record.id, it) }, Modifier.testTag("mine_todo_${record.id}"))
                            }
                        }
                        if (values.size > 3) TextButton(onClick = { onOpenKind(kind) }) {
                            Text("查看全部 ${values.size} 条") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
