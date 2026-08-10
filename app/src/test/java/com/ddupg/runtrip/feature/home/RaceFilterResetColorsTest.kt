package com.ddupg.runtrip.feature.home

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ddupg.runtrip.ui.theme.RunTripDarkColors
import com.ddupg.runtrip.ui.theme.RunTripLightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceFilterResetColorsTest {
    @Test
    fun resetTextMaintainsAccessibleContrastInBothThemes() {
        assertAccessible(RunTripLightColors)
        assertAccessible(RunTripDarkColors)
    }

    private fun assertAccessible(colorScheme: ColorScheme) {
        val colors = colorScheme.raceFilterResetColors()
        assertTrue(
            "enabled reset text contrast",
            contrastRatio(colors.enabledContentColor, colorScheme.surface) >= TEXT_CONTRAST,
        )
        assertTrue(
            "disabled reset text contrast",
            contrastRatio(colors.disabledContentColor, colorScheme.surface) >= TEXT_CONTRAST,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(first.relativeLuminance(), second.relativeLuminance())
        val darker = min(first.relativeLuminance(), second.relativeLuminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        0.2126 * red.linearized() +
            0.7152 * green.linearized() +
            0.0722 * blue.linearized()

    private fun Float.linearized(): Double =
        if (this <= 0.04045f) {
            this / 12.92
        } else {
            ((this + 0.055) / 1.055).pow(2.4)
        }

    private companion object {
        const val TEXT_CONTRAST = 4.5
    }
}
