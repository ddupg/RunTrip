package com.ddupg.runtrip.data.model

import com.ddupg.runtrip.data.source.DataSourceId
import java.time.LocalDate

enum class TrainingSport { RUNNING, CYCLING, SWIMMING }

enum class TrainingSubtype(val sport: TrainingSport) {
    OUTDOOR_RUN(TrainingSport.RUNNING), INDOOR_RUN(TrainingSport.RUNNING),
    TRAIL_RUN(TrainingSport.RUNNING), TRACK_RUN(TrainingSport.RUNNING),
    OUTDOOR_RIDE(TrainingSport.CYCLING), INDOOR_RIDE(TrainingSport.CYCLING),
    E_BIKE(TrainingSport.CYCLING), GRAVEL_RIDE(TrainingSport.CYCLING),
    MOUNTAIN_BIKE(TrainingSport.CYCLING), E_MOUNTAIN_BIKE(TrainingSport.CYCLING),
    HELMET_RIDE(TrainingSport.CYCLING),
    POOL_SWIM(TrainingSport.SWIMMING), OPEN_WATER_SWIM(TrainingSport.SWIMMING),
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
