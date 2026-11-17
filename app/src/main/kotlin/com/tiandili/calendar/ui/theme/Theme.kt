package com.tiandili.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.CalendarAccent
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = RicePaper,
    secondary = MineralBlue,
    onSecondary = RicePaper,
    tertiary = Cinnabar,
    onTertiary = RicePaper,
    background = Paper,
    onBackground = Ink,
    surface = RicePaper,
    onSurface = Ink,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = DarkJade,
    onPrimary = Night,
    secondary = DarkMineralBlue,
    onSecondary = Night,
    tertiary = DarkCinnabar,
    onTertiary = Night,
    background = Night,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = ColorTokens.DarkSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = ColorTokens.DarkOutline,
)

private object ColorTokens {
    val LightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5ECE7)
    val DarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF28332E)
    val DarkOutline = androidx.compose.ui.graphics.Color(0xFF48564F)
}

@Composable
fun TiandiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: CalendarAccent = CalendarAccent.RED,
    content: @Composable () -> Unit,
) {
    val dark = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && darkTheme)
    val primary = when (accent) {
        CalendarAccent.RED -> if (dark) DarkCinnabar else Color(0xFFC83D39)
        CalendarAccent.JADE -> if (dark) DarkJade else Jade
        CalendarAccent.BLUE -> if (dark) DarkMineralBlue else Color(0xFF366FA9)
    }
    MaterialTheme(
        colorScheme = (if (dark) DarkColors else LightColors).copy(primary = primary,
            background = if (dark) Night else Color(0xFFF5F5F5),
            surface = if (dark) NightSurface else Color.White,
            onPrimary = if (dark) Night else Color.White),
        typography = TiandiliTypography,
        content = content,
    )
}
