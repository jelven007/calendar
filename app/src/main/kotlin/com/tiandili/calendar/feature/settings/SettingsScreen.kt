package com.tiandili.calendar.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.BuildConfig
import com.tiandili.calendar.data.backup.ImportMode
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.UserPreferences
import java.time.DayOfWeek

data class ReminderPermissionState(val notificationsEnabled: Boolean, val exactAlarmsEnabled: Boolean)
private enum class SettingsDialog { WEEK, THEME, REMINDER, EXPORT, CLEAR, EXACT, ABOUT }

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    state: SettingsUiState,
    currentCity: String?,
    permissions: ReminderPermissionState,
    onWeekStart: (DayOfWeek) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDefaultReminder: (Int?) -> Unit,
    onLockScreenTitle: (Boolean) -> Unit,
    onWidgetSchedule: (Boolean) -> Unit,
    onManageCities: () -> Unit,
    onRefreshWeather: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: (ImportMode) -> Unit,
    onCancelImport: () -> Unit,
    onClearWeather: () -> Unit,
    onClearAll: () -> Unit,
    onOpenAttribution: () -> Unit,
    onOpenLicense: () -> Unit,
    openSourceLicenses: String = "",
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    LazyColumn(modifier = Modifier.testTag("settings_content").padding(horizontal = 20.dp)) {
        item { SectionLabel("显示与提醒") }
        item { SettingsRow("周起始日", if (preferences.firstDayOfWeek == DayOfWeek.SUNDAY) "周日" else "周一",
            !state.busy) { dialog = SettingsDialog.WEEK } }
        item { SettingsRow("明暗主题", themeLabel(preferences.themeMode), !state.busy) { dialog = SettingsDialog.THEME } }
        item { SettingsRow("默认提醒", reminderLabel(preferences.defaultReminderMinutes), !state.busy) {
            dialog = SettingsDialog.REMINDER } }
        item { SettingsRow("通知状态", if (permissions.notificationsEnabled) "已开启" else "未开启 · 点击设置",
            !state.busy, onNotifications) }
        item { SettingsRow("精确提醒", if (permissions.exactAlarmsEnabled) "已允许" else "可能延迟 · 点击设置",
            !state.busy) { dialog = SettingsDialog.EXACT } }
        item { SwitchRow("锁屏显示日程标题", "关闭时仅显示通用提醒文案",
            preferences.showScheduleTitleOnLockScreen, !state.busy, onLockScreenTitle) }
        item { SwitchRow("小组件显示下一日程", "开启后桌面可看到日程标题",
            preferences.showNextScheduleInWidget, !state.busy, onWidgetSchedule) }
        item { Text("强制停止应用可能中断提醒。重新打开后将检查并恢复。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
        item { SectionLabel("天气") }
        item { SettingsRow("当前城市", currentCity ?: "尚未选择", !state.busy, onManageCities) }
        item { SettingsRow("刷新天气", "更新当前城市", !state.busy, onRefreshWeather) }
        item { SettingsRow("仅清天气缓存", "保留日程、城市和设置", !state.busy, onClearWeather) }
        item { SectionLabel("本地数据") }
        item { Text("数据只保存在本机。卸载或设备损坏可能丢失日程，请主动备份。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp)) }
        item { SettingsRow("导出 JSON 备份", "日程、重复规则、城市和设置", !state.busy) {
            dialog = SettingsDialog.EXPORT } }
        item { SettingsRow("导入 JSON 备份", "先预览，再合并或替换", !state.busy, onImport) }
        item { SettingsRow("清除全部本地数据", "需要确认", !state.busy) { dialog = SettingsDialog.CLEAR } }
        item { state.message?.let { Text(it, modifier = Modifier.padding(vertical = 12.dp)) } }
        item { if (state.busy) CircularProgressIndicator(Modifier.padding(12.dp)) }
        item { SectionLabel("关于天地历") }
        item { Text("黄历宜忌为传统民俗资料，仅供文化参考。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp)) }
        item { SettingsRow("Weather data by Open-Meteo.com", "已进行中文化、单位转换与界面编排",
            true, onOpenAttribution) }
        item { SettingsRow("天气数据许可", "CC BY 4.0", true, onOpenLicense) }
        item { SettingsRow("开源许可", "天地历 ${BuildConfig.VERSION_NAME}", true) {
            dialog = SettingsDialog.ABOUT } }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) onCancelImport() },
            title = { Text("导入备份预览") },
            text = { Text("${preview.schedules.size} 个日程，${preview.cities.size} 个城市。\n" +
                "合并保留本机设置并跳过相同日程；替换会覆盖日程、城市和设置。") },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { onConfirmImport(ImportMode.MERGE) }) {
                Text("合并") } },
            dismissButton = {
                Row {
                    TextButton(enabled = !state.busy, onClick = onCancelImport) { Text("取消") }
                    TextButton(enabled = !state.busy, onClick = { onConfirmImport(ImportMode.REPLACE) }) {
                        Text("替换全部") }
                }
            },
        )
    }
    dialog?.let { selected ->
        val close = { dialog = null }
        when (selected) {
            SettingsDialog.WEEK -> ChoiceDialog("周起始日", listOf("周一", "周日"), close) {
                onWeekStart(if (it == 0) DayOfWeek.MONDAY else DayOfWeek.SUNDAY); close()
            }
            SettingsDialog.THEME -> ChoiceDialog("明暗主题", ThemeMode.entries.map(::themeLabel), close) {
                onTheme(ThemeMode.entries[it]); close()
            }
            SettingsDialog.REMINDER -> {
                val offsets = listOf(null, 0, 5, 15, 30, 60, 1440)
                ChoiceDialog("默认提醒", offsets.map(::reminderLabel), close) { onDefaultReminder(offsets[it]); close() }
            }
            SettingsDialog.EXPORT -> ConfirmDialog("导出备份", "备份文件包含个人日程，请保存到可信位置。",
                "选择保存位置", close) { close(); onExport() }
            SettingsDialog.CLEAR -> ConfirmDialog("确认清除全部数据？",
                "删除日程、城市、天气缓存和设置，并取消已有提醒。请先导出备份。",
                "确认清除", close) { close(); onClearAll() }
            SettingsDialog.EXACT -> ConfirmDialog("精确提醒",
                "允许精确闹钟后，系统会尽量按时提醒。未允许时使用近似提醒，可能延迟。",
                "打开系统设置", close) { close(); onExactAlarms() }
            SettingsDialog.ABOUT -> AlertDialog(
                onDismissRequest = close, title = { Text("开源许可") },
                text = { androidx.compose.foundation.layout.Box(Modifier.padding(4.dp)) {
                    androidx.compose.foundation.lazy.LazyColumn { item { Text(openSourceLicenses) } }
                } },
                confirmButton = { TextButton(onClick = close) { Text("关闭") } },
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SettingsRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun ChoiceDialog(title: String, choices: List<String>, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { choices.forEachIndexed { index, label ->
            TextButton(onClick = { onChoose(index) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun reminderLabel(minutes: Int?) = when (minutes) {
    null -> "不提醒"
    0 -> "开始时"
    1440 -> "提前一天"
    else -> "提前 $minutes 分钟"
}
