package com.tiandili.calendar.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun SettingsFeature(
    viewModel: SettingsViewModel,
    currentCity: String?,
    onManageCities: () -> Unit,
    onRefreshWeather: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onOpenAttribution: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    fun permissionState() = ReminderPermissionState(
        notificationsEnabled = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED),
        exactAlarmsEnabled = Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    )
    var permissions by remember { mutableStateOf(permissionState()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = permissionState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export { context.contentResolver.openOutputStream(it, "wt") } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readImport { context.contentResolver.openInputStream(it) } }
    }
    val licenses = remember {
        context.assets.open("open_source_licenses.txt").bufferedReader().use { it.readText() }
    }
    SettingsScreen(
        preferences, state, currentCity, permissions,
        onWeekStart = viewModel::setWeekStart,
        onTheme = viewModel::setTheme,
        onDefaultReminder = viewModel::setDefaultReminder,
        onLockScreenTitle = viewModel::setLockScreenTitle,
        onWidgetSchedule = viewModel::setWidgetSchedule,
        onManageCities = onManageCities,
        onRefreshWeather = onRefreshWeather,
        onNotifications = onNotifications,
        onExactAlarms = onExactAlarms,
        onExport = { exportLauncher.launch("天地历备份-${LocalDate.now()}.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelPreview,
        onClearWeather = viewModel::clearWeatherCache,
        onClearAll = viewModel::clearAll,
        onOpenAttribution = onOpenAttribution,
        onOpenLicense = onOpenLicense,
        openSourceLicenses = licenses,
    )
}
