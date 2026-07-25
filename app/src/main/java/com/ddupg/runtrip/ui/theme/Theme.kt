package com.ddupg.runtrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val RunTripLightColors = lightColorScheme(
    primary = RunTripLime,
    onPrimary = RunTripInk,
    primaryContainer = RunTripLime,
    onPrimaryContainer = RunTripInk,
    secondary = RunTripOlive,
    onSecondary = RunTripPaper,
    secondaryContainer = RunTripOlive,
    onSecondaryContainer = RunTripPaper,
    tertiary = RunTripLime,
    onTertiary = RunTripInk,
    background = RunTripPaper,
    onBackground = RunTripInk,
    surface = RunTripPaper,
    onSurface = RunTripInk,
    surfaceVariant = RunTripPaperVariant,
    onSurfaceVariant = Color(0xFF5D6158),
    outline = Color(0xFF8C9086),
    outlineVariant = Color(0xFFD4D7CF),
)

internal val RunTripDarkColors = darkColorScheme(
    primary = RunTripLimeDark,
    onPrimary = RunTripInk,
    primaryContainer = RunTripLimeDark,
    onPrimaryContainer = RunTripInk,
    secondary = RunTripLimeDark,
    onSecondary = RunTripInk,
    secondaryContainer = RunTripNightVariant,
    onSecondaryContainer = Color(0xFFF2F4ED),
    tertiary = RunTripLimeDark,
    onTertiary = RunTripInk,
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
        colorScheme = if (darkTheme) RunTripDarkColors else RunTripLightColors,
        typography = RunTripTypography,
        content = content,
    )
}
