package com.ddupg.runtrip.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.ui.theme.RunTripDarkColors
import com.ddupg.runtrip.ui.theme.RunTripInk
import com.ddupg.runtrip.ui.theme.RunTripLimeDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ddupg.runtrip.testing.contrastRatio

class RaceStatusBadgeTest {
    @Test
    fun everyStatusHasAnExplicitVisualStyleAndSymbol() {
        assertEquals(
            listOf(
                RaceStatusVisualSpec(RaceStatusBadgeStyle.NEUTRAL, RaceStatusSymbol.EYE),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.PENDING, RaceStatusSymbol.EDIT),
                RaceStatusVisualSpec(RaceStatusBadgeStyle.ATTENTION, RaceStatusSymbol.HOURGLASS),
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
    fun onlyAttentionConfirmedAndWonUseFilledContainers() {
        RaceStatusBadgeStyle.entries.forEach { style ->
            val colors = RunTripDarkColors.raceStatusBadgeColors(style)
            val shouldBeFilled = style == RaceStatusBadgeStyle.CONFIRMED ||
                style == RaceStatusBadgeStyle.ATTENTION ||
                style == RaceStatusBadgeStyle.WON

            assertEquals("$style fill", shouldBeFilled, colors.containerColor != Color.Transparent)
            if (!shouldBeFilled) {
                assertEquals("$style border", null, colors.borderColor)
            }
        }
    }

    @Test
    fun drawPendingUsesSameHighlightAsConfirmed() {
        assertEquals(
            RunTripDarkColors.raceStatusBadgeColors(RaceStatusBadgeStyle.CONFIRMED),
            RunTripDarkColors.raceStatusBadgeColors(RaceStatusBadgeStyle.ATTENTION),
        )
    }

    @Test
    fun darkPaletteKeepsConfirmedBrightAndWonSecondary() {
        val confirmed = RunTripDarkColors.raceStatusBadgeColors(
            RaceStatusBadgeStyle.CONFIRMED,
        )
        val won = RunTripDarkColors.raceStatusBadgeColors(RaceStatusBadgeStyle.WON)

        assertEquals(RunTripLimeDark, confirmed.containerColor)
        assertEquals(RunTripInk, confirmed.contentColor)
        assertEquals(RunTripDarkColors.secondaryContainer, won.containerColor)
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

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val NON_TEXT_CONTRAST = 3.0
    }
}
