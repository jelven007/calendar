package com.tiandili.calendar.feature.calendar

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionDialog(date: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1901..2099,
    )
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let {
                onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
            }
            onDismiss()
        }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        DatePicker(picker)
    }
}
