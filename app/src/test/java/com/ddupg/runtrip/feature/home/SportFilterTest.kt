package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.*
import com.ddupg.runtrip.testing.testRace
import com.ddupg.runtrip.ui.presentation.RacePresentation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SportFilterTest {
    @Test
    fun trailSelectionMatchesAllCustomDistancesAndIntersectsStatus() {
        val races = listOf(
            testRace(id = "short", details = TrailRunningDetails(20.0, 600)),
            testRace(id = "long", details = TrailRunningDetails(50.0), status = RaceStatus.FINISHED),
            testRace(id = "road"),
        )
        val selected = RacePresentation.categories(SportType.TRAIL_RUNNING).toSet()
        fun results(filter: RaceFilter) = buildRaceMonthGroups(races, RaceSection.UPCOMING, filter, LocalDate.of(2026, 9, 20))
            .flatMap { it.races }.map { it.id }.toSet()
        assertEquals(setOf("short", "long"), results(RaceFilter(categories = selected)))
        assertEquals(setOf("long"), results(RaceFilter(categories = selected, statuses = setOf(RaceStatus.FINISHED))))
    }

    @Test
    fun otherProjectsStayDistinctAndStatusIntersectsSportSelection() {
        val today = LocalDate.of(2026, 9, 20)
        val road = testRace(id = "road", details = RoadRunningDetails(RoadRunningCategory.OTHER))
        val triathlon = testRace(id = "tri", details = TriathlonDetails(TriathlonCategory.OTHER))
        val sprint = testRace(id = "sprint", details = TriathlonDetails(TriathlonCategory.SPRINT), status = RaceStatus.FINISHED)
        val races = listOf(road, triathlon, sprint)
        fun selected(filter: RaceFilter) = buildRaceMonthGroups(races, RaceSection.UPCOMING, filter, today)
            .flatMap { it.races }.map { it.id }.toSet()
        assertEquals(setOf("tri"), selected(RaceFilter(categories = setOf(TriathlonCategory.OTHER))))
        assertEquals(setOf("road", "tri"), selected(RaceFilter(categories = setOf(RoadRunningCategory.OTHER, TriathlonCategory.OTHER))))
        assertEquals(setOf("tri", "sprint"), selected(RaceFilter(categories = RacePresentation.categories(SportType.TRIATHLON).toSet())))
        assertEquals(setOf("sprint"), selected(RaceFilter(categories = RacePresentation.categories(SportType.TRIATHLON).toSet(), statuses = setOf(RaceStatus.FINISHED))))
        assertEquals(setOf("road", "tri", "sprint"), selected(RaceFilter()))
    }
}
