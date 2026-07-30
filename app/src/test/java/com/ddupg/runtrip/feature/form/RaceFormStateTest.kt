package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceFormStateTest {
    @Test
    fun requiredFieldsAreValidated() {
        val result = validateRaceForm(RaceDraft(name = " ", city = ""))

        assertNull(result.input)
        assertTrue(result.errors.name != null)
        assertTrue(result.errors.city != null)
    }

    @Test
    fun hotelPriceIsConvertedToExactCents() {
        val result = validateRaceForm(validDraft().copy(hotelPrice = "350.50"))

        assertEquals(35_050L, result.input?.hotelTotalPriceCents)
    }

    @Test
    fun valuesWithMoreThanTwoPriceDecimalsAreRejected() {
        val result = validateRaceForm(validDraft().copy(hotelPrice = "350.123"))

        assertNull(result.input)
        assertTrue(result.errors.hotelPrice != null)
    }

    @Test
    fun negativeDistanceIsRejected() {
        val result = validateRaceForm(validDraft().copy(travelDistance = "-1"))

        assertNull(result.input)
        assertTrue(result.errors.travelDistance != null)
    }

    @Test
    fun optionalRaceLevelsAreIncludedInValidatedInput() {
        val result = validateRaceForm(
            validDraft().copy(
                caaRaceLevel = CaaRaceLevel.A1,
                worldAthleticsLabel = WorldAthleticsLabel.PLATINUM,
            ),
        )

        assertEquals(CaaRaceLevel.A1, result.input?.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, result.input?.worldAthleticsLabel)
    }

    @Test
    fun raceLevelsDefaultToNotFilled() {
        val result = validateRaceForm(validDraft())

        assertNull(result.input?.caaRaceLevel)
        assertNull(result.input?.worldAthleticsLabel)
    }

    private fun validDraft(): RaceDraft = RaceDraft(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        category = RaceCategory.MARATHON,
        status = RaceStatus.DRAW_WON,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
    )
}
