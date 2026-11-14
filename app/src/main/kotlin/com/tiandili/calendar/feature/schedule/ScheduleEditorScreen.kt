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
) {
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }
    val titleError = state.errors.firstOrNull { it.field == ScheduleField.TITLE }
    val timingError = state.errors.firstOrNull { it.field == ScheduleField.TIMING }
    val noteError = state.errors.firstOrNull { it.field == ScheduleField.NOTE }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回日程列表",
                )
            }
            Text(
                text = if (state.id == 0L) "新建日程" else "编辑日程",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = "保存日程",
                )
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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

        DateTimeField(
            label = "开始日期",
            value = state.startDate.format(DateFormatter),
            onClick = { dateTarget = DateTarget.START },
        )
        DateTimeField(
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
            DateTimeField(
                label = "结束时间",
                value = state.endTime.format(TimeFormatter),
                onClick = { timeTarget = TimeTarget.END },
                isTime = true,
            )
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
        OptionField(
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

        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注") },
            minLines = 3,
            maxLines = 6,
            isError = noteError != null,
            supportingText = noteError?.let {
                { Text(noteError.messageText()) }
            },
        )
    }

    dateTarget?.let { target ->
        val selectedDate = if (target == DateTarget.START) state.startDate else state.endDate
        val pickerState = rememberDatePickerState(
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
                            } else {
                                onEndDateChanged(date)
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
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.TITLE_REQUIRED ->
            "请输入日程标题"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.TITLE_TOO_LONG ->
            "标题不能超过 80 个字符"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.INVALID_TIME_RANGE ->
            "结束时间不能早于开始时间"
        com.tiandili.calendar.domain.schedule.ScheduleValidationError.NOTE_TOO_LONG ->
            "备注不能超过 1000 个字符"
    }
