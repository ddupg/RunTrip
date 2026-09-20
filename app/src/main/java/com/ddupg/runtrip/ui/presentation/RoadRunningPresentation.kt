package com.ddupg.runtrip.ui.presentation

import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails

internal object RoadRunningPresentation {
    fun category(category: RoadRunningCategory, density: RaceLabelDensity): String = when (category) {
        RoadRunningCategory.MARATHON -> if (density == RaceLabelDensity.FULL) "全程马拉松" else "全马"
        RoadRunningCategory.HALF_MARATHON -> if (density == RaceLabelDensity.FULL) "半程马拉松" else "半马"
        RoadRunningCategory.TEN_K -> "10 公里"
        RoadRunningCategory.OTHER -> "其他"
    }

    fun fields(details: RoadRunningDetails, density: RaceLabelDensity): List<SportDetailField> = buildList {
        details.caaRaceLevel?.let { add(SportDetailField("中国田协", RacePresentation.caaRaceLevel(it).text, emphasized = true)) }
        details.worldAthleticsLabel?.let {
            add(SportDetailField("世界田联", RacePresentation.worldAthleticsLabel(it, density).text))
        }
    }
}
