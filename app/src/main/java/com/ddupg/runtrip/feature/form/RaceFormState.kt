package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import java.math.BigDecimal
import java.time.LocalDate

data class RaceFormErrors(
    val name: String? = null,
    val city: String? = null,
    val travelDistance: String? = null,
    val hotelPrice: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, city, travelDistance, hotelPrice).any { it != null }
}

data class RaceFormUiState(
    val name: String = "",
    val city: String = "",
    val raceDate: LocalDate = LocalDate.now(),
    val category: RaceCategory = RaceCategory.MARATHON,
    val status: RaceStatus = RaceStatus.WATCHING,
    val travelDistance: String = "",
    val hotelBookingStatus: HotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    val hotelName: String = "",
    val bookingPlatform: String = "",
    val hotelPrice: String = "",
    val hotelNotes: String = "",
    val raceNotes: String = "",
    val errors: RaceFormErrors = RaceFormErrors(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
)

data class RaceFormValidationResult(
    val input: RaceInput?,
    val errors: RaceFormErrors,
)

fun validateRaceForm(state: RaceFormUiState): RaceFormValidationResult {
    val nameError = if (state.name.isBlank()) "请输入比赛名称" else null
    val cityError = if (state.city.isBlank()) "请输入城市" else null

    val distanceText = state.travelDistance.trim()
    val distance = distanceText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val distanceError = when {
        distanceText.isEmpty() -> null
        distance == null || !distance.isFinite() -> "请输入有效的公里数"
        distance < 0 -> "路程距离不能小于 0"
        else -> null
    }

    val priceText = state.hotelPrice.trim()
    val priceCents = priceText.takeIf { it.isNotEmpty() }?.toPriceCentsOrNull()
    val priceError = when {
        priceText.isEmpty() -> null
        priceCents == null -> "请输入最多两位小数的有效金额"
        priceCents < 0 -> "酒店总价不能小于 0"
        else -> null
    }

    val errors = RaceFormErrors(
        name = nameError,
        city = cityError,
        travelDistance = distanceError,
        hotelPrice = priceError,
    )
    if (errors.hasErrors) {
        return RaceFormValidationResult(input = null, errors = errors)
    }

    return RaceFormValidationResult(
        input = RaceInput(
            name = state.name,
            city = state.city,
            raceDate = state.raceDate,
            category = state.category,
            status = state.status,
            travelDistanceKm = distance,
            hotelBookingStatus = state.hotelBookingStatus,
            hotelName = state.hotelName,
            bookingPlatform = state.bookingPlatform,
            hotelTotalPriceCents = priceCents,
            hotelNotes = state.hotelNotes,
            raceNotes = state.raceNotes,
        ),
        errors = errors,
    )
}

private fun String.toPriceCentsOrNull(): Long? = try {
    BigDecimal(this)
        .movePointRight(2)
        .longValueExact()
} catch (_: ArithmeticException) {
    null
} catch (_: NumberFormatException) {
    null
}
