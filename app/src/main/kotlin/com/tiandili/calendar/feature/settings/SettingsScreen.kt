package com.tiandili.calendar.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiandili.calendar.BuildConfig
import com.tiandili.calendar.data.backup.ImportMode
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.data.settings.CalendarAccent
import androidx.compose.ui.text.font.FontWeight
import java.time.DayOfWeek

data class ReminderPermissionState(val notificationsEnabled: Boolean, val exactAlarmsEnabled: Boolean)
private enum class SettingsDialog { WEEK, THEME, REMINDER, EXPORT, CLEAR, EXACT, ABOUT, NICKNAME }
private enum class SettingsPage(val title: String, val summary: String) {
    HOME("系统设置", ""),
    SKIN("皮肤主题", "中国红 · 青玉 · 晴蓝"),
    WIDGET("桌面小组件", "日期、天气和下一日程"),
    CALENDAR("日历设置", "周起始日与显示"),
    REMINDER("提醒设置", "通知、精确闹钟与锁屏"),
    WEATHER("天气设置", "城市与天气缓存"),
    DATA("数据管理", "备份、恢复与清理"),
    HELP("使用帮助", "常见问题与闹钟说明"),
    UPDATE("检查更新", "天地历版本信息"),
    PRIVACY("隐私说明", "本地存储与系统权限"),
    ABOUT("关于天地历", "昵称与开源许可"),
}

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
    onAccent: (CalendarAccent) -> Unit = {},
    onNickname: (String) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenDownload: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    val back = { if (page == SettingsPage.HOME) onBack() else page = SettingsPage.HOME }
    BackHandler(enabled = page != SettingsPage.HOME, onBack = back)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back, modifier = Modifier.testTag("settings_back"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("‹ 返回")
                }
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        key(page) {
            LazyColumn(Modifier.weight(1f).testTag("settings_content")) {
                when (page) {
                    SettingsPage.HOME -> {
                        SettingsPage.entries.filter { it != SettingsPage.HOME }.forEach { section ->
                            item {
                                SettingsRow(section.title, section.summary, true) {
                                    page = section
                                    if (section == SettingsPage.UPDATE) onCheckUpdate()
                                }
                            }
                        }
                        item { SettingsNote("天地历 ${BuildConfig.VERSION_NAME} · 好好记录每一天") }
                    }
                    SettingsPage.SKIN -> {
                        item { SectionLabel("选择日历主色") }
                        CalendarAccent.entries.forEach { accent ->
                            item { SettingsRow(accentLabel(accent),
                                if (preferences.accent == accent) "已选中 ✓" else "点击应用",
                                !state.busy) { onAccent(accent) } }
                        }
                        item { SettingsRow("明暗主题", themeLabel(preferences.themeMode), !state.busy) {
                            dialog = SettingsDialog.THEME } }
                    }
                    SettingsPage.WIDGET -> {
                        item { SwitchRow("小组件显示下一日程", "开启后桌面可看到日程标题",
                            preferences.showNextScheduleInWidget, !state.busy, onWidgetSchedule) }
                        item { SettingsNote("在桌面长按空白处，选择小组件，添加天地历。提供紧凑日期与宽版黄历天气两种布局。") }
                    }
                    SettingsPage.CALENDAR -> {
                        item { SettingsRow("周起始日",
                            if (preferences.firstDayOfWeek == DayOfWeek.SUNDAY) "周日" else "周一",
                            !state.busy) { dialog = SettingsDialog.WEEK } }
                        item { SettingsNote("点击年月选择日期，左右滑动切换月份。黄色条为当天事项；生日与纪念日支持农历年重复。") }
                    }
                    SettingsPage.REMINDER -> {
                        item { SettingsRow("默认提醒", reminderLabel(preferences.defaultReminderMinutes), !state.busy) {
                            dialog = SettingsDialog.REMINDER } }
                        item { SettingsRow("通知状态", if (permissions.notificationsEnabled) "已开启" else "未开启 · 点击设置",
                            !state.busy, onNotifications) }
                        item { SettingsRow("精确提醒", if (permissions.exactAlarmsEnabled) "已允许" else "可能延迟 · 点击设置",
                            !state.busy) { dialog = SettingsDialog.EXACT } }
                        item { SwitchRow("锁屏显示日程标题", "关闭时仅显示通用提醒文案",
                            preferences.showScheduleTitleOnLockScreen, !state.busy, onLockScreenTitle) }
                        item { SettingsNote(ALARM_HELP) }
                    }
                    SettingsPage.WEATHER -> {
                        item { SettingsRow("当前城市", currentCity ?: "尚未选择", !state.busy, onManageCities) }
                        item { SettingsRow("刷新天气", "更新当前城市", !state.busy, onRefreshWeather) }
                        item { SettingsRow("仅清天气缓存", "保留日程、城市和设置", !state.busy, onClearWeather) }
                        item { SettingsNote("天气由 Open-Meteo 提供。预报最多 15 天，空气预报仅显示接口可用日期；缓存与刷新失败会标注。") }
                    }
                    SettingsPage.DATA -> {
                        item { SettingsNote("数据只保存在本机。卸载或设备损坏可能丢失日程，请主动备份。") }
                        item { SettingsRow("导出 JSON 备份", "日程、重复规则、城市和设置", !state.busy) {
                            dialog = SettingsDialog.EXPORT } }
                        item { SettingsRow("导入 JSON 备份", "先预览，再合并或替换", !state.busy, onImport) }
                        item { SettingsNote("图片备份保存的是系统文件引用，不包含照片本身。换机后请复制原图并重新选择；本机文件删除后也需要重新选择。") }
                        item { SettingsRow("清除全部本地数据", "需要确认", !state.busy) { dialog = SettingsDialog.CLEAR } }
                    }
                    SettingsPage.HELP -> {
                        item { SectionLabel("提醒与闹钟") }
                        item { SettingsNote(ALARM_HELP) }
                        item { SectionLabel("记录与备份") }
                        item { SettingsNote("我的页面可新增日程、生日、纪念日、闹钟、待办、记事、图文与便签。完成待办或关闭闹钟会取消对应提醒，重新开启后恢复未来提醒。") }
                        item { SettingsRow("项目帮助", "查看仓库说明与反馈问题", true, onOpenHelp) }
                    }
                    SettingsPage.UPDATE -> {
                        item { SettingsRow("当前版本", "天地历 ${BuildConfig.VERSION_NAME}", false) {} }
                        item { SettingsNote(updateLabel(state.update)) }
                        item { SettingsRow("重新检查", "获取仓库最新发布信息",
                            state.update.status != UpdateStatus.CHECKING, onCheckUpdate) }
                        if (state.update.status == UpdateStatus.AVAILABLE) {
                            item { SettingsRow("下载新版本", state.update.release!!.versionName, true) {
                                onOpenDownload(state.update.release.apkUrl) } }
                        }
                        item { SettingsRow("仓库下载页", "查看 APK 与兼容性说明", true) { onOpenDownload(RELEASE_PAGE) } }
                    }
                    SettingsPage.PRIVACY -> {
                        item { SectionLabel("你的记录保存在本机") }
                        item { SettingsNote("天地历无需账户。日程、昵称、城市与设置保存在本机，不上传个人记录。导出文件由你选择保存位置；请自行保管。") }
                        item { SectionLabel("联网与系统权限") }
                        item { SettingsNote("联网用于城市搜索、经纬度天气查询与仓库更新检查。定位仅在主动选择附近城市时申请；照片由系统文件选择器授权。通知和精确闹钟权限用于提醒。") }
                        item { SwitchRow("锁屏显示日程标题", "关闭时仅显示通用提醒文案",
                            preferences.showScheduleTitleOnLockScreen, !state.busy, onLockScreenTitle) }
                    }
                    SettingsPage.ABOUT -> {
                        item { SettingsRow("本地昵称", preferences.nickname, !state.busy) { dialog = SettingsDialog.NICKNAME } }
                        item { SettingsNote("天地历 ${BuildConfig.VERSION_NAME}\n黄历宜忌为传统民俗资料，仅供文化参考。") }
                        item { SettingsRow("Weather data by Open-Meteo.com", "已进行中文化、单位转换与界面编排",
                            true, onOpenAttribution) }
                        item { SettingsRow("天气数据许可", "CC BY 4.0", true, onOpenLicense) }
                        item { SettingsRow("开源许可", "第三方组件与许可", true) { dialog = SettingsDialog.ABOUT } }
                    }
                }
                item { state.message?.let { SettingsNote(it) } }
                item { if (state.busy || state.update.status == UpdateStatus.CHECKING)
                    CircularProgressIndicator(Modifier.padding(20.dp)) }
            }
        }
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
            SettingsDialog.NICKNAME -> NicknameDialog(preferences.nickname, close) { onNickname(it); close() }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SettingsRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp))
}

