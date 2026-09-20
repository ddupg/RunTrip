package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.testing.testRace
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
                sportDrafts = mapOf(SportType.ROAD_RUNNING to RoadRunningDraft(
                    RoadRunningCategory.MARATHON, CaaRaceLevel.A1, WorldAthleticsLabel.PLATINUM,
                )),
            ),
        )

        assertEquals(CaaRaceLevel.A1, (result.input?.details as? RoadRunningDetails)?.caaRaceLevel)
        assertEquals(WorldAthleticsLabel.PLATINUM, (result.input?.details as? RoadRunningDetails)?.worldAthleticsLabel)
    }

    @Test
    fun raceLevelsDefaultToNotFilled() {
        val result = validateRaceForm(validDraft())

        assertNull((result.input?.details as? RoadRunningDetails)?.caaRaceLevel)
        assertNull((result.input?.details as? RoadRunningDetails)?.worldAthleticsLabel)
    }

    @Test
    fun newRaceDefaultsToRoadHalfMarathon() {
        val draft = RaceDraft()
        assertEquals(SportType.ROAD_RUNNING, draft.sportType)
        assertEquals(RoadRunningCategory.HALF_MARATHON, draft.activeSportDraft.category)
    }

    @Test
    fun switchingSportsRequiresSelectionAndPreservesUnsavedSportDrafts() {
        val road = RoadRunningDraft(RoadRunningCategory.MARATHON, CaaRaceLevel.A1)
        val original = validDraft().withSportDraft(road)
        val switched = original.copy(sportType = SportType.TRIATHLON)
        assertNull(switched.activeSportDraft.category)
        assertEquals("请选择比赛项目", validateRaceForm(switched).errors.category)

        val triathlon = switched.withSportDraft(TriathlonDraft(TriathlonCategory.SPRINT))
        assertEquals(TriathlonDetails(TriathlonCategory.SPRINT), validateRaceForm(triathlon).input?.details)
        assertEquals(original.name, triathlon.name)
        assertEquals(road, triathlon.copy(sportType = SportType.ROAD_RUNNING).activeSportDraft)
        assertEquals(TriathlonDraft(TriathlonCategory.SPRINT),
            triathlon.copy(sportType = SportType.ROAD_RUNNING).copy(sportType = SportType.TRIATHLON).activeSportDraft)
    }

    @Test
    fun editingSavedTriathlonDoesNotRestoreOldRoadDetails() {
        val race = testRace(details = TriathlonDetails(TriathlonCategory.STANDARD))
        val draft = race.toDraft()
        assertEquals(SportType.TRIATHLON, draft.sportType)
        assertEquals(race.details, validateRaceForm(draft).input?.details)
        val road = draft.copy(sportType = SportType.ROAD_RUNNING)
        assertEquals(RoadRunningDraft(), road.activeSportDraft)
        assertTrue(validateRaceForm(road).errors.hasErrors)
    }

    private fun validDraft(): RaceDraft = RaceDraft(
        name = "横店马拉松",
        city = "金华",
        raceDate = LocalDate.of(2026, 11, 15),
        sportDrafts = mapOf(SportType.ROAD_RUNNING to RoadRunningDraft(RoadRunningCategory.MARATHON)),
        status = RaceStatus.DRAW_WON,
        hotelBookingStatus = HotelBookingStatus.BOOKED,
    )
}
