package com.ddupg.runtrip.data

import com.ddupg.runtrip.data.local.toDomain
import com.ddupg.runtrip.data.local.toEntity
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceEntityTest {
    @Test
    fun entityUsesIsoDateAndEnglishCodes() {
        val race = Race(
            id = "89c79d5b-8d77-431b-a38b-1a1c9e629902",
            name = "横店马拉松",
            city = "金华",
            raceDate = LocalDate.of(2026, 11, 15),
            category = RaceCategory.MARATHON,
            status = RaceStatus.DRAW_WON,
            travelDistanceKm = 350.0,
            hotelBookingStatus = HotelBookingStatus.BOOKED,
            hotelName = "万豪万枫",
            bookingPlatform = "携程",
            hotelTotalPriceCents = 35_000,
            hotelNotes = null,
            raceNotes = "首场秋季全马",
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
            recordVersion = 2,
        )

        val entity = race.toEntity()

        assertEquals("2026-11-15", entity.raceDate)
        assertEquals("MARATHON", entity.categoryCode)
        assertEquals("DRAW_WON", entity.statusCode)
        assertEquals("BOOKED", entity.hotelBookingStatusCode)
        assertEquals(race, entity.toDomain())
    }
}
