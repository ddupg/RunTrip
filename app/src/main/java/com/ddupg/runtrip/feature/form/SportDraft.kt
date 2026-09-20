package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceDetails
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.data.model.TrailRunningDetails
import java.math.BigDecimal

sealed interface SportDraft {
    val sportType: SportType
    val category: RaceCategory?
    fun withCategory(category: RaceCategory): SportDraft
    fun toDetails(): RaceDetails?
    fun validationErrors(): Map<String, String> = emptyMap()
}

internal fun SportType.emptyDraft(): SportDraft = when (this) {
    SportType.ROAD_RUNNING -> RoadRunningDraft()
    SportType.TRIATHLON -> TriathlonDraft()
    SportType.TRAIL_RUNNING -> TrailRunningDraft()
}

internal fun RaceDetails.toDraft(): SportDraft = when (this) {
    is RoadRunningDetails -> RoadRunningDraft(category, caaRaceLevel, worldAthleticsLabel)
    is TriathlonDetails -> TriathlonDraft(category)
    is TrailRunningDetails -> TrailRunningDraft(
        BigDecimal.valueOf(distanceKm).stripTrailingZeros().toPlainString(),
        elevationGainMeters?.toString().orEmpty(),
    )
}
