package com.ddupg.runtrip.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import java.time.LocalDate

@Entity(tableName = "races")
data class RaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val raceDate: String,
    val categoryCode: String,
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

fun RaceEntity.toDomain(): Race = Race(
    id = id,
    name = name,
    city = city,
    raceDate = LocalDate.parse(raceDate),
    category = RaceCategory.fromCode(categoryCode),
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

fun Race.toEntity(): RaceEntity = RaceEntity(
    id = id,
    name = name,
    city = city,
    raceDate = raceDate.toString(),
    categoryCode = category.code,
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
)
