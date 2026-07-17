package com.ddupg.runtrip.feature.home

import com.ddupg.runtrip.data.model.Race
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
    val selectedStatus: RaceStatus? = null,
    val monthGroups: List<RaceMonthGroup> = emptyList(),
)

fun buildRaceMonthGroups(
    races: List<Race>,
    section: RaceSection,
    selectedStatus: RaceStatus?,
    today: LocalDate,
): List<RaceMonthGroup> {
    val inSection = races.filter { race ->
        when (section) {
            RaceSection.UPCOMING -> !race.raceDate.isBefore(today)
            RaceSection.HISTORY -> race.raceDate.isBefore(today)
        }
    }
    val filtered = inSection.filter { selectedStatus == null || it.status == selectedStatus }
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
