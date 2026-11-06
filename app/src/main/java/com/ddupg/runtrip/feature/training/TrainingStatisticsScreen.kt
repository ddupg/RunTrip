package com.ddupg.runtrip.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.feature.preferences.EquivalentDistanceHelpDialog
import com.ddupg.runtrip.feature.preferences.trainingPreferenceNumber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TrainingStatisticsContent(
    records: List<TrainingActivity>, preferences: TrainingPreferences, zone: ZoneId,
    today: LocalDate, historyComplete: Boolean,
) {
    var sport by rememberSaveable { mutableStateOf<TrainingSport?>(null) }
    var period by rememberSaveable { mutableStateOf(TrainingPeriod.WEEK) }
    val currentStart = period.start(today)
    var selectedDay by rememberSaveable(period, today) { mutableLongStateOf(currentStart.toEpochDay()) }
    var windowEndDay by rememberSaveable(period, today) { mutableLongStateOf(currentStart.toEpochDay()) }
    var help by rememberSaveable { mutableStateOf(false) }
    val index = remember(records, preferences, zone, today, period, historyComplete) {
        trainingStatistics(records, period, preferences, zone, today, historyComplete)
    }
    val earliest = remember(records, zone, period, currentStart) {
        records.minOfOrNull { trainingDate(it, zone) }?.let(period::start)?.coerceAtMost(currentStart) ?: currentStart
    }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val windowEnd = LocalDate.ofEpochDay(windowEndDay)
    val size = if (period == TrainingPeriod.WEEK) 8 else 12
    val summary = index.summary(selected, sport)
    val previous = index.summary(period.shift(selected, -1), sport)
    val points = index.window(windowEnd, sport, size)
    fun moveTo(date: LocalDate) {
        selectedDay = date.toEpochDay()
        if (date < points.first().start) windowEndDay = period.shift(date, size - 1).toEpochDay()
        if (date > windowEnd) windowEndDay = date.toEpochDay()
    }
    if (help) EquivalentDistanceHelpDialog(preferences, onDismiss = { help = false })
    LazyColumn(Modifier.fillMaxSize().testTag("training-statistics"), contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TrainingTextTabs(listOf(null to "综合", TrainingSport.RUNNING to "跑步",
                TrainingSport.CYCLING to "骑行", TrainingSport.SWIMMING to "游泳"), sport, { sport = it })
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.selectableGroup()) {
                        TrainingPeriod.entries.forEach { value ->
                            Box(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                                .background(if (period == value) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                                .selectable(period == value, role = Role.Tab, onClick = { period = value }),
                                contentAlignment = Alignment.Center) {
                                Text(if (value == TrainingPeriod.WEEK) "周" else "月", style = MaterialTheme.typography.bodyLarge,
                                    color = if (period == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { moveTo(period.shift(selected, -1)) }, enabled = selected > earliest) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一${periodLabel(period)}")
                        }
                        Text(statisticsDateLabel(summary, period), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { moveTo(period.shift(selected, 1)) }, enabled = selected < currentStart) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一${periodLabel(period)}")
                        }
                    }
                }
                val years = if (summary.start.year == summary.end.year) "${summary.start.year}"
                    else "${summary.start.year}–${summary.end.year}"
                Text(if (summary.isOngoing) "$years · 本${periodLabel(period)}进行中" else years,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sport == null) "等效跑量" else "累计距离", style = MaterialTheme.typography.bodyMedium)
                    if (sport == null) IconButton(onClick = { help = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.HelpOutline, "等效跑量说明", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val unit = if (sport == TrainingSport.SWIMMING) "m" else "km"
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statisticsDistance(summary.distanceMeters, sport),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp))
                    Text(unit, modifier = Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
                }
                if (sport == null) Text("按设定比例折算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    !historyComplete -> Text("记录尚未完整获取，统计不完整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    summary.missingDistanceCount > 0 -> Text("${summary.missingDistanceCount} 次训练未记录距离，统计不完整",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GrowthLabel(summary, previous, sport, period)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryMetric("总时长", statisticsDuration(summary.durationSeconds), Modifier.weight(1f))
                    SummaryMetric("训练次数", "${summary.count}次", Modifier.weight(1f))
                }
            }
        }
        if (sport == null) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("折算构成", style = MaterialTheme.typography.titleMedium)
                    Text("等效跑量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                summary.contributions.forEach { contribution ->
                    val factor = when (contribution.sport) {
                        TrainingSport.RUNNING -> 1.0
                        TrainingSport.CYCLING -> preferences.cyclingFactor
                        TrainingSport.SWIMMING -> preferences.swimmingFactor
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(sportLabel(contribution.sport), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
                        Text("${statisticsDistance(contribution.distanceMeters, null)} km × ${trainingPreferenceNumber(factor)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.5f))
                        Text("${statisticsDistance(contribution.equivalentDistanceMeters, null)} km",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (sport == TrainingSport.RUNNING) item {
            RunningGrowthReference(summary, previous, period, preferences)
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${periodLabel(period)}趋势", style = MaterialTheme.typography.titleMedium)
                    Text("近${size}${if (period == TrainingPeriod.WEEK) "周" else "个月"} · ${if (sport == TrainingSport.SWIMMING) "m" else "km"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TrainingTrendChart(points, period, sport, selected, onSelect = { selectedDay = it.toEpochDay() })
                Text("空心点为未结束周期的累计量", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GrowthLabel(summary: TrainingPeriodSummary, previous: TrainingPeriodSummary, sport: TrainingSport?, period: TrainingPeriod) {
    val comparison = "较上${periodLabel(period)}${if (summary.isOngoing) if (period == TrainingPeriod.WEEK) "整周" else "整月" else ""}"
    val text = when {
        !summary.isComplete || !previous.isComplete -> "$comparison — · 数据不完整"
        previous.distanceMeters == 0.0 -> if (summary.distanceMeters == 0.0) "本期与上期均无训练距离" else "上期距离为 0，增长率不适用"
        else -> "$comparison ${String.format(Locale.ROOT, "%+.1f%%", summary.growthPercent)}"
    }
    val exceeds = sport == TrainingSport.RUNNING && summary.referenceDistanceMeters != null &&
        summary.isComplete && (summary.distanceMeters ?: 0.0) > summary.referenceDistanceMeters
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = if (exceeds) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun RunningGrowthReference(summary: TrainingPeriodSummary, previous: TrainingPeriodSummary,
    period: TrainingPeriod, preferences: TrainingPreferences) {
    val reference = summary.referenceDistanceMeters
    val current = summary.distanceMeters
    val percent = if (period == TrainingPeriod.WEEK) preferences.runningWeeklyGrowthPercent else preferences.runningMonthlyGrowthPercent
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.outlineVariant
    val foreground = MaterialTheme.colorScheme.onSurface
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("增长参考线", style = MaterialTheme.typography.titleMedium)
            Text("${periodLabel(period)}增量 +${trainingPreferenceNumber(percent)}%", style = MaterialTheme.typography.bodySmall)
        }
        if (reference != null && current != null && summary.isComplete) {
            Text("参考线 ${statisticsDistance(reference, TrainingSport.RUNNING)} km", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val max = maxOf(reference, current) * 1.1
                val end = (current / max * size.width).toFloat()
                val tick = (reference / max * size.width).toFloat()
                val y = size.height / 2
                drawLine(track, Offset(0f, y), Offset(size.width, y), 8.dp.toPx(), StrokeCap.Round)
                drawLine(primary, Offset(0f, y), Offset(minOf(end, tick), y), 8.dp.toPx(), StrokeCap.Round)
                if (end > tick) drawLine(error, Offset(tick, y), Offset(end, y), 8.dp.toPx(), StrokeCap.Round)
                drawLine(foreground, Offset(tick, 0f), Offset(tick, size.height), 2.dp.toPx())
            }
            val delta = current - reference
            Text("${if (delta > 0) "超出参考线" else "距参考线"} ${statisticsDistance(kotlin.math.abs(delta), TrainingSport.RUNNING)} km",
                style = MaterialTheme.typography.bodyMedium, color = if (delta > 0) error else foreground)
            Text("上${periodLabel(period)} ${statisticsDistance(previous.distanceMeters, TrainingSport.RUNNING)} km",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(if (!summary.isComplete || !previous.isComplete) "数据不完整，暂不计算参考线" else "上期距离为 0，暂不计算参考线",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun statisticsDistance(meters: Double?, sport: TrainingSport?): String = when {
    meters == null -> "—"
    sport == TrainingSport.SWIMMING -> String.format(Locale.ROOT, "%.0f", meters)
    else -> String.format(Locale.ROOT, "%.1f", meters / 1000)
}

internal fun statisticsDuration(seconds: Long): String = if (seconds < 3600) "${seconds / 60}分"
    else "${seconds / 3600}小时${seconds / 60 % 60}分"

internal fun periodLabel(period: TrainingPeriod): String = if (period == TrainingPeriod.WEEK) "周" else "月"
internal fun sportLabel(sport: TrainingSport): String = when (sport) {
    TrainingSport.RUNNING -> "跑步"
    TrainingSport.CYCLING -> "骑行"
    TrainingSport.SWIMMING -> "游泳"
}

internal fun statisticsDateLabel(summary: TrainingPeriodSummary, period: TrainingPeriod): String =
    if (period == TrainingPeriod.MONTH) "${summary.start.year}年${summary.start.monthValue}月"
    else "${summary.start.format(DateTimeFormatter.ofPattern("MM.dd"))} – ${summary.end.format(DateTimeFormatter.ofPattern("MM.dd"))}"
