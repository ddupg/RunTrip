package com.ddupg.runtrip.data.model

import com.ddupg.runtrip.data.source.DataSourceId
import java.time.LocalDate

enum class TrainingSport { RUNNING, CYCLING, SWIMMING }

enum class TrainingSubtype(val sport: TrainingSport, val code: String) {
    OUTDOOR_RUN(TrainingSport.RUNNING, "OUTDOOR_RUN"), INDOOR_RUN(TrainingSport.RUNNING, "INDOOR_RUN"),
    TRAIL_RUN(TrainingSport.RUNNING, "TRAIL_RUN"), TRACK_RUN(TrainingSport.RUNNING, "TRACK_RUN"),
    OUTDOOR_RIDE(TrainingSport.CYCLING, "OUTDOOR_RIDE"), INDOOR_RIDE(TrainingSport.CYCLING, "INDOOR_RIDE"),
    E_BIKE(TrainingSport.CYCLING, "E_BIKE"), GRAVEL_RIDE(TrainingSport.CYCLING, "GRAVEL_RIDE"),
    MOUNTAIN_BIKE(TrainingSport.CYCLING, "MOUNTAIN_BIKE"), E_MOUNTAIN_BIKE(TrainingSport.CYCLING, "E_MOUNTAIN_BIKE"),
    HELMET_RIDE(TrainingSport.CYCLING, "HELMET_RIDE"),
    POOL_SWIM(TrainingSport.SWIMMING, "POOL_SWIM"), OPEN_WATER_SWIM(TrainingSport.SWIMMING, "OPEN_WATER_SWIM");

    companion object {
        fun fromCode(code: String): TrainingSubtype = entries.first { it.code == code }
    }
}

data class TrainingAccount(val source: DataSourceId, val accountId: String)

data class TrainingActivity(
    val id: String,
    val subtype: TrainingSubtype,
    val sourceDate: LocalDate,
    val startEpochSeconds: Long?,
    val distanceMeters: Double?,
    val durationSeconds: Long,
)
