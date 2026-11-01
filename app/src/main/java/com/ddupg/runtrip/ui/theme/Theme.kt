package com.ddupg.runtrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val RunTripLightColors = lightColorScheme(
    primary = RunTripOlive,
    onPrimary = Color.White,
    primaryContainer = RunTripPaperVariant,
    onPrimaryContainer = RunTripInk,
    secondary = RunTripOlive,
    onSecondary = RunTripPaper,
    secondaryContainer = RunTripPaperVariant,
    onSecondaryContainer = RunTripOlive,
    tertiary = RunTripOlive,
    onTertiary = Color.White,
    background = RunTripPaper,
    onBackground = RunTripInk,
    surface = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = RunTripPaperVariant,
    surfaceContainerHighest = RunTripPaperVariant,
    surfaceTint = Color.Transparent,
    onSurface = RunTripInk,
    surfaceVariant = RunTripPaperVariant,
    onSurfaceVariant = Color(0xFF646D65),
    outline = Color(0xFF8C9086),
    outlineVariant = Color(0xFFD7DDD8),
)

internal val RunTripDarkColors = darkColorScheme(
    primary = RunTripLimeDark,
    onPrimary = RunTripInk,
    primaryContainer = RunTripLimeDark,
    onPrimaryContainer = RunTripInk,
    secondary = RunTripLimeDark,
    onSecondary = RunTripInk,
    secondaryContainer = Color(0xFF2B3428),
    onSecondaryContainer = Color(0xFFF0F3EF),
    tertiary = RunTripLimeDark,
    onTertiary = RunTripInk,
    background = RunTripNight,
    onBackground = Color(0xFFF0F3EF),
    surface = RunTripNightVariant,
    surfaceContainerLowest = RunTripNight,
    surfaceContainerLow = RunTripNightVariant,
    surfaceContainer = RunTripNightVariant,
    surfaceContainerHigh = Color(0xFF252B26),
    surfaceContainerHighest = Color(0xFF2B3428),
    surfaceTint = Color.Transparent,
    onSurface = Color(0xFFF0F3EF),
    surfaceVariant = RunTripNightVariant,
    onSurfaceVariant = Color(0xFFA5AFA6),
    outline = Color(0xFF888D82),
    outlineVariant = Color(0xFF343C35),
)

@Composable
fun RunTripTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) RunTripDarkColors else RunTripLightColors,
        typography = RunTripTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
