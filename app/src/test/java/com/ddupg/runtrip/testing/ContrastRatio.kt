package com.ddupg.runtrip.testing

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal fun contrastRatio(first: Color, second: Color): Double {
    val lighter = max(first.relativeLuminance(), second.relativeLuminance())
    val darker = min(first.relativeLuminance(), second.relativeLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double =
    0.2126 * red.linearized() + 0.7152 * green.linearized() + 0.0722 * blue.linearized()

private fun Float.linearized(): Double =
    if (this <= 0.04045f) this / 12.92 else ((this + 0.055) / 1.055).pow(2.4)
