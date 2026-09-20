package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceDetails
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonDetails

sealed interface SportDraft {
    val sportType: SportType
    val category: RaceCategory?
    fun withCategory(category: RaceCategory): SportDraft
    fun toDetails(): RaceDetails?
}

internal fun SportType.emptyDraft(): SportDraft = when (this) {
    SportType.ROAD_RUNNING -> RoadRunningDraft()
    SportType.TRIATHLON -> TriathlonDraft()
}

internal fun RaceDetails.toDraft(): SportDraft = when (this) {
    is RoadRunningDetails -> RoadRunningDraft(category, caaRaceLevel, worldAthleticsLabel)
    is TriathlonDetails -> TriathlonDraft(category)
}
