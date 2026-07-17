package com.ddupg.runtrip.data

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
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
        RaceCategory.entries.forEach { category ->
            assertTrue(storageCodePattern.matches(category.code))
            assertEquals(category, RaceCategory.fromCode(category.code))
        }
    }

    @Test
    fun hotelStatusCodesAreStableEnglishValues() {
        HotelBookingStatus.entries.forEach { status ->
            assertTrue(storageCodePattern.matches(status.code))
            assertEquals(status, HotelBookingStatus.fromCode(status.code))
        }
    }
}
