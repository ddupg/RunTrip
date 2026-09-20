package com.ddupg.runtrip.data

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TrailRunningCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedCodeTest {
    private val storageCodePattern = Regex("[A-Z][A-Z0-9_]*")

    @Test
    fun raceStatusCodesAreStableEnglishValues() {
        RaceStatus.entries.forEach { status ->
            assertTrue(storageCodePattern.matches(status.code))
            assertEquals(status, RaceStatus.fromCode(status.code))
        }
    }

    @Test
    fun categoryCodesAreStableEnglishValues() {
        RoadRunningCategory.entries.forEach { category ->
            assertTrue(storageCodePattern.matches(category.code))
            assertEquals(category, RoadRunningCategory.fromCode(category.code))
        }
    }

    @Test
    fun sportAndTriathlonCodesAreStableEnglishValues() {
        SportType.entries.forEach {
            assertTrue(storageCodePattern.matches(it.code))
            assertEquals(it, SportType.fromCode(it.code))
        }
        TriathlonCategory.entries.forEach {
            assertTrue(storageCodePattern.matches(it.code))
            assertEquals(it, TriathlonCategory.fromCode(it.code))
        }
    }

    @Test
    fun trailCategoryUsesStableCode() {
        assertEquals("CUSTOM", TrailRunningCategory.CUSTOM.code)
        assertEquals(TrailRunningCategory.CUSTOM, TrailRunningCategory.fromCode("CUSTOM"))
        assertEquals(SportType.TRAIL_RUNNING, TrailRunningCategory.CUSTOM.sportType)
    }

    @Test
    fun hotelStatusCodesAreStableEnglishValues() {
        HotelBookingStatus.entries.forEach { status ->
            assertTrue(storageCodePattern.matches(status.code))
            assertEquals(status, HotelBookingStatus.fromCode(status.code))
        }
    }

    @Test
    fun caaRaceLevelCodesAreStableEnglishValues() {
        CaaRaceLevel.entries.forEach { level ->
            assertTrue(storageCodePattern.matches(level.code))
            assertEquals(level, CaaRaceLevel.fromCode(level.code))
        }
    }

    @Test
    fun worldAthleticsLabelCodesAreStableEnglishValues() {
        WorldAthleticsLabel.entries.forEach { label ->
            assertTrue(storageCodePattern.matches(label.code))
            assertEquals(label, WorldAthleticsLabel.fromCode(label.code))
        }
    }
}
