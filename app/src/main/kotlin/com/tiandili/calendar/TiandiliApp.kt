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
    TopLevelDestination("calendar", "月历", Icons.Outlined.CalendarMonth),
    TopLevelDestination("schedule", "日程", Icons.AutoMirrored.Outlined.EventNote),
    TopLevelDestination("weather", "天气", Icons.Outlined.Cloud),
    TopLevelDestination("settings", "设置", Icons.Outlined.Settings),
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
    scheduleContent: @Composable () -> Unit = {
        EmptyDestination(text = "暂无日程")
    },
    weatherContent: @Composable () -> Unit = {
        EmptyDestination(text = "尚未选择城市")
    },
    settingsContent: @Composable (() -> Unit) -> Unit = {
        EmptyDestination(text = "周起始日 · 周一")
    },
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(navigationRequest) {
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
        topBar = {
            TopAppBar(
                title = { Text("天地历") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
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
    settingsContent: @Composable (() -> Unit) -> Unit,
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val scheduleDates = remember(schedules, calendarState.cells) {
        val window = calendarState.cells.first().date..calendarState.cells.last().date
        schedules.flatMapTo(mutableSetOf()) { schedule ->
            schedule.occurrenceDates(window)
        }
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
                scheduleDates = scheduleDates,
                weather = selectedWeather,
                onPreviousMonth = calendarViewModel::previousMonth,
                onNextMonth = calendarViewModel::nextMonth,
                onReturnToToday = calendarViewModel::returnToToday,
                onDateSelected = calendarViewModel::selectDate,
            )
        }
        composable("schedule") {
            scheduleContent()
        }
        composable("weather") {
            weatherContent()
        }
        composable("settings") {
            settingsContent {
                navController.navigate("weather") { launchSingleTop = true }
            }
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
