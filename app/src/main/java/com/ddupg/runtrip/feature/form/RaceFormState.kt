package com.ddupg.runtrip.feature.form

import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceInput
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.RoadRunningCategory
import com.ddupg.runtrip.data.model.SportType
import java.math.BigDecimal
import java.time.LocalDate

data class RaceFormErrors(
    val category: String? = null,
    val sportFields: Map<String, String> = emptyMap(),
    val name: String? = null,
    val city: String? = null,
    val travelDistance: String? = null,
    val hotelPrice: String? = null,
) {
    val hasErrors: Boolean
        get() = sportFields.isNotEmpty() || listOf(category, name, city, travelDistance, hotelPrice).any { it != null }
}

data class RaceDraft(
    val name: String = "",
    val city: String = "",
    val raceDate: LocalDate = LocalDate.now(),
    val sportType: SportType = SportType.ROAD_RUNNING,
    val sportDrafts: Map<SportType, SportDraft> = mapOf(
        SportType.ROAD_RUNNING to RoadRunningDraft(RoadRunningCategory.HALF_MARATHON),
    ),
    val status: RaceStatus = RaceStatus.WATCHING,
    val travelDistance: String = "",
    val hotelBookingStatus: HotelBookingStatus = HotelBookingStatus.NOT_BOOKED,
    val hotelName: String = "",
    val bookingPlatform: String = "",
    val hotelPrice: String = "",
    val hotelNotes: String = "",
    val raceNotes: String = "",
) {
    val activeSportDraft: SportDraft get() = sportDrafts[sportType] ?: sportType.emptyDraft()

    fun withSportDraft(value: SportDraft): RaceDraft {
        require(value.sportType == sportType)
        return copy(sportDrafts = sportDrafts + (sportType to value))
    }
}

data class RaceFormUiState(
    val draft: RaceDraft = RaceDraft(),
    val errors: RaceFormErrors = RaceFormErrors(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveComplete: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
)

internal data class RaceFormValidationResult(
    val input: RaceInput?,
    val errors: RaceFormErrors,
)

internal fun validateRaceForm(draft: RaceDraft): RaceFormValidationResult {
    val nameError = if (draft.name.isBlank()) "请输入比赛名称" else null
    val cityError = if (draft.city.isBlank()) "请输入城市" else null

    val distanceText = draft.travelDistance.trim()
    val distance = distanceText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val distanceError = when {
        distanceText.isEmpty() -> null
        distance == null || !distance.isFinite() -> "请输入有效的公里数"
        distance < 0 -> "路程距离不能小于 0"
        else -> null
    }

    val priceText = draft.hotelPrice.trim()
    val priceCents = priceText.takeIf { it.isNotEmpty() }?.toPriceCentsOrNull()
    val priceError = when {
        priceText.isEmpty() -> null
        priceCents == null -> "请输入最多两位小数的有效金额"
        priceCents < 0 -> "酒店总价不能小于 0"
        else -> null
    }

    val details = draft.activeSportDraft.toDetails()
    val errors = RaceFormErrors(
        category = if (draft.activeSportDraft.category == null) "请选择比赛项目" else null,
        sportFields = draft.activeSportDraft.validationErrors(),
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
            details = requireNotNull(details),
            name = draft.name,
            city = draft.city,
            raceDate = draft.raceDate,
            status = draft.status,
            travelDistanceKm = distance,
            hotelBookingStatus = draft.hotelBookingStatus,
            hotelName = draft.hotelName,
            bookingPlatform = draft.bookingPlatform,
            hotelTotalPriceCents = priceCents,
            hotelNotes = draft.hotelNotes,
            raceNotes = draft.raceNotes,
        ),
        errors = errors,
    )
}

internal fun RaceFormUiState.withDraft(updatedDraft: RaceDraft): RaceFormUiState = copy(
    draft = updatedDraft,
    errors = errors.clearedForChanges(draft, updatedDraft),
    saveError = null,
)

internal fun Race.toDraft(): RaceDraft = RaceDraft(
    name = name,
    city = city,
    raceDate = raceDate,
    sportType = details.sportType,
    sportDrafts = mapOf(details.sportType to details.toDraft()),
    status = status,
    travelDistance = travelDistanceKm?.toPlainString().orEmpty(),
    hotelBookingStatus = hotelBookingStatus,
    hotelName = hotelName.orEmpty(),
    bookingPlatform = bookingPlatform.orEmpty(),
    hotelPrice = hotelTotalPriceCents?.let {
        BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString()
    }.orEmpty(),
    hotelNotes = hotelNotes.orEmpty(),
    raceNotes = raceNotes.orEmpty(),
)

private fun RaceFormErrors.clearedForChanges(
    previousDraft: RaceDraft,
    updatedDraft: RaceDraft,
): RaceFormErrors = copy(
    sportFields = if (previousDraft.sportType != updatedDraft.sportType) emptyMap() else
        updatedDraft.activeSportDraft.validationErrors().filterKeys { it in sportFields },
    category = category.takeIf {
        previousDraft.sportType == updatedDraft.sportType &&
            previousDraft.activeSportDraft.category == updatedDraft.activeSportDraft.category
    },
    name = name.takeIf { previousDraft.name == updatedDraft.name },
    city = city.takeIf { previousDraft.city == updatedDraft.city },
    travelDistance = travelDistance.takeIf {
        previousDraft.travelDistance == updatedDraft.travelDistance
    },
    hotelPrice = hotelPrice.takeIf { previousDraft.hotelPrice == updatedDraft.hotelPrice },
)

private fun String.toPriceCentsOrNull(): Long? = try {
    BigDecimal(this)
        .movePointRight(2)
        .longValueExact()
} catch (_: ArithmeticException) {
    null
} catch (_: NumberFormatException) {
    null
}

private fun Double.toPlainString(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
