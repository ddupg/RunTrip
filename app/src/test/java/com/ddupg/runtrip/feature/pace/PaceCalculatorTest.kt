package com.ddupg.runtrip.feature.pace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceCalculatorTest {
    @Test
    fun `calculates marathon pace and cumulative five kilometer splits`() {
        val calculation = requireNotNull(
            calculatePace(
                distanceKm = MARATHON_DISTANCE_KM,
                finishTimeSeconds = 3 * 3_600L + 30 * 60L,
            ),
        )

        assertEquals(299L, calculation.paceSecondsPerKm)
        assertEquals(
            listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 42.195),
            calculation.splits.map(PaceSplit::distanceKm),
        )
        assertEquals(
            listOf(1_493L, 2_986L, 4_479L, 5_972L, 7_465L, 8_958L, 10_451L, 11_945L, 12_600L),
            calculation.splits.map(PaceSplit::elapsedSeconds),
        )
        assertTrue(calculation.splits.last().isFinish)
        assertFalse(calculation.splits.dropLast(1).any(PaceSplit::isFinish))
    }

    @Test
    fun `uses one finish row when distance is an exact five kilometer multiple`() {
        val calculation = requireNotNull(calculatePace(distanceKm = 10.0, finishTimeSeconds = 3_600L))

        assertEquals(listOf(5.0, 10.0), calculation.splits.map(PaceSplit::distanceKm))
        assertEquals(listOf(false, true), calculation.splits.map(PaceSplit::isFinish))
    }

    @Test
    fun `includes custom finish after complete five kilometer splits`() {
        val calculation = requireNotNull(calculatePace(distanceKm = 12.5, finishTimeSeconds = 3_600L))

        assertEquals(listOf(5.0, 10.0, 12.5), calculation.splits.map(PaceSplit::distanceKm))
        assertEquals(listOf(1_440L, 2_880L, 3_600L), calculation.splits.map(PaceSplit::elapsedSeconds))
        assertTrue(calculation.splits.last().isFinish)
    }

    @Test
    fun `distance shorter than five kilometers has only a finish row`() {
        val calculation = requireNotNull(calculatePace(distanceKm = 3.2, finishTimeSeconds = 900L))

        assertEquals(1, calculation.splits.size)
        assertEquals(3.2, calculation.splits.single().distanceKm, 0.0)
        assertTrue(calculation.splits.single().isFinish)
    }

    @Test
    fun `calculates every preset distance`() {
        PaceDistancePreset.entries.filter { it.distanceKm != null }.forEach { preset ->
            val calculation = calculatePace(
                distanceKm = requireNotNull(preset.distanceKm),
                finishTimeSeconds = 3_600L,
            )

            assertEquals(3_600L, requireNotNull(calculation).splits.last().elapsedSeconds)
        }
    }

    @Test
    fun `rejects invalid inputs`() {
        assertNull(calculatePace(distanceKm = 0.0, finishTimeSeconds = 1L))
        assertNull(calculatePace(distanceKm = Double.NaN, finishTimeSeconds = 1L))
        assertNull(calculatePace(distanceKm = Double.POSITIVE_INFINITY, finishTimeSeconds = 1L))
        assertNull(calculatePace(distanceKm = 5.0, finishTimeSeconds = 0L))
    }

    @Test
    fun `formats pace elapsed time and distances`() {
        assertEquals("4′59″", formatPace(299L))
        assertEquals("每公里 4 分 59 秒", formatPaceContentDescription(299L))
        assertEquals("00:24:53", formatElapsedTime(1_493L))
        assertEquals("03:30:00", formatElapsedTime(12_600L))
        assertEquals("12.5", formatPaceDistance(12.5))
        assertEquals("42.195", formatPaceDistance(MARATHON_DISTANCE_KM))
    }

    @Test
    fun `parses only positive finite custom distances`() {
        assertEquals(12.5, requireNotNull(parseCustomDistance("12.5")), 0.0)
        assertNull(parseCustomDistance(""))
        assertNull(parseCustomDistance("0"))
        assertNull(parseCustomDistance("."))
        assertNull(parseCustomDistance("NaN"))
        assertNull(parseCustomDistance("Infinity"))
    }
}
