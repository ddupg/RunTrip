package com.ddupg.runtrip.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.RoadRunningDetails
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.data.model.TriathlonCategory
import com.ddupg.runtrip.data.model.TriathlonDetails
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.model.TrailRunningCategory
import com.ddupg.runtrip.data.model.TrailRunningDetails
import java.time.LocalDate

@Entity(tableName = "races")
data class RaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val raceDate: String,
    val sportTypeCode: String,
    val statusCode: String,
    val travelDistanceKm: Double?,
    val hotelBookingStatusCode: String,
    val hotelName: String?,
    val bookingPlatform: String?,
    val hotelTotalPriceCents: Long?,
    val hotelNotes: String?,
    val raceNotes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val recordVersion: Int,
)

fun RaceRecord.toDomain(): Race = with(race) {
    Race(
        id = id,
        name = name,
        city = city,
        raceDate = LocalDate.parse(raceDate),
        details = when (SportType.fromCode(sportTypeCode)) {
            SportType.ROAD_RUNNING -> {
                check(triathlon == null && trailRunning == null)
                requireNotNull(roadRunning).let {
                    RoadRunningDetails(
                        category = RoadRunningCategory.fromCode(it.categoryCode),
                        caaRaceLevel = it.caaRaceLevelCode?.let(CaaRaceLevel::fromCode),
                        worldAthleticsLabel = it.worldAthleticsLabelCode?.let(WorldAthleticsLabel::fromCode),
                    )
                }
            }
            SportType.TRIATHLON -> {
                check(roadRunning == null && trailRunning == null)
                TriathlonDetails(TriathlonCategory.fromCode(requireNotNull(triathlon).categoryCode))
            }
            SportType.TRAIL_RUNNING -> {
                check(roadRunning == null && triathlon == null)
                requireNotNull(trailRunning).let {
                    TrailRunningCategory.fromCode(it.categoryCode)
                    TrailRunningDetails(it.distanceKm, it.elevationGainMeters)
                }
            }
        },
        status = RaceStatus.fromCode(statusCode),
        travelDistanceKm = travelDistanceKm,
        hotelBookingStatus = HotelBookingStatus.fromCode(hotelBookingStatusCode),
        hotelName = hotelName,
        bookingPlatform = bookingPlatform,
        hotelTotalPriceCents = hotelTotalPriceCents,
        hotelNotes = hotelNotes,
        raceNotes = raceNotes,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        recordVersion = recordVersion,
    )
}

fun Race.toEntity(): RaceRecord = RaceRecord(
    race = RaceEntity(
        id = id,
        name = name,
        city = city,
        raceDate = raceDate.toString(),
        sportTypeCode = details.sportType.code,
        statusCode = status.code,
        travelDistanceKm = travelDistanceKm,
        hotelBookingStatusCode = hotelBookingStatus.code,
        hotelName = hotelName,
        bookingPlatform = bookingPlatform,
        hotelTotalPriceCents = hotelTotalPriceCents,
        hotelNotes = hotelNotes,
        raceNotes = raceNotes,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        recordVersion = recordVersion,
    ),
    roadRunning = (details as? RoadRunningDetails)?.let {
        RoadRunningEntity(id, it.category.code, it.caaRaceLevel?.code, it.worldAthleticsLabel?.code)
    },
    triathlon = (details as? TriathlonDetails)?.let { TriathlonEntity(id, it.category.code) },
    trailRunning = (details as? TrailRunningDetails)?.let {
        TrailRunningEntity(id, it.category.code, it.distanceKm, it.elevationGainMeters)
    },
)

data class RaceRecord(
    @Embedded val race: RaceEntity,
    @Relation(parentColumn = "id", entityColumn = "raceId") val roadRunning: RoadRunningEntity?,
    @Relation(parentColumn = "id", entityColumn = "raceId") val triathlon: TriathlonEntity?,
    @Relation(parentColumn = "id", entityColumn = "raceId") val trailRunning: TrailRunningEntity?,
)
