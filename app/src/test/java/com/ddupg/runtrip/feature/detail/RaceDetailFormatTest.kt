package com.ddupg.runtrip.feature.detail

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceDetailFormatTest {
    @Test
    fun formatsRaceDateWithWeekday() {
        assertEquals(
            "2026 年 11 月 15 日 周日",
            formatRaceDate(LocalDate.of(2026, 11, 15)),
        )
    }

    @Test
    fun formatsCnyFromExactCents() {
        assertEquals("¥350.00", formatCny(35_000))
        assertEquals("未填写", formatCny(null))
    }

    @Test
    fun formatsDistanceWithoutUnnecessaryDecimal() {
        assertEquals("350 km", formatDistance(350.0))
        assertEquals("350.5 km", formatDistance(350.5))
    }
}
