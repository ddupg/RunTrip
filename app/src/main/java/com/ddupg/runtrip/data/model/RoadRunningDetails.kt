package com.ddupg.runtrip.data.model

enum class RoadRunningCategory(
    override val code: String,
) : RaceCategory {
    MARATHON("MARATHON"),
    HALF_MARATHON("HALF_MARATHON"),
    TEN_K("TEN_K"),
    OTHER("OTHER"),
    ;

    override val sportType: SportType get() = SportType.ROAD_RUNNING

    companion object {
        fun fromCode(code: String): RoadRunningCategory =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown race category code: $code")
    }
}

data class RoadRunningDetails(
    override val category: RoadRunningCategory,
    val caaRaceLevel: CaaRaceLevel? = null,
    val worldAthleticsLabel: WorldAthleticsLabel? = null,
) : RaceDetails
