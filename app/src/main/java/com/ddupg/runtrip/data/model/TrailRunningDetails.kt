package com.ddupg.runtrip.data.model

enum class TrailRunningCategory(override val code: String) : RaceCategory {
    CUSTOM("CUSTOM");
    override val sportType: SportType get() = SportType.TRAIL_RUNNING

    companion object {
        fun fromCode(code: String): TrailRunningCategory = entries.first { it.code == code }
    }
}

data class TrailRunningDetails(
    val distanceKm: Double,
    val elevationGainMeters: Int? = null,
) : RaceDetails {
    override val category: TrailRunningCategory get() = TrailRunningCategory.CUSTOM

    init {
        require(distanceKm.isFinite() && distanceKm > 0) { "Trail distance must be finite and positive" }
        require(elevationGainMeters == null || elevationGainMeters >= 0) { "Elevation gain cannot be negative" }
    }
}
