package com.ddupg.runtrip.data.model

import java.time.LocalDate

data class Race(
    val id: String,
    val name: String,
    val city: String,
    val raceDate: LocalDate,
    val category: RaceCategory,
    val status: RaceStatus,
    val travelDistanceKm: Double?,
    val hotelBookingStatus: HotelBookingStatus,
    val hotelName: String?,
    val bookingPlatform: String?,
    val hotelTotalPriceCents: Long?,
    val hotelNotes: String?,
    val raceNotes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val recordVersion: Int,
)

data class RaceInput(
    val name: String,
    val city: String,
    val raceDate: LocalDate,
    val category: RaceCategory,
    val status: RaceStatus,
    val travelDistanceKm: Double? = null,
    val hotelBookingStatus: HotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    val hotelName: String? = null,
    val bookingPlatform: String? = null,
    val hotelTotalPriceCents: Long? = null,
    val hotelNotes: String? = null,
    val raceNotes: String? = null,
) {
    fun validatedAndNormalized(): RaceInput {
        require(name.isNotBlank()) { "Race name is required" }
        require(city.isNotBlank()) { "Race city is required" }
        require(travelDistanceKm == null || travelDistanceKm >= 0) {
            "Travel distance cannot be negative"
        }
        require(hotelTotalPriceCents == null || hotelTotalPriceCents >= 0) {
            "Hotel price cannot be negative"
        }

        return copy(
            name = name.trim(),
            city = city.trim(),
            hotelName = hotelName.normalizedOptionalText(),
            bookingPlatform = bookingPlatform.normalizedOptionalText(),
            hotelNotes = hotelNotes.normalizedOptionalText(),
            raceNotes = raceNotes.normalizedOptionalText(),
        )
    }
}

private fun String?.normalizedOptionalText(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
