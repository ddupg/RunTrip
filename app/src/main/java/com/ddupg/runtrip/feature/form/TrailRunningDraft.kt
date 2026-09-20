package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TrailRunningCategory
import com.ddupg.runtrip.data.model.TrailRunningDetails

data class TrailRunningDraft(
    val distance: String = "",
    val elevationGain: String = "",
) : SportDraft {
    override val sportType: SportType get() = SportType.TRAIL_RUNNING
    override val category: TrailRunningCategory get() = TrailRunningCategory.CUSTOM

    override fun withCategory(category: RaceCategory): TrailRunningDraft {
        require(category == TrailRunningCategory.CUSTOM)
        return this
    }

    override fun validationErrors(): Map<String, String> = buildMap {
        val distanceValue = distance.trim().toDoubleOrNull()
        if (distanceValue == null || !distanceValue.isFinite() || distanceValue <= 0) {
            put(DISTANCE, "请输入大于 0 的有效公里数")
        }
        val elevationText = elevationGain.trim()
        if (elevationText.isNotEmpty()) {
            val value = elevationText.toIntOrNull()
            if (value == null || value < 0) put(ELEVATION_GAIN, "请输入不小于 0 的整数米数")
        }
    }

    override fun toDetails(): TrailRunningDetails? =
        if (validationErrors().isEmpty()) TrailRunningDetails(
            distanceKm = distance.trim().toDouble(),
            elevationGainMeters = elevationGain.trim().takeIf { it.isNotEmpty() }?.toInt(),
        ) else null

    companion object {
        const val DISTANCE = "distance"
        const val ELEVATION_GAIN = "elevationGain"
    }
}
