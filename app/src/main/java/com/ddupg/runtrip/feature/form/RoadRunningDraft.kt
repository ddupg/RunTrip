package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.WorldAthleticsLabel

data class RoadRunningDraft(
    override val category: RoadRunningCategory? = null,
    val caaRaceLevel: CaaRaceLevel? = null,
    val worldAthleticsLabel: WorldAthleticsLabel? = null,
) : SportDraft {
    override val sportType: SportType get() = SportType.ROAD_RUNNING
    override fun withCategory(category: RaceCategory): RoadRunningDraft {
        require(category is RoadRunningCategory)
        return copy(category = category)
    }

    override fun toDetails(): RoadRunningDetails? = category?.let {
        RoadRunningDetails(it, caaRaceLevel, worldAthleticsLabel)
    }
}
