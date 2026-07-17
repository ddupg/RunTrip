package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceFormStateTest {
    @Test
    fun requiredFieldsAreValidated() {
        val result = validateRaceForm(RaceFormUiState(name = " ", city = ""))

        assertNull(result.input)
        assertTrue(result.errors.name != null)
        assertTrue(result.errors.city != null)
    }

    @Test
    fun hotelPriceIsConvertedToExactCents() {
        val result = validateRaceForm(validState().copy(hotelPrice = "350.50"))

        assertEquals(35_050L, result.input?.hotelTotalPriceCents)
    }

    @Test
    fun valuesWithMoreThanTwoPriceDecimalsAreRejected() {
        val result = validateRaceForm(validState().copy(hotelPrice = "350.123"))

        assertNull(result.input)
        assertTrue(result.errors.hotelPrice != null)
    }

    @Test
    fun negativeDistanceIsRejected() {
        val result = validateRaceForm(validState().copy(travelDistance = "-1"))

        assertNull(result.input)
        assertTrue(result.errors.travelDistance != null)
    }

    private fun validState(): RaceFormUiState = RaceFormUiState(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        category = RaceCategory.MARATHON,
        status = RaceStatus.DRAW_WON,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
    )
}
