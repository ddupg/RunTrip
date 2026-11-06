package com.ddupg.runtrip.feature.training

import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingPreferences
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.model.TrainingSubtype
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = date("2026-11-06")

    private fun date(value: String): LocalDate = LocalDate.parse(value)

    private fun activity(
        day: String,
        subtype: TrainingSubtype = TrainingSubtype.OUTDOOR_RUN,
        distance: Double? = 1_000.0,
        duration: Long = 600,
        timestamp: String? = null,
    ) = TrainingActivity("$day:$subtype:$distance", subtype, date(day), timestamp?.let { Instant.parse(it).epochSecond }, distance, duration)

    private fun index(
        records: List<TrainingActivity>,
        period: TrainingPeriod = TrainingPeriod.WEEK,
        preferences: TrainingPreferences = TrainingPreferences(),
        complete: Boolean = true,
        currentDay: LocalDate = today,
        currentZone: ZoneId = zone,
    ) = trainingStatistics(records, period, preferences, currentZone, currentDay, complete)

    @Test
    fun naturalPeriodsHandleYearBoundariesAndLeapFebruary() {
        assertEquals(date("2025-12-29"), TrainingPeriod.WEEK.start(date("2026-01-04")))
        assertEquals(date("2026-01-04"), TrainingPeriod.WEEK.end(date("2025-12-29")))
        assertEquals(date("2026-01-05"), TrainingPeriod.WEEK.shift(date("2025-12-29"), 1))
        assertEquals(date("2024-02-29"), TrainingPeriod.MONTH.end(date("2024-02-01")))
        assertEquals(date("2024-02-01"), TrainingPeriod.MONTH.shift(date("2024-03-01"), -1))

        val records = listOf(activity("2025-12-31"), activity("2026-01-04"), activity("2026-01-05"))
        val stats = index(records, currentDay = date("2026-01-05"))
        assertEquals(2, stats.summary(date("2025-12-29"), TrainingSport.RUNNING).count)
        assertEquals(1, stats.summary(date("2026-01-05"), TrainingSport.RUNNING).count)
    }

    @Test
    fun groupingUsesDeviceTimezoneAndSourceDateOnlyWithoutTimestamp() {
        val records = listOf(
            activity("2026-11-01", timestamp = "2026-11-01T23:30:00Z"),
            activity("2026-11-01", distance = 2_000.0),
        )
        val local = index(records)
        assertEquals(1_000.0, local.summary(date("2026-11-02"), TrainingSport.RUNNING).distanceMeters!!, 0.01)
        assertEquals(2_000.0, local.summary(date("2026-10-26"), TrainingSport.RUNNING).distanceMeters!!, 0.01)
        val utc = index(records, currentZone = ZoneId.of("UTC"))
        assertEquals(3_000.0, utc.summary(date("2026-10-26"), TrainingSport.RUNNING).distanceMeters!!, 0.01)
        assertEquals(0, utc.summary(date("2026-11-02"), TrainingSport.RUNNING).count)
    }

    @Test
    fun compositeDistanceIncludesSubtypeContributionsWithoutChangingSportDistance() {
        val stats = index(listOf(
            activity("2026-11-02", TrainingSubtype.TRAIL_RUN, 30_000.0, 10_800),
            activity("2026-11-03", TrainingSubtype.E_BIKE, 80_000.0, 10_800),
            activity("2026-11-04", TrainingSubtype.OPEN_WATER_SWIM, 2_000.0, 3_600),
        ))
        val combined = stats.summary(date("2026-11-02"), null)
        assertEquals(58_000.0, combined.distanceMeters!!, 0.01)
        assertEquals(25_200L, combined.durationSeconds)
        assertEquals(3, combined.count)
        assertTrue(combined.isComplete)
        assertEquals(80_000.0, stats.summary(date("2026-11-02"), TrainingSport.CYCLING).distanceMeters!!, 0.01)
        val cycling = combined.contributions.single { it.sport == TrainingSport.CYCLING }
        assertEquals(80_000.0, cycling.distanceMeters!!, 0.01)
        assertEquals(20_000.0, cycling.equivalentDistanceMeters!!, 0.01)
        assertNull(combined.referenceDistanceMeters)
    }

    @Test
    fun changedFactorsApplyToBothPeriodsBeforeComputingGrowth() {
        val records = listOf(
            activity("2026-10-26", TrainingSubtype.OUTDOOR_RIDE, 40_000.0),
            activity("2026-11-02", distance = 15_000.0),
        )
        val original = index(records).summary(date("2026-11-02"), null)
        val changed = index(records, preferences = TrainingPreferences(cyclingFactor = 0.5))
            .summary(date("2026-11-02"), null)
        assertEquals(50.0, original.growthPercent!!, 0.001)
        assertEquals(-25.0, changed.growthPercent!!, 0.001)
    }

    @Test
    fun runningGrowthAndReferenceUsePreviousWholePeriod() {
        val records = listOf(activity("2026-10-26", distance = 30_000.0), activity("2026-11-02", distance = 31_200.0))
        val weekly = index(records).summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertEquals(4.0, weekly.growthPercent!!, 0.001)
        assertEquals(31_500.0, weekly.referenceDistanceMeters!!, 0.01)
        assertTrue(weekly.isOngoing)
        val changed = index(records, preferences = TrainingPreferences(runningWeeklyGrowthPercent = 10.0))
            .summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertEquals(33_000.0, changed.referenceDistanceMeters!!, 0.01)

        val monthly = index(listOf(activity("2026-09-20", distance = 100_000.0), activity("2026-10-20", distance = 112_000.0)),
            period = TrainingPeriod.MONTH).summary(date("2026-10-01"), TrainingSport.RUNNING)
        assertEquals(12.0, monthly.growthPercent!!, 0.001)
        assertEquals(110_000.0, monthly.referenceDistanceMeters!!, 0.01)
        assertFalse(monthly.isOngoing)
    }

    @Test
    fun exactlyFifteenPercentGrowthDoesNotExceedReferenceThroughRounding() {
        val summary = index(
            listOf(activity("2026-09-20", distance = 100_000.0), activity("2026-10-20", distance = 115_000.0)),
            period = TrainingPeriod.MONTH,
            preferences = TrainingPreferences(runningMonthlyGrowthPercent = 15.0),
        ).summary(date("2026-10-01"), TrainingSport.RUNNING)

        val reference = summary.referenceDistanceMeters!!
        assertEquals(115_000.0, reference, 0.0)
        assertFalse(summary.distanceMeters!! > reference)
    }

    @Test
    fun zeroBaselineHasNoPercentageAndEmptyCompletedPeriodIsRealZero() {
        val stats = index(listOf(activity("2026-11-02", distance = 10_000.0)))
        val empty = stats.summary(date("2026-10-26"), TrainingSport.RUNNING)
        assertEquals(0.0, empty.distanceMeters!!, 0.0)
        assertTrue(empty.isComplete)
        val current = stats.summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertNull(current.growthPercent)
        assertNull(current.referenceDistanceMeters)

        val noCurrent = index(listOf(activity("2026-10-26", distance = 10_000.0)))
            .summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertEquals(-100.0, noCurrent.growthPercent!!, 0.001)
    }

    @Test
    fun missingDistancesPreserveTimeAndKnownTotalsButSuppressGrowth() {
        val records = listOf(
            activity("2026-10-26", distance = 10_000.0),
            activity("2026-11-02", distance = 10_000.0, duration = 3_600),
            activity("2026-11-03", distance = null, duration = 1_800),
            activity("2026-11-04", subtype = TrainingSubtype.POOL_SWIM, distance = null, duration = 1_200),
        )
        val stats = index(records)
        val running = stats.summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertEquals(10_000.0, running.distanceMeters!!, 0.01)
        assertEquals(5_400L, running.durationSeconds)
        assertEquals(2, running.count)
        assertEquals(1, running.missingDistanceCount)
        assertFalse(running.isComplete)
        assertNull(running.growthPercent)
        val swimming = stats.summary(date("2026-11-02"), TrainingSport.SWIMMING)
        assertNull(swimming.distanceMeters)
        assertEquals(1, swimming.count)
        assertEquals(1_200L, swimming.durationSeconds)
        val combined = stats.summary(date("2026-11-02"), null)
        assertEquals(10_000.0, combined.distanceMeters!!, 0.01)
        assertEquals(2, combined.missingDistanceCount)
        assertFalse(combined.isComplete)

        val missingBase = index(listOf(activity("2026-10-26", distance = null), activity("2026-11-02")))
            .summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertNull(missingBase.growthPercent)
        assertNull(missingBase.referenceDistanceMeters)
    }

    @Test
    fun unfinishedHistoryDoesNotInventZerosOrComparableGrowth() {
        val stats = index(listOf(activity("2026-11-02", distance = 12_000.0)), complete = false)
        val current = stats.summary(date("2026-11-02"), TrainingSport.RUNNING)
        assertEquals(12_000.0, current.distanceMeters!!, 0.01)
        assertFalse(current.isComplete)
        assertNull(current.growthPercent)
        val empty = stats.summary(date("2026-10-26"), TrainingSport.RUNNING)
        assertNull(empty.distanceMeters)
        assertFalse(empty.isComplete)
        assertNull(current.referenceDistanceMeters)
    }

    @Test
    fun currentPeriodRemainsOngoingOnLastDayAndFutureRecordsAreExcluded() {
        val currentDay = date("2026-11-30")
        val stats = index(listOf(activity("2026-11-30"), activity("2026-12-01", distance = 9_000.0)),
            period = TrainingPeriod.MONTH, currentDay = currentDay)
        val november = stats.summary(currentDay, TrainingSport.RUNNING)
        assertTrue(november.isOngoing)
        assertEquals(1, november.count)
        assertEquals(1_000.0, november.distanceMeters!!, 0.01)
        val future = stats.summary(date("2026-12-01"), TrainingSport.RUNNING)
        assertNull(future.distanceMeters)
        assertEquals(0, future.count)
        val visible = stats.window(date("2027-01-01"), TrainingSport.RUNNING)
        assertEquals(date("2026-11-01"), visible.last().start)
        assertTrue(visible.all { it.start <= currentDay })
    }
}
