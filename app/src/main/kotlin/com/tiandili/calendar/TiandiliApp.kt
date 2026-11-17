package com.tiandili.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tiandili.calendar.feature.calendar.CalendarScreen
import com.tiandili.calendar.feature.calendar.CalendarViewModel
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.occurrenceDates
import com.tiandili.calendar.domain.schedule.ScheduleKind
import com.tiandili.calendar.feature.almanac.AlmanacScreen
import com.tiandili.calendar.feature.mine.MineScreen
import com.tiandili.calendar.feature.explore.ExploreScreen
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.ui.PageHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tiandili.calendar.domain.weather.CalendarWeatherResolver
import com.tiandili.calendar.domain.weather.CalendarWeatherResult
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Clock

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TopLevelDestinations = listOf(
    TopLevelDestination("calendar", "日历", Icons.Outlined.CalendarMonth),
    TopLevelDestination("almanac", "黄历", Icons.Outlined.AutoAwesome),
    TopLevelDestination("weather", "天气", Icons.Outlined.Cloud),
    TopLevelDestination("explore", "发现", Icons.Outlined.Explore),
    TopLevelDestination("mine", "我的", Icons.Outlined.PersonOutline),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiandiliApp(
    calendarViewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
    schedules: List<Schedule> = emptyList(),
    weatherSnapshot: WeatherSnapshot? = null,
    clock: Clock = Clock.systemDefaultZone(),
    navigationRequest: AppNavigationRequest? = null,
    preferences: UserPreferences = UserPreferences(),
    cancelledOccurrences: Map<Long, Set<java.time.LocalDate>> = emptyMap(),
    onRecordsRequested: (ScheduleKind?, Boolean) -> Unit = { _, _ -> },
    onRecordRequested: (Long) -> Unit = {},
    onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    onSetCompleted: (Long, Boolean) -> Unit = { _, _ -> },
    recordError: String? = null,
    scheduleEditorVisible: Boolean = false,
    scheduleContent: @Composable () -> Unit = {
        EmptyDestination(text = "暂无日程")
    },
    weatherContent: @Composable () -> Unit = {
        EmptyDestination(text = "尚未选择城市")
    },
    settingsContent: @Composable (() -> Unit, () -> Unit) -> Unit = { _, _ ->
        EmptyDestination(text = "周起始日 · 周一")
    },
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(navigationRequest, currentRoute != null) {
        // On a cold launch the NavHost installs its graph after the first composition.
        if (currentRoute == null) return@LaunchedEffect
        navigationRequest?.let { request ->
            if (request.destination == "calendar") calendarViewModel.returnToToday()
            navController.navigate(request.destination) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute != "schedule" || !scheduleEditorVisible) {
            Surface(shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 5.dp)) {
                TopLevelDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    Box(Modifier.weight(1f).padding(horizontal = 3.dp).heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }), contentAlignment = Alignment.Center) {
                        Text(destination.label, style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1)
                    }
                }
            }
            }
            }
        },
    ) { contentPadding ->
        TiandiliNavHost(
            navController = navController,
            calendarViewModel = calendarViewModel,
            contentPadding = contentPadding,
            schedules = schedules,
            scheduleContent = scheduleContent,
            weatherSnapshot = weatherSnapshot,
            clock = clock,
            weatherContent = weatherContent,
            settingsContent = settingsContent,
            preferences = preferences,
            cancelledOccurrences = cancelledOccurrences,
            onRecordsRequested = onRecordsRequested,
            onRecordRequested = onRecordRequested,
            onSetEnabled = onSetEnabled,
            onSetCompleted = onSetCompleted,
            recordError = recordError,
        )
    }
}

@Composable
private fun TiandiliNavHost(
    navController: NavHostController,
    calendarViewModel: CalendarViewModel,
    contentPadding: PaddingValues,
    schedules: List<Schedule>,
    scheduleContent: @Composable () -> Unit,
    weatherSnapshot: WeatherSnapshot?,
    clock: Clock,
    weatherContent: @Composable () -> Unit,
    settingsContent: @Composable (() -> Unit, () -> Unit) -> Unit,
    preferences: UserPreferences,
    cancelledOccurrences: Map<Long, Set<java.time.LocalDate>>,
    onRecordsRequested: (ScheduleKind?, Boolean) -> Unit,
    onRecordRequested: (Long) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    onSetCompleted: (Long, Boolean) -> Unit,
    recordError: String?,
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val scheduleLabels = remember(schedules, calendarState.cells, cancelledOccurrences) {
        val window = calendarState.cells.first().date..calendarState.cells.last().date
        buildMap<java.time.LocalDate, MutableList<String>> {
            schedules.filter { it.calendarEligible }.forEach { schedule ->
                schedule.occurrenceDates(window, cancelledOccurrences[schedule.id].orEmpty()).forEach { date ->
                    getOrPut(date) { mutableListOf() }.add(schedule.title)
                }
            }
        }
    }
    fun openRecords(kind: ScheduleKind?, create: Boolean) {
        onRecordsRequested(kind, create)
        navController.navigate("schedule") { launchSingleTop = true }
    }
    val selectedWeather = weatherSnapshot?.daily?.value?.let { forecasts ->
        CalendarWeatherResolver.resolve(
            city = weatherSnapshot.city,
            forecasts = forecasts,
            date = calendarState.selectedDate,
            clock = clock,
        )
    } as? CalendarWeatherResult.Available

    NavHost(
        navController = navController,
        startDestination = "calendar",
        modifier = Modifier.padding(contentPadding),
    ) {
        composable("calendar") {
            CalendarScreen(
                state = calendarState,
                scheduleLabels = scheduleLabels,
                weather = selectedWeather,
                onPreviousMonth = calendarViewModel::previousMonth,
                onNextMonth = calendarViewModel::nextMonth,
                onReturnToToday = calendarViewModel::returnToToday,
                onDateSelected = calendarViewModel::selectDate,
                onAdd = { openRecords(ScheduleKind.SCHEDULE, true) },
                onOpenAlmanac = { navController.navigate("almanac") { launchSingleTop = true } },
                onOpenWeather = { navController.navigate("weather") { launchSingleTop = true } },
                cityName = weatherSnapshot?.city?.name,
            )
        }
        composable("almanac") { AlmanacScreen(calendarState.selectedAlmanac,
            calendarViewModel::selectDate, calendarViewModel::returnToToday) }
        composable("explore") { ExploreScreen(calendarState.selectedDate) {
            calendarViewModel.selectDate(it)
            navController.navigate("calendar") { launchSingleTop = true }
        } }
        composable("mine") {
            MineScreen(schedules, preferences, { openRecords(it, false) },
                { navController.navigate("settings") }, { navController.navigate("explore") },
                clock = clock, cancelledOccurrences = cancelledOccurrences,
                onCreateKind = { openRecords(it, true) },
                onOpenRecord = {
                    onRecordRequested(it)
                    navController.navigate("schedule") { launchSingleTop = true }
                },
                onSetEnabled = onSetEnabled, onSetCompleted = onSetCompleted, error = recordError)
        }
        composable("schedule") {
            scheduleContent()
        }
        composable("weather") {
            weatherContent()
        }
        composable("settings") {
            settingsContent({
                navController.navigate("weather") { launchSingleTop = true }
            }, { navController.popBackStack() })
        }
    }
}

@Composable
private fun EmptyDestination(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}
