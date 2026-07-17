package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    private val today = LocalDate.of(2026, 7, 17)

    @Test
    fun todayBelongsToUpcomingAndPastDatesBelongToHistory() {
        val races = listOf(
            race("past", today.minusDays(1), RaceStatus.WATCHING),
            race("today", today, RaceStatus.WITHDRAWN),
            race("future", today.plusMonths(1), RaceStatus.DRAW_PENDING),
        )

        val upcoming = buildRaceMonthGroups(
            races = races,
            section = RaceSection.UPCOMING,
            selectedStatus = null,
            today = today,
        ).flatMap { it.races }
        val history = buildRaceMonthGroups(
            races = races,
            section = RaceSection.HISTORY,
            selectedStatus = null,
            today = today,
        ).flatMap { it.races }

        assertEquals(listOf("today", "future"), upcoming.map { it.id })
        assertEquals(listOf("past"), history.map { it.id })
        assertEquals(RaceStatus.WITHDRAWN, upcoming.first().status)
    }

    @Test
    fun statusFilterDoesNotChangeStoredStatus() {
        val races = listOf(
            race("won", today.plusDays(1), RaceStatus.DRAW_WON),
            race("pending", today.plusDays(2), RaceStatus.DRAW_PENDING),
        )

        val filtered = buildRaceMonthGroups(
            races = races,
            section = RaceSection.UPCOMING,
            selectedStatus = RaceStatus.DRAW_WON,
            today = today,
        ).flatMap { it.races }

        assertEquals(listOf("won"), filtered.map { it.id })
        assertEquals(RaceStatus.DRAW_PENDING, races.last().status)
    }

    @Test
    fun historyUsesReverseChronologicalOrder() {
        val races = listOf(
            race("older", today.minusMonths(2), RaceStatus.FINISHED),
            race("newer", today.minusDays(2), RaceStatus.FINISHED),
        )

        val history = buildRaceMonthGroups(
            races = races,
            section = RaceSection.HISTORY,
            selectedStatus = null,
            today = today,
        ).flatMap { it.races }

        assertEquals(listOf("newer", "older"), history.map { it.id })
    }
}

private fun race(id: String, date: LocalDate, status: RaceStatus): Race = Race(
    id = id,
    name = id,
    city = "杭州",
    raceDate = date,
    category = RaceCategory.MARATHON,
    status = status,
    travelDistanceKm = null,
    hotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    hotelName = null,
    bookingPlatform = null,
    hotelTotalPriceCents = null,
    hotelNotes = null,
    raceNotes = null,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    recordVersion = 1,
)
