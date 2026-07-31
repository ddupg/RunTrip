package com.ddupg.runtrip.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.ui.theme.RunTripDarkColors
import com.ddupg.runtrip.ui.theme.RunTripInk
import com.ddupg.runtrip.ui.theme.RunTripLightColors
import com.ddupg.runtrip.ui.theme.RunTripLime
import com.ddupg.runtrip.ui.theme.RunTripLimeDark
import com.ddupg.runtrip.ui.theme.RunTripNightVariant
import com.ddupg.runtrip.ui.theme.RunTripOlive
import com.ddupg.runtrip.ui.theme.RunTripPaper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceStatusBadgeTest {
    @Test
    fun everyStatusHasAnExplicitVisualStyleAndSymbol() {
        assertEquals(
            listOf(
                RaceStatusVisualSpec(RaceStatusBadgeStyle.PENDING, RaceStatusSymbol.EYE),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.PENDING, RaceStatusSymbol.EDIT),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.PENDING, RaceStatusSymbol.HOURGLASS),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.WON, RaceStatusSymbol.STAR),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.MUTED, RaceStatusSymbol.MINUS_CIRCLE),
                RaceStatusVisualSpec(
                    RaceStatusBadgeStyle.CONFIRMED,
                    RaceStatusSymbol.CHECK_CIRCLE,
                ),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.MUTED, RaceStatusSymbol.CANCEL),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.FINISHED, RaceStatusSymbol.FLAG),
            ),
            RaceStatus.entries.map { it.visualSpec() },
        )
    }

    @Test
    fun lightPaletteUsesBrandFillForConfirmedAndOliveForWon() {
        val confirmed = RunTripLightColors.raceStatusBadgeColors(
            RaceStatusBadgeStyle.CONFIRMED,
        )
        val won = RunTripLightColors.raceStatusBadgeColors(RaceStatusBadgeStyle.WON)
        val muted = RunTripLightColors.raceStatusBadgeColors(RaceStatusBadgeStyle.MUTED)

        assertEquals(RunTripLime, confirmed.containerColor)
        assertEquals(RunTripInk, confirmed.contentColor)
        assertEquals(RunTripOlive, won.containerColor)
        assertEquals(RunTripPaper, won.contentColor)
        assertEquals(Color.Transparent, muted.containerColor)
        assertEquals(null, muted.borderColor)
        assertAccessible(RunTripLightColors)
    }

    @Test
    fun darkPaletteKeepsConfirmedBrightAndWonSecondary() {
        val confirmed = RunTripDarkColors.raceStatusBadgeColors(
            RaceStatusBadgeStyle.CONFIRMED,
        )
        val won = RunTripDarkColors.raceStatusBadgeColors(RaceStatusBadgeStyle.WON)

        assertEquals(RunTripLimeDark, confirmed.containerColor)
        assertEquals(RunTripInk, confirmed.contentColor)
        assertEquals(RunTripNightVariant, won.containerColor)
        assertEquals(RunTripLimeDark, won.borderColor)
        assertAccessible(RunTripDarkColors)
    }

    private fun assertAccessible(colorScheme: ColorScheme) {
        RaceStatusBadgeStyle.entries.forEach { style ->
            val colors = colorScheme.raceStatusBadgeColors(style)
            val effectiveContainer = if (colors.containerColor == Color.Transparent) {
                colorScheme.surface
            } else {
                colors.containerColor
            }
            assertTrue(
                "$style text contrast",
                contrastRatio(colors.contentColor, effectiveContainer) >= TEXT_CONTRAST,
            )
            colors.borderColor?.let { borderColor ->
                assertTrue(
                    "$style border contrast",
                    contrastRatio(borderColor, colorScheme.surface) >= NON_TEXT_CONTRAST,
                )
            }
        }
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
