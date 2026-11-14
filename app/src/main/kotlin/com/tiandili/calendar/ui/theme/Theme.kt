package com.tiandili.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.tiandili.calendar.data.settings.ThemeMode

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
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode == ThemeMode.DARK ||
            (themeMode == ThemeMode.SYSTEM && darkTheme)) DarkColors else LightColors,
        typography = TiandiliTypography,
        content = content,
    )
}
