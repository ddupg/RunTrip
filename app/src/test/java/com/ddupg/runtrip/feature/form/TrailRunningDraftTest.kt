package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TrailRunningDetails
import com.ddupg.runtrip.testing.testRace
import org.junit.Assert.*
import org.junit.Test

class TrailRunningDraftTest {
    private fun draft(trail: TrailRunningDraft) = RaceDraft(name = "山径赛", city = "杭州")
        .copy(sportType = SportType.TRAIL_RUNNING).withSportDraft(trail)

    @Test
    fun distanceIsRequiredAndElevationIsOptional() {
        val missing = validateRaceForm(draft(TrailRunningDraft()))
        assertNull(missing.input)
        assertNull(missing.errors.category)
        assertEquals(setOf(TrailRunningDraft.DISTANCE), missing.errors.sportFields.keys)
        assertEquals(TrailRunningDetails(30.5), validateRaceForm(draft(TrailRunningDraft(" 30.5 "))).input?.details)
        assertEquals(TrailRunningDetails(30.5, 0), validateRaceForm(draft(TrailRunningDraft("30.5", "0"))).input?.details)
    }

    @Test
    fun invalidNumbersDoNotReachPersistence() {
        listOf("0", "-1", "NaN", "Infinity", "abc", "1e309").forEach {
            assertNotNull(validateRaceForm(draft(TrailRunningDraft(it))).errors.sportFields[TrailRunningDraft.DISTANCE])
        }
        listOf("-1", "1.5", "NaN", "abc", "2147483648").forEach {
            assertNotNull(validateRaceForm(draft(TrailRunningDraft("30", it))).errors.sportFields[TrailRunningDraft.ELEVATION_GAIN])
        }
        assertThrows(IllegalArgumentException::class.java) { TrailRunningDetails(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { TrailRunningDetails(30.0, -1) }
    }

    @Test
    fun sportDraftSurvivesSwitchesButOnlyActiveSportIsValidated() {
        val trail = draft(TrailRunningDraft("30.5", "1200"))
        val road = trail.copy(sportType = SportType.ROAD_RUNNING)
        assertTrue(validateRaceForm(road).input?.details !is TrailRunningDetails)
        assertEquals(trail.activeSportDraft, road.copy(sportType = SportType.TRAIL_RUNNING).activeSportDraft)
        val loaded = testRace(details = TrailRunningDetails(30.5, 1200)).toDraft()
        assertEquals(TrailRunningDraft("30.5", "1200"), loaded.activeSportDraft)
        assertEquals(TrailRunningDetails(30.5, 1200), validateRaceForm(loaded).input?.details)
    }

    @Test
    fun correctionsClearOnlyResolvedSportErrorsAndSwitchClearsHiddenErrors() {
        val invalid = draft(TrailRunningDraft("bad", "-1"))
        val state = RaceFormUiState(invalid, validateRaceForm(invalid).errors)
        val fixedDistance = state.withDraft(invalid.withSportDraft(TrailRunningDraft("50", "-1")))
        assertEquals(setOf(TrailRunningDraft.ELEVATION_GAIN), fixedDistance.errors.sportFields.keys)
        assertFalse(fixedDistance.withDraft(invalid.copy(sportType = SportType.ROAD_RUNNING)).errors.hasErrors)
    }
}
