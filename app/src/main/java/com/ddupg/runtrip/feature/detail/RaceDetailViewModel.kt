package com.ddupg.runtrip.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.repository.RaceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RaceDetailUiState(
    val race: Race? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)

class RaceDetailViewModel(
    private val repository: RaceRepository,
    private val raceId: String,
) : ViewModel() {
    private val operationState = MutableStateFlow(RaceDetailUiState())

    val uiState: StateFlow<RaceDetailUiState> = combine(
        repository.observeRace(raceId),
        operationState,
    ) { race, operations ->
        operations.copy(
            race = race,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RaceDetailUiState(),
    )

    private val _deletedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deletedEvents: SharedFlow<Unit> = _deletedEvents.asSharedFlow()

    fun deleteRace() {
        viewModelScope.launch {
            operationState.update { it.copy(isDeleting = true, deleteError = null) }
            try {
                repository.delete(raceId)
                _deletedEvents.emit(Unit)
            } catch (_: RuntimeException) {
                operationState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = "删除失败，请重试",
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: RaceRepository,
        private val raceId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RaceDetailViewModel::class.java))
            return RaceDetailViewModel(repository, raceId) as T
        }
    }
}
