package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import java.time.LocalDate
import java.time.YearMonth

enum class RaceSection(val displayName: String) {
    UPCOMING("即将到来"),
    HISTORY("历史"),
}

data class RaceMonthGroup(
    val month: YearMonth,
    val races: List<Race>,
)

data class HomeUiState(
    val section: RaceSection = RaceSection.UPCOMING,
    val filter: RaceFilter = RaceFilter(),
    val sectionRaceCount: Int = 0,
    val monthGroups: List<RaceMonthGroup> = emptyList(),
    val quickStatusRace: Race? = null,
    val quickStatusUpdate: QuickStatusUpdate = QuickStatusUpdate.Idle,
)

data class RaceFilter(
    val statuses: Set<RaceStatus> = emptySet(),
    val categories: Set<RaceCategory> = emptySet(),
) {
    val activeDimensionCount: Int
        get() = listOf(statuses, categories).count { it.isNotEmpty() }

    val isActive: Boolean
        get() = activeDimensionCount > 0
}

sealed interface QuickStatusUpdate {
    data object Idle : QuickStatusUpdate

    data class Saving(val targetStatus: RaceStatus) : QuickStatusUpdate

    data class Failed(val message: String) : QuickStatusUpdate
}

fun buildRaceMonthGroups(
    races: List<Race>,
    section: RaceSection,
    filter: RaceFilter,
    today: LocalDate,
): List<RaceMonthGroup> {
    val inSection = races.filter { race ->
        when (section) {
            RaceSection.UPCOMING -> !race.raceDate.isBefore(today)
            RaceSection.HISTORY -> race.raceDate.isBefore(today)
        }
    }
    val filtered = inSection.filter { race ->
        (filter.statuses.isEmpty() || race.status in filter.statuses) &&
            (filter.categories.isEmpty() || race.category in filter.categories)
    }
    val sorted = when (section) {
        RaceSection.UPCOMING -> filtered.sortedWith(compareBy(Race::raceDate, Race::createdAtEpochMillis))
        RaceSection.HISTORY -> filtered.sortedWith(
            compareByDescending(Race::raceDate).thenByDescending(Race::createdAtEpochMillis),
        )
    }

    return sorted
        .groupBy { YearMonth.from(it.raceDate) }
        .map { (month, monthRaces) -> RaceMonthGroup(month, monthRaces) }
}

fun countRacesInSection(
    races: List<Race>,
    section: RaceSection,
    today: LocalDate,
): Int = races.count { race ->
    when (section) {
        RaceSection.UPCOMING -> !race.raceDate.isBefore(today)
        RaceSection.HISTORY -> race.raceDate.isBefore(today)
    }
}
