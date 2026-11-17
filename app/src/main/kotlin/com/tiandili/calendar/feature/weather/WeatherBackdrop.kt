package com.tiandili.calendar.feature.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun WeatherScene(isDay: Boolean, content: @Composable () -> Unit) {
    val colors = if (isDay) listOf(Color(0xFF326CA9), Color(0xFF1E4B75))
        else listOf(Color(0xFF171F50), Color(0xFF32315E))
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color.White, onPrimary = colors[0], secondary = Color(0xFFFFD078),
        background = colors[0], surface = colors[0], onSurface = Color.White,
        surfaceContainer = colors[1], onSurfaceVariant = Color(0xFFE4ECF8),
        outline = Color.White.copy(alpha = 0.22f), error = Color(0xFFFFD7DA),
    )) {
        Surface(color = Color.Transparent, contentColor = Color.White) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
                Canvas(Modifier.fillMaxSize()) {
                    if (isDay) {
                        drawCircle(Color.White.copy(alpha = 0.08f), size.width * .25f,
                            Offset(size.width * .84f, size.height * .16f))
                        drawCircle(Color.White.copy(alpha = 0.06f), size.width * .36f,
                            Offset(size.width * .8f, size.height * .22f))
                    } else {
                        repeat(55) { index ->
                            drawCircle(Color.White.copy(alpha = .10f + index % 4 * .05f), 1.3f,
                                Offset(size.width * ((index * 37 % 101) / 101f),
                                    size.height * ((index * 19 % 103) / 103f)))
                        }
                    }
                    val ridge = Path().apply {
                        moveTo(0f, size.height * .94f)
                        lineTo(size.width * .22f, size.height * .83f)
                        lineTo(size.width * .43f, size.height * .9f)
                        lineTo(size.width * .66f, size.height * .77f)
                        lineTo(size.width, size.height * .89f)
                        lineTo(size.width, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(ridge, Color.Black.copy(alpha = .09f))
                }
                content()
            }
        }
    }
}
