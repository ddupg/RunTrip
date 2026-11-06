package com.ddupg.runtrip.feature.training

import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.model.TrainingSport
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class TrainingPeriod {
    WEEK, MONTH;

    fun start(date: LocalDate): LocalDate = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> date.withDayOfMonth(1)
    }

    fun shift(start: LocalDate, offset: Int): LocalDate = when (this) {
        WEEK -> this.start(start).plusWeeks(offset.toLong())
        MONTH -> this.start(start).plusMonths(offset.toLong())
    }

    fun end(start: LocalDate): LocalDate = shift(start, 1).minusDays(1)
}

data class TrainingContribution(
    val sport: TrainingSport,
    val distanceMeters: Double?,
    val equivalentDistanceMeters: Double?,
    val missingDistanceCount: Int,
)

data class TrainingPeriodSummary(
    val start: LocalDate,
    val end: LocalDate,
    val distanceMeters: Double?,
    val durationSeconds: Long,
    val count: Int,
    val missingDistanceCount: Int,
    // Data completeness is separate from whether this calendar period has ended.
    val isComplete: Boolean,
    val isOngoing: Boolean,
    val contributions: List<TrainingContribution>,
    val growthPercent: Double? = null,
    val referenceDistanceMeters: Double? = null,
)

internal data class TrainingPeriodTotals(
    val count: Int = 0,
    val durationSeconds: Long = 0,
    val knownDistanceMeters: Double = 0.0,
    val missingDistanceCount: Int = 0,
) {
    fun distanceMeters(historyComplete: Boolean): Double? = when {
        count > missingDistanceCount -> knownDistanceMeters
        count == 0 && historyComplete -> 0.0
        else -> null
    }
}

class TrainingStatisticsIndex internal constructor(
    val period: TrainingPeriod,
    private val totals: Map<LocalDate, Map<TrainingSport, TrainingPeriodTotals>>,
    private val preferences: TrainingPreferences,
    private val today: LocalDate,
    private val historyComplete: Boolean,
) {
    fun summary(start: LocalDate, sport: TrainingSport?): TrainingPeriodSummary {
        val current = periodSummary(period.start(start), sport)
        val previous = periodSummary(period.shift(current.start, -1), sport)
        val currentDistance = current.distanceMeters
        val previousDistance = previous.distanceMeters?.takeIf { previous.isComplete && it > 0.0 }
        val growth = if (current.isComplete && currentDistance != null && previousDistance != null) {
            (currentDistance - previousDistance) / previousDistance * 100.0
        } else null
        val reference = if (sport == TrainingSport.RUNNING && previousDistance != null) {
            val percent = when (period) {
                TrainingPeriod.WEEK -> preferences.runningWeeklyGrowthPercent
                TrainingPeriod.MONTH -> preferences.runningMonthlyGrowthPercent
            }
            BigDecimal.valueOf(previousDistance)
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(percent).movePointLeft(2))).toDouble()
        } else null
        return current.copy(growthPercent = growth, referenceDistanceMeters = reference)
    }

    fun window(
        endingAt: LocalDate,
        sport: TrainingSport?,
        size: Int = if (period == TrainingPeriod.WEEK) 8 else 12,
    ): List<TrainingPeriodSummary> {
        val end = minOf(period.start(endingAt), period.start(today))
        return List(size) { summary(period.shift(end, it - size + 1), sport) }
    }

    private fun periodSummary(start: LocalDate, sport: TrainingSport?): TrainingPeriodSummary {
        val selectedSports = sport?.let { listOf(it) } ?: TrainingSport.entries
        val source = totals[start].orEmpty()
        val selected = selectedSports.map { source[it] ?: TrainingPeriodTotals() }
        val count = selected.sumOf { it.count }
        val missing = selected.sumOf { it.missingDistanceCount }
        val availableHistory = historyComplete && start <= today
        val contributions = selectedSports.map { selectedSport ->
            val item = source[selectedSport] ?: TrainingPeriodTotals()
            val distance = item.distanceMeters(availableHistory)
            TrainingContribution(selectedSport, distance, distance?.times(factor(selectedSport)), item.missingDistanceCount)
        }
        val distance = when {
            count > missing -> contributions.sumOf {
                (if (sport == null) it.equivalentDistanceMeters else it.distanceMeters) ?: 0.0
            }
            count == 0 && availableHistory -> 0.0
            else -> null
        }
        val end = period.end(start)
        return TrainingPeriodSummary(
            start = start,
            end = end,
            distanceMeters = distance,
            durationSeconds = selected.sumOf { it.durationSeconds },
            count = count,
            missingDistanceCount = missing,
            isComplete = availableHistory && missing == 0,
            isOngoing = start <= today && end >= today,
            contributions = contributions,
        )
    }

    private fun factor(sport: TrainingSport): Double = when (sport) {
        TrainingSport.RUNNING -> 1.0
        TrainingSport.CYCLING -> preferences.cyclingFactor
        TrainingSport.SWIMMING -> preferences.swimmingFactor
    }
}

fun trainingStatistics(
    records: List<TrainingActivity>,
    period: TrainingPeriod,
    preferences: TrainingPreferences,
    zone: ZoneId,
    today: LocalDate,
    historyComplete: Boolean,
): TrainingStatisticsIndex {
    val totals = mutableMapOf<LocalDate, MutableMap<TrainingSport, TrainingPeriodTotals>>()
    records.forEach { activity ->
        val date = trainingDate(activity, zone)
        if (date <= today) {
            val bySport = totals.getOrPut(period.start(date)) { mutableMapOf() }
            val sport = activity.subtype.sport
            val previous = bySport[sport] ?: TrainingPeriodTotals()
            bySport[sport] = TrainingPeriodTotals(
                count = previous.count + 1,
                durationSeconds = previous.durationSeconds + activity.durationSeconds,
                knownDistanceMeters = previous.knownDistanceMeters + (activity.distanceMeters ?: 0.0),
                missingDistanceCount = previous.missingDistanceCount + if (activity.distanceMeters == null) 1 else 0,
            )
        }
    }
    return TrainingStatisticsIndex(period, totals, preferences, today, historyComplete)
}
