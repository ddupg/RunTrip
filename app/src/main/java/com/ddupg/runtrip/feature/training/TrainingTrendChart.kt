package com.ddupg.runtrip.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ddupg.runtrip.data.model.TrainingSport
import java.time.LocalDate
import kotlin.math.ceil

@Composable
internal fun TrainingTrendChart(
    points: List<TrainingPeriodSummary>, period: TrainingPeriod, sport: TrainingSport?,
    selected: LocalDate, onSelect: (LocalDate) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.background
    val textStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val divisor = if (sport == TrainingSport.SWIMMING) 1.0 else 1000.0
    val values = points.map { point -> point.distanceMeters?.takeIf { point.isComplete }?.div(divisor) }
    val peak = values.filterNotNull().maxOrNull() ?: 0.0
    val step = when {
        peak <= 10 -> 5.0
        peak <= 100 -> 10.0
        peak <= 500 -> 50.0
        peak <= 2000 -> 250.0
        else -> 1000.0
    }
    val max = maxOf(step * 2, ceil(peak / step) * step)
    val left = 40.dp * fontScale
    val bottom = 24.dp * fontScale
    val top = 22.dp * fontScale
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, left + 8.dp + (points.size * 25).dp * fontScale)
        LaunchedEffect(selected, points.first().start, width, maxWidth, scroll.maxValue) {
            val index = points.indexOfFirst { it.start == selected }
            if (index >= 0) {
                val slot = with(density) { (width - left - 8.dp).toPx() } / points.size
                val start = with(density) { left.toPx() } + slot * index
                val end = start + slot
                val viewport = with(density) { maxWidth.toPx() }
                if (start < scroll.value) scroll.scrollTo(start.toInt())
                else if (end > scroll.value + viewport) scroll.scrollTo(kotlin.math.ceil(end - viewport).toInt())
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Box(Modifier.width(width).height(160.dp + top + bottom)) {
            Canvas(Modifier.fillMaxSize()) {
                val plotWidth = size.width - left.toPx() - 8.dp.toPx()
                val plotHeight = size.height - top.toPx() - bottom.toPx()
                val baseline = top.toPx() + plotHeight
                fun x(index: Int) = left.toPx() + plotWidth * (index + 0.5f) / points.size
                fun y(value: Double) = (baseline - value / max * plotHeight).toFloat()
                listOf(0.0, max / 2, max).forEach { tick ->
                    drawLine(grid, Offset(left.toPx(), y(tick)), Offset(size.width, y(tick)))
                    val label = measurer.measure(trainingChartNumber(tick), textStyle.copy(color = muted))
                    drawText(label, topLeft = Offset(left.toPx() - label.size.width - 6.dp.toPx(), y(tick) - label.size.height / 2))
                }
                points.forEachIndexed { i, point ->
                    val value = values[i]
                    val active = point.start == selected
                    val label = if (period == TrainingPeriod.MONTH) "${point.start.monthValue}月"
                        else "${point.start.monthValue}/${point.start.dayOfMonth}"
                    val measured = measurer.measure(label, textStyle.copy(color = if (active) accent else muted))
                    drawText(measured, topLeft = Offset(x(i) - measured.size.width / 2, baseline + 6.dp.toPx()))
                    if (value != null) {
                        val dot = Offset(x(i), y(value))
                        if (active) drawLine(grid, Offset(x(i), baseline), dot)
                        if (i > 0 && values[i - 1] != null) {
                            drawLine(accent, Offset(x(i - 1), y(values[i - 1]!!)), dot, strokeWidth = 2.dp.toPx(),
                                pathEffect = if (point.isOngoing) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
                        }
                        val radius = if (active) 5.dp.toPx() else 3.dp.toPx()
                        if (point.isOngoing) {
                            drawCircle(background, radius, dot)
                            drawCircle(accent, radius, dot, style = Stroke(2.dp.toPx()))
                        } else drawCircle(accent, radius, dot)
                        if (active) {
                            val amount = measurer.measure(trainingChartNumber(value), textStyle.copy(color = accent))
                            drawText(amount, topLeft = Offset((x(i) - amount.size.width / 2).coerceIn(0f, size.width - amount.size.width),
                                y(value) - amount.size.height - 8.dp.toPx()))
                        }
                    } else {
                        val missing = measurer.measure("—", textStyle.copy(color = muted))
                        drawText(missing, topLeft = Offset(x(i) - missing.size.width / 2, baseline - missing.size.height - 4.dp.toPx()))
                    }
                }
            }
            // Each period is an accessible selection target, including gaps in the plotted data.
            Row(Modifier.fillMaxSize().padding(start = left, end = 8.dp).selectableGroup()) {
                points.forEach { point ->
                    val value = if (point.isComplete) "${statisticsDistance(point.distanceMeters, sport)} ${if (sport == TrainingSport.SWIMMING) "m" else "km"}"
                        else "数据不完整"
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .semantics { contentDescription = "${point.start}，$value${if (point.isOngoing) "，进行中" else ""}" }
                        .selectable(point.start == selected, role = Role.RadioButton, onClick = { onSelect(point.start) }))
                }
            }
        }
        }
    }
}

private fun trainingChartNumber(value: Double): String = if (value % 1.0 == 0.0) "${value.toLong()}"
    else String.format(java.util.Locale.ROOT, "%.1f", value)
