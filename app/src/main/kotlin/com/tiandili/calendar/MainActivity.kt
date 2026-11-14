package com.tiandili.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.tiandili.calendar.data.almanac.LunarJavaAlmanacEngine
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.data.weather.WeatherRepository
import com.tiandili.calendar.data.backup.LocalDataManager
import com.tiandili.calendar.data.settings.SettingsRepository
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.weather.GeoPoint
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.feature.schedule.ScheduleEditorViewModel
import com.tiandili.calendar.feature.schedule.ScheduleFeature
import com.tiandili.calendar.feature.schedule.ScheduleListViewModel
import com.tiandili.calendar.feature.weather.WeatherScreen
import com.tiandili.calendar.feature.weather.WeatherViewModel
import com.tiandili.calendar.feature.settings.SettingsFeature
import com.tiandili.calendar.feature.settings.SettingsViewModel
import com.tiandili.calendar.platform.weather.AndroidCurrentLocationProvider
import com.tiandili.calendar.platform.reminder.EXTRA_SCHEDULE_ID
import com.tiandili.calendar.platform.background.onForeground
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<AppNavigationRequest?>(null)
    private var requestSequence = 0L
    private var pendingLocationResult: ((GeoPoint?) -> Unit)? = null
    private val currentLocationProvider by lazy {
        AndroidCurrentLocationProvider(applicationContext)
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            loadCurrentLocation()
        } else {
            pendingLocationResult?.invoke(null)
            pendingLocationResult = null
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            (application as TiandiliApplication).reminderRuntime.reconcile(
                rebuildScheduled = true,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val app = application as TiandiliApplication
            val preferences by app.userPreferences.collectAsStateWithLifecycle()
            TiandiliTheme(themeMode = preferences.themeMode) {
                AppContent(
                    scheduleRepository = app.scheduleRepository,
                    weatherRepository = app.weatherRepository,
                    settingsRepository = app.settingsRepository,
                    localDataManager = app.localDataManager,
                    preferences = preferences,
                    navigationRequest = navigationRequest,
                    defaultReminderMinutes = { app.userPreferences.value.defaultReminderMinutes },
                    onReminderSaved = ::handleReminderSaved,
                    onRequestCurrentLocation = ::requestCurrentLocation,
                    onOpenWeatherAttribution = ::openWeatherAttribution,
                    onNotifications = ::openNotificationSettings,
                    onExactAlarms = ::openExactAlarmSettings,
                    onOpenWeatherLicense = {
                        startActivity(Intent(Intent.ACTION_VIEW, "https://creativecommons.org/licenses/by/4.0/".toUri()))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, 0).takeIf { it > 0 }
        val destination = if (scheduleId != null) "schedule"
            else intent.getStringExtra(EXTRA_DESTINATION)?.takeIf { it in setOf("calendar", "weather", "schedule") }
                ?: return
        navigationRequest = AppNavigationRequest(destination, scheduleId, ++requestSequence)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                (application as TiandiliApplication).backgroundTasks.onForeground()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the local calendar usable; persistent workers retry maintenance.
            }
        }
    }

    private fun handleReminderSaved() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestCurrentLocation(onResult: (GeoPoint?) -> Unit) {
        pendingLocationResult = onResult
        if (
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun loadCurrentLocation() {
        lifecycleScope.launch {
            val result = currentLocationProvider.currentLocation()
            pendingLocationResult?.invoke(result)
            pendingLocationResult = null
        }
    }

    private fun openWeatherAttribution() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://open-meteo.com/".toUri(),
            ),
        )
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
        }
    }
}

