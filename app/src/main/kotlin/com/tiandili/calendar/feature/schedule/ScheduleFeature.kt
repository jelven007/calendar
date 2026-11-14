package com.tiandili.calendar.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiandili.calendar.domain.schedule.Schedule

@Composable
fun ScheduleFeature(
    listViewModel: ScheduleListViewModel,
    editorViewModel: ScheduleEditorViewModel,
    onReminderSaved: () -> Unit = {},
    requestedSchedule: Schedule? = null,
    externalRequestSequence: Long = 0,
    scheduleUnavailable: Boolean = false,
) {
    val listState by listViewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
    var editorOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(externalRequestSequence) {
        if (externalRequestSequence != 0L) {
            editorOpen = false
            detailId = requestedSchedule?.id
        }
    }
    val detail = listState.schedules.firstOrNull { it.id == detailId }

    LaunchedEffect(editorState.savedScheduleId) {
        if (editorState.savedScheduleId != null) {
            if (editorState.reminderMinutesBefore != null) {
                onReminderSaved()
            }
            editorOpen = false
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
        )
    } else {
        Column {
        if (scheduleUnavailable) Text("日程已删除或不可用")
        ScheduleListScreen(
            state = listState,
            onAddSchedule = {
                editorViewModel.startNew()
                editorOpen = true
            },
            onEditSchedule = { scheduleId ->
                editorViewModel.load(scheduleId)
                editorOpen = true
            },
            onDeleteSchedule = { pendingDelete = it },
        )
        }
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除日程？") },
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
