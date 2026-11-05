package com.ddupg.runtrip.ui.theme

import com.ddupg.runtrip.ui.components.withRunTripControlColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ddupg.runtrip.testing.contrastRatio

class RunTripControlColorsTest {
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

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val NON_TEXT_CONTRAST = 3.0
    }
}
