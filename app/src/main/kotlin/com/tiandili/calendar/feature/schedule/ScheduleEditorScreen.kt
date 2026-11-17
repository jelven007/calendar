package com.tiandili.calendar.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import com.tiandili.calendar.domain.schedule.ScheduleKind
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.domain.schedule.RecurrenceFrequency
import com.tiandili.calendar.domain.schedule.ScheduleField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class DateTarget {
    START,
    END,
    UNTIL,
}

private enum class TimeTarget {
    START,
    END,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onAllDayChanged: (Boolean) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onEndDateChanged: (LocalDate) -> Unit,
    onStartTimeChanged: (LocalTime) -> Unit,
    onEndTimeChanged: (LocalTime) -> Unit,
    onRecurrenceChanged: (RecurrenceFrequency?) -> Unit,
    onReminderMinutesChanged: (Int?) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onKindChanged: (ScheduleKind) -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit = {},
    onCompletedChanged: (Boolean) -> Unit = {},
    onAddChecklistItem: (String) -> Unit = {},
    onToggleChecklistItem: (Int, Boolean) -> Unit = { _, _ -> },
    onRemoveChecklistItem: (Int) -> Unit = {},
    onMediaChanged: (List<String>) -> Unit = {},
    onIntervalChanged: (Int) -> Unit = {},
    onWeekdayChanged: (java.time.DayOfWeek, Boolean) -> Unit = { _, _ -> },
    onUntilChanged: (LocalDate?) -> Unit = {},
    onCountChanged: (Int?) -> Unit = {},
    onLunarChanged: (Boolean) -> Unit = {},
    onLunarDateChanged: (Int, Int) -> Unit = { _, _ -> },
) {
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }
    val categoryScroll = rememberLazyListState(initialFirstVisibleItemIndex = state.kind.ordinal)
    LaunchedEffect(state.kind) { categoryScroll.scrollToItem(state.kind.ordinal) }
    val titleError = state.errors.firstOrNull { it.field == ScheduleField.TITLE }
    val timingError = state.errors.firstOrNull { it.field == ScheduleField.TIMING }
    val noteError = state.errors.firstOrNull { it.field == ScheduleField.NOTE }
    var checklistTitle by remember { mutableStateOf("") }
    var advanced by remember(state.id) { mutableStateOf(false) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var replacingImage by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        val kept = selected.filter { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (_: SecurityException) { mediaError = "此图片无法保留读取权限，请从系统文件中重新选择"; false }
        }.map { it.toString() }
        if (kept.isNotEmpty()) {
            val replacement = replacingImage
            onMediaChanged(if (replacement == null) (state.mediaUris + kept).distinct().take(9)
                else state.mediaUris.mapIndexed { index, uri -> if (index == replacement) kept.first() else uri })
        }
        replacingImage = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "返回日程列表",
                )
            }
            Text(
                text = (if (state.id == 0L) "新建" else "编辑") + state.kind.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSave, enabled = !state.saving) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = "保存${state.kind.label}",
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyRow(Modifier.fillMaxWidth(), state = categoryScroll,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScheduleKind.entries, key = { it.name }) { kind ->
                FilterChip(selected = state.kind == kind, onClick = { onKindChanged(kind) },
                    label = { Text(kind.label) }, modifier = Modifier.testTag("category_${kind.name}"))
            }
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标题") },
            singleLine = true,
            isError = titleError != null,
            supportingText = titleError?.let {
                { Text(titleError.messageText()) }
            },
        )

        if (state.kind == ScheduleKind.TODO) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.completed, onCompletedChanged, Modifier.testTag("todo_completed"))
                Text("完成全部待办")
            }
            state.checklist.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.completed, { onToggleChecklistItem(index, it) },
                        Modifier.testTag("checklist_$index"))
                    Text(item.title, Modifier.weight(1f))
                    IconButton(onClick = { onRemoveChecklistItem(index) }) {
                        Icon(Icons.Outlined.DeleteOutline, "移除子待办 ${index + 1}")
                    }
                }
            }
            fun addItem() {
                if (checklistTitle.isNotBlank()) { onAddChecklistItem(checklistTitle); checklistTitle = "" }
            }
            OutlinedTextField(checklistTitle, { if (it.length <= 200) checklistTitle = it },
                modifier = Modifier.fillMaxWidth().testTag("checklist_input"),
                label = { Text("添加子待办，回车确认") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addItem() }),
                trailingIcon = { TextButton(onClick = { addItem() }) { Text("添加") } })
        }
        if (state.kind == ScheduleKind.ALARM) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("启用闹钟", Modifier.weight(1f))
                Switch(state.enabled, onEnabledChanged)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (state.kind == ScheduleKind.SCHEDULE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "全天",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.allDay,
                onCheckedChange = onAllDayChanged,
            )
        }
        }

        DateTimeField(
            label = "开始日期",
            value = state.startDate.format(DateFormatter),
            onClick = { dateTarget = DateTarget.START },
        )
        if (state.kind == ScheduleKind.SCHEDULE) DateTimeField(
            label = "结束日期",
            value = state.endDate.format(DateFormatter),
            onClick = { dateTarget = DateTarget.END },
        )
        if (!state.allDay) {
            DateTimeField(
                label = "开始时间",
                value = state.startTime.format(TimeFormatter),
                onClick = { timeTarget = TimeTarget.START },
                isTime = true,
            )
            if (state.kind != ScheduleKind.ALARM) DateTimeField(
                label = "结束时间",
                value = state.endTime.format(TimeFormatter),
                onClick = { timeTarget = TimeTarget.END },
                isTime = true,
            )
        }
        if (state.kind in listOf(ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("按农历每年重复", Modifier.weight(1f))
                Switch(state.lunarMonth != null, onLunarChanged)
            }
            state.lunarMonth?.let { month ->
                val day = state.lunarDay ?: 1
                NumberStepper("农历${if (month < 0) "闰" else ""}月", kotlin.math.abs(month), 1..12) {
                    onLunarDateChanged(if (month < 0) -it else it, day)
                }
                NumberStepper("农历日", day, 1..30) { onLunarDateChanged(month, it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(month < 0, { onLunarDateChanged(if (it) -kotlin.math.abs(month) else kotlin.math.abs(month), day) })
                    Text("闰月生日")
                }
                Text("非闰年按同月；小月三十按当月最后一天", style = MaterialTheme.typography.labelSmall)
            }
        }
        timingError?.let {
            Text(
                text = it.messageText(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        OptionField(
            label = "重复",
            value = state.recurrenceFrequency.displayName(),
            expanded = recurrenceMenuExpanded,
            onExpandedChange = { recurrenceMenuExpanded = it },
        ) {
            RecurrenceOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onRecurrenceChanged(option.first)
                        recurrenceMenuExpanded = false
                    },
                )
            }
        }
        if (state.recurrenceFrequency != null) {
            NumberStepper("重复间隔", state.recurrenceInterval, 1..365, onIntervalChanged)
            Text("每 ${state.recurrenceInterval} ${when (state.recurrenceFrequency) {
                RecurrenceFrequency.DAILY -> "天"; RecurrenceFrequency.WEEKLY -> "周"
                RecurrenceFrequency.MONTHLY -> "月"; else -> "年"
            }}重复", style = MaterialTheme.typography.labelMedium)
            if (state.recurrenceFrequency == RecurrenceFrequency.WEEKLY) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    java.time.DayOfWeek.entries.forEach { day ->
                        FilterChip(day in state.recurrenceWeekdays,
                            { onWeekdayChanged(day, day !in state.recurrenceWeekdays) },
                            label = { Text("周" + listOf("一", "二", "三", "四", "五", "六", "日")[day.value - 1]) })
                    }
                }
            }
            TextButton(onClick = { advanced = !advanced }) { Text("重复截止与次数 ${if (advanced) "⌃" else "⌄"}") }
            if (advanced) {
                DateTimeField("截止日期", state.recurrenceUntil?.format(DateFormatter) ?: "不限",
                    onClick = { dateTarget = DateTarget.UNTIL })
                TextButton(onClick = { onUntilChanged(null) }) { Text("清除截止日期") }
                NumberStepper("重复次数", state.recurrenceCount ?: 1, 1..10_000) { onCountChanged(it) }
                TextButton(onClick = { onCountChanged(if (state.recurrenceCount == null) 10 else null) }) {
                    Text(if (state.recurrenceCount == null) "不限次数 · 点击限制" else "已限制 ${state.recurrenceCount} 次 · 点击取消")
                }
            }
        }
        if (state.kind != ScheduleKind.ALARM) OptionField(
            label = "提醒",
            value = state.reminderMinutesBefore.reminderText(),
            expanded = reminderMenuExpanded,
            onExpandedChange = { reminderMenuExpanded = it },
        ) {
            ReminderOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onReminderMinutesChanged(option.first)
                        reminderMenuExpanded = false
                    },
                )
            }
        }
        if (state.kind == ScheduleKind.ALARM) Text("准时响铃 · 系统闹钟声音 · 最长 5 分钟",
            style = MaterialTheme.typography.labelMedium)

        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (state.kind in listOf(ScheduleKind.NOTE, ScheduleKind.ARTICLE)) "正文" else "备注") },
            minLines = 3,
            maxLines = 6,
            isError = noteError != null,
            supportingText = noteError?.let {
                { Text(noteError.messageText()) }
            },
        )
        if (state.kind == ScheduleKind.SCHEDULE && !state.allDay) {
            Text("时区：${state.timingZoneId.id}", style = MaterialTheme.typography.labelSmall)
        }
        if (state.kind in listOf(ScheduleKind.ALBUM, ScheduleKind.ARTICLE) || state.mediaUris.isNotEmpty()) {
            Text("图片 ${state.mediaUris.size} / 9", style = MaterialTheme.typography.titleMedium)
            RecordImages(state.mediaUris,
                { index -> onMediaChanged(state.mediaUris.filterIndexed { i, _ -> i != index }) },
                { index -> replacingImage = index; imagePicker.launch(arrayOf("image/*")) })
            if (state.mediaUris.size < 9) OutlinedButton(onClick = {
                replacingImage = null; imagePicker.launch(arrayOf("image/*"))
            }) { Text("选择图片") }
            Text("备份保存图片引用，原图请单独保存。移除记录不会删除原图。", style = MaterialTheme.typography.labelSmall)
        }
        mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.errors.filter { it.field != ScheduleField.TITLE && it.field != ScheduleField.TIMING && it.field != ScheduleField.NOTE }
            .forEach { Text(it.messageText(), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        }
    }

    dateTarget?.let { target ->
        val selectedDate = when (target) { DateTarget.START -> state.startDate; DateTarget.END -> state.endDate
            DateTarget.UNTIL -> state.recurrenceUntil ?: state.startDate }
        val pickerState = rememberDatePickerState(
            yearRange = 1901..2099,
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { epochMillis ->
                            val date = Instant.ofEpochMilli(epochMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            if (target == DateTarget.START) {
                                onStartDateChanged(date)
                            } else if (target == DateTarget.END) {
                                onEndDateChanged(date)
                            } else {
                                onUntilChanged(date)
                            }
                        }
                        dateTarget = null
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    timeTarget?.let { target ->
        val selectedTime = if (target == TimeTarget.START) state.startTime else state.endTime
        val pickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timeTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = LocalTime.of(pickerState.hour, pickerState.minute)
                        if (target == TimeTarget.START) {
                            onStartTimeChanged(time)
                        } else {
                            onEndTimeChanged(time)
                        }
                        timeTarget = null
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { timeTarget = null }) {
                    Text("取消")
                }
            },
            text = { TimeInput(state = pickerState) },
        )
    }
}