@Composable
private fun NicknameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("本地昵称") },
        text = { OutlinedTextField(text, { text = it }, label = { Text("昵称（1–30 字）") }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.trim().length in 1..30, onClick = { onConfirm(text.trim()) }) {
            Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private const val ALARM_HELP = "闹钟使用系统闹钟音量，响铃最多持续 5 分钟，可在通知或响铃页停止。" +
    "Android 14 及以上的全屏闹钟受系统授权限制。\n\n请开启通知与精确闹钟，并在厂商电池设置中允许后台运行。" +
    "强制停止应用可能中断提醒，重新打开后将检查并恢复；日程与生日提醒不会持续响铃。"

private fun accentLabel(accent: CalendarAccent) = when (accent) {
    CalendarAccent.RED -> "中国红"
    CalendarAccent.JADE -> "青玉"
    CalendarAccent.BLUE -> "晴蓝"
}

private fun updateLabel(update: ReleaseUpdate): String = when (update.status) {
    UpdateStatus.IDLE -> "点击重新检查获取最新版本。"
    UpdateStatus.CHECKING -> "正在检查更新…"
    UpdateStatus.CURRENT -> "已是当前可用版本。"
    UpdateStatus.AVAILABLE -> "发现新版本 ${update.release?.versionName}，可打开下载链接。"
    UpdateStatus.INCOMPATIBLE -> "新版本需要 Android API ${update.release?.minSdk}，当前系统不支持。"
    UpdateStatus.FAILED -> "暂时无法检查更新，请重试或打开仓库下载页。"
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
