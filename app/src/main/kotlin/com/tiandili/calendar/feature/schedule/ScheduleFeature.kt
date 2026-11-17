package com.tiandili.calendar.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleKind
import java.time.LocalDate

@Composable
fun ScheduleFeature(
    listViewModel: ScheduleListViewModel,
    editorViewModel: ScheduleEditorViewModel,
    onReminderSaved: () -> Unit = {},
    requestedSchedule: Schedule? = null,
    externalRequestSequence: Long = 0,
    scheduleUnavailable: Boolean = false,
    category: ScheduleKind? = null,
    newRecordSequence: Long = 0,
    selectedDate: LocalDate? = null,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
) {
    val listState by listViewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
    var editorOpen by remember(category) { mutableStateOf(false) }
    var activeCategory by remember(category) { mutableStateOf(category) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(editorOpen) { onEditorVisibilityChanged(editorOpen) }
    DisposableEffect(Unit) { onDispose { onEditorVisibilityChanged(false) } }
    LaunchedEffect(externalRequestSequence) {
        if (externalRequestSequence != 0L) {
            editorOpen = false
            detailId = requestedSchedule?.id
        }
    }
    LaunchedEffect(newRecordSequence) {
        if (newRecordSequence != 0L) {
            editorViewModel.startNew(category ?: ScheduleKind.SCHEDULE, selectedDate)
            detailId = null
            editorOpen = true
        }
    }
    val detail = listState.schedules.firstOrNull { it.id == detailId }

    LaunchedEffect(editorState.savedScheduleId) {
        if (editorState.savedScheduleId != null && editorOpen) {
            if (editorState.reminderMinutesBefore != null) {
                onReminderSaved()
            }
            editorOpen = false
            if (activeCategory != null) activeCategory = editorState.kind
        }
    }
    BackHandler(enabled = editorOpen || detailId != null) {
        editorOpen = false
        detailId = null
    }

    if (detail != null && !editorOpen) {
        ScheduleDetailScreen(detail,
            onBack = { detailId = null },
            onEdit = {
                editorViewModel.load(detail.id)
                detailId = null
                editorOpen = true
            })
    } else if (editorOpen) {
        ScheduleEditorScreen(
            state = editorState,
            onBack = { editorOpen = false },
            onTitleChanged = editorViewModel::onTitleChanged,
            onAllDayChanged = editorViewModel::onAllDayChanged,
            onStartDateChanged = editorViewModel::onStartDateChanged,
            onEndDateChanged = editorViewModel::onEndDateChanged,
            onStartTimeChanged = editorViewModel::onStartTimeChanged,
            onEndTimeChanged = editorViewModel::onEndTimeChanged,
            onRecurrenceChanged = editorViewModel::onRecurrenceChanged,
            onReminderMinutesChanged = editorViewModel::onReminderMinutesChanged,
            onNoteChanged = editorViewModel::onNoteChanged,
            onSave = editorViewModel::save,
            onKindChanged = editorViewModel::onKindChanged,
            onEnabledChanged = editorViewModel::onEnabledChanged,
            onCompletedChanged = editorViewModel::onCompletedChanged,
            onAddChecklistItem = editorViewModel::addChecklistItem,
            onToggleChecklistItem = editorViewModel::toggleChecklistItem,
            onRemoveChecklistItem = editorViewModel::removeChecklistItem,
            onMediaChanged = editorViewModel::onMediaChanged,
            onIntervalChanged = editorViewModel::onRecurrenceIntervalChanged,
            onWeekdayChanged = editorViewModel::onWeekdayChanged,
            onUntilChanged = editorViewModel::onRecurrenceUntilChanged,
            onCountChanged = editorViewModel::onRecurrenceCountChanged,
            onLunarChanged = editorViewModel::onLunarChanged,
            onLunarDateChanged = editorViewModel::onLunarDateChanged,
        )
    } else {
        Column {
        if (scheduleUnavailable) Text("日程已删除或不可用")
        ScheduleListScreen(
            state = listState.copy(schedules = listState.schedules.filter { activeCategory == null || it.kind == activeCategory }),
            onAddSchedule = {
                editorViewModel.startNew(activeCategory ?: ScheduleKind.SCHEDULE)
                editorOpen = true
            },
            onEditSchedule = { scheduleId ->
                editorViewModel.load(scheduleId)
                editorOpen = true
            },
            onDeleteSchedule = { pendingDelete = it },
            category = activeCategory,
            onCompletedChanged = listViewModel::setCompleted,
            onEnabledChanged = { id, enabled -> listViewModel.setEnabled(id, enabled)
                if (enabled) onReminderSaved() },
        )
        }
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除${schedule.kind.label}？") },
            text = { Text(schedule.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        listViewModel.deleteSchedule(schedule.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}