@Composable
private fun NumberStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(value.toString())
        TextButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun DateTimeField(
    label: String,
    value: String,
    onClick: () -> Unit,
    isTime: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onClick) {
            Icon(
                imageVector = if (isTime) Icons.Outlined.Schedule else Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(value)
        }
    }
}

@Composable
private fun OptionField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { onExpandedChange(true) }) {
                Text(value)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                content()
            }
        }
    }
}

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val RecurrenceOptions = listOf(
    null to "不重复",
    RecurrenceFrequency.DAILY to "每天",
    RecurrenceFrequency.WEEKLY to "每周",
    RecurrenceFrequency.MONTHLY to "每月",
    RecurrenceFrequency.YEARLY to "每年",
)

private val ReminderOptions = listOf(
    null to "不提醒",
    0 to "准时提醒",
    10 to "提前 10 分钟",
    30 to "提前 30 分钟",
    60 to "提前 1 小时",
    1_440 to "提前 1 天",
)

private fun RecurrenceFrequency?.displayName(): String =
    RecurrenceOptions.first { it.first == this }.second

private fun Int?.reminderText(): String =
    ReminderOptions.firstOrNull { it.first == this }?.second ?: "提前 $this 分钟"

private fun com.tiandili.calendar.domain.schedule.ScheduleValidationError.messageText(): String =
    when (this) {
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.INVALID_CHECKLIST ->
            "子事项最多 50 项，每项需要 1–200 个字"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.INVALID_MEDIA ->
            "最多选择 9 张系统文件选择器中的图片"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.TITLE_REQUIRED ->
            "请输入日程标题"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.TITLE_TOO_LONG ->
            "标题不能超过 80 个字符"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.INVALID_TIME_RANGE ->
            "结束时间不能早于开始时间"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.NOTE_TOO_LONG ->
            "备注不能超过 1000 个字符"
    }
