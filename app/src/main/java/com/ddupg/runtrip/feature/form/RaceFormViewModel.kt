package com.ddupg.runtrip.feature.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RaceFormViewModel(
    private val repository: RaceRepository,
    private val raceId: String?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RaceFormUiState(isLoading = raceId != null),
    )
    val uiState: StateFlow<RaceFormUiState> = _uiState.asStateFlow()

    private val _savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvents: SharedFlow<Unit> = _savedEvents.asSharedFlow()

    init {
        if (raceId != null) {
            viewModelScope.launch {
                val race = repository.observeRace(raceId).first()
                _uiState.value = if (race == null) {
                    RaceFormUiState(
                        isLoading = false,
                        loadError = "没有找到这条比赛记录",
                    )
                } else {
                    race.toFormState()
                }
            }
        }
    }

    fun updateName(value: String) = updateState {
        copy(name = value, errors = errors.copy(name = null), saveError = null)
    }

    fun updateCity(value: String) = updateState {
        copy(city = value, errors = errors.copy(city = null), saveError = null)
    }

    fun updateRaceDate(value: LocalDate) = updateState { copy(raceDate = value, saveError = null) }

    fun updateCategory(value: RaceCategory) = updateState { copy(category = value, saveError = null) }

    fun updateStatus(value: RaceStatus) = updateState { copy(status = value, saveError = null) }

    fun updateTravelDistance(value: String) = updateState {
        copy(
            travelDistance = value,
            errors = errors.copy(travelDistance = null),
            saveError = null,
        )
    }

    fun updateHotelBookingStatus(value: HotelBookingStatus) = updateState {
        copy(hotelBookingStatus = value, saveError = null)
    }

    fun updateHotelName(value: String) = updateState { copy(hotelName = value, saveError = null) }

    fun updateBookingPlatform(value: String) = updateState {
        copy(bookingPlatform = value, saveError = null)
    }

    fun updateHotelPrice(value: String) = updateState {
        copy(
            hotelPrice = value,
            errors = errors.copy(hotelPrice = null),
            saveError = null,
        )
    }

    fun updateHotelNotes(value: String) = updateState { copy(hotelNotes = value, saveError = null) }

    fun updateRaceNotes(value: String) = updateState { copy(raceNotes = value, saveError = null) }

    fun save() {
        val validation = validateRaceForm(_uiState.value)
        if (validation.input == null) {
            _uiState.update { it.copy(errors = validation.errors) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            try {
                if (raceId == null) {
                    repository.create(validation.input)
                } else {
                    repository.update(raceId, validation.input)
                }
                _savedEvents.emit(Unit)
            } catch (_: RuntimeException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "保存失败，请重试",
                    )
                }
            }
        }
    }

    private inline fun updateState(transform: RaceFormUiState.() -> RaceFormUiState) {
        _uiState.update { state -> state.transform() }
    }

    class Factory(
        private val repository: RaceRepository,
        private val raceId: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RaceFormViewModel::class.java))
            return RaceFormViewModel(repository, raceId) as T
        }
    }
}

private fun Race.toFormState(): RaceFormUiState = RaceFormUiState(
    name = name,
    city = city,
    raceDate = raceDate,
    category = category,
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

private fun Double.toPlainString(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
