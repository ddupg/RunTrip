package com.ddupg.runtrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = RunTripLime,
    onPrimary = RunTripInk,
    primaryContainer = RunTripLime,
    onPrimaryContainer = RunTripInk,
    background = RunTripPaper,
    onBackground = RunTripInk,
    surface = RunTripPaper,
    onSurface = RunTripInk,
    surfaceVariant = RunTripPaperVariant,
    onSurfaceVariant = Color(0xFF5D6158),
    outline = Color(0xFF8C9086),
    outlineVariant = Color(0xFFD4D7CF),
)

private val DarkColors = darkColorScheme(
    primary = RunTripLimeDark,
    onPrimary = RunTripInk,
    primaryContainer = RunTripLimeDark,
    onPrimaryContainer = RunTripInk,
    background = RunTripNight,
    onBackground = Color(0xFFF2F4ED),
    surface = RunTripNight,
    onSurface = Color(0xFFF2F4ED),
    surfaceVariant = RunTripNightVariant,
    onSurfaceVariant = Color(0xFFB9BDB2),
    outline = Color(0xFF888D82),
    outlineVariant = Color(0xFF35382F),
)

@Composable
fun RunTripTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RunTripTypography,
        content = content,
    )
}
