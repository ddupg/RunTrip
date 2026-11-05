package com.ddupg.runtrip.feature.home

import androidx.compose.material3.ColorScheme
import com.ddupg.runtrip.ui.theme.RunTripDarkColors
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ddupg.runtrip.testing.contrastRatio

class RaceFilterResetColorsTest {
    @Test
    fun resetTextMaintainsAccessibleContrastInDarkTheme() {
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

    private companion object {
        const val TEXT_CONTRAST = 4.5
    }
}
