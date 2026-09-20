package com.ddupg.runtrip.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "road_running_details",
    foreignKeys = [ForeignKey(
        entity = RaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["raceId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class RoadRunningEntity(
    @PrimaryKey val raceId: String,
    val categoryCode: String,
    val caaRaceLevelCode: String?,
    val worldAthleticsLabelCode: String?,
)
