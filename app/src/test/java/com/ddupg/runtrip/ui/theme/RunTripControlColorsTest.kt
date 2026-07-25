package com.ddupg.runtrip.ui.theme

import androidx.compose.ui.graphics.Color
import com.ddupg.runtrip.ui.components.withRunTripControlColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RunTripControlColorsTest {
    @Test
    fun lightControlsUseOliveWithPaperContent() {
        val controls = RunTripLightColors.withRunTripControlColors()

        assertEquals(RunTripOlive, controls.primary)
        assertEquals(RunTripPaper, controls.onPrimary)
        assertTrue(contrastRatio(controls.primary, RunTripPaper) >= NON_TEXT_CONTRAST)
        assertTrue(contrastRatio(controls.onPrimary, controls.primary) >= TEXT_CONTRAST)
        assertTrue(
            contrastRatio(
                RunTripLightColors.onSecondaryContainer,
                RunTripLightColors.secondaryContainer,
            ) >= TEXT_CONTRAST,
        )
        assertTrue(contrastRatio(RunTripLime, RunTripOlive) >= NON_TEXT_CONTRAST)
    }

    @Test
    fun darkControlsUseLimeWithInkContent() {
        val controls = RunTripDarkColors.withRunTripControlColors()

        assertEquals(RunTripLimeDark, controls.primary)
        assertEquals(RunTripInk, controls.onPrimary)
        assertTrue(contrastRatio(controls.primary, RunTripNight) >= NON_TEXT_CONTRAST)
        assertTrue(contrastRatio(controls.onPrimary, controls.primary) >= TEXT_CONTRAST)
        assertTrue(
            contrastRatio(
                RunTripDarkColors.onSecondaryContainer,
                RunTripDarkColors.secondaryContainer,
            ) >= TEXT_CONTRAST,
        )
        assertTrue(
            contrastRatio(
                RunTripDarkColors.tertiary,
                RunTripDarkColors.secondaryContainer,
            ) >= NON_TEXT_CONTRAST,
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
        const val NON_TEXT_CONTRAST = 3.0
    }
}