@Composable
private fun AppContent(
    scheduleRepository: ScheduleRepository,
    weatherRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
    localDataManager: LocalDataManager,
    preferences: UserPreferences,
    navigationRequest: AppNavigationRequest?,
    defaultReminderMinutes: () -> Int?,
    onReminderSaved: () -> Unit,
    onRequestCurrentLocation: ((GeoPoint?) -> Unit) -> Unit,
    onOpenWeatherAttribution: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onOpenWeatherLicense: () -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val clock = Clock.system(zoneId)
    val calendarViewModel: CalendarViewModel = viewModel(
        factory = simpleViewModelFactory {
            CalendarViewModel(
                clock = clock,
                zoneId = zoneId,
                firstDayOfWeek = DayOfWeek.MONDAY,
                almanacEngine = LunarJavaAlmanacEngine(),
            )
        },
    )
    LaunchedEffect(preferences.firstDayOfWeek) {
        calendarViewModel.setFirstDayOfWeek(preferences.firstDayOfWeek)
    }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = simpleViewModelFactory { SettingsViewModel(settingsRepository, localDataManager) },
    )
    val scheduleListViewModel: ScheduleListViewModel = viewModel(
        factory = simpleViewModelFactory {
            ScheduleListViewModel(scheduleRepository)
        },
    )
    val scheduleEditorViewModel: ScheduleEditorViewModel = viewModel(
        factory = simpleViewModelFactory {
            ScheduleEditorViewModel(
                repository = scheduleRepository,
                clock = clock,
                zoneId = zoneId,
                defaultReminderMinutes = defaultReminderMinutes,
            )
        },
    )
    val weatherViewModel: WeatherViewModel = viewModel(
        factory = simpleViewModelFactory {
            WeatherViewModel(weatherRepository)
        },
    )
    val scheduleListState by scheduleListViewModel.uiState.collectAsStateWithLifecycle()
    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    var requestedSchedule by remember { mutableStateOf<Schedule?>(null) }
    var scheduleUnavailable by remember { mutableStateOf(false) }
    var resolvedSequence by remember { mutableLongStateOf(0L) }
    LaunchedEffect(navigationRequest) {
        requestedSchedule = null
        scheduleUnavailable = false
        navigationRequest?.scheduleId?.let { id ->
            requestedSchedule = scheduleRepository.get(id)
            scheduleUnavailable = requestedSchedule == null
            resolvedSequence = navigationRequest.sequence
        }
    }
    TiandiliApp(
        calendarViewModel = calendarViewModel,
        schedules = scheduleListState.schedules,
        weatherSnapshot = weatherState.snapshot,
        clock = clock,
        navigationRequest = navigationRequest,
        scheduleContent = {
            ScheduleFeature(
                listViewModel = scheduleListViewModel,
                editorViewModel = scheduleEditorViewModel,
                onReminderSaved = onReminderSaved,
                requestedSchedule = requestedSchedule,
                externalRequestSequence = resolvedSequence,
                scheduleUnavailable = scheduleUnavailable,
            )
        },
        weatherContent = {
            WeatherScreen(
                state = weatherState,
                now = clock.instant(),
                onRefresh = weatherViewModel::refresh,
                onOpenCityPicker = weatherViewModel::openCityPicker,
                onCloseCityPicker = weatherViewModel::closeCityPicker,
                onSearchQueryChange = weatherViewModel::updateSearchQuery,
                onSearch = weatherViewModel::search,
                onSelectCity = weatherViewModel::selectCity,
                onUseCurrentLocation = {
                    onRequestCurrentLocation { point ->
                        if (point == null) {
                            weatherViewModel.locationUnavailable()
                        } else {
                            weatherViewModel.selectCurrentLocation(point, zoneId)
                        }
                    }
                },
                onOpenAttribution = onOpenWeatherAttribution,
            )
        },
        settingsContent = { openWeather ->
            SettingsFeature(
                viewModel = settingsViewModel,
                currentCity = weatherState.snapshot?.city?.let { city ->
                    listOfNotNull(city.name, city.admin1, city.country).distinct().joinToString(" · ")
                },
                onManageCities = { weatherViewModel.openCityPicker(); openWeather() },
                onRefreshWeather = weatherViewModel::refresh,
                onNotifications = onNotifications,
                onExactAlarms = onExactAlarms,
                onOpenAttribution = onOpenWeatherAttribution,
                onOpenLicense = onOpenWeatherLicense,
            )
        },
    )
}

private fun <T : ViewModel> simpleViewModelFactory(
    create: () -> T,
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <R : ViewModel> create(modelClass: Class<R>): R = create() as R
    }
